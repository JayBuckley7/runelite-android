package rs117.hd.vulkan;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import lombok.extern.slf4j.Slf4j;

import static org.lwjgl.opengl.GL33C.*;

/**
 * The Vulkan backend as 117 HD sees it.
 *
 * Draw-time state follows GL's model (enable/disable, depth/color masks, blend functions,
 * current program and vertex array, texture units) so the renderer's existing state handling
 * maps onto it one call for one call. That state is folded into packed pipeline-state bits and
 * written to a command stream, which the native side turns into pipelines, descriptor sets and
 * vkCmdDraw* calls when it is flushed. Everything else (passes, transfers, resource changes)
 * flushes the stream first, so the GPU sees operations in exactly the order they were issued.
 *
 * Client thread only.
 */
@Slf4j
public final class Vk {
	private Vk() {}

	/** True while 117 HD renders through Vulkan. */
	public static boolean ACTIVE;

	private static final int OP_PROGRAM = 1;
	private static final int OP_LAYOUT = 2;
	private static final int OP_STATE = 3;
	private static final int OP_VIEWPORT = 4;
	private static final int OP_TEXTURE = 5;
	private static final int OP_STORAGE = 6;
	private static final int OP_UBO = 7;
	private static final int OP_DRAW = 8;
	private static final int OP_DRAW_INDEXED = 9;
	private static final int OP_MULTI_DRAW = 10;
	private static final int OP_PUSH = 11;
	private static final int OP_DRAW_INSTANCED = 12;

	private static final int STREAM_INTS = 64 * 1024;
	private static final ByteBuffer streamBytes = ByteBuffer.allocateDirect(STREAM_INTS * 4).order(ByteOrder.nativeOrder());
	private static final IntBuffer stream = streamBytes.asIntBuffer();

	// GL-style state
	private static boolean blend, cull, depthTest;
	private static boolean depthMask = true;
	private static int depthFunc = GL_LESS;
	private static int blendSrcRgb = GL_ONE, blendDstRgb = GL_ZERO, blendSrcA = GL_ONE, blendDstA = GL_ZERO;
	private static int colorMask = 0xF;
	private static boolean stateDirty = true;
	private static int emittedState = -1;

	/** Matches PUSH_CONSTANT_SIZE in hdvk.h. */
	public static final int PUSH_CONSTANT_WORDS = 32;

	private static int program, layout, indexBuffer;
	private static int[] currentPush;
	private static boolean pushDirty = true;
	private static final int[] viewport = new int[4];
	private static final int[] emittedViewport = { -1, -1, -1, -1 };

	// ---------------------------------------------------------------------------------------
	// Lifecycle
	// ---------------------------------------------------------------------------------------

	/** @return null on success, otherwise the reason Vulkan can't be used */
	/** @param cacheDir where compiled shaders and the pipeline cache persist; null disables caching */
	public static String init(java.io.File cacheDir) {
		if (!VkNative.load())
			return "libhdvk is missing";
		String dir = null;
		if (cacheDir != null && (cacheDir.isDirectory() || cacheDir.mkdirs()))
			dir = cacheDir.getAbsolutePath();
		String err = VkNative.init(dir);
		if (err != null)
			return err;
		resetState();
		return null;
	}

	public static void shutdown() {
		stream.clear();
		VkNative.shutdown();
		ACTIVE = false;
	}

	public static String deviceName() {
		return VkNative.deviceName();
	}

	public static int limit(int which) {
		return VkNative.limit(which);
	}

	/** Attach (or with null, detach) the Android Surface to present to. */
	public static boolean setSurface(Object surface) {
		flush();
		return VkNative.setSurface(surface);
	}

	public static boolean hasSwapchain() {
		return VkNative.limit(VkNative.LIMIT_HAS_SWAPCHAIN) != 0;
	}

	/** The surface changed size without the swapchain noticing; rebuild it before the next frame. */
	public static void invalidateSwapchain() {
		VkNative.invalidateSwapchain();
	}

