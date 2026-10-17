package net.runelite.mp.hd

/** GLES extension entry points 117 HD needs beyond the LWJGL shim (natives in hd_memory.c). */
object GlesExt {
    init {
        System.loadLibrary("hdmemory")
    }

    /**
     * Creates a framebuffer whose color is a single-sample texture rendered with [samples]×
     * MSAA through GL_EXT_multisampled_render_to_texture, plus a multisampled depth
     * renderbuffer, plus an ordinary framebuffer on the same texture to read the resolved
     * image from. Leaves the render framebuffer bound. On success writes {fbo, colorTexture,
     * depthRenderbuffer, readFbo} to [out] and returns true; returns false (nothing left
     * allocated) when the extension is missing or either framebuffer isn't complete.
     */
    @JvmStatic
    external fun createTileResolvedFbo(
        width: Int, height: Int, samples: Int, colorFormat: Int, depthFormat: Int, out: IntArray,
    ): Boolean
}
