package net.runelite.client.plugins.gpugles;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.util.Log;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import rs117.hd.vulkan.Vk;
import rs117.hd.vulkan.VkNative;
import rs117.hd.vulkan.VkShaders;

/**
 * The GL entry points the GPU plugin uses, with the same names and signatures as
 * android.opengl.GLES20/30. With {@link #vulkan} off every call goes straight to GLES; with
 * it on, the calls are carried out on the Vulkan backend shared with 117 HD (rs117.hd.vulkan),
 * so the plugin's GL-shaped code renders through Vulkan unchanged.
 *
 * The emulation covers exactly what the plugin does, no more: buffers, vertex arrays, programs
 * and their uniforms, 2D / 2D-array textures, the fixed-function state it touches, draws, and
 * clears. Render passes are implicit, as in GL: binding a framebuffer, uploading a texture or
 * presenting ends the current pass; a clear starts one, and so does a draw with none open.
 * Scene framebuffers themselves are created by the plugin through {@link Vk} directly, since
 * Vulkan resolves MSAA inside the pass instead of with a blit.
 *
 * Client thread only (render worker threads only ever write into already-mapped buffers).
 */
final class Gfx
{
	private static final String TAG = "Gfx";

	private Gfx() {}

	/** True while the GPU plugin renders through Vulkan. */
	static boolean vulkan;

	/** Set-0 binding of the scene program's loose-uniform block ("uniforms" is binding 0). */
	private static final int LOOSE_BLOCK_BINDING = 1;
	/** Scene-program uniforms that change between draws stay in push constants. */
	private static final Set<String> SCENE_PUSH_UNIFORMS = Set.of("entityProj", "entityTint", "base");
	private static final Map<String, Integer> SAMPLER_UNITS = Map.of("tex", 0, "textures", 1, "sceneTex", 2);
	private static final Map<String, Integer> UBO_BINDINGS = Map.of("uniforms", 0);

	private static final int GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF;
	private static final int GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE;

	// ---------------------------------------------------------------------------------------
	// Vulkan-side bookkeeping
	// ---------------------------------------------------------------------------------------

	private static final class VkBuf
	{
		int handle;
		long size;
		int target;
	}

	private static final class VkTex
	{
		int image;
		int target;
		int width, height, layers, levels;
		boolean owned = true; // false for images the plugin created itself (scene targets)
		int minFilter = GLES20.GL_NEAREST_MIPMAP_LINEAR, magFilter = GLES20.GL_LINEAR;
		int wrapS = GLES20.GL_REPEAT, wrapT = GLES20.GL_REPEAT;
		float anisotropy = 1;
	}

	private static final class VkProg
	{
		int handle;
		final int[] push = new int[Vk.PUSH_CONSTANT_WORDS];
		ByteBuffer block;
		int blockBuffer;
		boolean blockDirty;
	}

	private static final class Uniform
	{
		VkProg program;
		boolean inBlock;
		int offset;
		int stride;
	}

	private static int nextId = 1;
	private static final Map<Integer, VkBuf> buffers = new HashMap<>();
	private static final Map<Integer, VkTex> textures = new HashMap<>();
	private static final Map<Integer, VkProg> programs = new HashMap<>();
	private static final Map<Integer, Integer> vaoElementBuffer = new HashMap<>();
	/** Index = GL uniform location - 1 */
	private static final List<Uniform> uniforms = new ArrayList<>();

	private static int boundArrayBuffer, boundElementBuffer, boundUniformBuffer, boundVao;
	private static VkProg currentProgram;
	private static int activeUnit;
	private static final int[] unitTextures = new int[8];
	private static final int[] emittedUnitImages = new int[8];

	private static int framebuffer; // Vk framebuffer handle, 0 = swapchain
	private static boolean passOpen;
	private static float clearR, clearG, clearB, clearA = 1, clearDepth = 1;

	// Per-frame ring for index data the plugin streams in between draws (Zone's sorted alpha)
	private static final int[] indexRing = new int[3];
	private static final long[] indexRingSize = new long[3];
	private static int indexRingSlot;
	private static long indexRingPos;

	private static int lastSurfaceWidth, lastSurfaceHeight;

	/** Start rendering through Vulkan. @return null on success, otherwise why it can't */
	static String startVulkan()
	{
		String err = Vk.init(new java.io.File(net.runelite.client.RuneLite.RUNELITE_DIR, "gpu-vulkan-cache"));
		if (err != null)
		{
			return err;
		}
		resetVulkanState();
		vulkan = true;
		GlesHost.get().setVulkanOwnsSurface(true);
		// The scene program samples a 2D array at unit 1 before the texture array exists
		Vk.setUnitDummy(1, VkNative.DUMMY_2D_ARRAY);
		Log.i(TAG, "GPU plugin using Vulkan: " + Vk.deviceName());
		return null;
	}

