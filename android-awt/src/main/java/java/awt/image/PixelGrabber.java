package java.awt.image;
import java.awt.Image;
import java.util.Hashtable;
/** A pixel consumer for both buffered images and asynchronous image producers. */
public class PixelGrabber implements ImageConsumer {
    private final ImageProducer producer;
    private final int x,y,offset,scansize;
    private int width,height,status;
    private int[] pixels;
    private boolean started;
    private final boolean externalBuffer;
    private ColorModel model=ColorModel.getRGBdefault();
    public PixelGrabber(Image image,int x,int y,int w,int h,int[] pixels,int offset,int scansize){this(image.getSource(),x,y,w,h,pixels,offset,scansize);}
    public PixelGrabber(ImageProducer producer,int x,int y,int w,int h,int[] pixels,int offset,int scansize){
        this.producer=java.util.Objects.requireNonNull(producer);this.x=x;this.y=y;width=w;height=h;this.pixels=pixels;this.offset=offset;this.scansize=scansize;externalBuffer=pixels!=null;
    }
    public PixelGrabber(Image image,int x,int y,int w,int h,boolean forceRGB){this(image,x,y,w,h,null,0,w);}
    public boolean grabPixels() throws InterruptedException{return grabPixels(0);}
    public synchronized boolean grabPixels(long timeout) throws InterruptedException {
        startGrabbing();long end=timeout>0?System.currentTimeMillis()+timeout:0;
        while((status&(ImageObserver.ALLBITS|ImageObserver.FRAMEBITS|ImageObserver.ERROR|ImageObserver.ABORT))==0){
            if(timeout<0)break;if(timeout==0)wait();else{long remaining=end-System.currentTimeMillis();if(remaining<=0)break;wait(remaining);}
        }
        return (status&(ImageObserver.ALLBITS|ImageObserver.FRAMEBITS))!=0;
    }
    public synchronized void startGrabbing(){if(!started){started=true;producer.startProduction(this);}}
    public synchronized void abortGrabbing(){status|=ImageObserver.ABORT;producer.removeConsumer(this);notifyAll();}
    public synchronized int getStatus(){return status;}
    public int status(){return getStatus();}
    public synchronized int getWidth(){return width;}
    public synchronized int getHeight(){return height;}
    public synchronized Object getPixels(){return pixels;}
    public synchronized ColorModel getColorModel(){return model;}
    public synchronized void setDimensions(int w,int h){
        if(width<0)width=w-x;if(height<0)height=h-y;
        if(width<=0||height<=0){width=Math.max(0,width);height=Math.max(0,height);imageComplete(STATICIMAGEDONE);return;}
        if(pixels==null)pixels=new int[offset+(height-1)*stride()+width];
        status|=ImageObserver.WIDTH|ImageObserver.HEIGHT;
    }
    private int stride(){return externalBuffer?scansize:width;}
    public void setColorModel(ColorModel cm){/* Output is always normalized to RGBdefault. */}
    public void setHints(int hints){/* Output indexing handles every producer delivery order. */}
    public void setProperties(Hashtable<?,?> props){synchronized(this){status|=ImageObserver.PROPERTIES;}}
    public synchronized void setPixels(int sx,int sy,int w,int h,ColorModel cm,byte[] data,int off,int scan){copy(sx,sy,w,h,cm,off,scan,i->data[i]&255);}
    public synchronized void setPixels(int sx,int sy,int w,int h,ColorModel cm,int[] data,int off,int scan){copy(sx,sy,w,h,cm,off,scan,i->data[i]);}
    private interface Pixel{int get(int i);}
    private void copy(int sx,int sy,int w,int h,ColorModel cm,int off,int scan,Pixel source){
        if(pixels==null)return;
        int left=Math.max(x,sx),top=Math.max(y,sy),right=Math.min(x+width,sx+w),bottom=Math.min(y+height,sy+h);
        for(int row=top;row<bottom;row++)for(int col=left;col<right;col++)pixels[offset+(row-y)*stride()+col-x]=cm.getRGB(source.get(off+(row-sy)*scan+col-sx));
        status|=ImageObserver.SOMEBITS;
    }
    public synchronized void imageComplete(int value){
        status|=value==STATICIMAGEDONE?ImageObserver.ALLBITS:value==SINGLEFRAMEDONE?ImageObserver.FRAMEBITS:value==IMAGEABORTED?ImageObserver.ABORT:ImageObserver.ERROR|ImageObserver.ABORT;
        producer.removeConsumer(this);notifyAll();
    }
}
