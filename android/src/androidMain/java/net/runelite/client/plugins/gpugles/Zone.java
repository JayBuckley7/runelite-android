package net.runelite.client.plugins.gpugles;

import static android.opengl.GLES20.GL_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_STREAM_DRAW;
import static android.opengl.GLES20.GL_TRIANGLES;
import static android.opengl.GLES20.GL_UNSIGNED_INT;
import static net.runelite.client.plugins.gpugles.Gfx.glBindBuffer;
import static net.runelite.client.plugins.gpugles.Gfx.glBufferData;
import static net.runelite.client.plugins.gpugles.Gfx.glDeleteBuffers;
import static net.runelite.client.plugins.gpugles.Gfx.glDepthMask;
import static net.runelite.client.plugins.gpugles.Gfx.glDrawArrays;
import static net.runelite.client.plugins.gpugles.Gfx.glDrawElements;
import static net.runelite.client.plugins.gpugles.Gfx.glEnableVertexAttribArray;
import static net.runelite.client.plugins.gpugles.Gfx.glGenBuffers;
import static net.runelite.client.plugins.gpugles.Gfx.glUniform3i;
import static net.runelite.client.plugins.gpugles.Gfx.glVertexAttribPointer;
import static android.opengl.GLES30.GL_ELEMENT_ARRAY_BUFFER;
import static net.runelite.client.plugins.gpugles.Gfx.glBindVertexArray;
import static net.runelite.client.plugins.gpugles.Gfx.glDeleteVertexArrays;
import static net.runelite.client.plugins.gpugles.Gfx.glGenVertexArrays;
import static net.runelite.client.plugins.gpugles.Gfx.glVertexAttribIPointer;
import static net.runelite.client.plugins.gpugles.ModelUploader.MAX_DIAMETER;
import static net.runelite.client.plugins.gpugles.ModelUploader.MAX_VERTEX_COUNT;
import static net.runelite.client.plugins.gpugles.GpuGlesPlugin.uniBase;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;

/**
 * Ported from net.runelite.client.plugins.gpu.Zone. Two GLES-specific tweaks:
 *  - {@code glMultiDrawArrays} doesn't exist in GLES at all (it's a desktop-only
 *    call). We emulate it as a tight {@code glDrawArrays} loop over the same
 *    (offsets, counts) arrays — same effective draws, no driver-side batching.
 *  - {@code glDeleteBuffers}/{@code glDeleteVertexArrays} use the array form.
 */
class Zone
{
	private static final String TAG = "Zone";

	// Zone vertex format (per vert, 20 bytes)
	//  index 0: short vec3(x, y, z) — 6 bytes
	//  index 1: int abhsl            — 4 bytes
	//  index 2: short vec4(id, u, v, 0) — 8 bytes
	//  + 2 bytes padding (struct alignment up to 20)
	static final int VERT_SIZE = 20;

	int glVao;
	int bufLen;

	int glVaoA;
	int bufLenA;

	int sizeO, sizeA;
	VBO vboO, vboA;

	boolean initialized;
	boolean cull;
	boolean dirty;
	boolean invalidate;

	int[] levelOffsets = new int[4]; // buffer pos in ints for the end of the level

	int[][] rids;
	int[][] roofStart;
	int[][] roofEnd;

	final List<AlphaModel> alphaModels = new ArrayList<>(0);

	void init(VBO o, VBO a)
	{
		assert glVao == 0;
		assert glVaoA == 0;

		if (o != null)
		{
			vboO = o;
			int[] arr = new int[1];
			glGenVertexArrays(1, arr, 0);
			glVao = arr[0];
			setupVao(glVao, o.bufId);
		}

		if (a != null)
		{
			vboA = a;
			int[] arr = new int[1];
			glGenVertexArrays(1, arr, 0);
			glVaoA = arr[0];
			setupVao(glVaoA, a.bufId);
		}
	}

	void free()
	{
		if (vboO != null) { vboO.destroy(); vboO = null; }
		if (vboA != null) { vboA.destroy(); vboA = null; }

		if (glVao != 0)
		{
			glDeleteVertexArrays(1, new int[]{ glVao }, 0);
			glVao = 0;
		}
		if (glVaoA != 0)
		{
			glDeleteVertexArrays(1, new int[]{ glVaoA }, 0);
			glVaoA = 0;
		}

		alphaModels.clear();
	}

