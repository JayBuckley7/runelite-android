package rs117.hd.opengl.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import rs117.hd.opengl.uniforms.UniformBuffer;
import rs117.hd.utils.Destructible;
import rs117.hd.utils.DestructibleHandler;
import rs117.hd.vulkan.Vk;
import rs117.hd.vulkan.VkShaders;

import static org.lwjgl.opengl.GL33C.*;
import static rs117.hd.HdPlugin.APPLE;

@Slf4j
public class ShaderProgram implements Destructible {
	@RequiredArgsConstructor
	private static class UniformBufferBlockPair {
		public final UniformBuffer<?> buffer;
		public final int uboProgramIndex;
	}

	private final List<UniformProperty> uniformProperties = new ArrayList<>();
	private final List<UniformBufferBlockPair> uniformBlockMappings = new ArrayList<>();

	protected final ShaderTemplate shaderTemplate;

	private int program;
	@Getter
	private boolean viable = true;

	public ShaderProgram(Consumer<ShaderTemplate> templateConsumer) {
		shaderTemplate = new ShaderTemplate();
		templateConsumer.accept(shaderTemplate);
	}

	/** Vulkan: while non-null, initialize() is only being run to learn each sampler's texture unit. */
	private static Map<String, Integer> vkRecordingUnits;

	public void compile(ShaderIncludes includes) throws ShaderException, IOException {
		if (Vk.ACTIVE) {
			compileVulkan(includes);
			return;
		}

		int newProgram;
		try {
			newProgram = shaderTemplate.compile(includes);
		} catch (ShaderException ex) {
			viable = false;
			throw ex;
		}

		if (isValid())
			destroy();

		program = newProgram;
		assert isValid();

		for (var prop : uniformProperties) {
			prop.uniformIndex = glGetUniformLocation(program, prop.uniformName);
			if (prop.uniformIndex == -1 && !prop.ignoreMissing)
				log.warn("{} has missing or unused {}: {}", getClass().getSimpleName(), prop.getClass().getSimpleName(), prop.uniformName);
		}

		for (var ubo : includes.uniformBuffers) {
			int bindingIndex = glGetUniformBlockIndex(program, ubo.getUniformBlockName());
			if (bindingIndex != -1)
				uniformBlockMappings.add(new UniformBufferBlockPair(ubo, bindingIndex));
		}

		use();
		initialize();

		// Shader validation can be horribly slow on macOS with AMD GPUs
		if (!APPLE || log.isDebugEnabled()) {
			glValidateProgram(program);
			if (glGetProgrami(program, GL_VALIDATE_STATUS) == GL_FALSE) {
				String err = glGetProgramInfoLog(program);
				log.error("Failed to validate shader program: {}", getClass().getSimpleName(), new ShaderException(err));
			}
		}
	}

	private void compileVulkan(ShaderIncludes includes) throws ShaderException, IOException {
		// Samplers get fixed descriptor bindings at their texture unit, which subclasses only
		// reveal by assigning them in initialize(); run it once in recording mode to find out.
		Map<String, Integer> units = new HashMap<>();
		vkRecordingUnits = units;
		try {
			initialize();
		} finally {
			vkRecordingUnits = null;
		}

		Map<String, VkShaders.PushUniform> push = new HashMap<>();
		int newProgram;
		try {
			newProgram = shaderTemplate.compileVulkan(includes, units, push);
		} catch (ShaderException ex) {
			viable = false;
			throw ex;
		}

		if (isValid())
			destroy();
		program = newProgram;

		for (var prop : uniformProperties) {
			if (prop instanceof UniformTexture || prop instanceof UniformImage) {
				prop.uniformIndex = units.containsKey(prop.uniformName) ? 0 : -1;
				continue;
			}
			var pu = push.get(prop.uniformName);
			prop.uniformIndex = pu != null ? pu.offset : -1;
			if (prop.uniformIndex == -1 && !prop.ignoreMissing)
				log.warn("{} has missing or unused {}: {}", getClass().getSimpleName(), prop.getClass().getSimpleName(), prop.uniformName);
		}

		use();
		initialize();
	}

	/**
	 * Vulkan: this program's loose uniforms. GL keeps uniform values per program, but push
	 * constants are command-buffer state shared by every pipeline, so Vk re-pushes the whole
	 * block whenever this program is used.
	 */
	private final int[] vkPushData = new int[Vk.PUSH_CONSTANT_WORDS];

