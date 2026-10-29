package java.awt.image;

public abstract class SampleModel {
    protected int width;
    protected int height;
    protected int numBands;
    protected int dataType;

    public SampleModel(int dataType, int w, int h, int numBands) {
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Width and height must be > 0");
        }
        this.dataType = dataType;
        this.width = w;
        this.height = h;
        this.numBands = numBands;
    }

    public final int getWidth()    { return width; }
    public final int getHeight()   { return height; }
    public final int getNumBands() { return numBands; }
    public final int getDataType() { return dataType; }
    public abstract int getNumDataElements();

    public abstract int getSampleSize(int band);
    public abstract int[] getSampleSize();

    public abstract int getSample(int x, int y, int b, DataBuffer data);
    public abstract void setSample(int x, int y, int b, int s, DataBuffer data);

    public int[] getPixel(int x, int y, int[] iArray, DataBuffer data) {
        int[] pixel = (iArray == null) ? new int[numBands] : iArray;
        for (int i = 0; i < numBands; i++) {
            pixel[i] = getSample(x, y, i, data);
        }
        return pixel;
    }

    public void setPixel(int x, int y, int[] iArray, DataBuffer data) {
        for (int i = 0; i < numBands; i++) {
            setSample(x, y, i, iArray[i], data);
        }
    }

    public abstract SampleModel createCompatibleSampleModel(int w, int h);

    public abstract DataBuffer createDataBuffer();

    public abstract int[] getSampleSize(int band, int[] iArray);
    public int getTransferType() { return dataType; }
    public Object getDataElements(int x, int y, Object out, DataBuffer data) {
        int count = getNumDataElements();
        Object result = transferArray(out, count);
        for (int band = 0; band < count; band++) transferSet(result, band, getSample(x, y, band, data));
        return result;
    }
    public void setDataElements(int x, int y, Object in, DataBuffer data) {
        for (int band = 0; band < getNumDataElements(); band++) setSample(x, y, band, transferGet(in, band), data);
    }
    final Object transferArray(Object out, int count) {
        if (out != null) return out;
        switch (getTransferType()) {
            case DataBuffer.TYPE_BYTE: return new byte[count];
            case DataBuffer.TYPE_USHORT: case DataBuffer.TYPE_SHORT: return new short[count];
            case DataBuffer.TYPE_INT: return new int[count];
            default: throw new UnsupportedOperationException("Floating point transfer arrays");
        }
    }
    static int transferGet(Object array, int i) {
        if (array instanceof byte[]) return ((byte[]) array)[i] & 255;
        if (array instanceof short[]) return ((short[]) array)[i] & 65535;
        return ((int[]) array)[i];
    }
    static void transferSet(Object array, int i, int value) {
        if (array instanceof byte[]) ((byte[]) array)[i] = (byte) value;
        else if (array instanceof short[]) ((short[]) array)[i] = (short) value;
        else ((int[]) array)[i] = value;
    }
    public SampleModel createSubsetSampleModel(int[] bands) { throw new UnsupportedOperationException("Sample model cannot select bands"); }
}