	static void stopVulkan()
	{
		if (!vulkan) return;
		Vk.waitIdle();
		Vk.shutdown();
		vulkan = false;
		resetVulkanState();
		GlesHost.get().setVulkanOwnsSurface(false);
	}

	private static void resetVulkanState()
	{
		buffers.clear();
		textures.clear();
		programs.clear();
		vaoElementBuffer.clear();
		uniforms.clear();
		boundArrayBuffer = boundElementBuffer = boundUniformBuffer = boundVao = 0;
		currentProgram = null;
		activeUnit = 0;
		java.util.Arrays.fill(unitTextures, 0);
		java.util.Arrays.fill(emittedUnitImages, -1);
		framebuffer = 0;
		passOpen = false;
		java.util.Arrays.fill(indexRing, 0);
		java.util.Arrays.fill(indexRingSize, 0);
		indexRingSlot = 0;
		indexRingPos = 0;
		lastSurfaceWidth = lastSurfaceHeight = 0;
	}

	// ---------------------------------------------------------------------------------------
	// Window / frame
	// ---------------------------------------------------------------------------------------

	/** GlesHost.makeCurrent; Vulkan has no current context, so it is always ready. */
	static boolean makeCurrent()
	{
		return vulkan || GlesHost.get().makeCurrent();
	}

	/**
	 * Vulkan: attach the current window (or none) to the swapchain, following resizes. Once per
	 * frame, before it presents; a changed surface ends whatever pass is open.
	 */
	static void attachSurface()
	{
		if (!vulkan) return;
		GlesHost host = GlesHost.get();
		Object surface = host.surfaceForVulkan();
		int w = host.getWidth(), h = host.getHeight();
		if (surface != null && (w != lastSurfaceWidth || h != lastSurfaceHeight))
		{
			lastSurfaceWidth = w;
			lastSurfaceHeight = h;
			Vk.invalidateSwapchain();
		}
		// A new swapchain submits what was recorded so far, closing any open pass natively
		endPass();
		Vk.setSurface(surface);
		java.util.Arrays.fill(emittedUnitImages, -1);
	}

	/** @return false if the Vulkan device was lost */
	static boolean swapBuffers()
	{
		if (!vulkan)
		{
			GlesHost.get().swapBuffers();
			return true;
		}
		endPass();
		boolean ok = Vk.endFrame();
		indexRingSlot = (indexRingSlot + 1) % indexRing.length;
		indexRingPos = 0;
		java.util.Arrays.fill(emittedUnitImages, -1);
		return ok;
	}

	static int contextGeneration()
	{
		return vulkan ? 0 : GlesHost.get().contextGeneration();
	}

	static void setSwapInterval(int interval)
	{
		if (vulkan) Vk.setVsync(interval != 0);
		else GlesHost.get().setSwapInterval(interval);
	}

	// ---------------------------------------------------------------------------------------
	// Passes
	// ---------------------------------------------------------------------------------------

	private static void endPass()
	{
		if (passOpen)
		{
			Vk.endPass();
			passOpen = false;
		}
	}

	private static void ensurePass()
	{
		if (passOpen) return;
		passOpen = Vk.beginPass(framebuffer == 0 ? VkNative.SWAPCHAIN : framebuffer,
			VkNative.LOAD, 0, 0, 0, 1, VkNative.LOAD, 1, false);
		java.util.Arrays.fill(emittedUnitImages, -1);
	}

	static void glBindFramebuffer(int target, int fb)
	{
		if (!vulkan) { GLES30.glBindFramebuffer(target, fb); return; }
		if (fb != framebuffer)
		{
			endPass();
			framebuffer = fb;
		}
	}

	static void glClearColor(float r, float g, float b, float a)
	{
		if (!vulkan) { GLES20.glClearColor(r, g, b, a); return; }
		clearR = r; clearG = g; clearB = b; clearA = a;
	}

	static void glClearDepthf(float d)
	{
		if (!vulkan) { GLES20.glClearDepthf(d); return; }
		clearDepth = d;
	}

	static void glClear(int mask)
	{
		if (!vulkan) { GLES20.glClear(mask); return; }
		endPass();
		boolean color = (mask & GLES20.GL_COLOR_BUFFER_BIT) != 0;
		boolean depth = (mask & GLES20.GL_DEPTH_BUFFER_BIT) != 0;
		passOpen = Vk.beginPass(framebuffer == 0 ? VkNative.SWAPCHAIN : framebuffer,
			color ? VkNative.CLEAR : VkNative.LOAD, clearR, clearG, clearB, clearA,
			depth ? VkNative.CLEAR : VkNative.LOAD, clearDepth, false);
		java.util.Arrays.fill(emittedUnitImages, -1);
	}

