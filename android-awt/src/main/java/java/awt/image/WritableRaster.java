package java.awt.image;

import java.awt.Point;
import java.awt.Rectangle;

public class WritableRaster extends Raster {
    protected WritableRaster(SampleModel sm, Point origin) {
        super(sm, origin);
    }

    protected WritableRaster(SampleModel sm, DataBuffer db, Point origin) {
        super(sm, db, origin);
    }

    protected WritableRaster(SampleModel sm, DataBuffer db, Rectangle aRegion, Point sampleModelTranslate, WritableRaster parent) {
        super(sm, db, aRegion, sampleModelTranslate, parent);
    }

    public void setSample(int x, int y, int b, int s) {
        checkBounds(x,y);
        sampleModel.setSample(x - sampleModelTranslateX, y - sampleModelTranslateY, b, s, dataBuffer);
    }

    public void setPixel(int x, int y, int[] iArray) {
        checkBounds(x,y);
        sampleModel.setPixel(x - sampleModelTranslateX, y - sampleModelTranslateY, iArray, dataBuffer);
    }

    public void setPixels(int x, int y, int w, int h, int[] iArray) {
        int offset = 0;
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                for (int b = 0; b < numBands; b++) {
                    setSample(x + col, y + row, b, iArray[offset++]);
                }
            }
        }
    }
    public void setDataElements(int x, int y, Object data) {
        checkBounds(x, y);
        sampleModel.setDataElements(x - sampleModelTranslateX, y - sampleModelTranslateY, data, dataBuffer);
    }
    public void setDataElements(int x, int y, int w, int h, Object data) {
        Object pixel = sampleModel.transferArray(null, numDataElements);
        int index = 0;
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            for (int element = 0; element < numDataElements; element++) SampleModel.transferSet(pixel, element, SampleModel.transferGet(data, index++));
            setDataElements(x + col, y + row, pixel);
        }
    }
    public WritableRaster createWritableChild(int x,int y,int w,int h,int childX,int childY,int[] bands){
        if(x<minX||y<minY||w<=0||h<=0||x+w>minX+width||y+h>minY+height)throw new IllegalArgumentException("child bounds");
        SampleModel childModel = bands == null ? sampleModel : sampleModel.createSubsetSampleModel(bands);
        return new WritableRaster(childModel,dataBuffer,new Rectangle(childX,childY,w,h),new Point(sampleModelTranslateX+childX-x,sampleModelTranslateY+childY-y),this);
    }
    public WritableRaster createWritableTranslatedChild(int x,int y){return createWritableChild(minX,minY,width,height,x,y,null);}
}