	void unmap()
	{
		if (vboO != null) vboO.unmap();
		if (vboA != null) vboA.unmap();
		if (vboO != null) this.bufLen  = vboO.len / (VERT_SIZE / 4);
		if (vboA != null) this.bufLenA = vboA.len / (VERT_SIZE / 4);
	}

	private void setupVao(int vao, int buffer)
	{
		glBindVertexArray(vao);
		glBindBuffer(GL_ARRAY_BUFFER, buffer);

		glEnableVertexAttribArray(0);
		// Four components, not three: the vertex record already has a padding short after
		// xyz, and a 4x16 attribute is a native vertex format everywhere while 3x16 is not
		// (drivers emulate it with a wider fetch + mask). The shader still declares vec3, so
		// the extra component is dropped. Mirrors upstream gpu's Zone.setupVao.
		glVertexAttribPointer(0, 4, GLES20.GL_SHORT, false, VERT_SIZE, 0);

		glEnableVertexAttribArray(1);
		glVertexAttribIPointer(1, 1, GLES20.GL_INT, VERT_SIZE, 8);

		glEnableVertexAttribArray(2);
		glVertexAttribIPointer(2, 4, GLES20.GL_SHORT, VERT_SIZE, 12);

		glBindVertexArray(0);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	void updateRoofs(Map<Integer, Integer> updates)
	{
		for (int level = 0; level < 4; ++level)
		{
			for (int i = 0; i < rids[level].length; ++i)
			{
				rids[level][i] = updates.getOrDefault(rids[level][i], rids[level][i]);
			}
		}
		for (AlphaModel m : alphaModels)
		{
			m.rid = (short) (int) updates.getOrDefault((int) m.rid, (int) m.rid);
		}
	}

	private static final int NUM_DRAW_RANGES = 512;
	private static final IntBuffer drawOff = allocDirect(NUM_DRAW_RANGES);
	private static final IntBuffer drawEnd = allocDirect(NUM_DRAW_RANGES);

	private static IntBuffer allocDirect(int capacity)
	{
		return ByteBuffer.allocateDirect(capacity * Integer.BYTES).order(ByteOrder.nativeOrder()).asIntBuffer();
	}

	private void convertForDraw(int vertSize)
	{
		assert drawOff.position() == drawEnd.position();

		drawOff.flip();
		drawEnd.flip();

		for (int i = 0; i < drawOff.limit(); ++i)
		{
			int off = drawOff.get(i);
			int end = drawEnd.get(i);

			assert end >= off;
			// bytes → verts
			off /= vertSize >> 2;
			end /= vertSize >> 2;
			end -= off; // end pos → length

			drawOff.put(i, off);
			drawEnd.put(i, end);
		}
	}

	/** glMultiDrawArrays replacement — GLES has no batched form, so loop (Vulkan batches it). */
	private static void multiDrawArrays(int mode, IntBuffer offsets, IntBuffer counts)
	{
		Gfx.multiDrawArrays(mode, offsets, counts);
	}

	void renderOpaque(int zx, int zz, int minLevel, int currentLevel, int maxLevel, Set<Integer> hiddenRoofIds)
	{
		drawOff.clear();
		drawEnd.clear();

		for (int level = minLevel; level <= maxLevel; ++level)
		{
			int[] rids = this.rids[level];
			int[] roofStart = this.roofStart[level];
			int[] roofEnd = this.roofEnd[level];

			if (rids.length == 0 || hiddenRoofIds.isEmpty() || level <= currentLevel)
			{
				int start = level == 0 ? 0 : this.levelOffsets[level - 1];
				int end = this.levelOffsets[level];
				pushRange(start, end);
				continue;
			}

			for (int roofIdx = 0; roofIdx < rids.length; ++roofIdx)
			{
				int rid = rids[roofIdx];
				if (rid > 0 && !hiddenRoofIds.contains(rid))
				{
					assert roofEnd[roofIdx] >= roofStart[roofIdx];
					if (roofEnd[roofIdx] > roofStart[roofIdx])
					{
						pushRange(roofStart[roofIdx], roofEnd[roofIdx]);
					}
				}
			}

			int endpos = level == 0 ? 0 : this.levelOffsets[level - 1];
			for (int roofIdx = rids.length - 1; roofIdx >= 0; --roofIdx)
			{
				int rid = rids[roofIdx];
				if (rid > 0)
				{
					endpos = roofEnd[roofIdx];
					break;
				}
			}
			pushRange(endpos, this.levelOffsets[level]);
		}

		convertForDraw(VERT_SIZE);

		if (drawOff.limit() > 0)
		{
			glUniform3i(uniBase, zx << 10, 0, zz << 10);
			glBindVertexArray(glVao);
			multiDrawArrays(GL_TRIANGLES, drawOff, drawEnd);
		}
	}

	private static void pushRange(int start, int end)
	{
		assert end >= start;
		if (start == end) return;

		int idx = drawEnd.position();
		if (idx > 0 && drawEnd.get(idx - 1) == start)
		{
			drawEnd.put(idx - 1, end);
		}
		else if (!drawEnd.hasRemaining())
		{
			Log.d(TAG, "draw ranges exhausted");
		}
		else
		{
			drawOff.put(start);
			drawEnd.put(end);
		}
	}

	static class AlphaModel
	{
		int id;
		int startpos, endpos;
		short x, y, z;
		short rid;
		int vao;
		byte level;
		byte lx, lz, ux, uz;
		byte zofx, zofz;
		byte flags;

		int radius;
		int[] packedFaces;

		static final int SKIP = 1;
		static final int TEMP = 2;

		boolean isTemp() { return packedFaces == null; }
	}

	static final Queue<AlphaModel> modelCache = new ArrayDeque<>();

	void addAlphaModel(int vao, Model model, int startpos, int endpos, int x, int y, int z,
		int lx, int lz, int ux, int uz, int rid, int level, int id)
	{
		AlphaModel m = new AlphaModel();
		m.id = id;
		m.startpos = startpos;
		m.endpos = endpos;
		m.x = (short) x; m.y = (short) y; m.z = (short) z;
		m.vao = vao;
		m.rid = (short) rid;
		m.level = (byte) level;
		if (lx > -1)
		{
			m.lx = (byte) lx;
			m.lz = (byte) lz;
			m.ux = (byte) ux;
			m.uz = (byte) uz;
		}
		else
		{
			m.lx = m.lz = m.ux = m.uz = -1;
		}

		int faceCount = model.getFaceCount();
		int[] color3 = model.getFaceColors3();
		byte[] transparencies = model.getFaceTransparencies();
		float[] vertexX = model.getVerticesX();
		float[] vertexY = model.getVerticesY();
		float[] vertexZ = model.getVerticesZ();
		int[] indices1 = model.getFaceIndices1();
		int[] indices2 = model.getFaceIndices2();
		int[] indices3 = model.getFaceIndices3();

		int minX = Integer.MAX_VALUE, minY = minX, minZ = minY;
		int maxX = Integer.MIN_VALUE, maxY = maxX, maxZ = maxY;

		for (int f = 0; f < faceCount; ++f)
		{
			if (color3[f] == -2 || transparencies[f] == 0) continue;

			int fx = (int) (vertexX[indices1[f]] + vertexX[indices2[f]] + vertexX[indices3[f]]);
			int fy = (int) (vertexY[indices1[f]] + vertexY[indices2[f]] + vertexY[indices3[f]]);
			int fz = (int) (vertexZ[indices1[f]] + vertexZ[indices2[f]] + vertexZ[indices3[f]]);

			minX = Math.min(minX, fx);  maxX = Math.max(maxX, fx);
			minY = Math.min(minY, fy);  maxY = Math.max(maxY, fy);
			minZ = Math.min(minZ, fz);  maxZ = Math.max(maxZ, fz);
		}

		int cx = (minX + maxX) / 6;
		int cy = (minY + maxY) / 6;
		int cz = (minZ + maxZ) / 6;

		int size = Math.max(Math.max(
				Math.max(maxX / 3 - cx, minX / -3 - cx),
				Math.max(maxY / 3 - cy, minY / -3 - cy) * 2),
			Math.max(maxZ / 3 - cz, minZ / -3 - cz));

		int shift = 0;
		for (int v = size >> 10; v > 0; v >>= 1) shift++;

		int[] packedFaces = m.packedFaces = new int[(endpos - startpos) / ((3 * VERT_SIZE) >> 2)];
		int radius = 0;
		char bufferIdx = 0;
		for (int f = 0; f < faceCount; ++f)
		{
			if (color3[f] == -2 || transparencies[f] == 0) continue;

			int fx = (((int) (vertexX[indices1[f]] + vertexX[indices2[f]] + vertexX[indices3[f]]) / 3) - cx) >> shift;
			int fy = (((int) (vertexY[indices1[f]] + vertexY[indices2[f]] + vertexY[indices3[f]]) / 3) - cy) >> shift;
			int fz = (((int) (vertexZ[indices1[f]] + vertexZ[indices2[f]] + vertexZ[indices3[f]]) / 3) - cz) >> shift;

			radius = Math.max(radius, fx * fx + fy * fy + fz * fz);

			packedFaces[bufferIdx] = ((fx & ((1 << 11) - 1)) << 21)
				| ((fy & ((1 << 10) - 1)) << 11)
				| (fz & ((1 << 11) - 1));
			bufferIdx++;
		}

		assert radius >= 0;
		m.radius = 2 + (int) Math.sqrt(radius);
		assert packedFaces.length > 0;
		assert bufferIdx == packedFaces.length;

		alphaModels.add(m);
	}

	// synchronized: dynamic-object uploads can run on N worker render threads, and two of
	// them can land in the same zone. Guards both the alphaModels list and the shared
	// AlphaModel free-list. Mirrors upstream gpu's Zone.
	synchronized void addTempAlphaModel(int vao, int startpos, int endpos, int level, int x, int y, int z)
	{
		AlphaModel m;
		synchronized (modelCache)
		{
			m = modelCache.poll();
		}
		if (m == null) m = new AlphaModel();
		m.id = -1;
		m.startpos = startpos;
		m.endpos = endpos;
		m.x = (short) x; m.y = (short) y; m.z = (short) z;
		m.vao = vao;
		m.rid = -1;
		m.level = (byte) level;
		m.lx = m.lz = m.ux = m.uz = -1;
		m.flags = 0;
		m.zofx = m.zofz = 0;
		alphaModels.add(m);
	}

	void removeTemp()
	{
		for (int i = alphaModels.size() - 1; i >= 0; --i)
		{
			AlphaModel m = alphaModels.get(i);
			if (m.isTemp() || (m.flags & AlphaModel.TEMP) != 0)
			{
				alphaModels.remove(i);
				m.packedFaces = null;
				modelCache.add(m);
			}
			m.flags &= ~AlphaModel.SKIP;
		}
	}

	private static final IntBuffer alphaElements = allocDirect(MAX_VERTEX_COUNT * 3);

	private static final int STATIC = 1;
	private static final int TEMP = 2;
	private static final int STATIC_UNSORTED = 3;

	private static int lastDrawMode;
	private static int lastVao;
	private static int lastzx, lastzz;

	private static int elementBufferId;

	static void initBuffer()
	{
		int[] arr = new int[1];
		glGenBuffers(1, arr, 0);
		elementBufferId = arr[0];
	}

	static void freeBuffer()
	{
		glDeleteBuffers(1, new int[]{ elementBufferId }, 0);
		elementBufferId = 0;
	}

	static class AlphaModelComparator implements Comparator<AlphaModel>
	{
		int zx, zz;
		int cx, cy, cz;

		@Override
		public int compare(AlphaModel o1, AlphaModel o2)
		{
			return Integer.compare(z(o2), z(o1));
		}

		private int z(AlphaModel m)
		{
			final int mx = (m.x + ((zx - m.zofx) << 10));
			final int mz = (m.z + ((zz - m.zofz) << 10));
			return (mx - cx) * (mx - cx) +
				(m.y - cy) * (m.y - cy) +
				(mz - cz) * (mz - cz);
		}
	}

	private static final AlphaModelComparator alphaModelComparator = new AlphaModelComparator();

	void alphaSort(int zx, int zz, int cx, int cy, int cz)
	{
		alphaModelComparator.zx = zx; alphaModelComparator.zz = zz;
		alphaModelComparator.cx = cx; alphaModelComparator.cy = cy; alphaModelComparator.cz = cz;
		alphaModels.sort(alphaModelComparator);
	}

	void renderAlpha(ModelUploader mu, int zx, int zz, int cyaw, int cpitch, int minLevel, int currentLevel, int maxLevel, int level, Set<Integer> hiddenRoofIds, boolean useStaticUnsorted)
	{
		drawOff.clear();
		drawEnd.clear();
		alphaElements.clear();
		lastDrawMode = lastVao = 0;
		lastzx = zx;
		lastzz = zz;

		// cyaw/cpitch come straight from client.getCameraYaw()/getCameraPitch(), which as of
		// rev 1.12.31.1 report 14-bit angles (0..16383) instead of the legacy 11-bit (0..2047).
		// Index the 14-bit tables to match (the 2048-entry SINE/COSINE overflow at index>2047 —
		// the ArrayIndexOutOfBoundsException that crashed the render thread). Mirrors desktop
		// GpuPlugin's Zone.renderAlpha. Model-orientation lookups elsewhere stay 11-bit SINE.
		int yawsin = Perspective.SINE14[cyaw];
		int yawcos = Perspective.COSINE14[cyaw];
		int pitchsin = Perspective.SINE14[cpitch];
		int pitchcos = Perspective.COSINE14[cpitch];
		for (int j = 0; j < alphaModels.size(); ++j) // NOPMD: ForLoopCanBeForeach
		{
			AlphaModel m = alphaModels.get(j);

			if ((m.flags & AlphaModel.SKIP) != 0 || m.level != level) continue;

			boolean ok = false;
			if (level >= minLevel && level <= maxLevel)
			{
				if (level <= currentLevel || !hiddenRoofIds.contains((int) m.rid))
				{
					ok = true;
				}
			}
			if (!ok) continue;

			if (lastVao != m.vao
				|| lastzx != (zx - m.zofx) || lastzz != (zz - m.zofz))
			{
				flush();
			}

			lastVao = m.vao;
			lastzx = zx - m.zofx;
			lastzz = zz - m.zofz;

			if (m.isTemp())
			{
				lastDrawMode = TEMP;
				pushRange(m.startpos, m.endpos);
				continue;
			}

			if (useStaticUnsorted)
			{
				lastDrawMode = STATIC_UNSORTED;
				pushRange(m.startpos, m.endpos);
				continue;
			}

			lastDrawMode = STATIC;

			final int radius = m.radius;
			int diameter = 1 + radius * 2;
			final int[] packedFaces = m.packedFaces;
			if (diameter >= MAX_DIAMETER) continue;

			Arrays.fill(mu.zsortHead, 0, diameter, (char) -1);
			Arrays.fill(mu.zsortTail, 0, diameter, (char) -1);

			for (char i = 0; i < packedFaces.length; ++i)
			{
				int pack = packedFaces[i];

				int x = pack >> 21;
				int y = (pack << 11) >> 22;
				int z = (pack << 21) >> 21;

				int t = z * yawcos - x * yawsin >> 16;
				int fz = y * pitchsin + t * pitchcos >> 16;
				fz += radius;

				assert fz >= 0 && fz < diameter : fz;

				if (mu.zsortTail[fz] == (char) -1)
				{
					mu.zsortHead[fz] = mu.zsortTail[fz] = i;
					mu.zsortNext[i] = (char) -1;
				}
				else
				{
					char lastFace = mu.zsortTail[fz];
					mu.zsortNext[lastFace] = i;
					mu.zsortNext[i] = (char) -1;
					mu.zsortTail[fz] = i;
				}
			}

			if (packedFaces.length * 3 > alphaElements.remaining())
			{
				if (packedFaces.length * 3 > alphaElements.capacity())
				{
					Log.d(TAG, "Alpha model too large: " + packedFaces.length);
					continue;
				}
				flush();
			}

			final int start = m.startpos / (VERT_SIZE >> 2);
			for (int i = diameter - 1; i >= 0; --i)
			{
				for (char face = mu.zsortHead[i]; face != (char) -1; face = mu.zsortNext[face])
				{
					int faceIdx = face * 3;
					faceIdx += start;
					alphaElements.put(faceIdx++);
					alphaElements.put(faceIdx++);
					alphaElements.put(faceIdx++);
				}
			}
		}

		flush();
	}

	private void flush()
	{
		if (lastDrawMode == TEMP)
		{
			convertForDraw(VAO.VERT_SIZE);
			assert drawOff.limit() > 0;
			glUniform3i(uniBase, 0, 0, 0);
			glBindVertexArray(lastVao);
			glDepthMask(false);
			multiDrawArrays(GL_TRIANGLES, drawOff, drawEnd);
			glDepthMask(true);
			drawOff.clear();
			drawEnd.clear();
		}
		else if (lastDrawMode == STATIC)
		{
			alphaElements.flip();
			glUniform3i(uniBase, lastzx << 10, 0, lastzz << 10);
			glBindVertexArray(lastVao);
			if (Gfx.vulkan)
			{
				// Respecifying one element buffer per draw would mean a new buffer each time on
				// Vulkan; append to the frame's index ring instead
				long offset = Gfx.streamIndices(alphaElements);
				glDepthMask(false);
				glDrawElements(GL_TRIANGLES, alphaElements.limit(), GL_UNSIGNED_INT, (int) offset);
				glDepthMask(true);
			}
			else
			{
				glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, elementBufferId);
				glBufferData(GL_ELEMENT_ARRAY_BUFFER, alphaElements.limit() * Integer.BYTES, alphaElements, GL_STREAM_DRAW);
				glDepthMask(false);
				glDrawElements(GL_TRIANGLES, alphaElements.limit(), GL_UNSIGNED_INT, 0);
				glDepthMask(true);
				glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
			}
			alphaElements.clear();
		}
		else if (lastDrawMode == STATIC_UNSORTED)
		{
			convertForDraw(VERT_SIZE);
			assert drawOff.limit() > 0;
			glUniform3i(uniBase, lastzx << 10, 0, lastzz << 10);
			glBindVertexArray(lastVao);
			glDepthMask(false);
			multiDrawArrays(GL_TRIANGLES, drawOff, drawEnd);
			glDepthMask(true);
			drawOff.clear();
			drawEnd.clear();
		}
	}

