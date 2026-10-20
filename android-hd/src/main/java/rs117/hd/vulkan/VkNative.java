package rs117.hd.vulkan;

import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * JNI surface of the native Vulkan backend (android/src/androidMain/cpp/hdvk).
 *
 * Objects are integer handles (0 = none). Every call must be made on the client thread, and
 * anything that isn't a draw-time state change goes through {@link Vk} so that pending stream
 * commands are flushed first and ordering matches the order 117 HD issued them in.
 */
public final class VkNative {
	private VkNative() {}

	private static boolean loaded;

	public static synchronized boolean load() {
		if (!loaded) {
			try {
				System.loadLibrary("hdvk");
				loaded = true;
			} catch (UnsatisfiedLinkError ex) {
				return false;
			}
		}
		return true;
	}

	// Buffer usage
	public static final int BUF_VERTEX = 1;
	public static final int BUF_INDEX = 2;
	public static final int BUF_UNIFORM = 4;
	public static final int BUF_STORAGE = 8;
	public static final int BUF_TRANSFER = 16;
	public static final int BUF_INDIRECT = 32;

	// bufferMap modes
	public static final int MAP_PRESERVE = 0;
	public static final int MAP_DISCARD = 1;
	public static final int MAP_UNSYNCHRONIZED = 2;

	// Image types & usage
	public static final int IMG_2D = 0;
	public static final int IMG_2D_ARRAY = 1;
	public static final int IMG_3D = 2;

	public static final int IMG_SAMPLED = 1;
	public static final int IMG_COLOR_ATTACHMENT = 2;
	public static final int IMG_DEPTH_ATTACHMENT = 4;
	public static final int IMG_TRANSIENT = 8;
	public static final int IMG_GENERAL = 16;
	public static final int IMG_TRANSFER_SRC = 32;
	public static final int IMG_TRANSFER_DST = 64;

	// Dummy image kinds for unbound texture units
	public static final int DUMMY_2D = 0;
	public static final int DUMMY_2D_ARRAY = 1;
	public static final int DUMMY_2D_ARRAY_UINT = 2;
	public static final int DUMMY_3D_INT = 3;

	// Attachment load ops (VkAttachmentLoadOp)
	public static final int LOAD = 0;
	public static final int CLEAR = 1;
	public static final int DONT_CARE = 2;

	public static final int PASS_STORE_DEPTH = 1;

	public static final int SWAPCHAIN = -1;

	// limit() queries
	public static final int LIMIT_MAX_SAMPLES = 0;
	public static final int LIMIT_MAX_IMAGE_2D = 1;
	public static final int LIMIT_MAX_ARRAY_LAYERS = 2;
	public static final int LIMIT_UBO_ALIGNMENT = 3;
	public static final int LIMIT_MAX_ANISOTROPY = 4;
	public static final int LIMIT_MAX_UBO_RANGE = 5;
	public static final int LIMIT_SWAPCHAIN_WIDTH = 6;
	public static final int LIMIT_SWAPCHAIN_HEIGHT = 7;
	public static final int LIMIT_HAS_SWAPCHAIN = 8;
	public static final int LIMIT_ALLOCATED_MIB = 9;
	public static final int LIMIT_MAX_SSBO_RANGE = 10;

	/** @return null on success, otherwise why Vulkan can't be used */
	static native String init(String cacheDir);	static native void shutdown();
	static native String deviceName();
	static native int limit(int which);
	static native boolean setSurface(Object surface);
	static native void setVsync(boolean vsync);
	static native void invalidateSwapchain();
	static native boolean imageRead(int image, ByteBuffer dst);
	/** @return false if the device was lost */
	static native boolean endFrame(boolean present);
	static native void waitIdle();
	static native String lastError();

	static native int bufferCreate(long size, int usage);
	static native void bufferDestroy(int buffer);
	static native ByteBuffer bufferMap(int buffer, int mode);
	static native void bufferResize(int buffer, long size, long preserveBytes);
	static native void bufferCopy(int src, int dst, long srcOffset, long dstOffset, long size);

	static native int imageCreate(int type, int glFormat, int width, int height, int layers, int mips, int samples, int usage);
	static native void imageDestroy(int image);
	static native boolean imageUpload(
		int image, int level, int x, int y, int layer, int width, int height, int layers,
		Buffer data, int dataOffsetBytes, int dataLengthBytes, int srcBuffer, long srcBufferOffset);
	static native boolean imageUploadInts(
		int image, int level, int x, int y, int layer, int width, int height, int layers,
		int[] data, int offsetInts, int countInts);
	static native void imageGenerateMips(int image);
	static native void imageSampler(int image, int minFilter, int magFilter, int mipmap, int wrapS, int wrapT, int wrapR, float anisotropy);
	static native void setUnitDummy(int unit, int kind);

	static native int framebufferCreate(int colorImage, int colorLayer, int depthImage, int resolveImage);
	static native void framebufferDestroy(int framebuffer);

	static native int programCreate(String name, String vertexSource, String fragmentSource);
	static native String programError();
	static native void programDestroy(int program);

	static native int layoutCreate();
	static native void layoutDestroy(int layout);
	static native void layoutAttrib(int layout, int location, int buffer, int format, int offset, int stride, int perInstance);

	static native boolean beginPass(int framebuffer, int colorLoad, float r, float g, float b, float a, int depthLoad, float depth, int flags);
	static native void endPass();
	static native void execute(ByteBuffer stream, int intCount);
	static native boolean blit(int src, int sx0, int sy0, int sx1, int sy1, int dst, int dx0, int dy0, int dx1, int dy1, boolean linear);
	static native void clearSwapchain(float r, float g, float b, float a);
}
