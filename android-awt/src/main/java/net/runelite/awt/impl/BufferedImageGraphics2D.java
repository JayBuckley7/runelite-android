package net.runelite.awt.impl;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.FontRenderContext;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.text.AttributedCharacterIterator;

import net.runelite.awt.AwtNative;

/**
 * Graphics2D backed by a {@link BufferedImage} {@code int[]} pixel buffer. Bulk fills /
 * blits route through the {@link AwtNative} Rust bridge; small / single-pixel ops stay
 * in Java since the JNI hop costs more than the work.
 */
public class BufferedImageGraphics2D extends Graphics2D {
    private BufferedImage image;
    private int[] pixels;
    private int width;
    private int height;
    /** When non-null, this graphics targets a Canvas's LIVE backbuffer: {@link #syncTarget()}
     *  re-resolves the backing at the start of each draw call. This keeps a cached graphics
     *  (notably the OSRS client's MainBufferProvider {@code ln_fld}, captured once via
     *  Canvas.getGraphics()) painting into the CURRENT backbuffer after the Canvas
     *  reallocates it on a resize — otherwise the cached graphics is orphaned to the old
     *  int[] and the visible backbuffer stays black. Null for ordinary image-backed graphics
     *  (icons, glyph caches, sub-buffers), which are unaffected by this whole mechanism. */
    private final java.util.function.Supplier<BufferedImage> backbufferSource;

    /** Font and Stroke are immutable, and every graphics starts on the same pair — sharing
     *  them keeps two allocations out of each Graphics2D construction. Graphics2D objects
     *  are created per Window frame and again by {@link #create()} for Swing paint calls,
     *  so this is on the paint path rather than a one-off. */
    private static final Font DEFAULT_FONT = new Font(Font.DIALOG, Font.PLAIN, 12);
    private static final Stroke DEFAULT_STROKE = new BasicStroke();

    private Color foreground = Color.BLACK;
    private Color background = Color.WHITE;
    private Font font = DEFAULT_FONT;
    private Rectangle clip;
    private final AffineTransform transform = new AffineTransform();
    private Composite composite = AlphaComposite.SrcOver;
    private Stroke stroke = DEFAULT_STROKE;
    private Paint paint = Color.BLACK;
    private final RenderingHints renderingHints = new RenderingHints(null);
    /** Uniform user→device multiplier applied on top of {@link #transform}. Lets a host lay a
     *  Swing tree out in logical pixels but rasterize it at the display's real density
     *  (text, fills, borders and shapes are all drawn at device resolution instead of
     *  being painted small and stretched). 1 for every graphics that doesn't opt in, and
     *  every scaled branch below is gated on that so the default paths are unchanged. */
    private float deviceScale = 1f;

    public BufferedImageGraphics2D(BufferedImage image) {
        this(image, null);
    }

    /** Live-backbuffer variant: the graphics re-resolves its target from {@code source}
     *  each draw call (see {@link #syncTarget()}). Used by Canvas.getGraphics(). */
    public BufferedImageGraphics2D(java.util.function.Supplier<BufferedImage> source) {
        this(source.get(), source);
    }

    private BufferedImageGraphics2D(BufferedImage image, java.util.function.Supplier<BufferedImage> backbufferSource) {
        this.image = image;
        this.pixels = image.backingArray();
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.clip = new Rectangle(0, 0, width, height);
        this.backbufferSource = backbufferSource;
    }

    /** Re-resolve the backing from the live Canvas backbuffer (no-op for plain image-backed
     *  graphics). Single-threaded per instance — a handed-out graphics is only ever drawn on
     *  by its owning thread — so reading the volatile backbuffer and updating our own fields
     *  needs no extra locking. Called at the head of every native draw. */
    private void syncTarget() {
        if (backbufferSource == null) return;
        BufferedImage bb = backbufferSource.get();
        if (bb == null || bb == image) return;
        image = bb;
        pixels = bb.backingArray();
        width = bb.getWidth();
        height = bb.getHeight();
        // Clamp the clip into the new bounds so a shrink can't let a native write run past
        // the (now smaller) array; the client re-sets its own clip each frame anyway.
        int nx = Math.min(clip.x, width);
        int ny = Math.min(clip.y, height);
        int nw = Math.min(clip.width, width - nx);
        int nh = Math.min(clip.height, height - ny);
        clip = new Rectangle(nx, ny, Math.max(0, nw), Math.max(0, nh));
    }

    private Rectangle clipped(int x, int y, int w, int h) {
        int x0 = Math.max(x, clip.x);
        int y0 = Math.max(y, clip.y);
        int x1 = Math.min(x + w, clip.x + clip.width);
        int y1 = Math.min(y + h, clip.y + clip.height);
        int x2 = Math.max(0, Math.min(width, x1));
        int y2 = Math.max(0, Math.min(height, y1));
        int x3 = Math.max(0, x0);
        int y3 = Math.max(0, y0);
        return new Rectangle(x3, y3, Math.max(0, x2 - x3), Math.max(0, y2 - y3));
    }

    @Override
    public Graphics create() {
        BufferedImageGraphics2D copy = new BufferedImageGraphics2D(image, backbufferSource);
        copy.foreground = foreground;
        copy.background = background;
        copy.font = font;
        copy.clip = new Rectangle(clip);
        copy.transform.setTransform(transform);
        copy.composite = composite;
        copy.stroke = stroke;
        copy.paint = paint;
        copy.renderingHints.add(renderingHints);
        copy.deviceScale = deviceScale;
        return copy;
    }

    /** See {@link #deviceScale}. Set before painting; the clip stays in device pixels. */
    public void setDeviceScale(float scale) {
        deviceScale = scale > 0f ? scale : 1f;
    }

    public float getDeviceScale() { return deviceScale; }

    /** Logical x (translate-only user transform) → device pixel edge. Rect edges go through
     *  this individually so adjacent rects still tile without gaps or overlap. */
    private int devX(double x) {
        return (int) Math.floor((x + transform.getTranslateX()) * deviceScale + 0.5);
    }
    private int devY(double y) {
        return (int) Math.floor((y + transform.getTranslateY()) * deviceScale + 0.5);
    }

    /** Device-space rect for a logical rect, clipped. Only for deviceScale != 1. */
    private Rectangle clippedDevice(int x, int y, int w, int h) {
        int x0 = devX(x), y0 = devY(y);
        return clipped(x0, y0, devX(x + (double) w) - x0, devY(y + (double) h) - y0);
    }

    @Override public void translate(int x, int y) { transform.translate(x, y); }
    @Override public void translate(double tx, double ty) { transform.translate(tx, ty); }

    /** Current accumulated x translation as device-space integer. */
    private int tx() { return (int) Math.round(transform.getTranslateX()); }
    /** Current accumulated y translation as device-space integer. */
    private int ty() { return (int) Math.round(transform.getTranslateY()); }

    /** True iff the current AffineTransform has only translation (m00=m11=1, m01=m10=0).
     *  Lets the polygon / line paths skip the per-vertex full-matrix multiply when the
     *  caller hasn't actually rotated/scaled anything (the common case). */
    private boolean transformIsTranslateOnly() {
        return transform.getScaleX() == 1.0 && transform.getScaleY() == 1.0
            && transform.getShearX() == 0.0 && transform.getShearY() == 0.0;
    }

