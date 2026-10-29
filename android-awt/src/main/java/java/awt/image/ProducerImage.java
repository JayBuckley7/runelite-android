package java.awt.image;
import java.awt.Image;
import java.awt.Graphics;
/** Image backed by the standard producer/consumer protocol. */
public final class ProducerImage extends Image implements ImageConsumer {
    private final ImageProducer producer;
    private volatile BufferedImage image;
    private volatile java.util.Hashtable<?,?> properties = new java.util.Hashtable<>();
    private final java.util.List<ImageObserver> observers=new java.util.concurrent.CopyOnWriteArrayList<>();
    public ProducerImage(ImageProducer producer){this.producer=java.util.Objects.requireNonNull(producer);producer.startProduction(this);}
    public BufferedImage getBufferedImage(){return image;}
    public ImageProducer getSource(){return producer;}
    private void observe(ImageObserver observer){if(observer!=null&&!observers.contains(observer))observers.add(observer);}
    public int getWidth(ImageObserver observer){observe(observer);return image==null?-1:image.getWidth();}
    public int getHeight(ImageObserver observer){observe(observer);return image==null?-1:image.getHeight();}
    public Graphics getGraphics(){if(image==null)throw new IllegalStateException("image not loaded");return image.getGraphics();}
    public void setDimensions(int w,int h){image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);notifyObservers(ImageObserver.WIDTH|ImageObserver.HEIGHT);}
    public void setColorModel(ColorModel model){/* Pixels are normalized to ARGB in setPixels. */}
    public void setHints(int hints){/* All delivery orders are supported. */}
    public void setProperties(java.util.Hashtable<?,?> props){properties=props==null?new java.util.Hashtable<>():props;notifyObservers(ImageObserver.PROPERTIES);}
    @Override public Object getProperty(String name,ImageObserver observer){observe(observer);return properties.containsKey(name)?properties.get(name):UndefinedProperty;}
    public void setPixels(int x,int y,int w,int h,ColorModel cm,int[] pixels,int off,int scan){for(int row=0;row<h;row++)for(int col=0;col<w;col++)image.setRGB(x+col,y+row,cm.getRGB(pixels[off+row*scan+col]));notifyObservers(ImageObserver.SOMEBITS);}
    public void setPixels(int x,int y,int w,int h,ColorModel cm,byte[] pixels,int off,int scan){for(int row=0;row<h;row++)for(int col=0;col<w;col++)image.setRGB(x+col,y+row,cm.getRGB(pixels[off+row*scan+col]&255));notifyObservers(ImageObserver.SOMEBITS);}
    public void imageComplete(int status){notifyObservers(status==STATICIMAGEDONE?ImageObserver.ALLBITS:status==SINGLEFRAMEDONE?ImageObserver.FRAMEBITS:status==IMAGEABORTED?ImageObserver.ABORT:ImageObserver.ERROR);}
    private void notifyObservers(int status){for(ImageObserver o:observers)if(!o.imageUpdate(this,status,0,0,getWidth(null),getHeight(null)))observers.remove(o);}
}
