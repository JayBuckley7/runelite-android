#include <EGL/egl.h>
#include <GLES3/gl32.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

// Offscreen scene-varying regression test; never starts or changes the app.
// Usage: hd_triangle_probe vertex.glsl fragment.glsl [base|blend|inspect|constant:R,G,B] [output.rgba]
// base expects the fixture's identical HSL color; blend expects raw fTexBlend RGB.
// The optional output is 16x16 RGBA8, bottom row first, with no file header.
enum { WIDTH = 16, HEIGHT = 16 };
static int gl_errors;

static void check_gl(const char *stage) {
    GLenum error;
    while ((error = glGetError()) != GL_NO_ERROR) {
        fprintf(stderr, "gl_error stage=%s code=0x%x\n", stage, error);
        ++gl_errors;
    }
}

static GLuint compile_shader(GLenum type, const char *path) {
    FILE *file = fopen(path, "rb");
    if (!file) { perror(path); return 0; }
    if (fseek(file, 0, SEEK_END) != 0) { fclose(file); return 0; }
    long length = ftell(file);
    if (length < 0 || fseek(file, 0, SEEK_SET) != 0) { fclose(file); return 0; }
    char *source = malloc((size_t)length + 1);
    if (!source) { fclose(file); return 0; }
    if (fread(source, 1, (size_t)length, file) != (size_t)length) {
        free(source); fclose(file); return 0;
    }
    fclose(file);
    source[length] = 0;
    GLuint shader = glCreateShader(type);
    const char *sources[] = { source };
    glShaderSource(shader, 1, sources, NULL);
    glCompileShader(shader);
    free(source);
    GLint ok = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[16384];
        glGetShaderInfoLog(shader, sizeof(log), NULL, log);
        fprintf(stderr, "shader_compile=FAIL file=%s\n%s\n", path, log);
        glDeleteShader(shader);
        return 0;
    }
    printf("shader_compile=PASS file=%s\n", path);
    check_gl("compile_shader");
    return shader;
}

static int member_is(const char *name, const char *member) {
    const char *tail = strrchr(name, '.');
    return strcmp(tail ? tail + 1 : name, member) == 0;
}

static int material_ids[3];

