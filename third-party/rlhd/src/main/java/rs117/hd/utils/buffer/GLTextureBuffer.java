package rs117.hd.utils.buffer;

import lombok.Getter;

import static org.lwjgl.opengl.GL33C.*;

public class GLTextureBuffer extends GLBuffer {
	@Getter
	private int texId;

	public GLTextureBuffer(String name, int usage) {
		this(name, usage, 0);
	}

	public GLTextureBuffer(String name, int usage, int storageFlags) {
		super(name, GL_TEXTURE_BUFFER, usage, storageFlags);
	}

	@Override
	public GLTextureBuffer initialize(long initialCapacity) {
		super.initialize(initialCapacity);

		if (rs117.hd.vulkan.Vk.ACTIVE) {
			// Read through a storage buffer; the "texture" is the buffer itself
			texId = id;
			return this;
		}

		// Create texture
		texId = glGenTextures();
		glBindTexture(target, texId);

		// RGB32 signed integer texture buffer
		glTexBuffer(target, GL_RGB32I, id);

		glBindTexture(target, 0);
		return this;
	}

	@Override
	public boolean ensureCapacity(long byteOffset, long numBytes) {
		int oldId = id;
		boolean resized = super.ensureCapacity(byteOffset, numBytes);
		if (rs117.hd.vulkan.Vk.ACTIVE) {
			texId = id;
			return resized;
		}
		if (oldId != id) {
			glBindTexture(target, texId);
			glTexBuffer(target, GL_RGB32I, id);
			glBindTexture(target, 0);
		}
		return resized;
	}

	@Override
	public void destroy() {
		if (texId != 0 && rs117.hd.vulkan.Vk.ACTIVE) {
			texId = 0;
		} else if (texId != 0) {
			glDeleteTextures(texId);
			texId = 0;
		}

		super.destroy();
	}
}
