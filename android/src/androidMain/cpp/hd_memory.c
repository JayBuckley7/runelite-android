#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <EGL/egl.h>
#include <GLES3/gl32.h>

/* Scene framebuffer through GL_EXT_multisampled_render_to_texture: the multisampled color
 * and depth live only in tile memory and are resolved into a single-sample texture as each
 * tile is written out. The desktop path (multisampled renderbuffers + glBlitFramebuffer
 * resolve) instead stores every sample to memory and reads it back each frame. */
typedef void (*FramebufferTexture2DMultisampleEXT)(GLenum, GLenum, GLenum, GLuint, GLint, GLsizei);
typedef void (*RenderbufferStorageMultisampleEXT)(GLenum, GLsizei, GLenum, GLsizei, GLsizei);

JNIEXPORT jboolean JNICALL Java_net_runelite_mp_hd_GlesExt_createTileResolvedFbo(JNIEnv *env, jclass cls,
    jint width, jint height, jint samples, jint colorFormat, jint depthFormat, jintArray out) {
    (void)cls;
    static FramebufferTexture2DMultisampleEXT fbTex2DMs;
    static RenderbufferStorageMultisampleEXT rbStorageMs;
    if (!fbTex2DMs) fbTex2DMs = (FramebufferTexture2DMultisampleEXT)eglGetProcAddress("glFramebufferTexture2DMultisampleEXT");
    if (!rbStorageMs) rbStorageMs = (RenderbufferStorageMultisampleEXT)eglGetProcAddress("glRenderbufferStorageMultisampleEXT");
    if (!fbTex2DMs || !rbStorageMs) return JNI_FALSE;

    GLint prevTexture = 0;
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &prevTexture);
    while (glGetError() != GL_NO_ERROR) {}

    GLuint fbo = 0, tex = 0, rbo = 0;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);

    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexStorage2D(GL_TEXTURE_2D, 1, (GLenum)colorFormat, width, height);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glBindTexture(GL_TEXTURE_2D, (GLuint)prevTexture);
    fbTex2DMs(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0, samples);

    glGenRenderbuffers(1, &rbo);
    glBindRenderbuffer(GL_RENDERBUFFER, rbo);
    rbStorageMs(GL_RENDERBUFFER, samples, (GLenum)depthFormat, width, height);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, rbo);
    glBindRenderbuffer(GL_RENDERBUFFER, 0);

    GLenum renderStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);

    /* The frame is read back through a second, ordinary framebuffer on the same texture,
     * which holds the resolved image once the scene pass ends. Reading straight from the
     * render-to-texture framebuffer left the scene black on Adreno. */
    GLuint readFbo = 0;
    glGenFramebuffers(1, &readFbo);
    glBindFramebuffer(GL_FRAMEBUFFER, readFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
    GLenum readStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);

    if (glGetError() != GL_NO_ERROR || renderStatus != GL_FRAMEBUFFER_COMPLETE || readStatus != GL_FRAMEBUFFER_COMPLETE) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDeleteFramebuffers(1, &fbo);
        glDeleteFramebuffers(1, &readFbo);
        glDeleteTextures(1, &tex);
        glDeleteRenderbuffers(1, &rbo);
        while (glGetError() != GL_NO_ERROR) {}
        return JNI_FALSE;
    }

    jint ids[4] = { (jint)fbo, (jint)tex, (jint)rbo, (jint)readFbo };
    (*env)->SetIntArrayRegion(env, out, 0, 4, ids);
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL Java_org_lwjgl_system_MemoryUtil_allocate(JNIEnv *env, jclass cls, jlong size) {
    (void)env; (void)cls;
    if (size <= 0 || (uint64_t)size > SIZE_MAX) return 0;
    return (jlong)(uintptr_t)malloc((size_t)size);
}
JNIEXPORT void JNICALL Java_org_lwjgl_system_MemoryUtil_nmemFree(JNIEnv *env, jclass cls, jlong ptr) {
    (void)env; (void)cls;
    free((void *)(uintptr_t)ptr);
}
JNIEXPORT jobject JNICALL Java_org_lwjgl_system_MemoryUtil_view(JNIEnv *env, jclass cls, jlong ptr, jint size) {
    (void)cls;
    return (*env)->NewDirectByteBuffer(env, (void *)(uintptr_t)ptr, size);
}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_MemoryUtil_memAddress0(JNIEnv *env, jclass cls, jobject buffer) {
    (void)cls;
    return (jlong)(uintptr_t)(*env)->GetDirectBufferAddress(env, buffer);
}
JNIEXPORT void JNICALL Java_org_lwjgl_opengl_GL33C_glTexSubImage2D(JNIEnv *env, jclass cls,
    jint target, jint level, jint x, jint y, jint width, jint height, jint format, jint type, jlong offset) {
    (void)env; (void)cls;
    glTexSubImage2D(target, level, x, y, width, height, format, type, (const void *)(uintptr_t)offset);
}