    /** Apply the full AffineTransform to a user-space (x,y), returning device-space x.
     *  Used by drawLine / fillPolygon / drawPolygon to honor rotation+scale+shear in
     *  addition to translation — the previous tx()/ty() shortcut dropped rotation
     *  entirely, which was visible as Quest Helper's minimap arrow head sitting
     *  unrotated at the line endpoint regardless of the line's direction. */
    private int transformX(double x, double y) {
        return (int) Math.round((transform.getScaleX() * x + transform.getShearX() * y
            + transform.getTranslateX()) * deviceScale);
    }
    private int transformY(double x, double y) {
        return (int) Math.round((transform.getShearY() * x + transform.getScaleY() * y
            + transform.getTranslateY()) * deviceScale);
    }

    @Override public Color getColor() { return foreground; }
    @Override public void setColor(Color c) { if (c != null) { foreground = c; paint = c; } }
    @Override public Color getBackground() { return background; }
    @Override public void setBackground(Color c) { if (c != null) background = c; }

    @Override public void setPaintMode() {}
    @Override public void setXORMode(Color c1) {}

    @Override public Font getFont() { return font; }
    @Override public void setFont(Font font) { if (font != null) this.font = font; }
    @Override public FontMetrics getFontMetrics() { return BufferedImageFontMetrics.forFont(font); }
    @Override public FontMetrics getFontMetrics(Font f) { return BufferedImageFontMetrics.forFont(f); }

