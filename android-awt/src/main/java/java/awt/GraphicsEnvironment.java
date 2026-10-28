package java.awt;

public abstract class GraphicsEnvironment {
    private static GraphicsEnvironment localGE;

    protected GraphicsEnvironment() {}

    public static synchronized GraphicsEnvironment getLocalGraphicsEnvironment() {
        if (localGE == null) localGE = new HeadlessGraphicsEnvironment();
        return localGE;
    }

    public abstract GraphicsDevice[] getScreenDevices();
    public abstract GraphicsDevice getDefaultScreenDevice();
    public abstract Graphics2D createGraphics(java.awt.image.BufferedImage img);
    public abstract Font[] getAllFonts();
    public abstract String[] getAvailableFontFamilyNames();

    public static boolean isHeadless() { return true; }
    public boolean isHeadlessInstance() { return true; }

    public Rectangle getMaximumWindowBounds() {
        Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
        return new Rectangle(0, 0, size.width, size.height);
    }
    public boolean registerFont(Font font) { return true; }

    public java.awt.geom.Point2D getCenterPoint() { return new java.awt.geom.Point2D.Double(0, 0); }

    private static final class HeadlessGraphicsEnvironment extends GraphicsEnvironment {
        @Override public GraphicsDevice[] getScreenDevices() { return new GraphicsDevice[0]; }
        @Override public GraphicsDevice getDefaultScreenDevice() {
            throw new java.awt.HeadlessException("No screen devices on Android shadow");
        }
        @Override public Graphics2D createGraphics(java.awt.image.BufferedImage img) { return img.createGraphics(); }
        @Override public Font[] getAllFonts() { return new Font[]{new Font(Font.DIALOG, Font.PLAIN, 12)}; }
        @Override public String[] getAvailableFontFamilyNames() {
            return new String[]{Font.DIALOG, Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED};
        }
    }
}