	/** Copies a single-sample color image to dst, waiting for the GPU. Rows are in GL order (bottom first). */
	public static boolean imageRead(int image, ByteBuffer dst) {
		flush();
		boolean ok = VkNative.imageRead(image, dst);
		resetState();
		return ok;
	}

	public static void setVsync(boolean vsync) {
		VkNative.setVsync(vsync);
	}

	/** Submits the frame and presents it if a swapchain image was rendered. @return false on device loss */
	public static boolean endFrame() {
		flush();
		boolean ok = VkNative.endFrame(true);
		resetState();
		return ok;
	}

	/** Submits everything recorded so far without presenting, and waits for the GPU to finish. */
	public static void waitIdle() {
		flush();
		VkNative.waitIdle();
		resetState();
	}

	private static void resetState() {
		// The native side forgets bound state at the start of each command buffer
		emittedState = -1;
		stateDirty = true;
		pushDirty = true;
		program = layout = indexBuffer = 0;
		emittedViewport[0] = -1;
	}

	// ---------------------------------------------------------------------------------------
	// Stream
	// ---------------------------------------------------------------------------------------

	public static void flush() {
		int n = stream.position();
		if (n > 0) {
			VkNative.execute(streamBytes, n);
			stream.clear();
		}
	}

	private static void reserve(int ints) {
		if (stream.remaining() < ints)
			flush();
	}

	// ---------------------------------------------------------------------------------------
	// GL-style draw state
	// ---------------------------------------------------------------------------------------

	public static void enable(int cap) {
		toggle(cap, true);
	}

	public static void disable(int cap) {
		toggle(cap, false);
	}

	public static void toggle(int cap, boolean on) {
		switch (cap) {
			case GL_BLEND:
				if (blend != on) { blend = on; stateDirty = true; }
				break;
			case GL_CULL_FACE:
				if (cull != on) { cull = on; stateDirty = true; }
				break;
			case GL_DEPTH_TEST:
				if (depthTest != on) { depthTest = on; stateDirty = true; }
				break;
			default:
				// GL_MULTISAMPLE, GL_FRAMEBUFFER_SRGB, ... have no per-draw Vulkan equivalent
				break;
		}
	}

	public static void depthMask(boolean write) {
		if (depthMask != write) {
			depthMask = write;
			stateDirty = true;
		}
	}

	public static void depthFunc(int func) {
		if (depthFunc != func) {
			depthFunc = func;
			stateDirty = true;
		}
	}

	public static void blendFuncSeparate(int srcRgb, int dstRgb, int srcA, int dstA) {
		if (blendSrcRgb != srcRgb || blendDstRgb != dstRgb || blendSrcA != srcA || blendDstA != dstA) {
			blendSrcRgb = srcRgb;
			blendDstRgb = dstRgb;
			blendSrcA = srcA;
			blendDstA = dstA;
			stateDirty = true;
		}
	}

	public static void colorMask(boolean r, boolean g, boolean b, boolean a) {
		int mask = (r ? 1 : 0) | (g ? 2 : 0) | (b ? 4 : 0) | (a ? 8 : 0);
		if (colorMask != mask) {
			colorMask = mask;
			stateDirty = true;
		}
	}

	public static void viewport(int x, int y, int w, int h) {
		viewport[0] = x;
		viewport[1] = y;
		viewport[2] = w;
		viewport[3] = h;
	}

	/** @param pushData the program's loose-uniform block, re-pushed before its next draw */
	public static void useProgram(int prog, int[] pushData) {
		if (currentPush != pushData) {
			currentPush = pushData;
			pushDirty = true;
		}
		if (program == prog)
			return;
		program = prog;
		reserve(2);
		stream.put(OP_PROGRAM).put(prog);
	}

	/** A loose uniform in {@code pushData} changed; it reaches the GPU if that program is in use. */
	public static void pushDataChanged(int[] pushData) {
		if (pushData == currentPush)
			pushDirty = true;
	}

