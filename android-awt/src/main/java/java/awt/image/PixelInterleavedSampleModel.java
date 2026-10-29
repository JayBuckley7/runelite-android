package java.awt.image;

public class PixelInterleavedSampleModel extends ComponentSampleModel {
    public PixelInterleavedSampleModel(int type, int width, int height, int pixelStride, int scanlineStride, int[] offsets) {
        super(type, width, height, pixelStride, scanlineStride, offsets);
    }
    @Override public SampleModel createCompatibleSampleModel(int w, int h) {
        int minimum = Integer.MAX_VALUE;
        for (int offset : bandOffsets) minimum = Math.min(minimum, offset);
        int[] offsets = bandOffsets.clone(); for (int i = 0; i < offsets.length; i++) offsets[i] -= minimum;
        return new PixelInterleavedSampleModel(dataType, w, h, pixelStride, w * pixelStride, offsets);
    }
    @Override public SampleModel createSubsetSampleModel(int[] bands) {
        int[] offsets = new int[bands.length]; for (int i = 0; i < offsets.length; i++) offsets[i] = bandOffsets[bands[i]];
        return new PixelInterleavedSampleModel(dataType, width, height, pixelStride, scanlineStride, offsets);
    }
}