	/** Attachments are never stored past their pass on Vulkan, so there is nothing to discard. */
	static void glInvalidateFramebuffer(int target, int count, int[] attachments, int offset)
	{
		if (!vulkan) GLES30.glInvalidateFramebuffer(target, count, attachments, offset);
	}

	static void glBlitFramebuffer(int sx0, int sy0, int sx1, int sy1, int dx0, int dy0, int dx1, int dy1, int mask, int filter)
	{
		if (!vulkan) GLES30.glBlitFramebuffer(sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, mask, filter);
	}

	// ---------------------------------------------------------------------------------------
	// State
	// ---------------------------------------------------------------------------------------

	static void glEnable(int cap)
	{
		if (vulkan) Vk.enable(cap); else GLES20.glEnable(cap);
	}

	static void glDisable(int cap)
	{
		if (vulkan) Vk.disable(cap); else GLES20.glDisable(cap);
	}

	static void glBlendFunc(int src, int dst)
	{
		if (vulkan) Vk.blendFuncSeparate(src, dst, src, dst); else GLES20.glBlendFunc(src, dst);
	}

	static void glBlendFuncSeparate(int srcRgb, int dstRgb, int srcA, int dstA)
	{
		if (vulkan) Vk.blendFuncSeparate(srcRgb, dstRgb, srcA, dstA);
		else GLES20.glBlendFuncSeparate(srcRgb, dstRgb, srcA, dstA);
	}

	static void glDepthFunc(int func)
	{
		if (vulkan) Vk.depthFunc(func); else GLES20.glDepthFunc(func);
	}

	static void glDepthMask(boolean flag)
	{
		if (vulkan) Vk.depthMask(flag); else GLES20.glDepthMask(flag);
	}

	static void glColorMask(boolean r, boolean g, boolean b, boolean a)
	{
		if (vulkan) Vk.colorMask(r, g, b, a); else GLES20.glColorMask(r, g, b, a);
	}

	static void glViewport(int x, int y, int w, int h)
	{
		if (vulkan) Vk.viewport(x, y, w, h); else GLES20.glViewport(x, y, w, h);
	}

	// ---------------------------------------------------------------------------------------
	// Queries
	// ---------------------------------------------------------------------------------------

	static String glGetString(int name)
	{
		if (!vulkan) return GLES20.glGetString(name);
		return name == GLES20.GL_RENDERER ? Vk.deviceName() : name == GLES20.GL_VENDOR ? "Vulkan" : "Vulkan (hdvk)";
	}

	static int glGetError()
	{
		return vulkan ? 0 : GLES20.glGetError();
	}

	static void glGetIntegerv(int pname, int[] params, int offset)
	{
		if (!vulkan) { GLES30.glGetIntegerv(pname, params, offset); return; }
		params[offset] = pname == GLES30.GL_MAX_SAMPLES ? Vk.limit(VkNative.LIMIT_MAX_SAMPLES) : 0;
	}

	static void glGetFloatv(int pname, float[] params, int offset)
	{
		if (!vulkan) { GLES20.glGetFloatv(pname, params, offset); return; }
		params[offset] = pname == GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT ? Vk.limit(VkNative.LIMIT_MAX_ANISOTROPY) : 0;
	}

	// ---------------------------------------------------------------------------------------
	// Buffers
	// ---------------------------------------------------------------------------------------

	static void glGenBuffers(int n, int[] ids, int offset)
	{
		if (!vulkan) { GLES20.glGenBuffers(n, ids, offset); return; }
		for (int i = 0; i < n; ++i)
		{
			int id = nextId++;
			buffers.put(id, new VkBuf());
			ids[offset + i] = id;
		}
	}

	static void glDeleteBuffers(int n, int[] ids, int offset)
	{
		if (!vulkan) { GLES20.glDeleteBuffers(n, ids, offset); return; }
		for (int i = 0; i < n; ++i)
		{
			VkBuf b = buffers.remove(ids[offset + i]);
			if (b != null && b.handle != 0) Vk.bufferDestroy(b.handle);
		}
	}

	static void glBindBuffer(int target, int id)
	{
		if (!vulkan) { GLES20.glBindBuffer(target, id); return; }
		switch (target)
		{
			case GLES20.GL_ARRAY_BUFFER: boundArrayBuffer = id; break;
			case GLES20.GL_ELEMENT_ARRAY_BUFFER:
				boundElementBuffer = id;
				// Element buffer bindings are vertex-array state in GL
				if (boundVao != 0) vaoElementBuffer.put(boundVao, id);
				break;
			case GLES30.GL_UNIFORM_BUFFER: boundUniformBuffer = id; break;
			default: break;
		}
	}

	private static int boundBuffer(int target)
	{
		switch (target)
		{
			case GLES20.GL_ARRAY_BUFFER: return boundArrayBuffer;
			case GLES20.GL_ELEMENT_ARRAY_BUFFER: return boundElementBuffer;
			case GLES30.GL_UNIFORM_BUFFER: return boundUniformBuffer;
			default: return 0;
		}
	}