	public static void bindVertexLayout(int vao, int ebo) {
		if (layout == vao && (ebo == 0 || ebo == indexBuffer))
			return;
		layout = vao;
		if (ebo != 0)
			indexBuffer = ebo;
		reserve(3);
		stream.put(OP_LAYOUT).put(vao).put(ebo);
	}

	/** @param unit texture unit index (not GL_TEXTUREi) */
	public static void bindTexture(int unit, int image) {
		reserve(3);
		stream.put(OP_TEXTURE).put(unit).put(image);
	}

	public static void bindStorage(int buffer) {
		reserve(2);
		stream.put(OP_STORAGE).put(buffer);
	}

	public static void bindUniformBuffer(int binding, int buffer) {
		reserve(3);
		stream.put(OP_UBO).put(binding).put(buffer);
	}

	public static void pushConstants(int offsetBytes, int[] words, int count) {
		reserve(3 + count);
		stream.put(OP_PUSH).put(offsetBytes).put(count).put(words, 0, count);
	}

	private static int glBlendFactor(int gl) {
		switch (gl) {
			case GL_ZERO: return 0;
			case GL_ONE: return 1;
			case GL_SRC_COLOR: return 2;
			case GL_ONE_MINUS_SRC_COLOR: return 3;
			case GL_DST_COLOR: return 4;
			case GL_ONE_MINUS_DST_COLOR: return 5;
			case GL_SRC_ALPHA: return 6;
			case GL_ONE_MINUS_SRC_ALPHA: return 7;
			case GL_DST_ALPHA: return 8;
			case GL_ONE_MINUS_DST_ALPHA: return 9;
			default: return 1;
		}
	}

	private static void emitDrawState() {
		if (pushDirty && currentPush != null) {
			pushDirty = false;
			pushConstants(0, currentPush, currentPush.length);
		}
		if (stateDirty) {
			int bits = (blend ? 1 : 0)
				| glBlendFactor(blendSrcRgb) << 1
				| glBlendFactor(blendDstRgb) << 5
				| glBlendFactor(blendSrcA) << 9
				| glBlendFactor(blendDstA) << 13
				| (cull ? 1 : 0) << 17
				| (depthTest ? 1 : 0) << 18
				| (depthMask ? 1 : 0) << 19
				| ((depthFunc - GL_NEVER) & 7) << 20
				| colorMask << 23;
			stateDirty = false;
			if (bits != emittedState) {
				emittedState = bits;
				reserve(2);
				stream.put(OP_STATE).put(bits);
			}
		}
		if (viewport[0] != emittedViewport[0] || viewport[1] != emittedViewport[1] ||
			viewport[2] != emittedViewport[2] || viewport[3] != emittedViewport[3]) {
			System.arraycopy(viewport, 0, emittedViewport, 0, 4);
			reserve(5);
			stream.put(OP_VIEWPORT).put(viewport[0]).put(viewport[1]).put(viewport[2]).put(viewport[3]);
		}
	}

	public static void drawArrays(int first, int count) {
		if (count <= 0)
			return;
		emitDrawState();
		reserve(3);
		stream.put(OP_DRAW).put(first).put(count);
	}

	public static void drawArraysInstanced(int first, int count, int instances, int firstInstance) {
		if (count <= 0 || instances <= 0)
			return;
		emitDrawState();
		reserve(5);
		stream.put(OP_DRAW_INSTANCED).put(first).put(count).put(instances).put(firstInstance);
	}

	public static void drawElements(int count, long byteOffset) {
		if (count <= 0)
			return;
		emitDrawState();
		reserve(3);
		stream.put(OP_DRAW_INDEXED).put(count).put((int) (byteOffset / 4));
	}