	void multizoneLocs(Scene scene, int zx, int zz, int cx, int cz, Zone[][] zones)
	{
		int offset = scene.getWorldViewId() == WorldView.TOPLEVEL ? GpuGlesPlugin.SCENE_OFFSET >> 3 : 0;
		for (int i = 0; i < alphaModels.size(); ++i) // NOPMD: ForLoopCanBeForeach
		{
			AlphaModel m = alphaModels.get(i);
			if (m.lx == -1) continue;

			int max = Integer.MAX_VALUE;
			int closestZoneX = -50, closestZoneZ = -50;
			for (int x = m.lx >> 3; x <= m.ux >> 3; ++x)
			{
				for (int z = m.lz >> 3; z <= m.uz >> 3; ++z)
				{
					int centerX = (zx - m.zofx + x) * 8 + 4 << 7;
					int centerZ = (zz - m.zofz + z) * 8 + 4 << 7;
					int distance = (centerX - cx) * (centerX - cx) +
						(centerZ - cz) * (centerZ - cz);
					if (distance < max)
					{
						max = distance;
						closestZoneX = centerX >> 10;
						closestZoneZ = centerZ >> 10;
					}
				}
			}
			assert closestZoneX != -50;
			if (closestZoneX != zx || closestZoneZ != zz)
			{
				assert (m.flags & AlphaModel.TEMP) == 0;
				assert closestZoneX + offset >= 0 : closestZoneX;
				assert closestZoneX + offset < zones.length : closestZoneX;
				assert closestZoneZ + offset >= 0 : closestZoneZ;
				assert closestZoneZ + offset < zones[0].length : closestZoneZ;

				Zone z = zones[closestZoneX + offset][closestZoneZ + offset];
				assert z != null;
				assert z != this;

				AlphaModel m2 = modelCache.poll();
				if (m2 == null) m2 = new AlphaModel();
				m2.id = m.id;
				m2.startpos = m.startpos;
				m2.endpos = m.endpos;
				m2.x = m.x; m2.y = m.y; m2.z = m.z;
				m2.vao = m.vao;
				m2.rid = m.rid;
				m2.level = m.level;
				m2.lx = m.lx; m2.lz = m.lz; m2.ux = m.ux; m2.uz = m.uz;
				m2.zofx = (byte) (closestZoneX - zx);
				m2.zofz = (byte) (closestZoneZ - zz);
				m2.packedFaces = m.packedFaces;
				m2.radius = m.radius;
				m2.flags = AlphaModel.TEMP;
				m.flags |= AlphaModel.SKIP;

				z.alphaModels.add(m2);
			}
		}
	}
}
