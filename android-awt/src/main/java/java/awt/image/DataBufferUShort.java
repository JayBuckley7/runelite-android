package java.awt.image;

public final class DataBufferUShort extends DataBuffer {
    private final short[][] data;
    public DataBufferUShort(int size) { this(size, 1); }
    public DataBufferUShort(int size, int banks) { super(TYPE_USHORT, size, banks); data = new short[banks][size]; }
    public DataBufferUShort(short[] data, int size) { this(data, size, 0); }
    public DataBufferUShort(short[] data, int size, int offset) { super(TYPE_USHORT, size, 1, offset); this.data = new short[][]{data}; }
    public DataBufferUShort(short[][] data, int size) { super(TYPE_USHORT, size, data.length); this.data = data.clone(); }
    public DataBufferUShort(short[][] data, int size, int[] offsets) { super(TYPE_USHORT, size, data.length, offsets); this.data = data.clone(); }
    public short[] getData() { return data[0]; }
    public short[] getData(int bank) { return data[bank]; }
    public short[][] getBankData() { return data.clone(); }
    public int getElem(int bank, int index) { return data[bank][index + offsets[bank]] & 65535; }
    public void setElem(int bank, int index, int value) { data[bank][index + offsets[bank]] = (short) value; }
}