	public static void multiDrawArrays(int[] firsts, int[] counts, int drawCount) {
		if (drawCount <= 0)
			return;
		emitDrawState();
		int chunk = Math.min(drawCount, (STREAM_INTS - 8) / 2);
		for (int start = 0; start < drawCount; start += chunk) {
			int n = Math.min(chunk, drawCount - start);
			reserve(2 + n * 2);
			stream.put(OP_MULTI_DRAW).put(n);
			for (int i = start; i < start + n; i++)
				stream.put(firsts[i]).put(counts[i]);
		}
	}

	public static void multiDrawArrays(IntBuffer firsts, IntBuffer counts) {
		int n = counts.remaining();
		if (n <= 0)
			return;
		emitDrawState();
		reserve(2 + n * 2);
		stream.put(OP_MULTI_DRAW).put(n);
		for (int i = 0; i < n; i++)
			stream.put(firsts.get(firsts.position() + i)).put(counts.get(counts.position() + i));
	}

	// ---------------------------------------------------------------------------------------
	// Passes & transfers
	// ---------------------------------------------------------------------------------------

	public static boolean beginPass(int framebuffer, int colorLoad, float r, float g, float b, float a, int depthLoad, float depth, boolean storeDepth) {
		flush();
		boolean ok = VkNative.beginPass(framebuffer, colorLoad, r, g, b, a, depthLoad, depth, storeDepth ? VkNative.PASS_STORE_DEPTH : 0);
		// Pipelines and dynamic state are per render pass
		emittedState = -1;
		stateDirty = true;
		pushDirty = true;
		emittedViewport[0] = -1;
		return ok;
	}

	public static void endPass() {
		flush();
		VkNative.endPass();
	}

	public static boolean blit(int src, int sx0, int sy0, int sx1, int sy1, int dst, int dx0, int dy0, int dx1, int dy1, boolean linear) {
		flush();
		return VkNative.blit(src, sx0, sy0, sx1, sy1, dst, dx0, dy0, dx1, dy1, linear);
	}

	public static void clearSwapchain(float r, float g, float b, float a) {
		flush();
		VkNative.clearSwapchain(r, g, b, a);
	}

	// ---------------------------------------------------------------------------------------
	// Resources
	// ---------------------------------------------------------------------------------------

	public static int bufferCreate(long size, int usage) {
		flush();
		return VkNative.bufferCreate(size, usage);
	}

	public static void bufferDestroy(int buffer) {
		if (buffer == 0)
			return;
		flush();
		VkNative.bufferDestroy(buffer);
	}

	/** @return a native-order direct view of the buffer's current contents */
	public static ByteBuffer bufferMap(int buffer, int mode) {
		flush();
		ByteBuffer b = VkNative.bufferMap(buffer, mode);
		if (b != null)
			b.order(ByteOrder.nativeOrder()).clear();
		return b;
	}

	public static void bufferResize(int buffer, long size, long preserveBytes) {
		flush();
		VkNative.bufferResize(buffer, size, preserveBytes);
	}

	public static void bufferCopy(int src, int dst, long srcOffset, long dstOffset, long size) {
		flush();
		VkNative.bufferCopy(src, dst, srcOffset, dstOffset, size);
	}

	public static int imageCreate(int type, int glFormat, int width, int height, int layers, int mips, int samples, int usage) {
		flush();
		return VkNative.imageCreate(type, glFormat, width, height, layers, mips, samples, usage);
	}

	public static void imageDestroy(int image) {
		if (image == 0)
			return;
		flush();
		VkNative.imageDestroy(image);
	}

	public static boolean imageUpload(int image, int level, int x, int y, int layer, int w, int h, int layers, Buffer data, int elementSize) {
		flush();
		return VkNative.imageUpload(image, level, x, y, layer, w, h, layers,
			data, data.position() * elementSize, data.remaining() * elementSize, 0, 0);
	}

	public static boolean imageUploadFromBuffer(int image, int x, int y, int w, int h, int srcBuffer, long srcOffset) {
		flush();
		return VkNative.imageUpload(image, 0, x, y, 0, w, h, 1, null, 0, 0, srcBuffer, srcOffset);
	}

