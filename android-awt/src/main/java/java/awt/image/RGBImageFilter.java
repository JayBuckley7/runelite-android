package java.awt.image;

public abstract class RGBImageFilter extends ImageFilter {
    protected ColorModel origmodel;
    protected ColorModel newmodel;
    protected boolean canFilterIndexColorModel;

    public abstract int filterRGB(int x, int y, int rgb);

    public void filterRGBPixels(int x, int y, int w, int h, int[] pixels, int off, int scansize) {
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int i = off + row * scansize + col;
                pixels[i] = filterRGB(x + col, y + row, pixels[i]);
            }
        }
        if (consumer != null) consumer.setPixels(x, y, w, h, ColorModel.getRGBdefault(), pixels, off, scansize);
    }
    @Override public void setColorModel(ColorModel model) {
        if (canFilterIndexColorModel && model instanceof IndexColorModel) {
            IndexColorModel filtered = filterIndexColorModel((IndexColorModel) model);
            substituteColorModel(model, filtered);
            if (consumer != null) consumer.setColorModel(filtered);
        } else if (consumer != null) consumer.setColorModel(ColorModel.getRGBdefault());
    }
    public void substituteColorModel(ColorModel oldModel, ColorModel newModel) { origmodel = oldModel; newmodel = newModel; }
    public IndexColorModel filterIndexColorModel(IndexColorModel model) {
        int[] colors = new int[model.getMapSize()]; model.getRGBs(colors);
        for (int i = 0; i < colors.length; i++) colors[i] = filterRGB(-1, -1, colors[i]);
        return new IndexColorModel(model.getPixelSize(), colors.length, colors, 0, true, -1, model.getTransferType());
    }
    @Override public void setPixels(int x, int y, int w, int h, ColorModel model, int[] pixels, int offset, int scansize) {
        if (model == origmodel && newmodel != null) { if (consumer != null) consumer.setPixels(x, y, w, h, newmodel, pixels, offset, scansize); return; }
        int[] rgb = new int[w * h];
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) rgb[row * w + col] = model.getRGB(pixels[offset + row * scansize + col]);
        filterRGBPixels(x, y, w, h, rgb, 0, w);
    }
    @Override public void setPixels(int x, int y, int w, int h, ColorModel model, byte[] pixels, int offset, int scansize) {
        if (model == origmodel && newmodel != null) { if (consumer != null) consumer.setPixels(x, y, w, h, newmodel, pixels, offset, scansize); return; }
        int[] rgb = new int[w * h];
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) rgb[row * w + col] = model.getRGB(pixels[offset + row * scansize + col] & 255);
        filterRGBPixels(x, y, w, h, rgb, 0, w);
    }
}
