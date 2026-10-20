package net.runelite.client.plugins.gpugles;

import static android.opengl.GLES20.GL_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_STATIC_DRAW;
import static net.runelite.client.plugins.gpugles.Gfx.glBindBuffer;
import static net.runelite.client.plugins.gpugles.Gfx.glBufferData;
import static net.runelite.client.plugins.gpugles.Gfx.glDeleteBuffers;
import static net.runelite.client.plugins.gpugles.Gfx.glGenBuffers;
import static net.runelite.client.plugins.gpugles.Gfx.glGetError;
import static net.runelite.client.plugins.gpugles.Gfx.glGetString;
import static android.opengl.GLES30.GL_MAP_FLUSH_EXPLICIT_BIT;
import static android.opengl.GLES30.GL_MAP_INVALIDATE_BUFFER_BIT;
import static android.opengl.GLES30.GL_MAP_WRITE_BIT;
import static net.runelite.client.plugins.gpugles.Gfx.glFlushMappedBufferRange;
import static net.runelite.client.plugins.gpugles.Gfx.glMapBufferRange;
import static net.runelite.client.plugins.gpugles.Gfx.glUnmapBuffer;
import android.opengl.GLES20;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Ported from net.runelite.client.plugins.gpu.VBO. Differences from desktop:
 *  - glGenBuffers/glDeleteBuffers use the int[] array form (GLES doesn't expose
 *    the single-int return-value overload).
 *  - glMapBufferRange returns a {@link Buffer} (not {@link ByteBuffer}) in
 *    Android's binding — we order it native + view as IntBuffer manually.
 *  - The desktop binding lets you pass a recycled ByteBuffer to glMapBufferRange
 *    so it can reuse the wrapper allocation; we get a fresh one every map.
 */
class VBO
{
	final int size;
	private int usage;
	int bufId;
	private ByteBuffer buffer;
	IntBuffer vb;
	int len;
	boolean mapped;

	VBO(int size)
	{
		this.size = size;
	}

	void init(int usage)
	{
		this.usage = usage;
		int[] arr = new int[1];
		glGenBuffers(1, arr, 0);
		bufId = arr[0];

		glBindBuffer(GL_ARRAY_BUFFER, bufId);
		glBufferData(GL_ARRAY_BUFFER, size, null, usage);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	void destroy()
	{
		if (mapped)
		{
			glBindBuffer(GL_ARRAY_BUFFER, bufId);
			glUnmapBuffer(GL_ARRAY_BUFFER);
			glBindBuffer(GL_ARRAY_BUFFER, 0);
			mapped = false;
		}
		glDeleteBuffers(1, new int[]{ bufId }, 0);
		bufId = 0;
	}

	void map()
	{
		assert !mapped;
		glBindBuffer(GL_ARRAY_BUFFER, bufId);
		int access = GL_MAP_WRITE_BIT
			| (usage == GL_STATIC_DRAW ? 0 : (GL_MAP_INVALIDATE_BUFFER_BIT | GL_MAP_FLUSH_EXPLICIT_BIT));
		Buffer mapped = glMapBufferRange(GL_ARRAY_BUFFER, 0, size, access);
		if (mapped == null)
		{
			int err = glGetError();
			throw new RuntimeException("unable to map GL buffer (bufId: " + bufId + " size: " + size
				+ " renderer: " + glGetString(GLES20.GL_RENDERER) + " version: " + glGetString(GLES20.GL_VERSION)
				+ " err: " + err + ")");
		}
		// The driver hands us a Buffer whose underlying address points at the
		// mapped region — wrap as ByteBuffer to view it as int[]. ByteOrder
		// matters because the desktop code writes ints in native order.
		this.buffer = (ByteBuffer) mapped;
		this.buffer.order(ByteOrder.nativeOrder());
		this.vb = this.buffer.asIntBuffer();
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		this.mapped = true;
	}

	void unmap()
	{
		assert mapped;
		len = vb.position();
		vb = null;

		glBindBuffer(GL_ARRAY_BUFFER, bufId);
		if (usage != GL_STATIC_DRAW)
		{
			glFlushMappedBufferRange(GL_ARRAY_BUFFER, 0, len * Integer.BYTES);
		}
		glUnmapBuffer(GL_ARRAY_BUFFER);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		mapped = false;
	}
}
