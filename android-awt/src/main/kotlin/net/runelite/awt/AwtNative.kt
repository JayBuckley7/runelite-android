package net.runelite.awt

/**
 * Native side of the AWT shim — every JNI entry point used by the pure-Java shadow
 * classes lives here in a single Kotlin object so the symbol layout is predictable
 * from the Rust crate.
 *
 * Pixel buffers are always int[] in canonical ARGB packing (0xAARRGGBB). The Rust side
 * pins them via GetPrimitiveArrayCritical for the duration of one call — no escape, no
 * outstanding references after return.
 */
object AwtNative {
    init {
        AwtCompat.ensureReady()
    }

    @JvmStatic external fun fillRect(
        pixels: IntArray, imgW: Int, imgH: Int,
        x: Int, y: Int, w: Int, h: Int,
        argb: Int, compositeRule: Int,
    )

    @JvmStatic external fun fillOval(
        pixels: IntArray, imgW: Int, imgH: Int,
        x: Int, y: Int, w: Int, h: Int,
        argb: Int, compositeRule: Int,
    )

    @JvmStatic external fun drawOval(
        pixels: IntArray, imgW: Int, imgH: Int,
        x: Int, y: Int, w: Int, h: Int,
        argb: Int,
    )

    @JvmStatic external fun fillRoundRect(
        pixels: IntArray, imgW: Int, imgH: Int,
        x: Int, y: Int, w: Int, h: Int,
        arcW: Int, arcH: Int,
        argb: Int, compositeRule: Int,
    )

    /** 1px Bresenham line, clipped to the half-open device rect [clipX0,clipX1)x[clipY0,clipY1).
     *  [flags]: 1 = skip the first pixel, 2 = skip the last (shared polyline vertices). */
    @JvmStatic external fun drawLine(
        pixels: IntArray, imgW: Int, imgH: Int,
        x0: Int, y0: Int, x1: Int, y1: Int,
        argb: Int, compositeRule: Int,
        clipX0: Int, clipY0: Int, clipX1: Int, clipY1: Int,
        flags: Int,
    )

    /** Pixel-centre scanline fill of [nContours] implicitly closed contours (device-space
     *  x,y pairs in [coords], vertex counts in [counts]) under even-odd or non-zero, clipped
     *  to [nClip] disjoint (x0, y0, x1, y1) rects in [clip]. */
    @JvmStatic external fun fillPath(
        pixels: IntArray, imgW: Int, imgH: Int,
        coords: DoubleArray, counts: IntArray, nContours: Int,
        evenOdd: Boolean,
        clip: IntArray, nClip: Int,
        argb: Int, compositeRule: Int,
    )

    @JvmStatic external fun copyArea(
        pixels: IntArray, imgW: Int, imgH: Int,
        x: Int, y: Int, w: Int, h: Int,
        dstX: Int, dstY: Int,
    )

    @JvmStatic external fun blit(
        src: IntArray, srcW: Int, srcH: Int, sx: Int, sy: Int, sw: Int, sh: Int,
        dst: IntArray, dstW: Int, dstH: Int, dx: Int, dy: Int, dw: Int, dh: Int,
        compositeRule: Int, alpha: Float,
    )
}

