package java.awt;

public class BasicStroke implements Stroke {
    public static final int JOIN_MITER = 0;
    public static final int JOIN_ROUND = 1;
    public static final int JOIN_BEVEL = 2;
    public static final int CAP_BUTT = 0;
    public static final int CAP_ROUND = 1;
    public static final int CAP_SQUARE = 2;

    private final float width;
    private final int cap;
    private final int join;
    private final float miterLimit;
    private final float[] dash;
    private final float dashPhase;

    public BasicStroke() {
        this(1f);
    }

    public BasicStroke(float width) {
        this(width, CAP_SQUARE, JOIN_MITER, 10f, null, 0f);
    }

    public BasicStroke(float width, int cap, int join) {
        this(width, cap, join, 10f, null, 0f);
    }

    public BasicStroke(float width, int cap, int join, float miterLimit) {
        this(width, cap, join, miterLimit, null, 0f);
    }

    public BasicStroke(float width, int cap, int join, float miterLimit, float[] dash, float dashPhase) {
        this.width = width;
        this.cap = cap;
        this.join = join;
        this.miterLimit = miterLimit;
        this.dash = dash != null ? dash.clone() : null;
        this.dashPhase = dashPhase;
    }

    public float getLineWidth()  { return width; }
    public int getEndCap()       { return cap; }
    public int getLineJoin()     { return join; }
    public float getMiterLimit() { return miterLimit; }
    public float[] getDashArray() { return dash != null ? dash.clone() : null; }
    public float getDashPhase()  { return dashPhase; }

    /** The stroke outline as overlapping, consistently oriented pieces under non-zero
     *  winding — fills (and {@code new Area(...)}) to exactly the stroked region. */
    @Override
    public Shape createStrokedShape(Shape p) {
        java.awt.geom.Path2D.Double out = new java.awt.geom.Path2D.Double(java.awt.geom.Path2D.WIND_NON_ZERO);
        if (p == null) return out;
        net.runelite.awt.impl.Contours path = new net.runelite.awt.impl.Contours();
        path.appendFlattened(p.getPathIterator(null), null, 1.0, Math.max(0.01, Math.min(0.25, width / 8)));
        if (dash != null) {
            net.runelite.awt.impl.Contours dashed = new net.runelite.awt.impl.Contours();
            net.runelite.awt.impl.Stroker.dash(path, dash, 1.0, dashPhase, dashed);
            path = dashed;
        }
        net.runelite.awt.impl.Contours pieces = new net.runelite.awt.impl.Contours();
        net.runelite.awt.impl.Stroker.stroke(path, width / 2.0, cap, join, miterLimit, pieces);
        int off = 0;
        for (int c = 0; c < pieces.n; c++) {
            for (int i = 0; i < pieces.counts[c]; i++) {
                double x = pieces.xy[off + i * 2], y = pieces.xy[off + i * 2 + 1];
                if (i == 0) out.moveTo(x, y); else out.lineTo(x, y);
            }
            out.closePath();
            off += pieces.counts[c] * 2;
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BasicStroke)) return false;
        BasicStroke b = (BasicStroke) o;
        return width == b.width && cap == b.cap && join == b.join && miterLimit == b.miterLimit
            && java.util.Arrays.equals(dash, b.dash) && dashPhase == b.dashPhase;
    }

    @Override
    public int hashCode() {
        int h = Float.floatToIntBits(width);
        h = h * 31 + cap;
        h = h * 31 + join;
        h = h * 31 + Float.floatToIntBits(miterLimit);
        h = h * 31 + java.util.Arrays.hashCode(dash);
        return h * 31 + Float.floatToIntBits(dashPhase);
    }
}