	private static int vkUsage(int target)
	{
		switch (target)
		{
			case GLES20.GL_ARRAY_BUFFER: return VkNative.BUF_VERTEX;
			case GLES20.GL_ELEMENT_ARRAY_BUFFER: return VkNative.BUF_INDEX;
			case GLES30.GL_UNIFORM_BUFFER: return VkNative.BUF_UNIFORM;
			default: return VkNative.BUF_TRANSFER;
		}
	}

	/** The Vulkan buffer for a GL buffer id, sized at least {@code size}. */
	private static VkBuf storage(int id, int target, long size)
	{
		VkBuf b = buffers.get(id);
		if (b == null) return null;
		size = Math.max(size, 16);
		if (b.handle == 0)
		{
			b.handle = Vk.bufferCreate(size, vkUsage(target));
			b.size = size;
			b.target = target;
		}
		else if (size > b.size)
		{
			Vk.bufferResize(b.handle, size, 0);
			b.size = size;
		}
		return b;
	}

	static void glBufferData(int target, int size, Buffer data, int usage)
	{
		if (!vulkan) { GLES20.glBufferData(target, size, data, usage); return; }
		VkBuf b = storage(boundBuffer(target), target, size);
		if (b == null) return;
		// Like glBufferData, start from fresh storage: never write into what the GPU still reads
		ByteBuffer m = Vk.bufferMap(b.handle, VkNative.MAP_DISCARD);
		if (data != null && m != null) copyInto(m, 0, data, size);
	}

	private static void copyInto(ByteBuffer dst, int dstOffset, Buffer src, int bytes)
	{
		dst.position(dstOffset);
		if (src instanceof ByteBuffer)
		{
			ByteBuffer s = ((ByteBuffer) src).duplicate();
			s.limit(Math.min(s.limit(), s.position() + bytes));
			dst.put(s);
		}
		else if (src instanceof IntBuffer)
		{
			IntBuffer s = ((IntBuffer) src).duplicate();
			s.limit(Math.min(s.limit(), s.position() + bytes / 4));
			dst.asIntBuffer().put(s);
		}
		else if (src instanceof FloatBuffer)
		{
			FloatBuffer s = ((FloatBuffer) src).duplicate();
			s.limit(Math.min(s.limit(), s.position() + bytes / 4));
			dst.asFloatBuffer().put(s);
		}
		else
		{
			throw new IllegalArgumentException("Unsupported buffer " + src.getClass());
		}
	}

	static Buffer glMapBufferRange(int target, int offset, int length, int access)
	{
		if (!vulkan) return GLES30.glMapBufferRange(target, offset, length, access);
		VkBuf b = storage(boundBuffer(target), target, offset + length);
		if (b == null) return null;
		int mode = (access & GLES30.GL_MAP_INVALIDATE_BUFFER_BIT) != 0 ? VkNative.MAP_DISCARD
			: (access & GLES30.GL_MAP_UNSYNCHRONIZED_BIT) != 0 ? VkNative.MAP_UNSYNCHRONIZED
			: VkNative.MAP_PRESERVE;
		ByteBuffer whole = Vk.bufferMap(b.handle, mode);
		if (whole == null) return null;
		whole.position(offset).limit(offset + length);
		return whole.slice().order(ByteOrder.nativeOrder());
	}

	/** Host-visible coherent memory: writes land without an unmap or flush. */
	static boolean glUnmapBuffer(int target)
	{
		return vulkan || GLES30.glUnmapBuffer(target);
	}

	static void glFlushMappedBufferRange(int target, int offset, int length)
	{
		if (!vulkan) GLES30.glFlushMappedBufferRange(target, offset, length);
	}

	static void glBindBufferBase(int target, int index, int id)
	{
		if (!vulkan) { GLES30.glBindBufferBase(target, index, id); return; }
		VkBuf b = buffers.get(id);
		if (b != null && b.handle != 0) Vk.bindUniformBuffer(index, b.handle);
	}

	/**
	 * Vulkan: copies index data into this frame's index ring and binds it to the current
	 * vertex array. GL re-specifies one element buffer for every such draw, which on Vulkan
	 * would mean a fresh buffer each time.
	 *
	 * @return the byte offset to pass to glDrawElements
	 */
	static long streamIndices(IntBuffer indices)
	{
		long bytes = (long) indices.remaining() * Integer.BYTES;
		int slot = indexRingSlot;
		if (indexRing[slot] == 0 || indexRingPos + bytes > indexRingSize[slot])
		{
			long size = Math.max(Math.max(indexRingSize[slot] * 2, 256 * 1024), indexRingPos + bytes);
			if (indexRing[slot] == 0)
			{
				indexRing[slot] = Vk.bufferCreate(size, VkNative.BUF_INDEX);
			}
			else
			{
				// Draws already recorded keep the old storage until they've run
				Vk.bufferResize(indexRing[slot], size, indexRingPos);
			}
			indexRingSize[slot] = size;
		}
		// This slot's previous frame has finished, so writing behind the GPU is safe
		ByteBuffer m = Vk.bufferMap(indexRing[slot], VkNative.MAP_UNSYNCHRONIZED);
		long offset = indexRingPos;
		copyInto(m, (int) offset, indices, (int) bytes);
		indexRingPos += (bytes + 15) & ~15L;
		elementOverride = indexRing[slot];
		return offset;
	}