	public static boolean imageUpload(int image, int level, int x, int y, int layer, int w, int h, int[] pixels, int offset, int count) {
		flush();
		return VkNative.imageUploadInts(image, level, x, y, layer, w, h, 1, pixels, offset, count);
	}

	public static void imageGenerateMips(int image) {
		flush();
		VkNative.imageGenerateMips(image);
	}

	/**
	 * GL texture parameters to sampler state.
	 *
	 * @param minFilter GL_NEAREST, GL_LINEAR or one of the GL_*_MIPMAP_* filters
	 * @param wrap      GL_CLAMP_TO_EDGE, GL_REPEAT, GL_CLAMP_TO_BORDER or GL_MIRRORED_REPEAT, for S, T and R
	 */
	public static void imageSampler(int image, int minFilter, int magFilter, int wrapS, int wrapT, float anisotropy) {
		flush();
		int min = minFilter == GL_LINEAR || minFilter == GL_LINEAR_MIPMAP_NEAREST || minFilter == GL_LINEAR_MIPMAP_LINEAR ? 1 : 0;
		int mip = minFilter == GL_NEAREST_MIPMAP_NEAREST || minFilter == GL_LINEAR_MIPMAP_NEAREST ? 1 :
			minFilter == GL_NEAREST_MIPMAP_LINEAR || minFilter == GL_LINEAR_MIPMAP_LINEAR ? 2 : 0;
		int mag = magFilter == GL_LINEAR ? 1 : 0;
		VkNative.imageSampler(image, min, mag, mip, wrapMode(wrapS), wrapMode(wrapT), wrapMode(wrapT), anisotropy);
	}

	private static int wrapMode(int gl) {
		switch (gl) {
			case GL_REPEAT: return 1;
			case GL_CLAMP_TO_BORDER: return 2;
			case GL_MIRRORED_REPEAT: return 3;
			default: return 0;
		}
	}

	public static void setUnitDummy(int unit, int kind) {
		flush();
		VkNative.setUnitDummy(unit, kind);
	}

	public static int framebufferCreate(int color, int colorLayer, int depth, int resolve) {
		flush();
		return VkNative.framebufferCreate(color, colorLayer, depth, resolve);
	}

	public static void framebufferDestroy(int fb) {
		if (fb == 0)
			return;
		flush();
		VkNative.framebufferDestroy(fb);
	}

	/** @return the program handle, or 0 with the compiler log in {@link #programError()} */
	public static int programCreate(String name, String vertex, String fragment) {
		flush();
		return VkNative.programCreate(name, vertex, fragment);
	}

	public static String programError() {
		return VkNative.programError();
	}

	public static void programDestroy(int prog) {
		if (prog == 0)
			return;
		flush();
		if (program == prog)
			program = 0;
		VkNative.programDestroy(prog);
	}

	public static int layoutCreate() {
		flush();
		return VkNative.layoutCreate();
	}

	public static void layoutDestroy(int vao) {
		if (vao == 0)
			return;
		flush();
		if (layout == vao)
			layout = 0;
		VkNative.layoutDestroy(vao);
	}

	/**
	 * Mirrors glVertexAttrib[I]Pointer + glVertexAttribDivisor for one attribute location.
	 *
	 * @param glType    GL_FLOAT, GL_HALF_FLOAT, GL_SHORT, GL_INT, ...
	 * @param integer   true for glVertexAttribIPointer
	 */
	public static void layoutAttrib(int vao, int location, int buffer, int components, int glType, boolean normalized,
		boolean integer, int stride, long offset, int divisor) {
		flush();
		if (layout == vao)
			layout = -1; // force the stream to re-bind it
		int format = glType << 8 | components << 4 | (integer ? 2 : 0) | (normalized ? 1 : 0);
		VkNative.layoutAttrib(vao, location, buffer, format, (int) offset, stride, divisor);
	}

	public static String lastError() {
		return VkNative.lastError();
	}
}
