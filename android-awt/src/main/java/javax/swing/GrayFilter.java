package javax.swing;

import java.awt.Image;
import java.awt.image.ImageProducer;
import java.awt.image.RGBImageFilter;

public class GrayFilter extends RGBImageFilter {
    private final boolean brighter;
    private final int percent;

    public GrayFilter(boolean brighter, int percent) {
        this.brighter = brighter;
        this.percent = percent;
        canFilterIndexColorModel = true;
    }

    public static Image createDisabledImage(Image i) {
        return java.awt.Toolkit.getDefaultToolkit().createImage(new java.awt.image.FilteredImageSource(i.getSource(),new GrayFilter(true,50)));
    }

    @Override
    public int filterRGB(int x, int y, int rgb) {
        int a = (rgb >> 24) & 0xFF;
        int gray = (((rgb >> 16) & 0xFF) * 30 + ((rgb >> 8) & 0xFF) * 59 + (rgb & 0xFF) * 11) / 100;
        if (brighter) gray = (255 - ((255 - gray) * (100 - percent) / 100));
        else gray = (gray * (100 - percent) / 100);
        if (gray < 0) gray = 0;
        if (gray > 255) gray = 255;
        return (a << 24) | (gray << 16) | (gray << 8) | gray;
    }
}
