package java.awt.image;

public final class DataBufferByte extends DataBuffer {
    private final byte[][] data;
    public DataBufferByte(int size) { this(size, 1); }
    public DataBufferByte(int size, int banks) { super(TYPE_BYTE, size, banks); data = new byte[banks][size]; }
    public DataBufferByte(byte[] data, int size) { this(data, size, 0); }
    public DataBufferByte(byte[] data, int size, int offset) { super(TYPE_BYTE, size, 1, offset); this.data = new byte[][]{data}; }
    public DataBufferByte(byte[][] data, int size) { super(TYPE_BYTE, size, data.length); this.data = data.clone(); }
    public DataBufferByte(byte[][] data, int size, int[] offsets) { super(TYPE_BYTE, size, data.length, offsets); this.data = data.clone(); }
    public byte[] getData() { return data[0]; }
    public byte[] getData(int bank) { return data[bank]; }
    public byte[][] getBankData() { return data.clone(); }
    public int getElem(int bank, int index) { return data[bank][index + offsets[bank]] & 255; }
    public void setElem(int bank, int index, int value) { data[bank][index + offsets[bank]] = (byte) value; }
}
