package rs117.hd.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import rs117.hd.opengl.GLState;
import rs117.hd.opengl.shader.ShaderProgram;
import rs117.hd.vulkan.Vk;

import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;

public final class RenderState {
	private final List<GLState> states = new ArrayList<>();

	public final GLFramebuffer framebuffer = addState(GLFramebuffer::new);
	public final GLFramebufferTextureLayer framebufferTextureLayer = addState(GLFramebufferTextureLayer::new);
	public final GLDrawBuffer drawBuffer = addState(GLDrawBuffer::new);
	public final GLShaderProgram program = addState(GLShaderProgram::new);
	public final GLViewport viewport = addState(GLViewport::new);
	public final GLVao vao = addState(GLVao::new);
	public final GLIdo ido = addState(GLIdo::new);
	public final GLUbo ubo = addState(GLUbo::new);
	public final GLDepthMask depthMask = addState(GLDepthMask::new);
	public final GLDepthFunc depthFunc = addState(GLDepthFunc::new);
	public final GLColorMask colorMask = addState(GLColorMask::new);
	public final GLBlendFunc blendFunc = addState(GLBlendFunc::new);
	public final GLPolygonOffset polygonOffset = addState(GLPolygonOffset::new);
	public final GLCullFace cullFace = addState(GLCullFace::new);
	public final GLEnable enable = addState(GLEnable::new);
	public final GLDisable disable = addState(GLDisable::new);

	public void apply() {
		for (GLState state : states)
			state.apply();
	}

	public void reset() {
		for (GLState state : states)
			state.reset();
	}

	private <T extends GLState> T addState(Supplier<T> supplier) {
		T state = supplier.get();
		states.add(state);
		return state;
	}

	public static final class GLFramebuffer extends GLState.IntArray {
		private GLFramebuffer() {
			super(2);
		}

		@Override
		protected void applyValues(int[] values) { if (!Vk.ACTIVE) glBindFramebuffer(values[0], values[1]); }
	}

	public static final class GLFramebufferTextureLayer extends GLState.IntArray {
		private GLFramebufferTextureLayer() { super(5); }

		@Override
		protected void applyValues(int[] values) {
			if (!Vk.ACTIVE)
				glFramebufferTextureLayer(values[0], values[1], values[2], values[3], values[4]);
		}
	}

	public static final class GLViewport extends GLState.IntArray {
		private GLViewport() {
			super(4);
		}

		@Override
		protected void applyValues(int[] values) {
			if (Vk.ACTIVE) {
				Vk.viewport(values[0], values[1], values[2], values[3]);
			} else {
				glViewport(values[0], values[1], values[2], values[3]);
			}
		}
	}

	public static final class GLShaderProgram extends GLState.Object<ShaderProgram> {
		@Override
		protected void applyValue(ShaderProgram program) { program.use(); }
	}

	public static final class GLDrawBuffer extends GLState.Int {
		@Override
		protected void applyValue(int buf) { if (!Vk.ACTIVE) glDrawBuffer(buf); }
	}

	public static final class GLVao extends GLState {
		int vao, ebo;
		int appliedVao, appliedEbo;

		public void setVao(int vao) {
			setVaoAndEbo(vao, 0);
		}

		public void setVaoAndEbo(int vao, int ebo) {
			this.vao = vao;
			this.ebo = ebo;
			hasValue = true;
		}

		@Override
		protected void internalApply() {
			if (Vk.ACTIVE) {
				Vk.bindVertexLayout(vao, ebo);
				appliedVao = vao;
				appliedEbo = ebo;
				return;
			}
			if (!hasApplied || vao != appliedVao)
				glBindVertexArray(vao);
			if (ebo != 0 && (!hasApplied || ebo != appliedEbo))
				glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
		}
	}

	public static final class GLIdo extends GLState.Int {
		@Override
		protected void applyValue(int ebo) { if (!Vk.ACTIVE) glBindBuffer(GL_DRAW_INDIRECT_BUFFER, ebo); }
	}

	public static final class GLUbo extends GLState.Int {
		@Override
		protected void applyValue(int ubo) { if (!Vk.ACTIVE) glBindBuffer(GL_UNIFORM_BUFFER, ubo); }
	}

	public static final class GLDepthMask extends GLState.Bool {
		@Override
		protected void applyValue(boolean enabled) {
			if (Vk.ACTIVE) {
				Vk.depthMask(enabled);
			} else {
				glDepthMask(enabled);
			}
		}
	}

	public static final class GLDepthFunc extends GLState.Int {
		@Override
		protected void applyValue(int func) {
			if (Vk.ACTIVE) {
				Vk.depthFunc(func);
			} else {
				glDepthFunc(func);
			}
		}
	}

	public static final class GLCullFace extends GLState.Int {
		@Override
		protected void applyValue(int mode) {
			if (Vk.ACTIVE) {
				Vk.cullFace(mode);
			} else {
				glCullFace(mode);
			}
		}
	}

	public static final class GLBlendFunc extends GLState.IntArray {
		private GLBlendFunc() {
			super(4);
		}

		@Override
		protected void applyValues(int[] values) {
			if (Vk.ACTIVE) {
				Vk.blendFuncSeparate(values[0], values[1], values[2], values[3]);
			} else {
				glBlendFuncSeparate(values[0], values[1], values[2], values[3]);
			}
		}
	}

	public static final class GLPolygonOffset extends GLState.FloatArray {
		public GLPolygonOffset() {
			super(2);
		}

		@Override
		protected void applyValues(float[] values) {
			if (Vk.ACTIVE) {
				Vk.polygonOffset(values[0], values[1]);
			} else {
				glPolygonOffset(values[0], values[1]);
			}
		}
	}

	public static final class GLColorMask extends GLState.BoolArray {
		private GLColorMask() {
			super(4);
		}

		@Override
		protected void applyValues(boolean[] values) {
			if (Vk.ACTIVE) {
				Vk.colorMask(values[0], values[1], values[2], values[3]);
			} else {
				glColorMask(values[0], values[1], values[2], values[3]);
			}
		}
	}

	public final class GLEnable extends GLState.IntSet {
		@Override
		protected void applyTarget(int target) {
			if (Vk.ACTIVE) {
				Vk.enable(target);
			} else {
				glEnable(target);
			}
		}

		public void set(int target) {
			add(target);
			disable.remove(target);
		}
	}

	public final class GLDisable extends GLState.IntSet {
		@Override
		protected void applyTarget(int target) {
			if (Vk.ACTIVE) {
				Vk.disable(target);
			} else {
				glDisable(target);
			}
		}

		public void set(int target) {
			add(target);
			enable.remove(target);
		}
	}
}
