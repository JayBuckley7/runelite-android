package net.runelite.awt.impl;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;

public final class HeadlessGraphicsConfiguration extends GraphicsConfiguration {
    public static final HeadlessGraphicsConfiguration INSTANCE = new HeadlessGraphicsConfiguration();

    private static volatile int[] display = {1, 1, 96};

    /** Host viewport dimensions in AWT logical pixels, updated after display or window changes. */
    public static void updateDisplay(int width, int height, int dpi) {
        if (width <= 0 || height <= 0 || dpi <= 0) throw new IllegalArgumentException("Display metrics");
        display = new int[]{width, height, dpi};
    }
    public static int getResolution() { return display[2]; }

    private final GraphicsDevice device = new HeadlessGraphicsDevice(this);

    private HeadlessGraphicsConfiguration() {}

    @Override public GraphicsDevice getDevice() { return device; }
    @Override public Rectangle getBounds() { int[] metrics = display; return new Rectangle(0, 0, metrics[0], metrics[1]); }
}
