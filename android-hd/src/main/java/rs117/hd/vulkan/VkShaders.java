package rs117.hd.vulkan;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns GLES shader sources (117 HD's, and the GPU plugin's) into Vulkan GLSL.
 *
 * Bindings are fixed by name rather than queried after linking: uniform blocks go to set 0 at
 * their GL binding index, samplers to set 1 at their texture unit, the face data storage buffer
 * to set 2. Loose (non-opaque) uniforms become members of a push-constant block shared by both
 * stages, with the offsets reported back so the uniform setters can push them. Callers whose
 * loose uniforms don't fit in push constants can instead name the few that change per draw;
 * the rest (including arrays) then go to a std140 uniform block at a binding of their choice.
 * Varying locations and the clip-space depth remap are handled natively after preprocessing.
 */
@Slf4j
public final class VkShaders {
	private VkShaders() {}

	// glslang predefines VULKAN (=100) for Vulkan targets; redefining it is a compile error.
	public static final String HEADER = "#version 320 es\n"
		+ "precision highp float;\nprecision highp int;\n"
		+ "precision highp sampler2D;\nprecision highp sampler3D;\nprecision highp sampler2DArray;\n"
		+ "precision highp isampler3D;\nprecision highp usampler2DArray;\n";

	/** A loose uniform placed in the push-constant block. */
	public static final class PushUniform {
		public final String type;
		public final int offset;

		PushUniform(String type, int offset) {
			this.type = type;
			this.offset = offset;
		}
	}

	/** A loose uniform placed in the std140 loose-uniform block. */
	public static final class BlockUniform {
		public final String type;
		public final int offset;
		/** 0 for a non-array; otherwise the element count, each element {@link #stride} bytes apart */
		public final int arrayLength, stride;

		BlockUniform(String type, int offset, int arrayLength, int stride) {
			this.type = type;
			this.offset = offset;
			this.arrayLength = arrayLength;
			this.stride = stride;
		}
	}

	public static final class Program {
		public String vertex, fragment;
		public final Map<String, PushUniform> pushUniforms = new LinkedHashMap<>();
		public final Map<String, BlockUniform> blockUniforms = new LinkedHashMap<>();
		/** Size in bytes of the loose-uniform block, 0 when there is none */
		public int blockSize;
	}

	private static final Pattern VERSION = Pattern.compile("(?m)^\\s*#version[^\\r\\n]*");
	private static final Pattern PRECISION = Pattern.compile("(?m)^\\s*precision\\s+\\w+\\s+\\w+\\s*;");
	private static final Pattern UBO = Pattern.compile("layout\\s*\\(\\s*std140\\s*\\)\\s*uniform\\s+(\\w+)");
	private static final Pattern SAMPLER = Pattern.compile(
		"(?m)^(\\s*)uniform\\s+((?:(?:highp|mediump|lowp)\\s+)?[iu]?sampler\\w+)\\s+(\\w+)\\s*;");
	private static final String LOOSE_TYPE =
		"((?:(?:highp|mediump|lowp)\\s+)?(?:float|int|uint|bool|[iu]?vec[234]|mat[234]))";
	private static final Pattern LOOSE = Pattern.compile(
		"(?m)^\\s*uniform\\s+" + LOOSE_TYPE + "\\s+(\\w+)\\s*;");
	/** Loose uniforms including arrays; group 3 is the array length (a number or a #define). */
	private static final Pattern LOOSE_OR_ARRAY = Pattern.compile(
		"(?m)^\\s*uniform\\s+" + LOOSE_TYPE + "\\s+(\\w+)\\s*(?:\\[\\s*(\\w+)\\s*\\])?\\s*;");
	private static final Pattern DEFINE = Pattern.compile("(?m)^\\s*#define\\s+(\\w+)\\s+(\\d+)\\s*$");

	private static final Pattern VK_RESERVED = Pattern.compile(
		"\\b(sampler|samplerShadow|texture[123]D|textureCube|texture2DArray|subpassInput)\\b");

	private static final String FACE_DATA_GL = "uniform isamplerBuffer textureFaces;";
	private static final String FACE_DATA_VK =
		"layout(std430, set = 2, binding = 0) readonly buffer AndroidFaceData { int faceData[]; };\n"
		+ "ivec4 fetchFaceData(int index) { int base = index * 3; "
		+ "return ivec4(faceData[base], faceData[base + 1], faceData[base + 2], 1); }\n";