	/** Vk handle of the streamed index ring, used by the next glDrawElements instead of the VAO's. */
	private static int elementOverride;

	// ---------------------------------------------------------------------------------------
	// Vertex arrays
	// ---------------------------------------------------------------------------------------

	static void glGenVertexArrays(int n, int[] ids, int offset)
	{
		if (!vulkan) { GLES30.glGenVertexArrays(n, ids, offset); return; }
		for (int i = 0; i < n; ++i) ids[offset + i] = Vk.layoutCreate();
	}

	static void glDeleteVertexArrays(int n, int[] ids, int offset)
	{
		if (!vulkan) { GLES30.glDeleteVertexArrays(n, ids, offset); return; }
		for (int i = 0; i < n; ++i)
		{
			int vao = ids[offset + i];
			vaoElementBuffer.remove(vao);
			if (vao == boundVao) boundVao = 0;
			Vk.layoutDestroy(vao);
		}
	}

	static void glBindVertexArray(int vao)
	{
		if (!vulkan) { GLES30.glBindVertexArray(vao); return; }
		boundVao = vao;
		Integer ebo = vaoElementBuffer.get(vao);
		boundElementBuffer = ebo != null ? ebo : 0;
	}

	static void glEnableVertexAttribArray(int index)
	{
		if (!vulkan) GLES20.glEnableVertexAttribArray(index);
	}

