package net.runelite.client.plugins.gpugles;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_NEAREST;
import static android.opengl.GLES20.GL_NEAREST_MIPMAP_LINEAR;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TEXTURE1;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static net.runelite.client.plugins.gpugles.Gfx.glActiveTexture;
import static net.runelite.client.plugins.gpugles.Gfx.glBindTexture;
import static net.runelite.client.plugins.gpugles.Gfx.glDeleteTextures;
import static net.runelite.client.plugins.gpugles.Gfx.glGenTextures;
import static net.runelite.client.plugins.gpugles.Gfx.glGenerateMipmap;
import static net.runelite.client.plugins.gpugles.Gfx.glTexParameterf;
import static net.runelite.client.plugins.gpugles.Gfx.glTexParameteri;
import static android.opengl.GLES30.GL_RGBA8;
import static android.opengl.GLES30.GL_TEXTURE_2D_ARRAY;
import static net.runelite.client.plugins.gpugles.Gfx.glTexStorage3D;
import static net.runelite.client.plugins.gpugles.Gfx.glTexSubImage3D;
import android.util.Log;

import java.nio.ByteBuffer;

import javax.inject.Singleton;

import net.runelite.api.Texture;
import net.runelite.api.TextureProvider;

/**
 * Ported from net.runelite.client.plugins.gpu.TextureManager.
 * GLES 3.0 has glTexStorage3D natively (no LWJGL capability check needed) and
 * glTexImage3D/glTexSubImage3D. Anisotropic filtering is an extension and is
 * detected via {@code glGetString(GL_EXTENSIONS)} containing "GL_EXT_texture_filter_anisotropic"
 * — keep it stubbed for now; GLES will silently ignore unknown texparams.
 */
@Singleton
class TextureManager
{
	private static final String TAG = "TextureManager";

	static final int TEXTURE_COUNT = 256;
	private static final int TEXTURE_SIZE = 128;

	int initTextureArray(TextureProvider textureProvider)
	{
		if (!allTexturesLoaded(textureProvider))
		{
			return -1;
		}

		Texture[] textures = textureProvider.getTextures();

		int[] arr = new int[1];
		glGenTextures(1, arr, 0);
		int textureArrayId = arr[0];

		glBindTexture(GL_TEXTURE_2D_ARRAY, textureArrayId);
		// glTexStorage3D is core in GLES 3.0+ — desktop falls back to glTexImage3D
		// for old GL drivers without GL 4.2. We always have it.
		glTexStorage3D(GL_TEXTURE_2D_ARRAY, 8, GL_RGBA8, TEXTURE_SIZE, TEXTURE_SIZE, textures.length);

		glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		// CRITICAL: WRAP_T defaults to REPEAT, and the desktop plugin leaves it
		// at the default on purpose. Many OSRS leaf/foliage textures rely on V
		// tiling beyond [0, 1] — setting WRAP_T = CLAMP_TO_EDGE causes every
		// fragment of those triangles to clamp to the texture's edge texel
		// (often a near-solid colour), producing the triangle-shaped "blur"
		// patches where adjacent tris sample real texture while the tiled ones
		// sample a single texel. Leave T alone.

		// Force brightness to 1.0 to upload unmodified textures to GPU
		double save = textureProvider.getBrightness();
		textureProvider.setBrightness(1.0d);

		updateTextures(textureProvider, textureArrayId);

		textureProvider.setBrightness(save);

		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_2D_ARRAY, textureArrayId);
		glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
		glActiveTexture(GL_TEXTURE0);

