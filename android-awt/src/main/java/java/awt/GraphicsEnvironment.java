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

    public Point getCenterPoint() { Rectangle bounds = getMaximumWindowBounds(); return new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2); }
    public String[] getAvailableFontFamilyNames(java.util.Locale locale) { java.util.Objects.requireNonNull(locale); return getAvailableFontFamilyNames(); }

    private static final class HeadlessGraphicsEnvironment extends GraphicsEnvironment {
        @Override public GraphicsDevice[] getScreenDevices() { return new GraphicsDevice[]{getDefaultScreenDevice()}; }
        @Override public GraphicsDevice getDefaultScreenDevice() {
            return net.runelite.awt.impl.HeadlessGraphicsConfiguration.INSTANCE.getDevice();
        }
        @Override public Graphics2D createGraphics(java.awt.image.BufferedImage img) { return img.createGraphics(); }
        @Override public Font[] getAllFonts() { return new Font[]{new Font(Font.DIALOG, Font.PLAIN, 12)}; }
        @Override public String[] getAvailableFontFamilyNames() {
            return new String[]{Font.DIALOG, Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED};
        }
    }
}
