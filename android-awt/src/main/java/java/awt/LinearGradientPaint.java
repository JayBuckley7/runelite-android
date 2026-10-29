package java.awt;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

public final class LinearGradientPaint extends MultipleGradientPaint {
    private final Point2D start, end;
    public LinearGradientPaint(float x1, float y1, float x2, float y2, float[] fractions, Color[] colors) {
        this(new Point2D.Float(x1, y1), new Point2D.Float(x2, y2), fractions, colors);
    }
    public LinearGradientPaint(float x1, float y1, float x2, float y2, float[] fractions, Color[] colors, CycleMethod cycle) {
        this(new Point2D.Float(x1, y1), new Point2D.Float(x2, y2), fractions, colors, cycle);
    }
    public LinearGradientPaint(Point2D start, Point2D end, float[] fractions, Color[] colors) {
        this(start, end, fractions, colors, CycleMethod.NO_CYCLE);
    }
    public LinearGradientPaint(Point2D start, Point2D end, float[] fractions, Color[] colors, CycleMethod cycle) {
        this(start, end, fractions, colors, cycle, ColorSpaceType.SRGB, new AffineTransform());
    }
    public LinearGradientPaint(Point2D start, Point2D end, float[] fractions, Color[] colors, CycleMethod cycle, ColorSpaceType space, AffineTransform transform) {
        super(fractions, colors, cycle, space, transform);
        this.start = new Point2D.Double(start.getX(), start.getY());
        this.end = new Point2D.Double(end.getX(), end.getY());
        if (this.start.equals(this.end)) throw new IllegalArgumentException("Gradient endpoints coincide");
    }
    public Point2D getStartPoint() { return new Point2D.Double(start.getX(), start.getY()); }
    public Point2D getEndPoint() { return new Point2D.Double(end.getX(), end.getY()); }
    protected double fraction(double x, double y) {
        double dx = end.getX() - start.getX(), dy = end.getY() - start.getY();
        return ((x - start.getX()) * dx + (y - start.getY()) * dy) / (dx * dx + dy * dy);
    }
}