static int initialize_uniforms(GLuint program, int material_mode) {
    GLint count = 0, binding_limit = 0;
    glGetProgramiv(program, GL_ACTIVE_UNIFORM_BLOCKS, &count);
    glGetIntegerv(GL_MAX_UNIFORM_BUFFER_BINDINGS, &binding_limit);
    if (count > binding_limit) {
        fprintf(stderr, "uniform_bindings=FAIL blocks=%d limit=%d\n", count, binding_limit);
        return 0;
    }
    for (GLint bi = 0; bi < count; ++bi) {
        GLint bytes = 0, members = 0;
        char block[256];
        glGetActiveUniformBlockName(program, (GLuint)bi, sizeof(block), NULL, block);
        glGetActiveUniformBlockiv(program, (GLuint)bi, GL_UNIFORM_BLOCK_DATA_SIZE, &bytes);
        glGetActiveUniformBlockiv(program, (GLuint)bi, GL_UNIFORM_BLOCK_ACTIVE_UNIFORMS, &members);
        if (bytes <= 0 || members <= 0) return 0;
        GLint *indices = calloc((size_t)members, sizeof(GLint));
        unsigned char *data = calloc(1, (size_t)bytes);
        if (!indices || !data) { free(indices); free(data); return 0; }
        glGetActiveUniformBlockiv(program, (GLuint)bi, GL_UNIFORM_BLOCK_ACTIVE_UNIFORM_INDICES, indices);
        printf("uniform_block=%s bytes=%d members=%d binding=%d\n", block, bytes, members, bi);
        for (int i = 0; i < members; ++i) {
            GLuint index = (GLuint)indices[i];
            GLint offset = 0, length = 0, matrix_stride = 0, row_major = 0;
            GLenum type = 0;
            char name[512];
            glGetActiveUniform(program, index, sizeof(name), NULL, &length, &type, name);
            glGetActiveUniformsiv(program, 1, &index, GL_UNIFORM_OFFSET, &offset);
            glGetActiveUniformsiv(program, 1, &index, GL_UNIFORM_MATRIX_STRIDE, &matrix_stride);
            glGetActiveUniformsiv(program, 1, &index, GL_UNIFORM_IS_ROW_MAJOR, &row_major);
            float values[16] = { 0 };
            int32_t integer = 0;
            size_t write_bytes = 0;
            int write_integer = 0;
            if (strcmp(block, "UBOGlobal") == 0) {
                if (member_is(name, "cameraPos")) { values[1] = -4; write_bytes = 3 * sizeof(float); }
                if (member_is(name, "orthographicProjection")) { integer = 1; write_integer = 1; write_bytes = sizeof(integer); }
                if (member_is(name, "drawDistance")) { values[0] = 100; write_bytes = sizeof(float); }
                if (member_is(name, "projectionMatrix")) {
                    if (type != GL_FLOAT_MAT4 || matrix_stride != 16 || row_major) {
                        fprintf(stderr, "projection_layout=FAIL type=%x stride=%d row_major=%d\n", type, matrix_stride, row_major);
                        free(indices); free(data); return 0;
                    }
                    // world X/Z become clip X/Y; all six vertices have world Y = 0 and clip W = 1.
                    values[0] = values[9] = values[6] = values[15] = 1;
                    write_bytes = sizeof(values);
                }
            }
            if (strcmp(block, "UBOMaterials") == 0) {
                if (member_is(name, "displacementScale")) { values[0] = .1f; write_bytes = sizeof(float); }
                if (member_is(name, "textureScale")) { values[0] = values[1] = values[2] = 1; write_bytes = 3 * sizeof(float); }
            }
            if (write_bytes) {
                if (offset < 0 || (size_t)offset + write_bytes > (size_t)bytes) {
                    fprintf(stderr, "uniform_write=FAIL name=%s offset=%d bytes=%zu block_bytes=%d\n", name, offset, write_bytes, bytes);
                    free(indices); free(data); return 0;
                }
                memcpy(data + offset, write_integer ? (const void *)&integer : (const void *)values, write_bytes);
                if (strcmp(block, "UBOGlobal") == 0 || strstr(name, "Array[0]."))
                    printf("uniform_initialized=%s offset=%d size=%zu\n", name, offset, write_bytes);
            }
        }
        if (material_mode && strcmp(block, "UBOMaterials") == 0) {
            // std140 material stride is 96 bytes; the final vec3 ends at byte 92.
            int count = (bytes + 4) / 96;
            if (count < 3) { free(indices); free(data); return 0; }
            material_ids[0] = 1; material_ids[1] = count / 2; material_ids[2] = count - 1;
            for (int m = 0; m * 96 + 92 <= bytes; ++m) {
                int32_t *q = (int32_t *)(data + m * 96);
                float *f = (float *)q;
                q[0] = m == material_ids[1] ? 1 : m == material_ids[2] ? 2 : 0;
                q[1] = q[2] = q[3] = q[4] = q[6] = -1; q[5] = 0; q[7] = 4;
                f[9] = 1; f[20] = f[21] = f[22] = 1;
            }
        }
        GLuint buffer;
        glGenBuffers(1, &buffer);
        glBindBuffer(GL_UNIFORM_BUFFER, buffer);
        glBufferData(GL_UNIFORM_BUFFER, bytes, data, GL_STATIC_DRAW);
        glUniformBlockBinding(program, (GLuint)bi, (GLuint)bi);
        glBindBufferBase(GL_UNIFORM_BUFFER, (GLuint)bi, buffer);
        free(indices); free(data);
        check_gl("uniform_block_upload");
    }
    return gl_errors == 0;
}

