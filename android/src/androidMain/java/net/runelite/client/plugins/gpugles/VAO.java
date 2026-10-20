package net.runelite.client.plugins.gpugles;

import static android.opengl.GLES20.GL_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_INT;
import static android.opengl.GLES20.GL_SHORT;
import static android.opengl.GLES20.GL_TRIANGLES;
import static net.runelite.client.plugins.gpugles.Gfx.glBindBuffer;
import static net.runelite.client.plugins.gpugles.Gfx.glDrawArrays;
import static net.runelite.client.plugins.gpugles.Gfx.glEnableVertexAttribArray;
import static net.runelite.client.plugins.gpugles.Gfx.glColorMask;
import static net.runelite.client.plugins.gpugles.Gfx.glDepthMask;
import static net.runelite.client.plugins.gpugles.Gfx.glUniform3i;
import static net.runelite.client.plugins.gpugles.Gfx.glUniform4i;
import static net.runelite.client.plugins.gpugles.Gfx.glVertexAttribPointer;
import static android.opengl.GLES30.GL_DYNAMIC_DRAW;
import static net.runelite.client.plugins.gpugles.Gfx.glBindVertexArray;
import static net.runelite.client.plugins.gpugles.Gfx.glDeleteVertexArrays;
import static net.runelite.client.plugins.gpugles.Gfx.glGenVertexArrays;
import static net.runelite.client.plugins.gpugles.Gfx.glVertexAttribIPointer;
import static net.runelite.client.plugins.gpugles.GpuGlesPlugin.uniEntityTint;

import java.util.Arrays;

import net.runelite.api.Renderable;
import net.runelite.api.Scene;

/**
 * Per-temporary-mesh VAO + VBO. The desktop plugin keeps a pool of dynamic VAOs
 * around for transient geometry (dynamic objects, NPCs, temp models) — each one
 * collects a batch of vertices and tracks 1..N draw ranges with their projection
 * + scene context. On commit, {@link #draw()} replays the batch.
 *
 * Ported from net.runelite.client.plugins.gpu.VAO. Minimal differences from
 * desktop: glGen/Delete VAO use array-form GLES bindings.
 */
class VAO
{
	// Temporary vertex format
	//  index 0: vec3(x, y, z)         (floats, 12 bytes)
	//  index 1: int abhsl             (4 bytes)
	//  index 2: short vec4(id, u, v, 0) (8 bytes)
	static final int VERT_SIZE = 24;

	final VBO vbo;
	int vao;

	VAO(int size)
	{
		vbo = new VBO(size);
	}

	void init()
	{
		int[] arr = new int[1];
		glGenVertexArrays(1, arr, 0);
		vao = arr[0];
		glBindVertexArray(vao);

		vbo.init(GL_DYNAMIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, vbo.bufId);

		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, VERT_SIZE, 0);

		glEnableVertexAttribArray(1);
		glVertexAttribIPointer(1, 1, GL_INT, VERT_SIZE, 12);

		glEnableVertexAttribArray(2);
		glVertexAttribIPointer(2, 4, GL_SHORT, VERT_SIZE, 16);

		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);
	}

	void destroy()
	{
		vbo.destroy();
		glDeleteVertexArrays(1, new int[]{ vao }, 0);
		vao = 0;
	}

	static class Range
	{
		int endpos;
		float[] projection;
		byte h, s, l, a;
		byte renderMethod;
	}

	// Per-range we store a *reference* to the owning scene's captured projection MATRIX
	// (SceneContext.projection — a stable float[16] filled once per frame at
	// preSceneDraw), NOT the Projection object. The opaque/alpha VAOs are drawn lazily
	// at the TOPLEVEL pass, long after a sub-WorldView entity was sealed; RuneLite reuses
	// the Projection instance across worldviews, so reading getProjection() off the object
	// at draw time yielded the wrong (since-overwritten) matrix and the entity — e.g. the
	// Gemstone Crab, hosted on an instance sub-WorldView — drew off-screen. Each
	// SceneContext owns its own projection array, so the reference stays correct. Mirrors
	// desktop GpuPlugin's VAO.Range.projection.
	Range[] ranges = new Range[4];
	int off;

	{
		for (int i = 0; i < ranges.length; ++i)
		{
			ranges[i] = new Range();
		}
	}

	void addRange(float[] projection, Scene scene, int renderMode)
	{
		assert vbo.mapped;

		if (off > 0)
		{
			Range r = ranges[off - 1];
			int pos = vbo.vb.position();
			if (r.endpos == pos)
			{
				return;
			}

			// Consecutive ranges under the same projection AND render mode collapse.
			if (projection == r.projection && renderMode == r.renderMethod)
			{
				assert pos > r.endpos;
				r.endpos = pos;
				return;
			}
		}

		if (ranges.length == off)
		{
			int l = ranges.length << 1;
			ranges = Arrays.copyOf(ranges, l);
			for (int i = ranges.length >> 1; i < ranges.length; ++i)
			{
				ranges[i] = new Range();
			}
		}

		Range r = ranges[off++];
		r.endpos = vbo.vb.position();
		r.projection = projection;
		r.h = scene.getOverrideHue();
		r.s = scene.getOverrideSaturation();
		r.l = scene.getOverrideLuminance();
		r.a = scene.getOverrideAmount();
		r.renderMethod = (byte) renderMode;
	}

	void draw()
	{
		assert !vbo.mapped;

		glUniform3i(GpuGlesPlugin.uniBase, 0, 0, 0);

		int start = 0;
		for (int i = 0; i < off; ++i)
		{
			Range range = ranges[i];
			int end = range.endpos;

			int count = end - start;

			GpuGlesPlugin.setEntityProjection(range.projection);
			glUniform4i(uniEntityTint, range.h, range.s, range.l, range.a);

			glBindVertexArray(vao);

			if (range.renderMethod == Renderable.RENDERMODE_SORTED_NO_DEPTH)
			{
				// Colour without writing depth, then depth without writing colour: the model
				// composites internally in its own sorted order but still occludes what comes
				// after it. Two passes over the same range — cheap, these are small models.
				glDepthMask(false);
				glDrawArrays(GL_TRIANGLES, start / (VERT_SIZE / 4), count / (VERT_SIZE / 4));
				glDepthMask(true);

				glColorMask(false, false, false, false);
				glDrawArrays(GL_TRIANGLES, start / (VERT_SIZE / 4), count / (VERT_SIZE / 4));
				glColorMask(true, true, true, true);
			}
			else if (range.renderMethod == Renderable.RENDERMODE_UNSORTED_NO_DEPTH)
			{
				glDepthMask(false);
				glDrawArrays(GL_TRIANGLES, start / (VERT_SIZE / 4), count / (VERT_SIZE / 4));
				glDepthMask(true);
			}
			else
			{
				glDrawArrays(GL_TRIANGLES, start / (VERT_SIZE / 4), count / (VERT_SIZE / 4));
			}

			start = end;
		}
	}

	void reset()
	{
		for (int i = 0; i < off; ++i)
		{
			ranges[i].projection = null;
		}
		off = 0;
	}
}
