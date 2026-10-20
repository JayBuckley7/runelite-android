/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2021, 117 <https://twitter.com/117scape>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#version 330

#include <uniforms/global.glsl>
#include <uniforms/world_views.glsl>

#include <utils/constants.glsl>
#include <utils/color_utils.glsl>
#include <utils/uvs.glsl>

layout (location = 0) in vec3 vPosition;

#if ZONE_RENDERER
    layout (location = 1) in vec4 vUv;
    layout (location = 2) in vec4 vNormal;
    layout (location = 3) in int vTextureFaceIdx;
    layout (location = 6) in int vWorldViewId;
    layout (location = 7) in ivec2 vSceneBase;

    uniform isamplerBuffer textureFaces;
#else
    layout (location = 1) in vec3 vUv;
    layout (location = 2) in vec3 vNormal;
    layout (location = 3) in int vAlphaBiasHsl;
    layout (location = 4) in int vMaterialData;
    layout (location = 5) in int vTerrainData;
#endif

#if ZONE_RENDERER
    flat out int fWorldViewId;
    flat out ivec3 fAlphaBiasHsl;
    flat out ivec3 fMaterialData;
    flat out ivec3 fTerrainData;
    // Android: the three vertex colors (tinted, linear RGB + alpha), converted once per
    // triangle here instead of three times per fragment. Inputs are flat, so the result is
    // the same for every fragment of the triangle.
    flat out vec4 fBaseColor1;
    flat out vec4 fBaseColor2;
    flat out vec4 fBaseColor3;

    #if CENTROID_QUALIFIER
        centroid out vec3 fTexBlend;
    #else
        out vec3 fTexBlend;
    #endif

    vec4 vertexBaseColor(int alphaBiasHsl, ivec4 tint) {
        vec3 hsl = unpackRawHsl(alphaBiasHsl);
        if (tint.w > 0)
            hsl += ((vec3(tint.xyz) - hsl) * float(tint.w)) / 128.0;
        vec4 color = vec4(convertHsl(hsl), 1.0 - float(alphaBiasHsl >> 24 & 0xff) / 255.);
        color.rgb = srgbToLinear(hslToSrgb(color.xyz));
        return color;
    }

    out FragmentData {
        vec3 position;
        vec2 uv;
        vec3 normal;
    } OUT;

    void main() {
        int vertex = gl_VertexID % 3;
        bool isProvoking = vertex == 2;

        int faceIdx = vTextureFaceIdx & 0x7FFFFFFF;
        bool windingReversed = vTextureFaceIdx < 0;
        if (windingReversed)
            vertex = 2 - vertex;

        int materialData = 0;
        int alphaBiasHsl = 0;
        if (isProvoking) {
            // Only the Provoking vertex needs to fetch the face data
            fAlphaBiasHsl = texelFetch(textureFaces, faceIdx).xyz;
            fMaterialData = texelFetch(textureFaces, faceIdx + 1).xyz;
            fWorldViewId = vWorldViewId;
            alphaBiasHsl = fAlphaBiasHsl[vertex];
            materialData = fMaterialData[vertex];
            ivec4 tint = getWorldViewTint(vWorldViewId);
            fBaseColor1 = vertexBaseColor(fAlphaBiasHsl[0], tint);
            fBaseColor2 = vertexBaseColor(fAlphaBiasHsl[1], tint);
            fBaseColor3 = vertexBaseColor(fAlphaBiasHsl[2], tint);
        } else {
            // All outputs must be written to for macOS compatibility
            fAlphaBiasHsl = ivec3(0);
            fMaterialData = ivec3(0);
            fWorldViewId  = 0;
            fBaseColor1 = vec4(0);
            fBaseColor2 = vec4(0);
            fBaseColor3 = vec4(0);
            alphaBiasHsl = texelFetch(textureFaces, faceIdx)[vertex];
            materialData = texelFetch(textureFaces, faceIdx + 1)[vertex];
        }
        fTerrainData = texelFetch(textureFaces, faceIdx + 2).xyz;

        vec3 sceneOffset = vec3(vSceneBase.x, 0, vSceneBase.y);
        vec3 worldNormal = vNormal.xyz;
        vec3 worldPosition = sceneOffset + vPosition;
        if (vWorldViewId != -1) {
            mat4x3 worldViewProjection = mat4x3(getWorldViewProjection(vWorldViewId));
            worldPosition = worldViewProjection * vec4(worldPosition, 1.0);
            worldNormal = mat3(worldViewProjection) * worldNormal;
        }

        // Clamp underwater vertices to the water surface along the draw distance border, excluding
        // waterDepth == 1, which is used when the geometry already sits flush with the surface
        int waterDepth = fTerrainData[vertex] >> 11 & 0xFFF;
        if (waterDepth > 1) {
            const int CHUNK_SIZE = TILE_SIZE * 8;
            ivec2 cam = ivec2(uboGlobal.cameraPos.xz / float(CHUNK_SIZE)) * CHUNK_SIZE + CHUNK_SIZE / 2;
            ivec2 d = ivec2(abs(worldPosition.xz - vec2(cam)) / float(TILE_SIZE));
            if (max(d.x, d.y) > int(uboGlobal.drawDistance / 8.0) * 8 + 3)
                worldPosition.y -= float(waterDepth);
        }

        fTexBlend = vec3(0);
        fTexBlend[vertex] = 1.0;

        OUT.position = worldPosition;
        OUT.uv = computeVertexUvs(materialData, worldPosition, vUv.xyz);
        OUT.normal = worldNormal;

        vec4 clipPosition = uboGlobal.projectionMatrix * vec4(worldPosition, 1.0);
        int depthBias = (alphaBiasHsl >> 16) & 0xff;
        if (!uboGlobal.orthographicProjection)
            clipPosition.z += float(depthBias) / 128.0;

        gl_Position = clipPosition;
    }
#else
    out vec3 gPosition;
    out vec3 gUv;
    out vec3 gNormal;
    out int gAlphaBiasHsl;
    out int gMaterialData;
    out int gTerrainData;

    void main() {
        gPosition = vPosition;
        gUv = vUv.xyz;
        gNormal = vNormal.xyz;
        gAlphaBiasHsl = vAlphaBiasHsl;
        gMaterialData = vMaterialData;
        gTerrainData = vTerrainData;
    }
#endif
