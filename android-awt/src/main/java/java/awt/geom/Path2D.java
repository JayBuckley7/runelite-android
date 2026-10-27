package java.awt.geom;

import java.awt.Rectangle;
import java.awt.Shape;

/**
 * Shadow of {@link java.awt.geom.Path2D}. Real OpenJDK has Path2D as the abstract parent
 * of GeneralPath; this shadow tree already ships a standalone {@link GeneralPath}, so this
 * is an independent implementation with the same segment storage plus the two concrete
 * inner subclasses {@link Double} / {@link Float} that callers instantiate.
 *
 * Unlike GeneralPath (whose contains() is only a bounding-box test), {@link #contains}
 * here does a real even-odd point-in-polygon test, because the one external-plugin caller
 * we shadow for — quest-helper's PolyZone — relies on accurate polygon containment to
 * decide whether a world tile is inside a zone. Curve segments (quad/cubic) are treated as
 * straight lines to their endpoint for the containment test; PolyZone only emits lines.
 */
public abstract class Path2D implements Shape, Cloneable
{
    public static final int WIND_EVEN_ODD = PathIterator.WIND_EVEN_ODD;
    public static final int WIND_NON_ZERO = PathIterator.WIND_NON_ZERO;

    private int windingRule;

    byte[] segTypes = new byte[8];
    double[] segCoords = new double[16];
    int numSegs = 0;
    int numCoords = 0;

    private double minX = java.lang.Double.POSITIVE_INFINITY, minY = java.lang.Double.POSITIVE_INFINITY;
    private double maxX = java.lang.Double.NEGATIVE_INFINITY, maxY = java.lang.Double.NEGATIVE_INFINITY;
    private boolean empty = true;

    Path2D(int rule) { this.windingRule = rule; }

    private void ensureSegCapacity(int n) {
        if (numSegs + n > segTypes.length) {
            byte[] nt = new byte[Math.max(segTypes.length * 2, numSegs + n)];
            System.arraycopy(segTypes, 0, nt, 0, numSegs);
            segTypes = nt;
        }
    }
    private void ensureCoordCapacity(int n) {
        if (numCoords + n > segCoords.length) {
            double[] nc = new double[Math.max(segCoords.length * 2, numCoords + n)];
            System.arraycopy(segCoords, 0, nc, 0, numCoords);
            segCoords = nc;
        }
    }
    private void grow(double x, double y) {
        if (empty) { minX = maxX = x; minY = maxY = y; empty = false; return; }
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
    }

    public final void moveTo(double x, double y) {
        ensureSegCapacity(1); ensureCoordCapacity(2);
        segTypes[numSegs++] = (byte) PathIterator.SEG_MOVETO;
        segCoords[numCoords++] = x; segCoords[numCoords++] = y;
        grow(x, y);
    }
    public final void lineTo(double x, double y) {
        ensureSegCapacity(1); ensureCoordCapacity(2);
        segTypes[numSegs++] = (byte) PathIterator.SEG_LINETO;
        segCoords[numCoords++] = x; segCoords[numCoords++] = y;
        grow(x, y);
    }
    public final void quadTo(double x1, double y1, double x2, double y2) {
        ensureSegCapacity(1); ensureCoordCapacity(4);
        segTypes[numSegs++] = (byte) PathIterator.SEG_QUADTO;
        segCoords[numCoords++] = x1; segCoords[numCoords++] = y1;
        segCoords[numCoords++] = x2; segCoords[numCoords++] = y2;
        grow(x1, y1); grow(x2, y2);
    }
    public final void curveTo(double x1, double y1, double x2, double y2, double x3, double y3) {
        ensureSegCapacity(1); ensureCoordCapacity(6);
        segTypes[numSegs++] = (byte) PathIterator.SEG_CUBICTO;
        segCoords[numCoords++] = x1; segCoords[numCoords++] = y1;
        segCoords[numCoords++] = x2; segCoords[numCoords++] = y2;
        segCoords[numCoords++] = x3; segCoords[numCoords++] = y3;
        grow(x1, y1); grow(x2, y2); grow(x3, y3);
    }
    public final void closePath() {
        ensureSegCapacity(1);
        segTypes[numSegs++] = (byte) PathIterator.SEG_CLOSE;
    }

    public final int getWindingRule() { return windingRule; }
    public final void setWindingRule(int rule) { this.windingRule = rule; }

    public final void reset() {
        numSegs = 0; numCoords = 0; empty = true;
        minX = minY = java.lang.Double.POSITIVE_INFINITY;
        maxX = maxY = java.lang.Double.NEGATIVE_INFINITY;
    }

    @Override
    public Rectangle getBounds() {
        if (empty) return new Rectangle();
        return new Rectangle((int) Math.floor(minX), (int) Math.floor(minY),
            (int) Math.ceil(maxX - minX), (int) Math.ceil(maxY - minY));
    }

