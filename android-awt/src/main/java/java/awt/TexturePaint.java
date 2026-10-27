package java.awt;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.util.Objects;

/** An image repeated in user space over an anchor rectangle. */
public class TexturePaint implements Paint {
    private final BufferedImage image;
    private final Rectangle2D anchor;

    public TexturePaint(BufferedImage image, Rectangle2D anchor) {
        this.image = Objects.requireNonNull(image, "image");
        this.anchor = new Rectangle2D.Double(anchor.getX(), anchor.getY(),
            anchor.getWidth(), anchor.getHeight());
    }

    public BufferedImage getImage() { return image; }
    public Rectangle2D getAnchorRect() {
        return new Rectangle2D.Double(anchor.getX(), anchor.getY(), anchor.getWidth(), anchor.getHeight());
    }
    @Override public int getTransparency() { return image.getColorModel().getTransparency(); }

    @Override public PaintContext createContext(ColorModel cm, Rectangle bounds,
            Rectangle2D userBounds, AffineTransform xform, RenderingHints hints) {
        final double a = xform.getScaleX(), b = xform.getShearX(), c = xform.getTranslateX();
        final double d = xform.getShearY(), e = xform.getScaleY(), f = xform.getTranslateY();
        final double det = a * e - b * d;
        return new PaintContext() {
            private BufferedImage buffer;
            @Override public void dispose() { buffer = null; }
            @Override public ColorModel getColorModel() {
                return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).getColorModel();
            }
            @Override public Raster getRaster(int x, int y, int w, int h) {
                if (buffer == null || buffer.getWidth() != w || buffer.getHeight() != h)
                    buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                for (int row = 0; row < h; row++) {
                    for (int col = 0; col < w; col++) {
                        double dx = x + col + 0.5 - c, dy = y + row + 0.5 - f;
                        double ux = det == 0 ? 0 : (e * dx - b * dy) / det;
                        double uy = det == 0 ? 0 : (a * dy - d * dx) / det;
                        double u = (ux - anchor.getX()) / anchor.getWidth();
                        double v = (uy - anchor.getY()) / anchor.getHeight();
                        int sx = (int) ((u - Math.floor(u)) * image.getWidth());
                        int sy = (int) ((v - Math.floor(v)) * image.getHeight());
                        buffer.setRGB(col, row, image.getRGB(sx, sy));
                    }
                }
                return buffer.getRaster();
            }
        };
    }
}
