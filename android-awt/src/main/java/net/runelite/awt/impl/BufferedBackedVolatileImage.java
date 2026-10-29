package net.runelite.awt.impl;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.ImageCapabilities;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;

/**
 * {@link VolatileImage} implementation backed by a regular {@link BufferedImage}.
 *
 * Real VolatileImage on the JDK uses a managed GPU-backed surface whose contents can
 * be "lost" (display mode change, screensaver, etc.), at which point the application
 * has to re-render. None of those concerns apply to a software rasterizer on Android:
 * the backing BufferedImage lives in heap memory and contents never go invalid. So
 * {@link #validate(GraphicsConfiguration)} always returns {@link #IMAGE_OK} and
 * {@link #contentsLost()} always returns {@code false}.
 *
 * Created by the {@code createCompatibleVolatileImage(int, int[, int])} overloads on
 * {@link GraphicsConfiguration}; the patched OSRS gamepack uses these to construct a
 * double-buffer for software-mode 3D rendering. Without this shim, the SW path dies
 * the first time the engine tries to validate the back-buffer.
 */
public final class BufferedBackedVolatileImage extends VolatileImage {
    private final BufferedImage backing;

    public BufferedBackedVolatileImage(int width, int height, int transparency) {
        // OPAQUE → TYPE_INT_RGB matches the BufferedImage createImage()/createGraphics
        // path the rest of android-awt already uses; anything else keeps a real alpha
        // channel so Java2D composite rules behave.
        int type = transparency == Transparency.OPAQUE
            ? BufferedImage.TYPE_INT_RGB
            : BufferedImage.TYPE_INT_ARGB;
        this.backing = new BufferedImage(width, height, type);
        this.transparency = transparency;
    }

    @Override public BufferedImage getSnapshot() { BufferedImage copy=new BufferedImage(getWidth(),getHeight(),backing.getType());copy.setRGB(0,0,getWidth(),getHeight(),backing.getRGB(0,0,getWidth(),getHeight(),null,0,getWidth()),0,getWidth());return copy; }
    @Override public int getWidth() { return backing.getWidth(); }
    @Override public int getHeight() { return backing.getHeight(); }
    @Override public Graphics2D createGraphics() { return backing.createGraphics(); }
    @Override public int validate(GraphicsConfiguration gc) { return IMAGE_OK; }
    @Override public boolean contentsLost() { return false; }
    @Override public ImageCapabilities getCapabilities() { return new ImageCapabilities(false); }

    @Override public Graphics getGraphics() { return createGraphics(); }
}
