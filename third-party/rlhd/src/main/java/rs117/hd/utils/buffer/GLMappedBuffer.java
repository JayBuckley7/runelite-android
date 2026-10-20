package rs117.hd.utils.buffer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import lombok.Getter;
import lombok.experimental.Accessors;

import static org.lwjgl.opengl.GL33C.*;
import static rs117.hd.HdPlugin.GL_CAPS;
import java.nio.ByteOrder;
import rs117.hd.vulkan.Vk;
import rs117.hd.vulkan.VkNative;
import static rs117.hd.HdPlugin.checkGLErrors;
import static rs117.hd.utils.MathUtils.*;
import static rs117.hd.utils.buffer.GLBuffer.MAP_INVALIDATE;
import static rs117.hd.utils.buffer.GLBuffer.MAP_READ;
import static rs117.hd.utils.buffer.GLBuffer.MAP_UNSYNCHRONIZED;
import static rs117.hd.utils.buffer.GLBuffer.MAP_WRITE;

public final class GLMappedBuffer {
	@Getter
	private final GLBuffer owner;

	@Getter
	@Accessors(fluent = true)
	private ByteBuffer byteView;

	@Getter
	@Accessors(fluent = true)
	private IntBuffer intView;

	@Getter
	@Accessors(fluent = true)
	private FloatBuffer floatView;

	@Getter
	private boolean mapped;

	@Getter
	private int mappedFlags;

	@Getter
	private long mappedOffset;

	GLMappedBuffer(GLBuffer owner) {
		this.owner = owner;
	}

	GLMappedBuffer(GLBuffer owner, ByteBuffer mappedBuffer) {
		this.owner = owner;
		this.byteView = mappedBuffer;
		this.intView = mappedBuffer.asIntBuffer();
		this.floatView = mappedBuffer.asFloatBuffer();
		this.mapped = true;
	}

	public GLMappedBuffer map(int flags) {
		return map(flags, 0, owner.size);
	}

	public GLMappedBuffer map(int flags, long offsetBytes, long sizeBytes) {
		if (byteView != null) {
			byteView.position(0);
			intView.position(0);
			floatView.position(0);
		}

		if (mapped || owner.isStorageBuffer())
			return this;

		if (Vk.ACTIVE)
			return vkMap(flags, offsetBytes, sizeBytes);

		owner.bind();

		final ByteBuffer buf;
		if (owner.target != GL_STATIC_DRAW && GL_CAPS.GL_ARB_map_buffer_range && !GLBuffer.DEBUG_MAC_OS) {
			int glFlags = 0;
			if ((flags & MAP_WRITE) != 0) glFlags |= GL_MAP_WRITE_BIT;
			if ((flags & MAP_READ) != 0) glFlags |= GL_MAP_READ_BIT;
			if ((flags & MAP_UNSYNCHRONIZED) != 0) glFlags |= GL_MAP_UNSYNCHRONIZED_BIT;

			long mapSize = clamp(owner.size - offsetBytes, 0, sizeBytes);
			if (mapSize <= 0)
				return this;

			if ((flags & MAP_INVALIDATE) != 0) {
				if (mapSize == owner.size) glFlags |= GL_MAP_INVALIDATE_BUFFER_BIT;
				else glFlags |= GL_MAP_INVALIDATE_RANGE_BIT;
			}
			buf = glMapBufferRange(
				owner.target,
				offsetBytes,
				mapSize,
				glFlags | GL_MAP_FLUSH_EXPLICIT_BIT,
				byteView
			);
			this.mappedOffset = offsetBytes;
		} else {
			int glAccess;
			if ((flags & (MAP_WRITE | MAP_READ)) == (MAP_WRITE | MAP_READ)) {
				glAccess = GL_READ_WRITE;
			} else if ((flags & MAP_WRITE) != 0) {
				glAccess = GL_WRITE_ONLY;
			} else {
				glAccess = GL_READ_ONLY;
			}

			buf = glMapBuffer(owner.target, glAccess, byteView);
			this.mappedOffset = 0;
		}

		if (buf == null)
			throw new RuntimeException("Failed to map buffer " + owner.id);

		if (byteView != buf) {
			byteView = buf;
			intView = buf.asIntBuffer();
			floatView = buf.asFloatBuffer();
		}

		byteView.clear();
		intView.clear();
		floatView.clear();

		this.mappedFlags = flags;
		mapped = true;

		owner.unbind();
		checkGLErrors(() -> "Mapping Buffer: " + owner.name + " Size: " + owner.size);
		return this;
	}

