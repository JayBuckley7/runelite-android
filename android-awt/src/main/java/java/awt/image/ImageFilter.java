package java.awt.image;

import java.util.Hashtable;

public class ImageFilter implements ImageConsumer, Cloneable {
    protected ImageConsumer consumer;

    @Override public void setDimensions(int width, int height) { if (consumer != null) consumer.setDimensions(width, height); }
    @Override public void setProperties(Hashtable<?, ?> props) { if (consumer != null) consumer.setProperties(props); }
    @Override public void setColorModel(ColorModel model) { if (consumer != null) consumer.setColorModel(model); }
    @Override public void setHints(int hintflags) { if (consumer != null) consumer.setHints(hintflags); }
    @Override public void setPixels(int x, int y, int w, int h, ColorModel model, byte[] pixels, int off, int scansize) {
        if (consumer != null) consumer.setPixels(x, y, w, h, model, pixels, off, scansize);
    }
    @Override public void setPixels(int x, int y, int w, int h, ColorModel model, int[] pixels, int off, int scansize) {
        if (consumer != null) consumer.setPixels(x, y, w, h, model, pixels, off, scansize);
    }
    @Override public void imageComplete(int status) { if (consumer != null) consumer.imageComplete(status); }

    public void resendTopDownLeftRight(ImageProducer source) { source.requestTopDownLeftRightResend(this); }

    public ImageFilter getFilterInstance(ImageConsumer ic) {
        try {
            ImageFilter copy = (ImageFilter) clone();
            copy.consumer = ic;
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }
}
