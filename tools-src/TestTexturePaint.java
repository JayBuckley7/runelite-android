import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;

/** Run with the Android TexturePaint classes patched into java.desktop. */
public final class TestTexturePaint {
    public static void main(String[] args) {
        BufferedImage tile = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        int[] colors = {0xffff0000, 0xff00ff00, 0xff0000ff, 0x800000ff};
        tile.setRGB(0, 0, 2, 2, colors, 0, 2);
        Rectangle anchor = new Rectangle(0, 0, 2, 2);
        TexturePaint paint = new TexturePaint(tile, anchor);
        anchor.width = 100;
        paint.getAnchorRect().setRect(0, 0, 100, 100);
        check(paint.getAnchorRect().getWidth() == 2, "Anchor must be copied");
        check(paint.getImage() == tile, "Image identity");
        check(paint.getTransparency() == Transparency.TRANSLUCENT, "Transparency");
        PaintContext c = paint.createContext(null, new Rectangle(), new Rectangle(),
            new AffineTransform(), new RenderingHints(null));
        Raster r = c.getRaster(-2, -2, 6, 6);
        for (int y=0;y<6;y++) for (int x=0;x<6;x++)
            check(c.getColorModel().getRGB(((DataBufferInt) r.getDataBuffer()).getData()[y*6+x]) == colors[(y%2)*2+x%2], "Negative-coordinate tiling");
        c.dispose();
        BufferedImage target = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.translate(2, 2);
        g.setClip(0, 0, 6, 6);
        g.scale(2, 2);
        g.setPaint(paint);
        g.fillRect(-10,-10,20,20);
        g.dispose();
        for (int y=0;y<10;y++) for (int x=0;x<10;x++) {
            int expected = x>=2 && x<8 && y>=2 && y<8 ? colors[((y-2)/2%2)*2+(x-2)/2%2] : 0;
            check(target.getRGB(x,y)==expected, "Translated/scaled/clipped texture at " + x + "," + y);
        }
        BufferedImage masked = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D m = masked.createGraphics();
        m.setPaint(paint);
        m.setClip(new Ellipse2D.Double(1, 1, 6, 6));
        m.fillRect(0, 0, 8, 8);
        check(masked.getRGB(0,0) == 0 && masked.getRGB(4,4) != 0, "Shape clip");
        m.setClip(null);
        m.setBackground(Color.WHITE);
        m.clearRect(0,0,8,8);
        check(masked.getRGB(4,4) == Color.WHITE.getRGB(), "clearRect must ignore texture");
        m.setColor(Color.RED);
        m.fillRect(0,0,8,8);
        check(masked.getRGB(4,4) == Color.RED.getRGB(), "setColor must replace texture");
        m.dispose();
        System.out.println("PASS: anchor ownership, repeat, negative coordinates, alpha, translation, scaling and clip");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
