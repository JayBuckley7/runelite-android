package java.awt;

public abstract class GraphicsConfiguration {
    protected GraphicsConfiguration() {
    }

    public GraphicsDevice getDevice() { return null; }
    public java.awt.image.ColorModel getColorModel() { return java.awt.image.ColorModel.getRGBdefault(); }
    public java.awt.image.ColorModel getColorModel(int transparency) { return transparency==Transparency.OPAQUE?new java.awt.image.DirectColorModel(24,0xff0000,0xff00,0xff):getColorModel(); }
    public java.awt.geom.AffineTransform getDefaultTransform() { return new java.awt.geom.AffineTransform(); }
    public java.awt.geom.AffineTransform getNormalizingTransform() { return new java.awt.geom.AffineTransform(); }
    public Rectangle getBounds() { return new Rectangle(0, 0, 0, 0); }

    // VolatileImage factories. The patched OSRS gamepack calls these to allocate the
    // SW-mode double buffer; without them the Client thread dies with
    // NoSuchMethodError on first frame and the screen stays black (toggling
    // GpuGlesPlugin doesn't recover because the dead thread won't restart).
    // Backed by a heap-resident BufferedImage via BufferedBackedVolatileImage.
    // Overloads that take ImageCapabilities are intentionally omitted; they declare
    // `throws AWTException` and the gamepack only calls the simple forms.
    public java.awt.image.VolatileImage createCompatibleVolatileImage(int width, int height) {
        return new net.runelite.awt.impl.BufferedBackedVolatileImage(width, height, Transparency.OPAQUE);
    }

    public java.awt.image.VolatileImage createCompatibleVolatileImage(int width, int height, int transparency) {
        return new net.runelite.awt.impl.BufferedBackedVolatileImage(width, height, transparency);
    }
    public java.awt.image.BufferedImage createCompatibleImage(int width,int height){return createCompatibleImage(width,height,Transparency.OPAQUE);}
    public java.awt.image.BufferedImage createCompatibleImage(int width,int height,int transparency){return new java.awt.image.BufferedImage(width,height,transparency==Transparency.OPAQUE?java.awt.image.BufferedImage.TYPE_INT_RGB:java.awt.image.BufferedImage.TYPE_INT_ARGB);}
}