	/** Every loose uniform goes to the push-constant block (117 HD). */
	public static Program adapt(
		String vertex,
		String fragment,
		Map<String, Integer> uboBindings,
		Map<String, Integer> samplerUnits
	) {
		return adapt(vertex, fragment, uboBindings, samplerUnits, null, -1);
	}

	/**
	 * @param pushNames       loose uniforms to keep in push constants; null keeps all of them there
	 * @param blockBinding    set-0 binding of the std140 block holding the other loose uniforms;
	 *                        only used when pushNames is non-null
	 */
	public static Program adapt(
		String vertex,
		String fragment,
		Map<String, Integer> uboBindings,
		Map<String, Integer> samplerUnits,
		Set<String> pushNames,
		int blockBinding
	) {
		Program p = new Program();
		boolean split = pushNames != null;
		Map<String, Integer> defines = new HashMap<>();
		collectDefines(vertex, defines);
		collectDefines(fragment, defines);

		// Loose uniforms from both stages share one push-constant block (and one loose block),
		// so lay them out first
		Map<String, String[]> loose = new LinkedHashMap<>();
		collectLoose(vertex, loose, split);
		collectLoose(fragment, loose, split);
		int pushOffset = 0, blockOffset = 0;
		for (var e : loose.entrySet()) {
			String name = e.getKey();
			String type = stripPrecision(e.getValue()[0]);
			String lengthToken = e.getValue()[1];
			if (!split || lengthToken == null && pushNames.contains(name)) {
				int align = alignment(type);
				pushOffset = (pushOffset + align - 1) / align * align;
				p.pushUniforms.put(name, new PushUniform(type, pushOffset));
				pushOffset += size(type);
				continue;
			}
			int length = 0;
			if (lengthToken != null) {
				Integer n = lengthToken.matches("\\d+") ? Integer.valueOf(lengthToken) : defines.get(lengthToken);
				if (n == null)
					throw new IllegalArgumentException("Unknown array length " + lengthToken + " for uniform " + name);
				length = n;
			}
			int align = length > 0 ? 16 : std140Alignment(type);
			int stride = length > 0 ? (size(type) + 15) / 16 * 16 : 0;
			blockOffset = (blockOffset + align - 1) / align * align;
			p.blockUniforms.put(name, new BlockUniform(type, blockOffset, length, stride));
			blockOffset += length > 0 ? stride * length : std140Size(type);
		}
		if (pushOffset > 128)
			log.warn("Push constants exceed 128 bytes ({}): {}", pushOffset, p.pushUniforms.keySet());
		p.blockSize = (blockOffset + 15) / 16 * 16;

		p.vertex = adaptStage(vertex, true, uboBindings, samplerUnits, p, split, blockBinding);
		p.fragment = adaptStage(fragment, false, uboBindings, samplerUnits, p, split, blockBinding);
		return p;
	}

	private static void collectDefines(String src, Map<String, Integer> out) {
		Matcher m = DEFINE.matcher(src);
		while (m.find())
			out.putIfAbsent(m.group(1), Integer.parseInt(m.group(2)));
	}

	private static void collectLoose(String src, Map<String, String[]> out, boolean withArrays) {
		Matcher m = (withArrays ? LOOSE_OR_ARRAY : LOOSE).matcher(src);
		while (m.find())
			out.putIfAbsent(m.group(2), new String[] { m.group(1), withArrays ? m.group(3) : null });
	}

	private static String stripPrecision(String type) {
		return type.replaceAll("^(highp|mediump|lowp)\\s+", "");
	}

	private static int size(String type) {
		switch (type) {
			case "vec2": case "ivec2": case "uvec2": return 8;
			case "vec3": case "ivec3": case "uvec3": return 12;
			case "vec4": case "ivec4": case "uvec4": return 16;
			case "mat2": return 32;
			case "mat3": return 48;
			case "mat4": return 64;
			default: return 4;
		}
	}

	// Push constants: what glslang lays out for a push_constant block
	private static int alignment(String type) {
		switch (type) {
			case "vec2": case "ivec2": case "uvec2": return 8;
			case "float": case "int": case "uint": case "bool": return 4;
			default: return 16;
		}
	}