static int render(GLuint program, const char *mode, const char *output_path) {
    int material_mode = strcmp(mode, "material") == 0;
    if (!initialize_uniforms(program, material_mode)) return 0;
    GLuint vao, position_buffer, uv_buffer, face_buffer, face_texture, texture;
    const float positions[] = { -1,0,-1, 1,0,-1, -1,0,1, -1,0,1, 1,0,-1, 1,0,1 };
    const float uvs[] = { 0,0,0,0, 1,0,0,0, 0,1,0,0, 0,1,0,0, 1,0,0,0, 1,1,0,0 };
    glGenVertexArrays(1, &vao); glBindVertexArray(vao);
    glGenBuffers(1, &position_buffer); glBindBuffer(GL_ARRAY_BUFFER, position_buffer);
    glBufferData(GL_ARRAY_BUFFER, sizeof(positions), positions, GL_STATIC_DRAW);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 0, NULL); glEnableVertexAttribArray(0);
    glGenBuffers(1, &uv_buffer); glBindBuffer(GL_ARRAY_BUFFER, uv_buffer);
    glBufferData(GL_ARRAY_BUFFER, sizeof(uvs), uvs, GL_STATIC_DRAW);
    glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE, 0, NULL); glEnableVertexAttribArray(1);
    glVertexAttrib4f(2, 0,-1,0,0);
    glVertexAttribI4i(3, 0,0,0,0);
    glVertexAttribI4i(6, -1,0,0,0);
    glVertexAttribI4i(7, 0,0,0,0);
    const int hsl = (20 << 10) | (4 << 7) | 64;
    int faces[] = { hsl,hsl,hsl, 0,0,0, 1,1,1 };
    if (material_mode) for (int i = 0; i < 3; ++i) faces[3 + i] = material_ids[i] << 21;
    glGenBuffers(1, &face_buffer); glBindBuffer(GL_SHADER_STORAGE_BUFFER, face_buffer);
    glBufferData(GL_SHADER_STORAGE_BUFFER, sizeof(faces), faces, GL_STATIC_DRAW);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, face_buffer);
    // Expanded shaders can use either the Android SSBO adaptation or textureFaces.
    GLint face_sampler = glGetUniformLocation(program, "textureFaces");
    if (face_sampler >= 0) {
        glGenTextures(1, &face_texture); glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_BUFFER, face_texture);
        glTexBuffer(GL_TEXTURE_BUFFER, GL_RGB32I, face_buffer);
    }
    check_gl("geometry_and_face_data");

    const unsigned char texels[] = { 100,100,100,255, 180,180,180,255, 180,180,180,255, 100,100,100,255 };
    glGenTextures(1, &texture); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D_ARRAY, texture);
    glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
    glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);
    // Preserve the old fixture's texture format so its baseline can be compared directly.
    // This is not a physical normal-map fixture; production uses sRGB texture decoding.
    const unsigned char material_texels[] = {255,0,0,255, 0,255,0,255, 0,0,255,255};
    glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, material_mode ? 1 : 2, material_mode ? 1 : 2, material_mode ? 3 : 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, material_mode ? material_texels : texels);
    glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
    glUseProgram(program);
    GLint location = glGetUniformLocation(program, "textureArray");
    if (location >= 0) glUniform1i(location, 0);
    if (face_sampler >= 0) glUniform1i(face_sampler, 1);
    check_gl("texture_and_program");

    glViewport(0,0,WIDTH,HEIGHT);
    glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE); glDisable(GL_BLEND);
    glDisable(GL_SCISSOR_TEST); glDisable(GL_STENCIL_TEST); glDisable(GL_DITHER);
    glDisable(GL_RASTERIZER_DISCARD); glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE); glDisable(GL_SAMPLE_COVERAGE);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glClearColor(1,0,1,1); glClear(GL_COLOR_BUFFER_BIT);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    check_gl("draw");
    unsigned char pixels[WIDTH * HEIGHT * 4];
    memset(pixels, 0xcd, sizeof(pixels));
    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadPixels(0,0,WIDTH,HEIGHT,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
    check_gl("readback");

    int expected[3] = {67,199,56};
    int is_blend = strcmp(mode, "blend") == 0 || material_mode;
    int is_constant = strcmp(mode, "base") == 0;
    int is_inspect = strcmp(mode, "inspect") == 0;
    if (strncmp(mode, "constant:", 9) == 0) {
        is_constant = sscanf(mode + 9, "%d,%d,%d", &expected[0], &expected[1], &expected[2]) == 3;
        for (int c = 0; c < 3; ++c) if (expected[c] < 0 || expected[c] > 255) is_constant = 0;
    }
    if (!is_blend && !is_constant && !is_inspect) { fprintf(stderr, "unknown_mode=%s\n", mode); return 0; }
    int lo[4] = {255,255,255,255}, hi[4] = {0,0,0,0};
    int sum_lo = 766, sum_hi = -1, sum_lo_x = -1, sum_lo_y = -1, sum_hi_x = -1, sum_hi_y = -1;
    int bad = 0, coverage_bad = 0, total = 0, max_error = 0, first_bad_x = -1, first_bad_y = -1;
    for (int y = 2; y < HEIGHT - 2; ++y) for (int x = 2; x < WIDTH - 2; ++x) {
        const unsigned char *p = pixels + 4 * (WIDTH * y + x);
        int sum = p[0] + p[1] + p[2];
        if (sum < sum_lo) { sum_lo = sum; sum_lo_x = x; sum_lo_y = y; }
        if (sum > sum_hi) { sum_hi = sum; sum_hi_x = x; sum_hi_y = y; }
        for (int c = 0; c < 4; ++c) { if (p[c] < lo[c]) lo[c] = p[c]; if (p[c] > hi[c]) hi[c] = p[c]; }
        if (p[0] == 255 && p[1] == 0 && p[2] == 255) ++coverage_bad;
        int error = 0;
        if (is_constant) for (int c = 0; c < 3; ++c) { int d = abs((int)p[c] - expected[c]); if (d > error) error = d; }
        if (is_blend) {
            error = abs(sum - 255);
            // Pixel centers on the shared diagonal may be covered by either triangle.
            if (x + y != WIDTH - 1) {
                float u = ((float)x + .5f) / WIDTH, v = ((float)y + .5f) / HEIGHT;
                float weights[3];
                if (u + v < 1) { weights[0] = 1-u-v; weights[1] = u; weights[2] = v; }
                else { weights[0] = 1-u; weights[1] = 1-v; weights[2] = u+v-1; }
                for (int c = 0; c < 3; ++c) { int d = abs((int)p[c] - (int)lroundf(255 * weights[c])); if (d > error) error = d; }
            }
        }
        if (error > max_error) max_error = error;
        if (error > 2) { ++bad; if (first_bad_x < 0) { first_bad_x = x; first_bad_y = y; } }
        ++total;
    }
    const unsigned char *center = pixels + 4 * (WIDTH * 8 + 8);
    printf("rgba_range=%d..%d,%d..%d,%d..%d,%d..%d center=%u,%u,%u,%u\n", lo[0],hi[0],lo[1],hi[1],lo[2],hi[2],lo[3],hi[3],center[0],center[1],center[2],center[3]);
    printf("rgb_sum=%d..%d min_at=%d,%d max_at=%d,%d expected_blend_sum=255\n", sum_lo,sum_hi,sum_lo_x,sum_lo_y,sum_hi_x,sum_hi_y);
    printf("fixture_check mode=%s bad=%d total=%d max_error=%d first_bad=%d,%d uncovered=%d result=%s\n", mode,bad,total,max_error,first_bad_x,first_bad_y,coverage_bad,(bad == 0 && coverage_bad == 0) ? (is_inspect ? "OBSERVED" : "PASS") : "FAIL");
    if (output_path) {
        FILE *file = fopen(output_path, "wb");
        if (!file) { perror(output_path); return 0; }
        size_t written = fwrite(pixels, 1, sizeof(pixels), file);
        int close_result = fclose(file);
        if (written != sizeof(pixels) || close_result != 0) { fprintf(stderr, "rgba_dump=FAIL\n"); return 0; }
        printf("rgba_dump=%s bytes=%zu width=%d height=%d bottom_row_first=1\n", output_path,written,WIDTH,HEIGHT);
    }
    return bad == 0 && coverage_bad == 0 && gl_errors == 0;
}