    @Override
    public Rectangle2D getBounds2D() {
        if (empty) return new Rectangle2D.Double();
        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Even-odd point-in-polygon test over every subpath (each treated as implicitly
     * closed, as Path2D does). Curve endpoints are used as straight-line vertices.
     */
    @Override
    public boolean contains(double x, double y) {
        if (empty || x < minX || x > maxX || y < minY || y > maxY) return false;
        boolean inside = false;
        double startX = 0, startY = 0;   // current subpath's first vertex
        double prevX = 0, prevY = 0;     // previous vertex
        boolean haveSubpath = false;
        int c = 0;
        for (int s = 0; s < numSegs; s++) {
            int t = segTypes[s];
            switch (t) {
                case PathIterator.SEG_MOVETO: {
                    if (haveSubpath && (prevX != startX || prevY != startY)) {
                        if (crosses(x, y, prevX, prevY, startX, startY)) inside = !inside;
                    }
                    startX = segCoords[c]; startY = segCoords[c + 1]; c += 2;
                    prevX = startX; prevY = startY; haveSubpath = true;
                    break;
                }
                case PathIterator.SEG_LINETO: {
                    double nx = segCoords[c], ny = segCoords[c + 1]; c += 2;
                    if (crosses(x, y, prevX, prevY, nx, ny)) inside = !inside;
                    prevX = nx; prevY = ny;
                    break;
                }
                case PathIterator.SEG_QUADTO: {
                    double nx = segCoords[c + 2], ny = segCoords[c + 3]; c += 4;
                    if (crosses(x, y, prevX, prevY, nx, ny)) inside = !inside;
                    prevX = nx; prevY = ny;
                    break;
                }
                case PathIterator.SEG_CUBICTO: {
                    double nx = segCoords[c + 4], ny = segCoords[c + 5]; c += 6;
                    if (crosses(x, y, prevX, prevY, nx, ny)) inside = !inside;
                    prevX = nx; prevY = ny;
                    break;
                }
                case PathIterator.SEG_CLOSE: {
                    if (haveSubpath && (prevX != startX || prevY != startY)) {
                        if (crosses(x, y, prevX, prevY, startX, startY)) inside = !inside;
                    }
                    prevX = startX; prevY = startY;
                    break;
                }
            }
        }
        // implicitly close a trailing open subpath
        if (haveSubpath && (prevX != startX || prevY != startY)) {
            if (crosses(x, y, prevX, prevY, startX, startY)) inside = !inside;
        }
        return inside;
    }

    // Standard ray-cast crossing test: does a +x ray from (px,py) cross edge (ax,ay)-(bx,by)?
    private static boolean crosses(double px, double py, double ax, double ay, double bx, double by) {
        if ((ay > py) == (by > py)) return false;
        return px < (bx - ax) * (py - ay) / (by - ay) + ax;
    }

    @Override
    public boolean contains(double x, double y, double w, double h) {
        return contains(x, y) && contains(x + w, y) && contains(x, y + h) && contains(x + w, y + h);
    }

    @Override
    public boolean intersects(double x, double y, double w, double h) {
        return getBounds2D().intersects(x, y, w, h);
    }

    @Override
    public PathIterator getPathIterator(AffineTransform at) {
        return new Iter(this, at);
    }

    @Override
    public Object clone() {
        try {
            Path2D p = (Path2D) super.clone();
            p.segTypes = segTypes.clone();
            p.segCoords = segCoords.clone();
            return p;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    public static class Double extends Path2D {
        public Double() { super(WIND_NON_ZERO); }
        public Double(int rule) { super(rule); }
    }

    public static class Float extends Path2D {
        public void moveTo(float x,float y) { super.moveTo(x,y); }
        public void lineTo(float x,float y) { super.lineTo(x,y); }
        public Float() { super(WIND_NON_ZERO); }
        public Float(int rule) { super(rule); }
    }

    private static final class Iter implements PathIterator {
        private final Path2D p;
        private final AffineTransform at;
        private int seg = 0, coord = 0;

        Iter(Path2D p, AffineTransform at) { this.p = p; this.at = at; }

        private static int sizeOf(int t) {
            switch (t) {
                case SEG_MOVETO: case SEG_LINETO: return 2;
                case SEG_QUADTO: return 4;
                case SEG_CUBICTO: return 6;
                default: return 0;
            }
        }

        @Override public int getWindingRule() { return p.getWindingRule(); }
        @Override public boolean isDone() { return seg >= p.numSegs; }

        @Override public void next() {
            if (isDone()) return;
            coord += sizeOf(p.segTypes[seg]);
            seg++;
        }

        @Override public int currentSegment(float[] coords) {
            int t = p.segTypes[seg];
            int n = sizeOf(t);
            for (int i = 0; i < n; i++) coords[i] = (float) p.segCoords[coord + i];
            if (at != null && n > 0) at.transform(coords, 0, coords, 0, n / 2);
            return t;
        }

        @Override public int currentSegment(double[] coords) {
            int t = p.segTypes[seg];
            int n = sizeOf(t);
            System.arraycopy(p.segCoords, coord, coords, 0, n);
            if (at != null && n > 0) at.transform(coords, 0, coords, 0, n / 2);
            return t;
        }
    }
}
