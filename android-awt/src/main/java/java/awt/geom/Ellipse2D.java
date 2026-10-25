package java.awt.geom;

import java.awt.Shape;
import java.awt.Rectangle;
import java.io.Serializable;

public abstract class Ellipse2D extends RectangularShape {
    protected Ellipse2D() {}

    @Override
    public boolean contains(double x, double y) {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return false;
        double nx = (x - getX()) / w - 0.5;
        double ny = (y - getY()) / h - 0.5;
        return nx * nx + ny * ny < 0.25;
    }

    @Override
    public boolean intersects(double x, double y, double w, double h) {
        return getBounds2D().intersects(x, y, w, h);
    }

    @Override
    public boolean contains(double x, double y, double w, double h) {
        return contains(x, y) && contains(x + w, y) && contains(x, y + h) && contains(x + w, y + h);
    }

    @Override
    public PathIterator getPathIterator(AffineTransform at) {
        return ArcIterator.arc(getX(), getY(), getWidth(), getHeight(), 0, 360, Arc2D.CHORD, at);
    }

    @Override
    public PathIterator getPathIterator(AffineTransform at, double flatness) {
        return getPathIterator(at);
    }

    public static class Double extends Ellipse2D implements Serializable {
        private static final long serialVersionUID = 5555464816971876003L;
        public double x, y, width, height;
        public Double() {}
        public Double(double x, double y, double w, double h) { setFrame(x, y, w, h); }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public boolean isEmpty() { return width <= 0 || height <= 0; }
        @Override public void setFrame(double x, double y, double w, double h) {
            this.x = x; this.y = y; this.width = w; this.height = h;
        }
        @Override public Rectangle2D getBounds2D() { return new Rectangle2D.Double(x, y, width, height); }
    }

    public static class Float extends Ellipse2D implements Serializable {
        private static final long serialVersionUID = -6633761252372475977L;
        public float x, y, width, height;
        public Float() {}
        public Float(float x, float y, float w, float h) { setFrame(x, y, w, h); }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public boolean isEmpty() { return width <= 0 || height <= 0; }
        @Override public void setFrame(double x, double y, double w, double h) {
            this.x = (float) x; this.y = (float) y; this.width = (float) w; this.height = (float) h;
        }
        @Override public Rectangle2D getBounds2D() { return new Rectangle2D.Float(x, y, width, height); }
    }

    static class EmptyPathIterator implements PathIterator {
        @Override public int getWindingRule() { return WIND_NON_ZERO; }
        @Override public boolean isDone() { return true; }
        @Override public void next() {}
        @Override public int currentSegment(float[] coords) { return SEG_CLOSE; }
        @Override public int currentSegment(double[] coords) { return SEG_CLOSE; }
    }
}