	private static int std140Alignment(String type) {
		return alignment(type);
	}

	private static int std140Size(String type) {
		// mat2 columns are padded to vec4 in std140
		return type.equals("mat2") ? 32 : size(type);
	}

	private static String adaptStage(
		String src,
		boolean vertex,
		Map<String, Integer> uboBindings,
		Map<String, Integer> samplerUnits,
		Program p,
		boolean split,
		int blockBinding
	) {
		src = VERSION.matcher(src).replaceAll("");
		src = PRECISION.matcher(src).replaceAll("");
		// textureFaces is read through a storage buffer, as on GLES (texture buffers are capped
		// at 65536 texels on mobile, and RGB32I texel buffers are optional in Vulkan)
		if (src.contains(FACE_DATA_GL))
			src = src.replace(FACE_DATA_GL, FACE_DATA_VK).replace("texelFetch(textureFaces,", "fetchFaceData(");

		StringBuffer sb = new StringBuffer();
		Matcher m = UBO.matcher(src);
		while (m.find()) {
			Integer binding = uboBindings.get(m.group(1));
			if (binding == null) {
				log.warn("Uniform block {} has no binding; using 7", m.group(1));
				binding = 7;
			}
			m.appendReplacement(sb, "layout(std140, set = 0, binding = " + binding + ") uniform " + m.group(1));
		}
		m.appendTail(sb);
		src = sb.toString();

		sb = new StringBuffer();
		m = SAMPLER.matcher(src);
		while (m.find()) {
			Integer unit = samplerUnits.get(m.group(3));
			if (unit == null) {
				log.warn("Sampler {} has no texture unit; using 0", m.group(3));
				unit = 0;
			}
			m.appendReplacement(sb, Matcher.quoteReplacement(
				m.group(1) + "layout(set = 1, binding = " + unit + ") uniform " + m.group(2) + " " + m.group(3) + ";"));
		}
		m.appendTail(sb);
		src = sb.toString();

		StringBuilder header = new StringBuilder(HEADER);
		if (!p.pushUniforms.isEmpty() || !p.blockUniforms.isEmpty())
			src = (split ? LOOSE_OR_ARRAY : LOOSE).matcher(src).replaceAll("");
		if (!p.pushUniforms.isEmpty()) {
			header.append("layout(push_constant) uniform HdvkPush {\n");
			for (var e : p.pushUniforms.entrySet())
				header.append("\tlayout(offset = ").append(e.getValue().offset).append(") ")
					.append(e.getValue().type).append(" ").append(e.getKey()).append(";\n");
			header.append("} hdvkPush;\n");
			for (var name : p.pushUniforms.keySet())
				header.append("#define ").append(name).append(" hdvkPush.").append(name).append('\n');
		}
		if (!p.blockUniforms.isEmpty()) {
			// Members in offset order, so std140 places them exactly where the offsets say
			header.append("layout(std140, set = 0, binding = ").append(blockBinding).append(") uniform HdvkLoose {\n");
			for (var e : p.blockUniforms.entrySet()) {
				header.append('\t').append(e.getValue().type).append(' ').append(e.getKey());
				if (e.getValue().arrayLength > 0)
					header.append('[').append(e.getValue().arrayLength).append(']');
				header.append(";\n");
			}
			header.append("} hdvkLoose;\n");
			for (var name : p.blockUniforms.keySet())
				header.append("#define ").append(name).append(" hdvkLoose.").append(name).append('\n');
		}

		// Vulkan GLSL reserves the separate-sampler type names (bicubic.glsl names a parameter `sampler`)
		src = VK_RESERVED.matcher(src).replaceAll("hdvk_$1");

		if (vertex)
			src = src.replace("gl_VertexID", "gl_VertexIndex");

		return header + src;
	}

	/** Uniform block name -> GL binding index, for the blocks the includes declare. */
	public static Map<String, Integer> uboBindings(Iterable<? extends Map.Entry<String, Integer>> blocks) {
		Map<String, Integer> out = new HashMap<>();
		for (var e : blocks)
			out.put(e.getKey(), e.getValue());
		return out;
	}
}