	static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, int offset)
	{
		if (!vulkan) { GLES20.glVertexAttribPointer(index, size, type, normalized, stride, offset); return; }
		attrib(index, size, type, normalized, false, stride, offset);
	}

	static void glVertexAttribIPointer(int index, int size, int type, int stride, int offset)
	{
		if (!vulkan) { GLES30.glVertexAttribIPointer(index, size, type, stride, offset); return; }
		attrib(index, size, type, false, true, stride, offset);
	}

	private static void attrib(int index, int size, int type, boolean normalized, boolean integer, int stride, int offset)
	{
		VkBuf b = buffers.get(boundArrayBuffer);
		if (boundVao == 0 || b == null)
		{
			Log.w(TAG, "vertex attribute " + index + " without a vertex array or buffer");
			return;
		}
		// The attribute refers to the buffer's storage, which must exist by now
		if (b.handle == 0) storage(boundArrayBuffer, GLES20.GL_ARRAY_BUFFER, 16);
		Vk.layoutAttrib(boundVao, index, b.handle, size, type, normalized, integer, stride, offset, 0);
	}

	// ---------------------------------------------------------------------------------------
	// Programs and uniforms
	// ---------------------------------------------------------------------------------------

	/**
	 * Vulkan: compile a program from GLES sources.
	 *
	 * @return the program handle
	 */
	static int createProgram(String name, String vertexSource, String fragmentSource) throws ShaderException
	{
		boolean scene = !name.contains("ui");
		VkShaders.Program adapted = VkShaders.adapt(vertexSource, fragmentSource, UBO_BINDINGS, SAMPLER_UNITS,
			scene ? SCENE_PUSH_UNIFORMS : null, LOOSE_BLOCK_BINDING);
		int handle = Vk.programCreate("gpu_" + name, adapted.vertex, adapted.fragment);
		if (handle == 0)
		{
			throw new ShaderException(name + ": " + Vk.programError());
		}
		VkProg p = new VkProg();
		p.handle = handle;
		if (adapted.blockSize > 0)
		{
			p.block = ByteBuffer.allocateDirect(adapted.blockSize).order(ByteOrder.nativeOrder());
			p.blockBuffer = Vk.bufferCreate(adapted.blockSize, VkNative.BUF_UNIFORM);
			p.blockDirty = true;
		}
		programs.put(handle, p);
		for (var e : adapted.pushUniforms.entrySet())
		{
			Uniform u = new Uniform();
			u.program = p;
			u.offset = e.getValue().offset;
			uniformNames.put(key(handle, e.getKey()), u);
		}
		for (var e : adapted.blockUniforms.entrySet())
		{
			Uniform u = new Uniform();
			u.program = p;
			u.inBlock = true;
			u.offset = e.getValue().offset;
			u.stride = e.getValue().stride;
			uniformNames.put(key(handle, e.getKey()), u);
		}
		return handle;
	}

	private static final Map<String, Uniform> uniformNames = new HashMap<>();

	private static String key(int program, String name)
	{
		return program + ":" + name;
	}

	static void glDeleteProgram(int program)
	{
		if (!vulkan) { GLES20.glDeleteProgram(program); return; }
		VkProg p = programs.remove(program);
		if (p == null) return;
		if (currentProgram == p) currentProgram = null;
		if (p.blockBuffer != 0) Vk.bufferDestroy(p.blockBuffer);
		uniformNames.values().removeIf(u -> u.program == p);
		Vk.programDestroy(program);
	}

	static void glUseProgram(int program)
	{
		if (!vulkan) { GLES20.glUseProgram(program); return; }
		if (program == 0) return; // Vulkan always draws with some program; keep the last one
		VkProg p = programs.get(program);
		if (p == null) return;
		currentProgram = p;
		Vk.useProgram(p.handle, p.push);
	}

	/** Samplers and unknown names give -1, like an inactive uniform in GL. */
	static int glGetUniformLocation(int program, String name)
	{
		if (!vulkan) return GLES20.glGetUniformLocation(program, name);
		Uniform u = uniformNames.get(key(program, name));
		if (u == null) return -1;
		uniforms.add(u);
		return uniforms.size();
	}

	static int glGetUniformBlockIndex(int program, String name)
	{
		return vulkan ? 0 : GLES30.glGetUniformBlockIndex(program, name);
	}

	/** Vulkan: uniform blocks are bound by name (see UBO_BINDINGS). */
	static void glUniformBlockBinding(int program, int blockIndex, int binding)
	{
		if (!vulkan) GLES30.glUniformBlockBinding(program, blockIndex, binding);
	}

	private static Uniform uniform(int location)
	{
		return location > 0 && location <= uniforms.size() ? uniforms.get(location - 1) : null;
	}

	private static void setInts(Uniform u, int... v)
	{
		if (u.inBlock)
		{
			for (int i = 0; i < v.length; ++i) u.program.block.putInt(u.offset + i * 4, v[i]);
			u.program.blockDirty = true;
		}
		else
		{
			System.arraycopy(v, 0, u.program.push, u.offset / 4, v.length);
			Vk.pushDataChanged(u.program.push);
		}
	}

	private static void setFloats(Uniform u, float[] v, int offset, int count)
	{
		if (u.inBlock)
		{
			for (int i = 0; i < count; ++i) u.program.block.putFloat(u.offset + i * 4, v[offset + i]);
			u.program.blockDirty = true;
		}
		else
		{
			for (int i = 0; i < count; ++i) u.program.push[u.offset / 4 + i] = Float.floatToRawIntBits(v[offset + i]);
			Vk.pushDataChanged(u.program.push);
		}
	}

	static void glUniform1i(int location, int x)
	{
		if (!vulkan) { GLES20.glUniform1i(location, x); return; }
		Uniform u = uniform(location);
		if (u != null) setInts(u, x);
	}

	static void glUniform2i(int location, int x, int y)
	{
		if (!vulkan) { GLES20.glUniform2i(location, x, y); return; }
		Uniform u = uniform(location);
		if (u != null) setInts(u, x, y);
	}

	static void glUniform3i(int location, int x, int y, int z)
	{
		if (!vulkan) { GLES20.glUniform3i(location, x, y, z); return; }
		Uniform u = uniform(location);
		if (u != null) setInts(u, x, y, z);
	}

	static void glUniform4i(int location, int x, int y, int z, int w)
	{
		if (!vulkan) { GLES20.glUniform4i(location, x, y, z, w); return; }
		Uniform u = uniform(location);
		if (u != null) setInts(u, x, y, z, w);
	}

	private static final float[] scratch = new float[4];

	static void glUniform1f(int location, float x)
	{
		if (!vulkan) { GLES20.glUniform1f(location, x); return; }
		Uniform u = uniform(location);
		if (u == null) return;
		scratch[0] = x;
		setFloats(u, scratch, 0, 1);
	}

	static void glUniform4f(int location, float x, float y, float z, float w)
	{
		if (!vulkan) { GLES20.glUniform4f(location, x, y, z, w); return; }
		Uniform u = uniform(location);
		if (u == null) return;
		scratch[0] = x; scratch[1] = y; scratch[2] = z; scratch[3] = w;
		setFloats(u, scratch, 0, 4);
	}

	static void glUniformMatrix4fv(int location, int count, boolean transpose, float[] value, int offset)
	{
		if (!vulkan) { GLES20.glUniformMatrix4fv(location, count, transpose, value, offset); return; }
		Uniform u = uniform(location);
		if (u != null) setFloats(u, value, offset, 16);
	}

	/** An array of vec2: std140 gives each element a 16-byte stride. */
	static void glUniform2fv(int location, int count, float[] value, int offset)
	{
		if (!vulkan) { GLES20.glUniform2fv(location, count, value, offset); return; }
		Uniform u = uniform(location);
		if (u == null || !u.inBlock) return;
		int stride = u.stride > 0 ? u.stride : 8;
		for (int i = 0; i < count; ++i)
		{
			u.program.block.putFloat(u.offset + i * stride, value[offset + i * 2]);
			u.program.block.putFloat(u.offset + i * stride + 4, value[offset + i * 2 + 1]);
		}
		u.program.blockDirty = true;
	}

	// ---------------------------------------------------------------------------------------
	// Textures
	// ---------------------------------------------------------------------------------------

	static void glGenTextures(int n, int[] ids, int offset)
	{
		if (!vulkan) { GLES20.glGenTextures(n, ids, offset); return; }
		for (int i = 0; i < n; ++i)
		{
			int id = nextId++;
			textures.put(id, new VkTex());
			ids[offset + i] = id;
		}
	}

	/** Vulkan: wrap an image the plugin created itself (a scene target) as a texture id. */
	static int wrapImage(int image, int width, int height)
	{
		int id = nextId++;
		VkTex t = new VkTex();
		t.image = image;
		t.target = GLES20.GL_TEXTURE_2D;
		t.width = width;
		t.height = height;
		t.layers = t.levels = 1;
		t.owned = false;
		t.minFilter = t.magFilter = GLES20.GL_LINEAR;
		t.wrapS = t.wrapT = GLES20.GL_CLAMP_TO_EDGE;
		textures.put(id, t);
		applySampler(t);
		return id;
	}

	static void glDeleteTextures(int n, int[] ids, int offset)
	{
		if (!vulkan) { GLES20.glDeleteTextures(n, ids, offset); return; }
		for (int i = 0; i < n; ++i)
		{
			VkTex t = textures.remove(ids[offset + i]);
			if (t != null && t.owned && t.image != 0) Vk.imageDestroy(t.image);
			for (int u = 0; u < unitTextures.length; ++u)
				if (unitTextures[u] == ids[offset + i]) unitTextures[u] = 0;
		}
	}

	static void glActiveTexture(int unit)
	{
		if (vulkan) activeUnit = unit - GLES20.GL_TEXTURE0; else GLES20.glActiveTexture(unit);
	}

	static void glBindTexture(int target, int id)
	{
		if (!vulkan) { GLES20.glBindTexture(target, id); return; }
		if (activeUnit >= 0 && activeUnit < unitTextures.length) unitTextures[activeUnit] = id;
	}

	private static VkTex bound()
	{
		return activeUnit >= 0 && activeUnit < unitTextures.length ? textures.get(unitTextures[activeUnit]) : null;
	}

	private static void applySampler(VkTex t)
	{
		if (t.image == 0) return;
		int min = t.minFilter;
		// A single-level image can't be sampled with a mipmap filter in GL either (it's incomplete)
		if (t.levels <= 1 && min != GLES20.GL_NEAREST && min != GLES20.GL_LINEAR)
			min = min == GLES20.GL_LINEAR_MIPMAP_LINEAR || min == GLES20.GL_LINEAR_MIPMAP_NEAREST ? GLES20.GL_LINEAR : GLES20.GL_NEAREST;
		Vk.imageSampler(t.image, min, t.magFilter, t.wrapS, t.wrapT, t.anisotropy);
	}

	static void glTexImage2D(int target, int level, int internalFormat, int width, int height, int border,
		int format, int type, Buffer pixels)
	{
		if (!vulkan) { GLES20.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels); return; }
		VkTex t = bound();
		if (t == null || level != 0) return;
		endPass();
		if (t.owned && t.image != 0) Vk.imageDestroy(t.image);
		t.image = Vk.imageCreate(VkNative.IMG_2D, GLES30.GL_RGBA8, width, height, 1, 1, 1, VkNative.IMG_SAMPLED);
		t.target = target;
		t.width = width;
		t.height = height;
		t.layers = t.levels = 1;
		applySampler(t);
		if (pixels != null) Vk.imageUpload(t.image, 0, 0, 0, 0, width, height, 1, pixels, 1);
		emittedUnitImages[activeUnit] = -1;
	}

	static void glTexStorage3D(int target, int levels, int internalFormat, int width, int height, int depth)
	{
		if (!vulkan) { GLES30.glTexStorage3D(target, levels, internalFormat, width, height, depth); return; }
		VkTex t = bound();
		if (t == null) return;
		endPass();
		if (t.owned && t.image != 0) Vk.imageDestroy(t.image);
		t.image = Vk.imageCreate(VkNative.IMG_2D_ARRAY, internalFormat, width, height, depth, levels, 1, VkNative.IMG_SAMPLED);
		t.target = target;
		t.width = width;
		t.height = height;
		t.layers = depth;
		t.levels = levels;
		applySampler(t);
		emittedUnitImages[activeUnit] = -1;
	}

	static void glTexSubImage2D(int target, int level, int x, int y, int width, int height, int format, int type, Buffer pixels)
	{
		if (!vulkan) { GLES20.glTexSubImage2D(target, level, x, y, width, height, format, type, pixels); return; }
		VkTex t = bound();
		if (t == null || t.image == 0) return;
		endPass();
		Vk.imageUpload(t.image, level, x, y, 0, width, height, 1, pixels, 1);
	}

	static void glTexSubImage3D(int target, int level, int x, int y, int z, int width, int height, int depth,
		int format, int type, Buffer pixels)
	{
		if (!vulkan) { GLES30.glTexSubImage3D(target, level, x, y, z, width, height, depth, format, type, pixels); return; }
		VkTex t = bound();
		if (t == null || t.image == 0) return;
		endPass();
		Vk.imageUpload(t.image, level, x, y, z, width, height, depth, pixels, 1);
	}

	static void glGenerateMipmap(int target)
	{
		if (!vulkan) { GLES20.glGenerateMipmap(target); return; }
		VkTex t = bound();
		if (t == null || t.image == 0) return;
		endPass();
		Vk.imageGenerateMips(t.image);
	}

	static void glTexParameteri(int target, int pname, int param)
	{
		if (!vulkan) { GLES20.glTexParameteri(target, pname, param); return; }
		VkTex t = bound();
		if (t == null) return;
		switch (pname)
		{
			case GLES20.GL_TEXTURE_MIN_FILTER: t.minFilter = param; break;
			case GLES20.GL_TEXTURE_MAG_FILTER: t.magFilter = param; break;
			case GLES20.GL_TEXTURE_WRAP_S: t.wrapS = param; break;
			case GLES20.GL_TEXTURE_WRAP_T: t.wrapT = param; break;
			default: return;
		}
		applySampler(t);
	}

	static void glTexParameterf(int target, int pname, float param)
	{
		if (!vulkan) { GLES20.glTexParameterf(target, pname, param); return; }
		VkTex t = bound();
		if (t == null || pname != GL_TEXTURE_MAX_ANISOTROPY_EXT) return;
		t.anisotropy = param;
		applySampler(t);
	}

	// ---------------------------------------------------------------------------------------
	// Draws
	// ---------------------------------------------------------------------------------------

	/** Everything a draw needs that GL keeps as bound state. */
	private static void prepareDraw()
	{
		ensurePass();
		VkProg p = currentProgram;
		if (p != null && p.blockBuffer != 0)
		{
			if (p.blockDirty)
			{
				ByteBuffer m = Vk.bufferMap(p.blockBuffer, VkNative.MAP_DISCARD);
				ByteBuffer src = p.block.duplicate();
				src.clear();
				m.put(src);
				p.blockDirty = false;
			}
			Vk.bindUniformBuffer(LOOSE_BLOCK_BINDING, p.blockBuffer);
		}
		for (int unit = 0; unit < unitTextures.length; ++unit)
		{
			VkTex t = textures.get(unitTextures[unit]);
			int image = t != null ? t.image : 0;
			if (emittedUnitImages[unit] != image)
			{
				Vk.bindTexture(unit, image);
				emittedUnitImages[unit] = image;
			}
		}
	}

	static void glDrawArrays(int mode, int first, int count)
	{
		if (!vulkan) { GLES20.glDrawArrays(mode, first, count); return; }
		if (count <= 0) return;
		prepareDraw();
		Vk.bindVertexLayout(boundVao, 0);
		Vk.drawArrays(first, count);
	}

	/** glDrawArrays over (first, count) pairs; one batched draw op on Vulkan. */
	static void multiDrawArrays(int mode, IntBuffer firsts, IntBuffer counts)
	{
		if (!vulkan)
		{
			int n = firsts.limit();
			for (int i = 0; i < n; ++i) GLES20.glDrawArrays(mode, firsts.get(i), counts.get(i));
			return;
		}
		if (counts.limit() <= 0) return;
		prepareDraw();
		Vk.bindVertexLayout(boundVao, 0);
		IntBuffer f = firsts.duplicate(), c = counts.duplicate();
		f.position(0);
		c.position(0);
		Vk.multiDrawArrays(f, c);
	}

	static void glDrawElements(int mode, int count, int type, int offset)
	{
		if (!vulkan) { GLES20.glDrawElements(mode, count, type, offset); return; }
		if (count <= 0) return;
		prepareDraw();
		int ebo = elementOverride;
		elementOverride = 0;
		if (ebo == 0)
		{
			VkBuf b = buffers.get(boundElementBuffer);
			ebo = b != null ? b.handle : 0;
		}
		Vk.bindVertexLayout(boundVao, ebo);
		Vk.drawElements(count, offset);
	}
}
