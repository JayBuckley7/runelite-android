package java.awt;

import java.awt.datatransfer.Clipboard;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;

public abstract class Toolkit {
    private static Toolkit defaultToolkit;
    private final EventQueue eventQueue = new EventQueue();
    private final Clipboard clipboard = new Clipboard("system");

    protected Toolkit() {
    }

    public static synchronized Toolkit getDefaultToolkit() {
        if (defaultToolkit == null) {
            defaultToolkit = new HeadlessToolkit();
        }
        return defaultToolkit;
    }

    public Clipboard getSystemClipboard() {
        return clipboard;
    }

    public EventQueue getSystemEventQueue() {
        return eventQueue;
    }

    public Dimension getScreenSize() {
        Rectangle bounds = net.runelite.awt.impl.HeadlessGraphicsConfiguration.INSTANCE.getBounds();
        return new Dimension(bounds.width, bounds.height);
    }

    public int getScreenResolution() {
        return net.runelite.awt.impl.HeadlessGraphicsConfiguration.getResolution();
    }

    public ColorModel getColorModel() {
        return new java.awt.image.DirectColorModel(32, 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000);
    }

    public String[] getFontList() {
        return new String[]{Font.DIALOG, Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED};
    }

    public Image createImage(int[] pixels, int width, int height) {
        java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(
            width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        bi.setRGB(0, 0, width, height, pixels, 0, width);
        return bi;
    }

    /** Decode via ImageIO so callers that go through Toolkit (rather than
     *  ImageIO directly) actually get a populated image. Real Swing/Toolkit
     *  caches by filename + does async loading via ImageObserver; we just
     *  return a fully-decoded BufferedImage synchronously. */
    public Image getImage(String filename) {
        if (filename == null) return null;
        try { return javax.imageio.ImageIO.read(new java.io.File(filename)); }
        catch (Throwable ignored) { return null; }
    }

    public Image getImage(java.net.URL url) {
        if (url == null) return null;
        try { return javax.imageio.ImageIO.read(url); }
        catch (Throwable ignored) { return null; }
    }

    public Image createImage(java.awt.image.ImageProducer producer) { return new java.awt.image.ProducerImage(producer); }

    public Image createImage(String filename) { return getImage(filename); }
    public Image createImage(java.net.URL url) { return getImage(url); }
    public Image createImage(byte[] data) {
        if (data == null) return null;
        try { return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data)); }
        catch (Throwable ignored) { return null; }
    }
    public Image createImage(byte[] data, int off, int len) {
        if (data == null) return null;
        try { return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data, off, len)); }
        catch (Throwable ignored) { return null; }
    }

    /** Android can't host an OS-level cursor — just hand back a Cursor object
     *  so callers that expect a non-null don't NPE. CustomCursorPlugin etc.
     *  used to hit a NoSuchMethodError here and abort plugin startup. */
    public Cursor createCustomCursor(Image image, Point hotspot, String name) {
        return new Cursor(Cursor.CUSTOM_CURSOR);
    }

    public void sync() {}
    public void beep() {}

    public boolean isFrameStateSupported(int state) { return state == Frame.NORMAL; }

    public boolean prepareImage(Image image, int width, int height, ImageObserver observer) {
        return true;
    }

    public int checkImage(Image image, int width, int height, ImageObserver observer) {
        return ImageObserver.ALLBITS;
    }

    private static final class HeadlessToolkit extends Toolkit {
    }
}