    @Override public Rectangle getClipBounds() {
        // Return clip in user space (subtract current translation).
        if (deviceScale != 1f) {
            // Round outward so a component's damage test never drops a partially covered
            // logical pixel.
            double s = deviceScale;
            int x0 = (int) Math.floor(clip.x / s - transform.getTranslateX());
            int y0 = (int) Math.floor(clip.y / s - transform.getTranslateY());
            int x1 = (int) Math.ceil((clip.x + clip.width) / s - transform.getTranslateX());
            int y1 = (int) Math.ceil((clip.y + clip.height) / s - transform.getTranslateY());
            return new Rectangle(x0, y0, x1 - x0, y1 - y0);
        }
        return new Rectangle(clip.x - tx(), clip.y - ty(), clip.width, clip.height);
    }
    @Override public void clipRect(int x, int y, int w, int h) {
        // Inlined `clip.intersection(new Rectangle(...))` — the JDK version allocates
        // two Rectangles per call (the operand and the result). hostPaint hits this for
        // every component in the tree, so killing both allocations is a meaningful per-
        // frame win when the AWT path is alive (logcat showed 40MB/s of AllocSpace churn
        // dominated by tree-walk overhead).
        int ax, ay, ax2, ay2;
        if (deviceScale == 1f) {
            ax = x + tx(); ay = y + ty(); ax2 = ax + w; ay2 = ay + h;
        } else {
            ax = devX(x); ay = devY(y); ax2 = devX(x + (double) w); ay2 = devY(y + (double) h);
        }
        int x0 = Math.max(clip.x, ax);
        int y0 = Math.max(clip.y, ay);
        int x1 = Math.min(clip.x + clip.width, ax2);
        int y1 = Math.min(clip.y + clip.height, ay2);
        clip.setBounds(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    // -------------------------------------------------------------------------
    // Zero-allocation state save/restore for [java.awt.Window#hostPaint]. The
    // standard getTransform()/getClip()/setTransform()/setClip() cycle the JDK
    // requires us to expose allocates a fresh AffineTransform AND a fresh
    // Rectangle per component visited. With ~hundreds of components painted per
    // frame on the AWT-rendered chrome, that's a few MB/sec of garbage we can
    // sidestep entirely by snapshotting into caller-owned scratch arrays.
    // -------------------------------------------------------------------------

    /** Captures the current transform's six matrix elements into out[0..5]. */
    public void snapshotTransform(double[] out) {
        out[0] = transform.getScaleX();
        out[1] = transform.getShearY();
        out[2] = transform.getShearX();
        out[3] = transform.getScaleY();
        out[4] = transform.getTranslateX();
        out[5] = transform.getTranslateY();
    }

    /** Restores transform from a previous snapshotTransform() result. */
    public void restoreTransform(double[] in) {
        transform.setTransform(in[0], in[1], in[2], in[3], in[4], in[5]);
    }

    /** Captures clip into out[0..3] = (x, y, w, h). */
    public void snapshotClip(int[] out) {
        out[0] = clip.x;
        out[1] = clip.y;
        out[2] = clip.width;
        out[3] = clip.height;
    }

    /** Restores clip from a previous snapshotClip() result. Reuses the existing
     *  Rectangle field via setBounds so no new instance is created. */
    public void restoreClip(int[] in) {
        clip.setBounds(in[0], in[1], in[2], in[3]);
    }
    @Override public void setClip(int x, int y, int w, int h) {
        clip = deviceRect(x, y, w, h);
    }
    @Override public Shape getClip() { return getClipBounds(); }
    @Override public void setClip(Shape shape) {
        syncTarget();
        if (shape == null) clip = new Rectangle(0, 0, width, height);
        else {
            Rectangle b = shape.getBounds();
            clip = deviceRect(b.x, b.y, b.width, b.height);
        }
    }

    /** Unclipped device rect for a logical rect. */
    private Rectangle deviceRect(int x, int y, int w, int h) {
        if (deviceScale == 1f) return new Rectangle(x + tx(), y + ty(), w, h);
        int x0 = devX(x), y0 = devY(y);
        return new Rectangle(x0, y0, devX(x + (double) w) - x0, devY(y + (double) h) - y0);
    }

    @Override
    public void copyArea(int x, int y, int w, int h, int dx, int dy) {
        syncTarget();
        Rectangle src = deviceScale == 1f ? clipped(x + tx(), y + ty(), w, h) : clippedDevice(x, y, w, h);
        if (src.width == 0 || src.height == 0) return;
        int ddx = deviceScale == 1f ? dx : Math.round(dx * deviceScale);
        int ddy = deviceScale == 1f ? dy : Math.round(dy * deviceScale);
        AwtNative.copyArea(pixels, width, height,
            src.x, src.y, src.width, src.height, src.x + ddx, src.y + ddy);
    }

    /**
     * A 1px Swing line at deviceScale: keep it one LOGICAL pixel thick, otherwise every
     * border and separator turns into a hairline. Axis-aligned lines (nearly all of Swing)
     * fill the covered logical pixel cells as a device rect; anything else becomes a quad
     * of the same thickness through the pixel centres.
     */
    private void drawScaledLine(int x1, int y1, int x2, int y2) {
        if (transformIsTranslateOnly() && (x1 == x2 || y1 == y2)) {
            int lx = Math.min(x1, x2), ly = Math.min(y1, y2);
            Rectangle r = clippedDevice(lx, ly, Math.max(x1, x2) - lx + 1, Math.max(y1, y2) - ly + 1);
            if (r.width == 0 || r.height == 0) return;
            AwtNative.fillRect(pixels, width, height, r.x, r.y, r.width, r.height,
                foreground.getRGB(), compositeRule());
            return;
        }
        double s = deviceScale;
        double ax = (transform.getScaleX() * (x1 + 0.5) + transform.getShearX() * (y1 + 0.5) + transform.getTranslateX()) * s;
        double ay = (transform.getShearY() * (x1 + 0.5) + transform.getScaleY() * (y1 + 0.5) + transform.getTranslateY()) * s;
        double bx = (transform.getScaleX() * (x2 + 0.5) + transform.getShearX() * (y2 + 0.5) + transform.getTranslateX()) * s;
        double by = (transform.getShearY() * (x2 + 0.5) + transform.getScaleY() * (y2 + 0.5) + transform.getTranslateY()) * s;
        double dx = bx - ax, dy = by - ay;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3) return;
        double hw = s * 0.5;
        double nx = -dy / len * hw, ny = dx / len * hw;
        double ex = dx / len * hw, ey = dy / len * hw;
        int[][] scratch = THICK_SCRATCH.get();
        int[] xs = scratch[0];
        int[] ys = scratch[1];
        xs[0] = (int) Math.round(ax - ex + nx); ys[0] = (int) Math.round(ay - ey + ny);
        xs[1] = (int) Math.round(ax - ex - nx); ys[1] = (int) Math.round(ay - ey - ny);
        xs[2] = (int) Math.round(bx + ex - nx); ys[2] = (int) Math.round(by + ey - ny);
        xs[3] = (int) Math.round(bx + ex + nx); ys[3] = (int) Math.round(by + ey + ny);
        AwtNative.fillPolygon(pixels, width, height, xs, ys, 4, 0, 0, foreground.getRGB(), compositeRule());
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        syncTarget();
        if (deviceScale != 1f) { drawScaledLine(x1, y1, x2, y2); return; }
        int sx1, sy1, sx2, sy2;
        if (transformIsTranslateOnly()) {
            int dx = tx(), dy = ty();
            sx1 = x1 + dx; sy1 = y1 + dy;
            sx2 = x2 + dx; sy2 = y2 + dy;
        } else {
            sx1 = transformX(x1, y1); sy1 = transformY(x1, y1);
            sx2 = transformX(x2, y2); sy2 = transformY(x2, y2);
        }
        AwtNative.drawLine(pixels, width, height, sx1, sy1, sx2, sy2, foreground.getRGB());
    }

    @Override
    public void fillRect(int x, int y, int w, int h) {
        syncTarget();
        Rectangle r = deviceScale == 1f ? clipped(x + tx(), y + ty(), w, h) : clippedDevice(x, y, w, h);
        if (r.width == 0 || r.height == 0) return;
        AwtNative.fillRect(pixels, width, height, r.x, r.y, r.width, r.height,
            foreground.getRGB(), compositeRule());
    }

    @Override
    public void clearRect(int x, int y, int w, int h) {
        syncTarget();
        Rectangle r = deviceScale == 1f ? clipped(x + tx(), y + ty(), w, h) : clippedDevice(x, y, w, h);
        if (r.width == 0 || r.height == 0) return;
        AwtNative.fillRect(pixels, width, height, r.x, r.y, r.width, r.height,
            background.getRGB(), AlphaComposite.SRC);
    }

    private boolean strokeWidthIsOne() {
        return !(stroke instanceof BasicStroke) || ((BasicStroke) stroke).getLineWidth() <= 1f;
    }

    @Override public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) {
        rasterizeShape(x, y, w, h, false, (c, p, sw, sh) -> c.drawRoundRect(
            new android.graphics.RectF(0, 0, sw, sh), aw / 2f, ah / 2f, p));
    }
    @Override public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) {
        if (transformIsTranslateOnly() && deviceScale == 1f) {
            syncTarget();
            AwtNative.fillRoundRect(pixels, width, height, x + tx(), y + ty(), w, h, aw, ah,
                foreground.getRGB(), compositeRule());
            return;
        }
        rasterizeShape(x, y, w, h, true, (c, p, sw, sh) -> c.drawRoundRect(
            new android.graphics.RectF(0, 0, sw, sh), aw / 2f, ah / 2f, p));
    }
    @Override public void drawOval(int x, int y, int w, int h) {
        if (transformIsTranslateOnly() && strokeWidthIsOne() && deviceScale == 1f) {
            syncTarget();
            AwtNative.drawOval(pixels, width, height, x + tx(), y + ty(), w, h, foreground.getRGB());
            return;
        }
        rasterizeShape(x, y, w, h, false, (c, p, sw, sh) -> c.drawOval(
            new android.graphics.RectF(0, 0, sw, sh), p));
    }
    @Override public void fillOval(int x, int y, int w, int h) {
        if (transformIsTranslateOnly() && deviceScale == 1f) {
            syncTarget();
            AwtNative.fillOval(pixels, width, height, x + tx(), y + ty(), w, h,
                foreground.getRGB(), compositeRule());
            return;
        }
        rasterizeShape(x, y, w, h, true, (c, p, sw, sh) -> c.drawOval(
            new android.graphics.RectF(0, 0, sw, sh), p));
    }
    @Override public void drawArc(int x, int y, int w, int h, int sa, int aa) {
        // AWT angles: 0° = 3 o'clock, positive = counter-clockwise.
        // Android angles: same origin, but positive = clockwise → negate both.
        rasterizeShape(x, y, w, h, false, (c, p, sw, sh) -> c.drawArc(
            new android.graphics.RectF(0, 0, sw, sh), -sa, -aa, false, p));
    }
    @Override public void fillArc(int x, int y, int w, int h, int sa, int aa) {
        // useCenter=true → pie slice (matches AWT fillArc semantics).
        rasterizeShape(x, y, w, h, true, (c, p, sw, sh) -> c.drawArc(
            new android.graphics.RectF(0, 0, sw, sh), -sa, -aa, true, p));
    }

    @FunctionalInterface
    private interface AndroidShapeDrawer {
        void draw(android.graphics.Canvas c, android.graphics.Paint p, int sw, int sh);
    }

    /**
     * Rasterize an Android-Canvas-drawn shape into an ARGB bitmap then blit onto the
     * backing buffer at {@code (x + tx(), y + ty())}, clipped against the active clip.
     * Used by drawArc/fillArc/drawOval/fillOval/drawRoundRect/fillRoundRect.
     */
    /**
     * Reusable rasterization target for {@link #rasterizeShape}. Every arc / oval /
     * round-rect that can't take a native fast path used to allocate a Bitmap, a Canvas,
     * a Paint and an {@code int[bw*bh]} per call — and RuneLite overlays draw arcs every
     * frame (cooldown pies, progress rings), so that is a steady stream of garbage in the
     * middle of the paint loop. One scratch per drawing thread, grown on demand, instead.
     */
    private static final class ShapeScratch {
        android.graphics.Bitmap bmp;
        android.graphics.Canvas canvas;
        int[] pixels;
        int w, h;
        final android.graphics.Paint paint = new android.graphics.Paint();
    }

    /** Above this the scratch is not retained — a one-off huge shape shouldn't leave a
     *  multi-megabyte bitmap parked on the thread forever. */
    private static final int MAX_SCRATCH_PIXELS = 1024 * 1024;

    private static final ThreadLocal<ShapeScratch> SHAPE_SCRATCH =
        ThreadLocal.withInitial(ShapeScratch::new);

    private void rasterizeShape(int x, int y, int w, int h, boolean filled, AndroidShapeDrawer drawer) {
        if (w <= 0 || h <= 0) return;
        syncTarget();
        float strokeW = filled ? 0f : ((stroke instanceof BasicStroke) ? ((BasicStroke) stroke).getLineWidth() : 1f);
        // At deviceScale the shape is drawn in logical units under a canvas scale, so the
        // bitmap, pad and stroke reach are all sized in device pixels.
        int adx, ady, dw, dh;
        if (deviceScale == 1f) {
            adx = x + tx(); ady = y + ty(); dw = w; dh = h;
        } else {
            adx = devX(x); ady = devY(y);
            dw = Math.max(1, devX(x + (double) w) - adx);
            dh = Math.max(1, devY(y + (double) h) - ady);
        }
        // +1 covers AA bleed; ceil(strokeW/2) covers the stroke's outward reach.
        int pad = (int) Math.ceil(strokeW * deviceScale / 2f) + 1;
        int bw = dw + 2 * pad;
        int bh = dh + 2 * pad;

        ShapeScratch sc = SHAPE_SCRATCH.get();
        boolean pooled = (long) bw * bh <= MAX_SCRATCH_PIXELS;

        android.graphics.Bitmap bmp;
        android.graphics.Canvas canvas;
        int[] sp;
        if (pooled) {
            if (sc.bmp == null || sc.w < bw || sc.h < bh) {
                int nw = Math.max(bw, sc.w);
                int nh = Math.max(bh, sc.h);
                // Growing both dimensions independently can compound (a wide shape then a
                // tall one), so fall back to exactly what's asked for if the union blows
                // the budget.
                if ((long) nw * nh > MAX_SCRATCH_PIXELS) { nw = bw; nh = bh; }
                if (sc.bmp != null) sc.bmp.recycle();
                sc.bmp = android.graphics.Bitmap.createBitmap(
                    nw, nh, android.graphics.Bitmap.Config.ARGB_8888);
                sc.canvas = new android.graphics.Canvas(sc.bmp);
                sc.w = nw;
                sc.h = nh;
            }
            if (sc.pixels == null || sc.pixels.length < bw * bh) {
                sc.pixels = new int[bw * bh];
            }
            bmp = sc.bmp;
            canvas = sc.canvas;
            sp = sc.pixels;
        } else {
            bmp = android.graphics.Bitmap.createBitmap(
                bw, bh, android.graphics.Bitmap.Config.ARGB_8888);
            canvas = new android.graphics.Canvas(bmp);
            sp = new int[bw * bh];
        }

        canvas.save();
        try {
            // A reused bitmap still holds the previous shape; clear just the region we're
            // about to use rather than the whole (possibly much larger) scratch.
            canvas.clipRect(0, 0, bw, bh);
            canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
            canvas.translate(pad, pad);
            if (deviceScale != 1f) canvas.scale(dw / (float) w, dh / (float) h);
            android.graphics.Paint p = sc.paint;
            p.reset();
            p.setAntiAlias(true);
            p.setColor(foreground.getRGB());
            p.setStyle(filled ? android.graphics.Paint.Style.FILL : android.graphics.Paint.Style.STROKE);
            if (!filled) {
                p.setStrokeWidth(Math.max(1f, strokeW));
            }
            drawer.draw(canvas, p, w, h);
        } catch (Throwable ignored) {
            return;
        } finally {
            canvas.restore();
        }
        // Reads a bw x bh sub-rect at stride bw, so a larger pooled bitmap is fine.
        bmp.getPixels(sp, 0, bw, 0, 0, bw, bh);
        if (!pooled) bmp.recycle();

        int dstX = adx - pad;
        int dstY = ady - pad;
        int cx0 = clip.x, cy0 = clip.y;
        int cx1 = clip.x + clip.width, cy1 = clip.y + clip.height;
        int ndx = Math.max(dstX, cx0);
        int ndy = Math.max(dstY, cy0);
        int ndx2 = Math.min(dstX + bw, cx1);
        int ndy2 = Math.min(dstY + bh, cy1);
        int ndw = ndx2 - ndx;
        int ndh = ndy2 - ndy;
        if (ndw <= 0 || ndh <= 0) return;

        AwtNative.blit(sp, bw, bh, ndx - dstX, ndy - dstY, ndw, ndh,
            pixels, width, height, ndx, ndy, ndw, ndh,
            compositeRule(), compositeAlpha());
    }

    @Override
    public void drawPolyline(int[] xs, int[] ys, int n) {
        for (int i = 1; i < n; i++) drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i]);
    }

    @Override
    public void drawPolygon(int[] xs, int[] ys, int n) {
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            drawLine(xs[i], ys[i], xs[j], ys[j]);
        }
    }

    @Override
    public void fillPolygon(int[] xs, int[] ys, int n) {
        if (n < 3 || xs == null || ys == null) return;
        syncTarget();
        if (transformIsTranslateOnly() && deviceScale == 1f) {
            AwtNative.fillPolygon(pixels, width, height, xs, ys, n, tx(), ty(), foreground.getRGB(), compositeRule());
        } else {
            // Rotation/scale/shear present: must run each vertex through the full
            // matrix. Quest Helper's minimap-arrow head goes through this branch
            // (Polygon → fill → fillPolygon under a translate+rotate transform).
            int[] sx = new int[n];
            int[] sy = new int[n];
            for (int i = 0; i < n; i++) {
                sx[i] = transformX(xs[i], ys[i]);
                sy[i] = transformY(xs[i], ys[i]);
            }
            AwtNative.fillPolygon(pixels, width, height, sx, sy, n, 0, 0, foreground.getRGB(), compositeRule());
        }
    }


    @Override
    public void drawString(String str, int x, int y) {
        if (str == null || str.isEmpty()) return;
        syncTarget();
        int baseX, baseY;
        if (deviceScale == 1f) {
            baseX = x + tx(); baseY = y + ty();
        } else {
            baseX = devX(x); baseY = devY(y);
            // Rows scrolled out of a viewport still get painted; skip them before the
            // cache lookup so off-screen labels don't churn the (now larger) glyph runs.
            float size = BufferedImageFontMetrics.pxSize(font) * deviceScale;
            if (baseY + size < clip.y || baseY - 2 * size > clip.y + clip.height) return;
        }
        TextRender r = TextCache.get(str, font, foreground.getRGB(), textAaMode(), deviceScale);
        int dx = baseX + r.boundsLeft - TextCache.PADDING;
        int dy = baseY + r.boundsTop - TextCache.PADDING;
        // Honor the clip — text in a scrolled JViewport otherwise paints
        // outside the viewport bounds (same bug pattern as blit()). 1:1 scale
        // here so source shift == dst clip offset.
        int cx0 = clip.x, cy0 = clip.y;
        int cx1 = clip.x + clip.width, cy1 = clip.y + clip.height;
        int ndx = Math.max(dx, cx0);
        int ndy = Math.max(dy, cy0);
        int ndx2 = Math.min(dx + r.w, cx1);
        int ndy2 = Math.min(dy + r.h, cy1);
        int ndw = ndx2 - ndx;
        int ndh = ndy2 - ndy;
        if (ndw <= 0 || ndh <= 0) return;
        int nsx = ndx - dx;
        int nsy = ndy - dy;
        AwtNative.blit(r.pixels, r.w, r.h, nsx, nsy, ndw, ndh,
            pixels, width, height, ndx, ndy, ndw, ndh, compositeRule(), compositeAlpha());
    }

    /**
     * Resolves the active text-AA mode from our RenderingHints map. Maps the JDK constants
     * onto a small enum so the TextCache key can include it (cached bitmaps depend on AA
     * mode + subpixel; without this in the key, switching hints leaks stale bitmaps).
     * Falls back to KEY_ANTIALIASING when the text-specific hint is left at DEFAULT.
     */
    private TextAa textAaMode() {
        Object v = renderingHints.get(RenderingHints.KEY_TEXT_ANTIALIASING);
        if (v == null || v == RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT) {
            // Fall back to the general antialiasing hint.
            Object g = renderingHints.get(RenderingHints.KEY_ANTIALIASING);
            if (g == RenderingHints.VALUE_ANTIALIAS_OFF) return TextAa.OFF;
            // Default to ON — RuneLite + FlatLaf both expect AA text by default.
            return TextAa.ON;
        }
        if (v == RenderingHints.VALUE_TEXT_ANTIALIAS_OFF) return TextAa.OFF;
        if (v == RenderingHints.VALUE_TEXT_ANTIALIAS_GASP) return TextAa.ON;
        if (v == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB
            || v == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR
            || v == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VRGB
            || v == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VBGR) return TextAa.LCD;
        return TextAa.ON;
    }

    enum TextAa { OFF, ON, LCD }

    /**
     * Cached prerendered glyph runs. drawString used to allocate a bitmap + int[] per call
     * and JNI-copy pixels through Bitmap.getPixels every label paint; profile showed >30%
     * of GameViewport time there. With a per-(text,font,argb) cache + LRU eviction we
     * pay the cost once per string and blit cached pixels forever after.
     */
    private static final class TextRender {
        final int[] pixels;
        final int w, h;
        final int boundsLeft, boundsTop;
        TextRender(int[] pixels, int w, int h, int boundsLeft, int boundsTop) {
            this.pixels = pixels; this.w = w; this.h = h;
            this.boundsLeft = boundsLeft; this.boundsTop = boundsTop;
        }
    }
    private static final class TextCache {
        static final int PADDING = 2;
        private static final int MAX = 1024;
        /**
         * Byte budget for the cached glyph pixels, expressed in ints. The entry count
         * alone doesn't bound anything useful: entries are int[w*h] and a single wrapped
         * tooltip line at 16px is easily 600x20 = 48 KB, so 1024 of those is ~49 MB held
         * for the life of the process on a phone. Cap the pixels instead and evict LRU
         * until back under budget.
         */
        private static final int MAX_PIXELS = 2 * 1024 * 1024; // 8 MB
        /**
         * A render bigger than this never enters the cache at all. One oversized string
         * would otherwise evict most of the small labels that the cache actually exists
         * for, and it is the entry least likely to be asked for twice.
         */
        private static final int MAX_ENTRY_PIXELS = 96 * 1024; // 384 KB
        private static int cachedPixels;
        /**
         * Composite key for (string, font, color). Earlier we packed the three hashCodes
         * into a single long with XOR, but the bit ranges overlapped — different (str,
         * font, argb) tuples collided to the same key and the cache returned a stale
         * bitmap, painting "Account" pixels for "Agility" etc. (visible as "shadowed"
         * or corrupted text in the plugin list panel).
         */
        private static final class Key {
            String str;
            Font font;
            int argb;
            TextAa aa;
            float scale;
            int hash;
            Key() {}
            Key(String s, Font f, int a, TextAa aa, float scale) {
                set(s, f, a, aa, scale);
            }
            void set(String s, Font f, int a, TextAa aa, float scale) {
                this.str = s; this.font = f; this.argb = a; this.aa = aa; this.scale = scale;
                int h = s != null ? s.hashCode() : 0;
                h = 31 * h + (f != null ? f.hashCode() : 0);
                h = 31 * h + a;
                h = 31 * h + (aa != null ? aa.ordinal() : 0);
                h = 31 * h + Float.floatToIntBits(scale);
                this.hash = h;
            }
            void clear() { this.str = null; this.font = null; }
            @Override public int hashCode() { return hash; }
            @Override public boolean equals(Object o) {
                if (!(o instanceof Key)) return false;
                Key k = (Key) o;
                return argb == k.argb && aa == k.aa && scale == k.scale
                    && (font == k.font || (font != null && font.equals(k.font)))
                    && (str == k.str || (str != null && str.equals(k.str)));
            }
        }
        private static final ThreadLocal<Key> LOOKUP_KEY = new ThreadLocal<Key>() {
            @Override protected Key initialValue() { return new Key(); }
        };
        // access-order, so the iterator hands back least-recently-used first when we trim.
        private static final java.util.LinkedHashMap<Key, TextRender> cache =
            new java.util.LinkedHashMap<Key, TextRender>(256, 0.75f, true);

        static synchronized TextRender get(String str, Font font, int argb, TextAa aa, float scale) {
            Key lookup = LOOKUP_KEY.get();
            lookup.set(str, font, argb, aa, scale);
            TextRender r = cache.get(lookup);
            // Don't let the thread-local probe key pin the last string + font drawn on
            // this thread until the next drawString.
            lookup.clear();
            if (r != null) return r;

            r = render(str, font, argb, aa, scale);
            if (r.pixels.length > MAX_ENTRY_PIXELS) return r;

            cache.put(new Key(str, font, argb, aa, scale), r);
            cachedPixels += r.pixels.length;
            if (cache.size() > MAX || cachedPixels > MAX_PIXELS) {
                java.util.Iterator<java.util.Map.Entry<Key, TextRender>> it =
                    cache.entrySet().iterator();
                while (it.hasNext() && (cache.size() > MAX || cachedPixels > MAX_PIXELS)) {
                    cachedPixels -= it.next().getValue().pixels.length;
                    it.remove();
                }
            }
            return r;
        }
        private static TextRender render(String str, Font font, int argb, TextAa aa, float scale) {
            android.graphics.Paint p = new android.graphics.Paint();
            p.setColor(argb);
            applyAa(p, aa);
            // Snap glyphs to the pixel grid. Without this Android's grayscale AA leaves
            // even glyph-interior pixels with alpha < 255 at small sizes — body pixels
            // then look "see-through" because src_over blends bg through them.
            p.setHinting(android.graphics.Paint.HINTING_ON);
            float textSize = BufferedImageFontMetrics.pxSize(font) * scale;
            p.setTextSize(textSize);
            p.setTypeface(BufferedImageFontMetrics.typefaceFor(font));
            android.graphics.Rect bounds = new android.graphics.Rect();
            p.getTextBounds(str, 0, str.length(), bounds);
            int textW = Math.max(1, bounds.width() + 2 * PADDING);
            int textH = Math.max(1, bounds.height() + 2 * PADDING);
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                textW, textH, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
            canvas.drawText(str, PADDING - bounds.left, PADDING - bounds.top, p);
            int[] pixels = new int[textW * textH];
            bmp.getPixels(pixels, 0, textW, 0, 0, textW, textH);
            bmp.recycle();
            // Alpha contrast boost. Android's Paint at small sizes (12px) doesn't give
            // glyph interiors alpha=255 even with HINTING_ON — it produces ~180–230
            // per the glyph's coverage rounding. Our src_over then renders the body as
            // `text·α + dst·(1-α)`, leaking the bg through "opaque" text (the red-bar
            // /white-text "see-through" symptom). Saturate any near-opaque pixel to
            // 255 while preserving the AA gradient on edges so the silhouette stays
            // smooth and blends cleanly with the destination. Device-scaled runs are large
            // enough that interiors are already opaque; boosting them would only harden
            // the AA edges we rendered at full resolution to get.
            if (scale == 1f || textSize < 20f) for (int i = 0; i < pixels.length; i++) {
                int px = pixels[i];
                int a = (px >>> 24) & 0xFF;
                if (a == 0 || a == 255) continue;
                int boosted = a + (a >> 1); // ×1.5
                if (boosted > 255) boosted = 255;
                pixels[i] = (boosted << 24) | (px & 0x00FFFFFF);
            }
            return new TextRender(pixels, textW, textH, bounds.left, bounds.top);
        }
    }

    /** Builds an Android Paint matching our current foreground + font. Not cached — fonts
     * are immutable on the AWT side so recreation is fine for occasional text. */
    private android.graphics.Paint androidPaint() {
        android.graphics.Paint p = new android.graphics.Paint();
        p.setColor(foreground.getRGB());
        applyAa(p, textAaMode());
        p.setTextSize(BufferedImageFontMetrics.pxSize(font));
        p.setTypeface(BufferedImageFontMetrics.typefaceFor(font));
        return p;
    }

    private static void applyAa(android.graphics.Paint p, TextAa aa) {
        switch (aa) {
            case OFF:
                p.setAntiAlias(false);
                p.setSubpixelText(false);
                break;
            case LCD:
                p.setAntiAlias(true);
                p.setSubpixelText(true);
                break;
            default:
                p.setAntiAlias(true);
                p.setSubpixelText(false);
                break;
        }
    }

    @Override public void drawString(String str, float x, float y) { drawString(str, (int) x, (int) y); }
    @Override public void drawString(AttributedCharacterIterator it, int x, int y) {}
    @Override public void drawString(AttributedCharacterIterator it, float x, float y) {}

    @Override
    public boolean drawImage(java.awt.Image img, int x, int y, ImageObserver obs) {
        if (img instanceof BufferedImage) {
            BufferedImage bi = (BufferedImage) img;
            blit(bi, 0, 0, bi.getWidth(), bi.getHeight(), x, y, bi.getWidth(), bi.getHeight());
        }
        return true;
    }

    @Override
    public boolean drawImage(java.awt.Image img, int x, int y, int w, int h, ImageObserver obs) {
        if (img instanceof BufferedImage) {
            BufferedImage bi = (BufferedImage) img;
            blit(bi, 0, 0, bi.getWidth(), bi.getHeight(), x, y, w, h);
        }
        return true;
    }

    @Override public boolean drawImage(java.awt.Image img, int x, int y, Color bg, ImageObserver obs) { return drawImage(img, x, y, obs); }
    @Override public boolean drawImage(java.awt.Image img, int x, int y, int w, int h, Color bg, ImageObserver obs) { return drawImage(img, x, y, w, h, obs); }

    @Override
    public boolean drawImage(java.awt.Image img, int dx1, int dy1, int dx2, int dy2,
                              int sx1, int sy1, int sx2, int sy2, ImageObserver obs) {
        if (img instanceof BufferedImage) {
            BufferedImage bi = (BufferedImage) img;
            blit(bi, sx1, sy1, sx2 - sx1, sy2 - sy1, dx1, dy1, dx2 - dx1, dy2 - dy1);
        }
        return true;
    }

    @Override
    public boolean drawImage(java.awt.Image img, int dx1, int dy1, int dx2, int dy2,
                              int sx1, int sy1, int sx2, int sy2, Color bg, ImageObserver obs) {
        return drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, obs);
    }

    @Override
    public boolean drawImage(java.awt.Image img, AffineTransform xform, ImageObserver obs) {
        return drawImage(img, (int) xform.getTranslateX(), (int) xform.getTranslateY(), obs);
    }

    @Override
    public void drawImage(BufferedImage img, int x, int y) {
        blit(img, 0, 0, img.getWidth(), img.getHeight(), x, y, img.getWidth(), img.getHeight());
    }

    private void blit(BufferedImage src, int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh) {
        int[] srcPixels = src.backingArray();
        if (srcPixels == null) return;
        syncTarget();

        // Honor the active clip. Without this, components in a JViewport that
        // ever blit an image (icon, sub-buffer, glyph cache hit) overflow the
        // viewport bounds when scrolled — content visually "drags" over the
        // parent UI even though fillRect/drawLine clip correctly. We clip the
        // destination rect to the current clip, then shift the source rect
        // proportionally so the same source pixels map to the same world
        // positions.
        int adx, ady, adx2, ady2;
        if (deviceScale == 1f) {
            adx = dx + tx(); ady = dy + ty(); adx2 = adx + dw; ady2 = ady + dh;
        } else {
            adx = devX(dx); ady = devY(dy);
            adx2 = devX(dx + (double) dw); ady2 = devY(dy + (double) dh);
            dw = adx2 - adx; dh = ady2 - ady;
            if (dw <= 0 || dh <= 0) return;
        }
        int cx0 = clip.x, cy0 = clip.y;
        int cx1 = clip.x + clip.width, cy1 = clip.y + clip.height;
        int ndx = Math.max(adx, cx0);
        int ndy = Math.max(ady, cy0);
        int ndx2 = Math.min(adx2, cx1);
        int ndy2 = Math.min(ady2, cy1);
        int ndw = ndx2 - ndx;
        int ndh = ndy2 - ndy;
        if (ndw <= 0 || ndh <= 0) return;

        // Source shift in destination-pixel units. With non-1:1 scaling the
        // source advance per dest pixel is sw/dw, so multiply the dst clip
        // offset by that ratio. Use long to avoid intermediate overflow.
        int leftCut = ndx - adx;
        int topCut = ndy - ady;
        int nsx = sx + (int) ((long) leftCut * sw / dw);
        int nsy = sy + (int) ((long) topCut * sh / dh);
        int nsw = (int) ((long) ndw * sw / dw);
        int nsh = (int) ((long) ndh * sh / dh);
        if (nsw <= 0 || nsh <= 0) return;

        int ruleDiag = compositeRuleFor(src);

        // TEMP DIAGNOSTIC: software-mode present into a Canvas backbuffer. Logs whether the
        // source image (the engine's rasterized frame) actually has content and what
        // composite rule we use — so we can tell "engine rendered nothing" from "blit dropped it".
        if (backbufferSource != null && !java.awt.Canvas.isRenderedByGles())
        {
            long now = System.currentTimeMillis();
            if (now - lastSwBlitLog > 1000)
            {
                lastSwBlitLog = now;
                int sw2 = src.getWidth(), sh2 = src.getHeight();
                int ci = (sh2 / 2) * sw2 + sw2 / 2;
                int srcCenter = (ci >= 0 && ci < srcPixels.length) ? srcPixels[ci] : -1;
                java.awt.image.ColorModel cm = src.getColorModel();
                android.util.Log.i("SwDiag", "PRESENT src=" + sw2 + "x" + sh2
                    + " srcCenter=0x" + Integer.toHexString(srcCenter)
                    + " srcType=" + src.getType() + " hasAlpha=" + (cm != null && cm.hasAlpha())
                    + " opaqueSrc=" + isOpaqueSource(src) + " rule=" + ruleDiag + "(SRC=" + AlphaComposite.SRC + ")"
                    + " dst=" + width + "x" + height + " dstRect=[" + ndx + "," + ndy + " " + ndw + "x" + ndh + "]");
            }
        }

        AwtNative.blit(srcPixels, src.getWidth(), src.getHeight(), nsx, nsy, nsw, nsh,
            pixels, width, height, ndx, ndy, ndw, ndh,
            ruleDiag, compositeAlpha());
    }

    private static long lastSwBlitLog = 0;

    /**
     * Pick the composite for blitting from this source image. For opaque source images
     * (no alpha channel) the JDK treats source alpha as 1.0 regardless of what's in the
     * pixel's high byte. Our Rust src_over honors source alpha literally — which means
     * for OSRS-style raw RGB writes (alpha bits = 0) the blit becomes a no-op. Promote
     * to SRC so the source pixels overwrite the destination unconditionally.
     */
    private int compositeRuleFor(BufferedImage src) {
        int rule = compositeRule();
        if (rule == AlphaComposite.SRC_OVER
            && (isOpaqueSource(src)
                || (backbufferSource != null && !java.awt.Canvas.isRenderedByGles()))) {
            return AlphaComposite.SRC;
        }
        return rule;
    }

    private static boolean isOpaqueSource(BufferedImage src) {
        if (isOpaqueType(src.getType())) return true;
        java.awt.image.ColorModel cm = src.getColorModel();
        return cm != null && !cm.hasAlpha();
    }

    private static boolean isOpaqueType(int t) {
        return t == BufferedImage.TYPE_INT_RGB
            || t == BufferedImage.TYPE_INT_BGR
            || t == BufferedImage.TYPE_USHORT_565_RGB
            || t == BufferedImage.TYPE_USHORT_555_RGB
            || t == BufferedImage.TYPE_3BYTE_BGR
            || t == BufferedImage.TYPE_BYTE_GRAY
            || t == BufferedImage.TYPE_USHORT_GRAY;
    }

    private int compositeRule() {
        if (composite instanceof AlphaComposite) return ((AlphaComposite) composite).getRule();
        return AlphaComposite.SRC_OVER;
    }

    private float compositeAlpha() {
        if (composite instanceof AlphaComposite) return ((AlphaComposite) composite).getAlpha();
        return 1f;
    }

    @Override public void draw(Shape s) {
        if (s instanceof Polygon) {
            Polygon p = (Polygon) s;
            drawPolygon(p.xpoints, p.ypoints, p.npoints);
            return;
        }
        if (s instanceof Rectangle) {
            Rectangle r = (Rectangle) s;
            drawRect(r.x, r.y, r.width, r.height);
            return;
        }
        if (s instanceof java.awt.geom.Ellipse2D) {
            java.awt.geom.Ellipse2D e = (java.awt.geom.Ellipse2D) s;
            drawOval((int) e.getX(), (int) e.getY(), (int) e.getWidth(), (int) e.getHeight());
            return;
        }
        if (s instanceof java.awt.geom.Arc2D) {
            java.awt.geom.Arc2D a = (java.awt.geom.Arc2D) s;
            drawArc((int) a.getX(), (int) a.getY(), (int) a.getWidth(), (int) a.getHeight(),
                (int) Math.round(a.getAngleStart()), (int) Math.round(a.getAngleExtent()));
            return;
        }
        float strokeWidth = 1f;
        int cap = BasicStroke.CAP_SQUARE;
        if (stroke instanceof BasicStroke) {
            BasicStroke bs = (BasicStroke) stroke;
            strokeWidth = bs.getLineWidth();
            cap = bs.getEndCap();
        }
        if (strokeWidth > 1f && strokeAndFillPath(s, strokeWidth, cap)) {
            return;
        }
        if (walkShape(s, false)) return;
        Rectangle b = s.getBounds();
        drawRect(b.x, b.y, b.width, b.height);
    }

    private static final ThreadLocal<float[]> COORDS_SCRATCH = new ThreadLocal<float[]>() {
        @Override protected float[] initialValue() { return new float[6]; }
    };

    private static final ThreadLocal<int[][]> THICK_SCRATCH = new ThreadLocal<int[][]>() {
        @Override protected int[][] initialValue() { return new int[][] { new int[4], new int[4] }; }
    };

    private static final ThreadLocal<int[][]> WALK_SCRATCH = new ThreadLocal<int[][]>() {
        @Override protected int[][] initialValue() { return new int[][] { new int[64], new int[64] }; }
    };

    private boolean strokeAndFillPath(Shape s, float width, int cap) {
        java.awt.geom.PathIterator it = s.getPathIterator(null, 1.0);
        if (it == null) return false;
        float[] coords = COORDS_SCRATCH.get();
        float firstX = 0f, firstY = 0f, curX = 0f, curY = 0f;
        boolean hasCur = false;
        boolean any = false;
        while (!it.isDone()) {
            int seg = it.currentSegment(coords);
            switch (seg) {
                case java.awt.geom.PathIterator.SEG_MOVETO:
                    firstX = coords[0]; firstY = coords[1];
                    curX = firstX; curY = firstY;
                    hasCur = true;
                    break;
                case java.awt.geom.PathIterator.SEG_LINETO:
                    if (hasCur && emitThickSegment(curX, curY, coords[0], coords[1], width, cap)) any = true;
                    curX = coords[0]; curY = coords[1];
                    hasCur = true;
                    break;
                case java.awt.geom.PathIterator.SEG_CLOSE:
                    if (hasCur && emitThickSegment(curX, curY, firstX, firstY, width, cap)) any = true;
                    curX = firstX; curY = firstY;
                    break;
                default:
                    break;
            }
            it.next();
        }
        return any;
    }

    private boolean emitThickSegment(float x1, float y1, float x2, float y2, float width, int cap) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return false;
        float hw = width * 0.5f;
        float nx = -dy / len * hw;
        float ny = dx / len * hw;
        float ex = 0f, ey = 0f;
        if (cap == BasicStroke.CAP_SQUARE || cap == BasicStroke.CAP_ROUND) {
            ex = dx / len * hw;
            ey = dy / len * hw;
        }
        int[][] scratch = THICK_SCRATCH.get();
        int[] xs = scratch[0];
        int[] ys = scratch[1];
        xs[0] = Math.round(x1 - ex + nx);
        xs[1] = Math.round(x1 - ex - nx);
        xs[2] = Math.round(x2 + ex - nx);
        xs[3] = Math.round(x2 + ex + nx);
        ys[0] = Math.round(y1 - ey + ny);
        ys[1] = Math.round(y1 - ey - ny);
        ys[2] = Math.round(y2 + ey - ny);
        ys[3] = Math.round(y2 + ey + ny);
        fillPolygon(xs, ys, 4);
        return true;
    }

    @Override public void fill(Shape s) {
        if (s instanceof Polygon) {
            Polygon p = (Polygon) s;
            fillPolygon(p.xpoints, p.ypoints, p.npoints);
            return;
        }
        if (s instanceof Rectangle) {
            Rectangle r = (Rectangle) s;
            fillRect(r.x, r.y, r.width, r.height);
            return;
        }
        if (s instanceof java.awt.geom.Ellipse2D) {
            java.awt.geom.Ellipse2D e = (java.awt.geom.Ellipse2D) s;
            fillOval((int) e.getX(), (int) e.getY(), (int) e.getWidth(), (int) e.getHeight());
            return;
        }
        if (s instanceof java.awt.geom.Arc2D) {
            java.awt.geom.Arc2D a = (java.awt.geom.Arc2D) s;
            fillArc((int) a.getX(), (int) a.getY(), (int) a.getWidth(), (int) a.getHeight(),
                (int) Math.round(a.getAngleStart()), (int) Math.round(a.getAngleExtent()));
            return;
        }
        if (walkShape(s, true)) return;
        Rectangle b = s.getBounds();
        fillRect(b.x, b.y, b.width, b.height);
    }

    private boolean walkShape(Shape s, boolean fill) {
        java.awt.geom.PathIterator it = s.getPathIterator(null, 1.0);
        if (it == null) return false;
        int[][] scratch = WALK_SCRATCH.get();
        int[] xs = scratch[0];
        int[] ys = scratch[1];
        int n = 0;
        boolean closed = false;
        float[] coords = COORDS_SCRATCH.get();
        boolean any = false;
        while (!it.isDone()) {
            int seg = it.currentSegment(coords);
            switch (seg) {
                case java.awt.geom.PathIterator.SEG_MOVETO:
                    if (emitSubPath(xs, ys, n, fill, closed)) any = true;
                    n = 0;
                    closed = false;
                    xs[n] = (int) coords[0]; ys[n] = (int) coords[1]; n++;
                    break;
                case java.awt.geom.PathIterator.SEG_LINETO:
                    if (n >= xs.length) {
                        // Publish the grown arrays back into the scratch. Without this the
                        // growth was thrown away at the end of the call, so every path with
                        // more than 64 vertices re-grew from scratch — two array allocations
                        // per doubling, per draw.
                        xs = grow(xs); ys = grow(ys);
                        scratch[0] = xs; scratch[1] = ys;
                    }
                    xs[n] = (int) coords[0]; ys[n] = (int) coords[1]; n++;
                    break;
                case java.awt.geom.PathIterator.SEG_CLOSE:
                    closed = true;
                    if (emitSubPath(xs, ys, n, fill, closed)) any = true;
                    n = 0;
                    closed = false;
                    break;
                default:
                    break;
            }
            it.next();
        }
        if (emitSubPath(xs, ys, n, fill, closed)) any = true;
        return any;
    }


    /**
     * Rasterize a single sub-path. Three shapes are possible per emission:
     *   - n == 2 → a single straight segment (stroked as drawLine, never filled)
     *   - n  > 2 with SEG_CLOSE → a closed polygon (fillPolygon if fill, else closed polyline)
     *   - n  > 2 without SEG_CLOSE → an open polyline (stroked as a chain of drawLines; fills
     *     are still rasterized as polygons because that's what Graphics2D.fill(Shape) implies)
     * The previous implementation gated everything on {@code n >= 3} and always drew closed,
     * so paths that were a sequence of two-vertex {@code moveTo + lineTo} pairs (NPC unaggro
     * area boundary lines, etc.) emitted nothing.
     */
    private boolean emitSubPath(int[] xs, int[] ys, int n, boolean fill, boolean closed) {
        if (fill) {
            if (n < 3) return false;
            fillPolygon(xs, ys, n);
            return true;
        }
        if (n < 2) return false;
        if (n == 2) {
            drawLine(xs[0], ys[0], xs[1], ys[1]);
            return true;
        }
        if (closed) {
            drawClosedPolyline(xs, ys, n);
        } else {
            drawOpenPolyline(xs, ys, n);
        }
        return true;
    }

    private void drawOpenPolyline(int[] xs, int[] ys, int n) {
        for (int i = 0; i < n - 1; i++) drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
    }

    private void drawClosedPolyline(int[] xs, int[] ys, int n) {
        for (int i = 0; i < n - 1; i++) drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
        drawLine(xs[n - 1], ys[n - 1], xs[0], ys[0]);
    }

    private static int[] grow(int[] a) {
        int[] n = new int[a.length * 2];
        System.arraycopy(a, 0, n, 0, a.length);
        return n;
    }

    @Override public Composite getComposite() { return composite; }
    @Override public void setComposite(Composite comp) { if (comp != null) composite = comp; }

    @Override public Paint getPaint() { return paint; }
    @Override public void setPaint(Paint paint) {
        if (paint != null) {
            this.paint = paint;
            if (paint instanceof Color) this.foreground = (Color) paint;
        }
    }

    @Override public Stroke getStroke() { return stroke; }
    @Override public void setStroke(Stroke s) { if (s != null) stroke = s; }

    @Override public AffineTransform getTransform() { return new AffineTransform(transform); }
    @Override public void setTransform(AffineTransform tx) { if (tx != null) transform.setTransform(tx); }
    @Override public void transform(AffineTransform tx) {
        // Compose; ignored beyond translation for now.
        translate(tx.getTranslateX(), tx.getTranslateY());
    }
    @Override public void rotate(double theta) {}
    @Override public void rotate(double theta, double x, double y) {}
    @Override public void scale(double sx, double sy) { transform.scale(sx, sy); }
    @Override public void shear(double shx, double shy) {}

    @Override public void setRenderingHint(RenderingHints.Key key, Object val) { renderingHints.put(key, val); }
    @Override public Object getRenderingHint(RenderingHints.Key key) { return renderingHints.get(key); }
    @Override public void setRenderingHints(java.util.Map<?, ?> hints) { renderingHints.clear(); renderingHints.putAll(hints); }
    @Override public void addRenderingHints(java.util.Map<?, ?> hints) { renderingHints.putAll(hints); }
    @Override public RenderingHints getRenderingHints() { return (RenderingHints) renderingHints.clone(); }

    @Override public FontRenderContext getFontRenderContext() { return new FontRenderContext(transform, false, false); }

    @Override public void dispose() {}
}