	private static void vkPushFloats(ShaderProgram p, int offset, float... values) {
		if (offset < 0)
			return;
		int base = offset / 4;
		for (int i = 0; i < values.length && base + i < p.vkPushData.length; i++)
			p.vkPushData[base + i] = Float.floatToRawIntBits(values[i]);
		Vk.pushDataChanged(p.vkPushData);
	}

	private static void vkPushInts(ShaderProgram p, int offset, int... values) {
		if (offset < 0)
			return;
		int base = offset / 4;
		for (int i = 0; i < values.length && base + i < p.vkPushData.length; i++)
			p.vkPushData[base + i] = values[i];
		Vk.pushDataChanged(p.vkPushData);
	}

	protected void initialize() {}

	public boolean isValid() {
		return program != 0;
	}

	public boolean isActive() {
		// Meant for debugging only
		if (Vk.ACTIVE)
			return true;
		return program == glGetInteger(GL_CURRENT_PROGRAM);
	}

	@SuppressWarnings("unchecked")
	public <T extends UniformBuffer<?>> T getUniformBufferBlock(int UniformBlockIndex) {
		for (UniformBufferBlockPair pair : uniformBlockMappings)
			if (pair.buffer.getBindingIndex() == UniformBlockIndex)
				return (T) pair.buffer;
		return null;
	}

	public void use() {
		assert program != 0;
		if (Vk.ACTIVE) {
			Vk.useProgram(program, vkPushData);
			return;
		}
		glUseProgram(program);

		for (UniformBufferBlockPair pair : uniformBlockMappings)
			glUniformBlockBinding(program, pair.uboProgramIndex, pair.buffer.getBindingIndex());
	}

	@Override
	@SuppressWarnings("deprecation")
	protected void finalize() {
		if (program != 0)
			DestructibleHandler.queueLeakedDestruction(this);
	}

	@Override
	public void destroy() {
		viable = true;
		if (program == 0)
			return;

		if (Vk.ACTIVE) {
			Vk.programDestroy(program);
		} else {
			glDeleteProgram(program);
		}
		program = 0;

		for (var prop : uniformProperties)
			prop.destroy();

		uniformBlockMappings.clear();
	}

	private static class UniformProperty {
		ShaderProgram program;
		String uniformName;
		int uniformIndex;
		boolean ignoreMissing;

		void destroy() {
			uniformIndex = -1;
		}
	}

	private <T extends UniformProperty> T addUniform(T property, String uniformName) {
		property.program = this;
		property.uniformName = uniformName;
		uniformProperties.add(property);
		return property;
	}