int main(int argc, char **argv) {
    if (argc < 3 || argc > 5) {
        fprintf(stderr, "Usage: %s vertex.glsl fragment.glsl [base|blend|inspect|constant:R,G,B] [output.rgba]\n", argv[0]);
        return 2;
    }
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major, minor;
    if (!eglInitialize(display, &major, &minor)) { fprintf(stderr, "egl_initialize=FAIL error=%x\n", eglGetError()); return 3; }
    EGLint attributes[] = { EGL_RENDERABLE_TYPE,EGL_OPENGL_ES3_BIT, EGL_SURFACE_TYPE,EGL_PBUFFER_BIT, EGL_RED_SIZE,8, EGL_GREEN_SIZE,8, EGL_BLUE_SIZE,8, EGL_ALPHA_SIZE,8, EGL_SAMPLE_BUFFERS,0, EGL_NONE };
    EGLConfig config;
    EGLint count = 0;
    if (!eglChooseConfig(display,attributes,&config,1,&count) || !count) { fprintf(stderr, "egl_config=FAIL error=%x\n", eglGetError()); return 4; }
    EGLint context_attributes[] = { EGL_CONTEXT_CLIENT_VERSION,3,EGL_NONE };
    EGLint surface_attributes[] = { EGL_WIDTH,WIDTH,EGL_HEIGHT,HEIGHT,EGL_NONE };
    EGLContext context = eglCreateContext(display,config,EGL_NO_CONTEXT,context_attributes);
    EGLSurface surface = eglCreatePbufferSurface(display,config,surface_attributes);
    if (context == EGL_NO_CONTEXT || surface == EGL_NO_SURFACE || !eglMakeCurrent(display,surface,surface,context)) { fprintf(stderr, "egl_context=FAIL error=%x\n", eglGetError()); return 5; }
    printf("renderer=%s\nversion=%s\nshading=%s\n",glGetString(GL_RENDERER),glGetString(GL_VERSION),glGetString(GL_SHADING_LANGUAGE_VERSION));
    GLint samples = 0; glGetIntegerv(GL_SAMPLES, &samples); printf("samples=%d\n", samples);
    GLuint vertex = compile_shader(GL_VERTEX_SHADER,argv[1]), fragment = compile_shader(GL_FRAGMENT_SHADER,argv[2]);
    int passed = 0;
    GLuint program = 0;
    if (vertex && fragment) {
        program = glCreateProgram(); glAttachShader(program,vertex); glAttachShader(program,fragment); glLinkProgram(program);
        GLint linked = GL_FALSE; glGetProgramiv(program,GL_LINK_STATUS,&linked);
        if (!linked) { char log[16384]; glGetProgramInfoLog(program,sizeof(log),NULL,log); fprintf(stderr,"shader_link=FAIL\n%s\n",log); }
        else { printf("shader_link=PASS\n"); check_gl("link"); passed = render(program,argc > 3 ? argv[3] : "base",argc > 4 ? argv[4] : NULL); }
    }
    check_gl("final");
    printf("gl_error_count=%d\n",gl_errors);
    if (program) glDeleteProgram(program);
    if (vertex) glDeleteShader(vertex);
    if (fragment) glDeleteShader(fragment);
    eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
    eglDestroySurface(display,surface); eglDestroyContext(display,context); eglTerminate(display);
    return passed && gl_errors == 0 ? 0 : 15;
}
