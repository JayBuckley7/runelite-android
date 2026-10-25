package java.awt;

import java.awt.image.ImageObserver;

public abstract class Graphics {
    protected Graphics() {
    }

    public abstract Graphics create();

    public Graphics create(int x, int y, int width, int height) {
        Graphics g = create();
        if (g == null) return null;
        g.translate(x, y);
        g.clipRect(0, 0, width, height);
        return g;
    }

    public abstract void translate(int x, int y);

    public abstract Color getColor();
    public abstract void setColor(Color c);

    public abstract void setPaintMode();
    public abstract void setXORMode(Color c1);

    public abstract Font getFont();
    public abstract void setFont(Font font);

    public FontMetrics getFontMetrics() {
        return getFontMetrics(getFont());
    }

    public abstract FontMetrics getFontMetrics(Font f);

    public abstract Rectangle getClipBounds();
    public abstract void clipRect(int x, int y, int width, int height);
    public abstract void setClip(int x, int y, int width, int height);
    public abstract Shape getClip();
    public abstract void setClip(Shape clip);

    public boolean hitClip(int x, int y, int width, int height) {
        Rectangle c = getClipBounds();
        return c == null || c.intersects(x, y, width, height);
    }

    public abstract void copyArea(int x, int y, int width, int height, int dx, int dy);
    public abstract void drawLine(int x1, int y1, int x2, int y2);
    public abstract void fillRect(int x, int y, int width, int height);
    public abstract void clearRect(int x, int y, int width, int height);
    public abstract void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight);
    public abstract void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight);
    public abstract void drawOval(int x, int y, int width, int height);
    public abstract void fillOval(int x, int y, int width, int height);
    public abstract void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle);
    public abstract void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle);
    public abstract void drawPolyline(int[] xPoints, int[] yPoints, int nPoints);
    public abstract void drawPolygon(int[] xPoints, int[] yPoints, int nPoints);
    public abstract void fillPolygon(int[] xPoints, int[] yPoints, int nPoints);

    public void drawRect(int x, int y, int width, int height) {
        if (width < 0 || height < 0) return;
        if (height == 0 || width == 0) {
            drawLine(x, y, x + width, y + height);
        } else {
            drawLine(x, y, x + width - 1, y);
            drawLine(x + width, y, x + width, y + height - 1);
            drawLine(x + width, y + height, x + 1, y + height);
            drawLine(x, y + height, x, y + 1);
        }
    }

    public void drawPolygon(Polygon p) { drawPolygon(p.xpoints, p.ypoints, p.npoints); }
    public void fillPolygon(Polygon p) { fillPolygon(p.xpoints, p.ypoints, p.npoints); }

    public abstract void drawString(String str, int x, int y);
    public abstract boolean drawImage(java.awt.Image img, int x, int y, ImageObserver observer);
    public abstract boolean drawImage(java.awt.Image img, int x, int y, int width, int height, ImageObserver observer);
    public abstract boolean drawImage(java.awt.Image img, int x, int y, Color bgcolor, ImageObserver observer);
    public abstract boolean drawImage(java.awt.Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer);
    public abstract boolean drawImage(java.awt.Image img, int dx1, int dy1, int dx2, int dy2,
                                       int sx1, int sy1, int sx2, int sy2, ImageObserver observer);
    public abstract boolean drawImage(java.awt.Image img, int dx1, int dy1, int dx2, int dy2,
                                       int sx1, int sy1, int sx2, int sy2, Color bgcolor, ImageObserver observer);

    public abstract void dispose();

    public void finalize() {
        dispose();
    }
}