	public static class UniformBool extends UniformProperty {
		public void set(boolean bool) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushInts(program, uniformIndex, bool ? 1 : 0);
				return;
			}
			assert program.isActive();
			glUniform1i(uniformIndex, bool ? 1 : 0);
		}
	}

	public UniformBool addUniformBool(String uniformName) {
		return addUniform(new UniformBool(), uniformName);
	}

	public static class UniformTexture extends UniformProperty {
		public void set(int textureUnit) {
			assert textureUnit >= GL_TEXTURE0 : "Did you accidentally pass in an image unit?";
			if (Vk.ACTIVE) {
				// Bound by unit through the descriptor set; only the mapping matters
				if (vkRecordingUnits != null) vkRecordingUnits.put(uniformName, textureUnit - GL_TEXTURE0);
				return;
			}
			assert program.isActive();
			if (uniformIndex != -1)
				glUniform1i(uniformIndex, textureUnit - GL_TEXTURE0);
		}
	}

	public UniformTexture addUniformTexture(String uniformName) {
		return addUniform(new UniformTexture(), uniformName);
	}

	public static class UniformImage extends UniformProperty {
		public void set(int imageUnit) {
			assert imageUnit < GL_TEXTURE0 : "Did you accidentally pass in a texture unit?";
			if (Vk.ACTIVE)
				return; // image load/store isn't used by the Vulkan renderer
			assert program.isActive();
			glUniform1i(uniformIndex, imageUnit);
		}
	}

	public UniformImage addUniformImage(String uniformName) {
		return addUniform(new UniformImage(), uniformName);
	}

	public static class Uniform1i extends UniformProperty {
		public void set(int value) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushInts(program, uniformIndex, value);
				return;
			}
			assert program.isActive();
			glUniform1i(uniformIndex, value);
		}
	}

	public Uniform1i addUniform1i(String uniformName) {
		return addUniform(new Uniform1i(), uniformName);
	}

	public static class Uniform2i extends UniformProperty {
		public void set(int x, int y) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushInts(program, uniformIndex, x, y);
				return;
			}
			assert program.isActive();
			glUniform2i(uniformIndex, x, y);
		}

		public void set(int... ivec2) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushInts(program, uniformIndex, ivec2);
				return;
			}
			assert program.isActive();
			glUniform2iv(uniformIndex, ivec2);
		}
	}

	public Uniform2i addUniform2i(String uniformName) {
		return addUniform(new Uniform2i(), uniformName);
	}

	public static class Uniform3i extends UniformProperty {
		public void set(int x, int y, int z) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushInts(program, uniformIndex, x, y, z);
				return;
			}
			assert program.isActive();
			glUniform3i(uniformIndex, x, y, z);
		}

		public void set(int... ivec3) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushInts(program, uniformIndex, ivec3);
				return;
			}
			assert program.isActive();
			glUniform3iv(uniformIndex, ivec3);
		}
	}

	public Uniform3i addUniform3i(String uniformName) {
		return addUniform(new Uniform3i(), uniformName);
	}

	public static class Uniform4i extends UniformProperty {
		public void set(int x, int y, int z, int w) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushInts(program, uniformIndex, x, y, z, w);
				return;
			}
			assert program.isActive();
			glUniform4i(uniformIndex, x, y, z, w);
		}

		public void set(int... ivec4) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushInts(program, uniformIndex, ivec4);
				return;
			}
			assert program.isActive();
			glUniform4iv(uniformIndex, ivec4);
		}
	}

	public Uniform4i addUniform4i(String uniformName) {
		return addUniform(new Uniform4i(), uniformName);
	}

	public static class Uniform1f extends UniformProperty {
		public void set(float value) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushFloats(program, uniformIndex, value);
				return;
			}
			assert program.isActive();
			glUniform1f(uniformIndex, value);
		}
	}

	public Uniform1f addUniform1f(String uniformName) {
		return addUniform(new Uniform1f(), uniformName);
	}

	public static class Uniform2f extends UniformProperty {
		public void set(float x, float y) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushFloats(program, uniformIndex, x, y);
				return;
			}
			assert program.isActive();
			glUniform2f(uniformIndex, x, y);
		}

		public void set(float... vec2) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushFloats(program, uniformIndex, vec2);
				return;
			}
			assert program.isActive();
			glUniform2fv(uniformIndex, vec2);
		}
	}

	public Uniform2f addUniform2f(String uniformName) {
		return addUniform(new Uniform2f(), uniformName);
	}

	public static class Uniform3f extends UniformProperty {
		public void set(float x, float y, float z) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushFloats(program, uniformIndex, x, y, z);
				return;
			}
			assert program.isActive();
			glUniform3f(uniformIndex, x, y, z);
		}

		public void set(float... vec3) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushFloats(program, uniformIndex, vec3);
				return;
			}
			assert program.isActive();
			glUniform3fv(uniformIndex, vec3);
		}
	}

	public Uniform3f addUniform3f(String uniformName) {
		return addUniform(new Uniform3f(), uniformName);
	}

	public static class Uniform4f extends UniformProperty {
		public void set(float x, float y, float z, float w) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushFloats(program, uniformIndex, x, y, z, w);
				return;
			}
			assert program.isActive();
			glUniform4f(uniformIndex, x, y, z, w);
		}

		public void set(float... vec4) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushFloats(program, uniformIndex, vec4);
				return;
			}
			assert program.isActive();
			glUniform4fv(uniformIndex, vec4);
		}
	}

	public Uniform4f addUniform4f(String uniformName) {
		return addUniform(new Uniform4f(), uniformName);
	}

	public static class UniformMat4 extends UniformProperty {
		public void set(float[] mat4) {
			if (Vk.ACTIVE) {
				if (vkRecordingUnits == null) vkPushFloats(program, uniformIndex, mat4);
				return;
			}
			assert program.isActive();
			glUniformMatrix4fv(uniformIndex, false, mat4);
		}
	}

	public UniformMat4 addUniformMat4(String uniformName) {
		return addUniform(new UniformMat4(), uniformName);
	}
}