	private GLMappedBuffer vkMap(int flags, long offsetBytes, long sizeBytes) {
		long mapSize = clamp(owner.size - offsetBytes, 0, sizeBytes);
		if (mapSize <= 0)
			return this;

		// Invalidating the whole buffer lets the driver orphan it, which is what makes GL's
		// unsynchronized + invalidate maps safe while earlier frames still read the old contents.
		int mode;
		if ((flags & MAP_INVALIDATE) != 0 && offsetBytes == 0 && mapSize == owner.size) {
			mode = VkNative.MAP_DISCARD;
		} else if ((flags & MAP_UNSYNCHRONIZED) != 0) {
			mode = VkNative.MAP_UNSYNCHRONIZED;
		} else {
			mode = VkNative.MAP_PRESERVE;
		}
		ByteBuffer whole = Vk.bufferMap(owner.id, mode);
		if (whole == null)
			throw new RuntimeException("Failed to map buffer " + owner.id);

		// Like glMapBufferRange, the returned view starts at the requested offset. The native
		// side hands back the same ByteBuffer object for an allocation every time, so the views
		// are rebuilt on every map: int/float views keep the size they were created with, and a
		// larger map of the same allocation (the UI growing within its power-of-two buffer)
		// would otherwise overflow them.
		whole.position((int) offsetBytes).limit((int) (offsetBytes + mapSize));
		ByteBuffer buf = whole.slice().order(ByteOrder.nativeOrder());
		byteView = buf;
		intView = buf.asIntBuffer();
		floatView = buf.asFloatBuffer();
		byteView.clear();
		intView.clear();
		floatView.clear();

		this.mappedOffset = offsetBytes;
		this.mappedFlags = flags;
		mapped = true;
		return this;
	}

	/** Vulkan: the owner was resized; point the views at the new storage, keeping the write position. */
	void vkRemap() {
		if (!mapped)
			return;
		int pos = getPositionBytes();
		long offset = mappedOffset;
		int flags = mappedFlags;
		mapped = false;
		vkMap(flags | MAP_UNSYNCHRONIZED, offset, owner.size - offset);
		mappedFlags = flags;
		setPositionBytes(pos);
	}

	public int getPositionBytes() {
		int bytesPos = byteView.position();
		if (intView.position() * 4 > bytesPos)
			bytesPos = intView.position() * 4;
		if (floatView.position() * 4 > bytesPos)
			bytesPos = floatView.position() * 4;
		return bytesPos;
	}

	public void setPositionBytes(int bytesPos) {
		if (byteView == null)
			return;
		byteView.position(bytesPos);
		intView.position(bytesPos / 4);
		floatView.position(bytesPos / 4);
	}

	public void syncViews() {
		int bytesPos = getPositionBytes();
		byteView.position(bytesPos);
		intView.position(bytesPos / 4);
		floatView.position(bytesPos / 4);
	}

	public void unmap() {
		if (!mapped || owner.isStorageBuffer())
			return;

		syncViews();

		if (Vk.ACTIVE) {
			// Host-coherent memory: nothing to flush
			mapped = false;
			return;
		}

		owner.bind();
		if (owner.target != GL_STATIC_DRAW && GL_CAPS.GL_ARB_map_buffer_range && !GLBuffer.DEBUG_MAC_OS) {
			byteView.flip();
			glFlushMappedBufferRange(owner.target, byteView.position(), byteView.remaining());
			byteView.clear();
		}
		glUnmapBuffer(owner.target);
		owner.unbind();

		mapped = false;
	}

	void destroy() {
		unmap();

		byteView = null;
		intView = null;
		floatView = null;
	}
}
