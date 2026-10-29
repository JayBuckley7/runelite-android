package java.awt.image;

public class FilteredImageSource implements ImageProducer {
    private final ImageProducer source;
    private final ImageFilter filter;
    private final java.util.Map<ImageConsumer, ImageFilter> filters = new java.util.HashMap<>();
    public FilteredImageSource(ImageProducer source, ImageFilter filter) { this.source = java.util.Objects.requireNonNull(source); this.filter = java.util.Objects.requireNonNull(filter); }
    private ImageFilter instance(ImageConsumer consumer) { ImageFilter result = filters.get(consumer); if (result == null) { result = filter.getFilterInstance(consumer); filters.put(consumer, result); } return result; }
    public synchronized void addConsumer(ImageConsumer consumer) { source.addConsumer(instance(consumer)); }
    public synchronized boolean isConsumer(ImageConsumer consumer) { return filters.containsKey(consumer); }
    public synchronized void removeConsumer(ImageConsumer consumer) { ImageFilter instance = filters.remove(consumer); if (instance != null) source.removeConsumer(instance); }
    public synchronized void startProduction(ImageConsumer consumer) { source.startProduction(instance(consumer)); }
    public synchronized void requestTopDownLeftRightResend(ImageConsumer consumer) { ImageFilter instance = filters.get(consumer); if (instance != null) instance.resendTopDownLeftRight(source); }
}