		return textureArrayId;
	}

	private static final int GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE;
	private static final int GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF;

	void setAnisotropicFilteringLevel(int textureArrayId, int level)
	{
		glBindTexture(GL_TEXTURE_2D_ARRAY, textureArrayId);

		if (level == 0)
		{
			glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		}
		else
		{
			glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
		}

		// Anisotropic filtering is an extension on GLES. Setting the param is
		// harmless if the driver doesn't support it (it'll throw GL_INVALID_ENUM
		// silently). On Adreno 6xx+ / Mali T-class+ this is widely supported.
		float[] maxParam = new float[1];
		Gfx.glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, maxParam, 0);
		if (maxParam[0] >= 1f)
		{
			float aniso = Math.max(1f, Math.min(maxParam[0], level));
			glTexParameterf(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
		}
	}

	void freeTextureArray(int textureArrayId)
	{
		glDeleteTextures(1, new int[]{ textureArrayId }, 0);
	}

	private boolean allTexturesLoaded(TextureProvider textureProvider)
	{
		Texture[] textures = textureProvider.getTextures();
		if (textures == null || textures.length == 0) return false;

		for (int textureId = 0; textureId < textures.length; textureId++)
		{
			Texture texture = textures[textureId];
			if (texture != null)
			{
				int[] pixels = textureProvider.load(textureId);
				if (pixels == null) return false;
			}
		}
		return true;
	}

	private void updateTextures(TextureProvider textureProvider, int textureArrayId)
	{
		Texture[] textures = textureProvider.getTextures();
		glBindTexture(GL_TEXTURE_2D_ARRAY, textureArrayId);

		int cnt = 0;
		for (int textureId = 0; textureId < textures.length; textureId++)
		{
			Texture texture = textures[textureId];
			if (texture != null)
			{
				int[] srcPixels = textureProvider.load(textureId);
				if (srcPixels == null)
				{
					Log.w(TAG, "No pixels for texture " + textureId);
					continue;
				}
				++cnt;

				if (srcPixels.length != TEXTURE_SIZE * TEXTURE_SIZE)
				{
					Log.w(TAG, "Texture size for " + textureId + " is " + srcPixels.length);
					continue;
				}

				byte[] pixels = convertPixels(srcPixels, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
				ByteBuffer pixelBuffer = ByteBuffer.allocateDirect(pixels.length);
				pixelBuffer.put(pixels);
				pixelBuffer.flip();
				glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, textureId, TEXTURE_SIZE, TEXTURE_SIZE,
					1, GL_RGBA, GL_UNSIGNED_BYTE, pixelBuffer);
			}
		}

		Log.d(TAG, "Uploaded textures " + cnt);
	}

	private static byte[] convertPixels(int[] srcPixels, int width, int height, int textureWidth, int textureHeight)
	{
		byte[] pixels = new byte[textureWidth * textureHeight * 4];

		int pixelIdx = 0;
		int srcPixelIdx = 0;

		int offset = (textureWidth - width) * 4;

		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int rgb = srcPixels[srcPixelIdx++];
				if (rgb != 0)
				{
					pixels[pixelIdx++] = (byte) (rgb >> 16);
					pixels[pixelIdx++] = (byte) (rgb >> 8);
					pixels[pixelIdx++] = (byte) rgb;
					pixels[pixelIdx++] = (byte) -1;
				}
				else
				{
					pixelIdx += 4;
				}
			}
			pixelIdx += offset;
		}
		return pixels;
	}

	float[] computeTextureAnimations(TextureProvider textureProvider)
	{
		Texture[] textures = textureProvider.getTextures();
		if (textures.length > TEXTURE_COUNT)
		{
			Log.w(TAG, "texture limit exceeded: " + textures.length + " > " + TEXTURE_COUNT);
		}

		float[] anims = new float[TEXTURE_COUNT * 2];
		for (int i = 0; i < Math.min(TEXTURE_COUNT, textures.length); ++i)
		{
			Texture texture = textures[i];
			if (texture == null) continue;

			float u = 0f, v = 0f;
			switch (texture.getAnimationDirection())
			{
				case 1: v = -1f; break;
				case 3: v = 1f; break;
				case 2: u = -1f; break;
				case 4: u = 1f; break;
			}
			int speed = texture.getAnimationSpeed();
			u *= speed;
			v *= speed;

			anims[i * 2] = u;
			anims[i * 2 + 1] = v;
		}
		return anims;
	}
}
