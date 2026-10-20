#include "hdvk.h"

#include <shaderc/shaderc.h>

#include <algorithm>
#include <cctype>
#include <cstdio>

namespace hdvk {

// ---------------------------------------------------------------------------------------
// Pipeline layout: one for every program, so bindings never need re-binding on program switch
//   set 0: uniform blocks by GL binding index
//   set 1: combined image samplers by GL texture unit
//   set 2: the face-data storage buffer (117 HD's textureFaces)
// ---------------------------------------------------------------------------------------

bool createPipelineLayout() {
	VkShaderStageFlags stages = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;

	VkDescriptorSetLayoutBinding ubos[NUM_UBO_BINDINGS];
	for (int i = 0; i < NUM_UBO_BINDINGS; i++)
		ubos[i] = { (uint32_t) i, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, stages, nullptr };
	VkDescriptorSetLayoutBinding textures[NUM_TEXTURE_UNITS];
	for (int i = 0; i < NUM_TEXTURE_UNITS; i++)
		textures[i] = { (uint32_t) i, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, stages, nullptr };
	VkDescriptorSetLayoutBinding storage = { 0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, stages, nullptr };

	VkDescriptorSetLayoutCreateInfo info{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
	info.bindingCount = NUM_UBO_BINDINGS;
	info.pBindings = ubos;
	if (vkCreateDescriptorSetLayout(ctx->device, &info, nullptr, &ctx->setLayouts[0]) != VK_SUCCESS) return false;
	info.bindingCount = NUM_TEXTURE_UNITS;
	info.pBindings = textures;
	if (vkCreateDescriptorSetLayout(ctx->device, &info, nullptr, &ctx->setLayouts[1]) != VK_SUCCESS) return false;
	info.bindingCount = 1;
	info.pBindings = &storage;
	if (vkCreateDescriptorSetLayout(ctx->device, &info, nullptr, &ctx->setLayouts[2]) != VK_SUCCESS) return false;

	VkPushConstantRange push = { stages, 0, PUSH_CONSTANT_SIZE };
	VkPipelineLayoutCreateInfo plci{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
	plci.setLayoutCount = 3;
	plci.pSetLayouts = ctx->setLayouts;
	plci.pushConstantRangeCount = 1;
	plci.pPushConstantRanges = &push;
	return vkCreatePipelineLayout(ctx->device, &plci, nullptr, &ctx->pipelineLayout) == VK_SUCCESS;
}

// ---------------------------------------------------------------------------------------
// GLSL interface fix-ups
//
// GL links vertex outputs to fragment inputs by name; Vulkan matches them by location. After
// preprocessing (so #if branches are resolved) every global in/out without an explicit location
// gets one: vertex outputs sequentially, fragment inputs by looking up the same name (variable
// or interface block name). Struct and array sizes are accounted for so ranges never overlap.
// ---------------------------------------------------------------------------------------

namespace {

struct Statement {
	size_t begin, end; // [begin, end) in the source, excluding leading whitespace
};

bool isIdent(char c) { return std::isalnum((unsigned char) c) || c == '_'; }

std::vector<std::string> identifiers(const std::string &s) {
	std::vector<std::string> out;
	size_t i = 0;
	while (i < s.size()) {
		if (isIdent(s[i]) && !std::isdigit((unsigned char) s[i])) {
			size_t j = i;
			while (j < s.size() && isIdent(s[j])) j++;
			out.push_back(s.substr(i, j - i));
			i = j;
		} else {
			i++;
		}
	}
	return out;
}

// Splits the top level of a preprocessed shader into statements (declarations end with ';' at
// depth 0, function definitions end with their closing brace). Preprocessor lines are skipped.
std::vector<Statement> topLevelStatements(const std::string &src) {
	std::vector<Statement> out;
	size_t i = 0, n = src.size();
	while (i < n) {
		while (i < n && std::isspace((unsigned char) src[i])) i++;
		if (i >= n) break;
		if (src[i] == '#') {
			while (i < n && src[i] != '\n') i++;
			continue;
		}
		if (src[i] == '/' && i + 1 < n && src[i + 1] == '/') {
			while (i < n && src[i] != '\n') i++;
			continue;
		}
		if (src[i] == '/' && i + 1 < n && src[i + 1] == '*') {
			size_t e = src.find("*/", i + 2);
			i = e == std::string::npos ? n : e + 2;
			continue;
		}
		size_t start = i;
		int brace = 0, paren = 0;
		bool sawParenBeforeBrace = false;
		for (; i < n; i++) {
			char c = src[i];
			if (c == '(') paren++;
			else if (c == ')') { paren--; if (brace == 0) sawParenBeforeBrace = true; }
			else if (c == '{') brace++;
			else if (c == '}') {
				brace--;
				if (brace == 0 && sawParenBeforeBrace) {
					// end of a function body
					i++;
					break;
				}
			} else if (c == ';' && brace == 0 && paren == 0) {
				i++;
				break;
			}
		}
		out.push_back({ start, i });
	}
	return out;
}

int typeSlots(const std::string &type, const std::map<std::string, int> &structs) {
	if (type == "mat2" || type == "mat2x2" || type == "mat2x3" || type == "mat2x4") return 2;
	if (type == "mat3" || type == "mat3x2" || type == "mat3x3" || type == "mat3x4") return 3;
	if (type == "mat4" || type == "mat4x2" || type == "mat4x3" || type == "mat4x4") return 4;
	auto it = structs.find(type);
	if (it != structs.end()) return it->second;
	return 1;
}

const char *QUALIFIERS[] = {
	"flat", "smooth", "noperspective", "centroid", "sample", "highp", "mediump", "lowp", "invariant", "precise", "patch", "const",
};

bool isQualifier(const std::string &w) {
	for (auto q : QUALIFIERS)
		if (w == q) return true;
	return false;
}

// Slots used by "type name[N] ; type name ;" member lists
int memberSlots(const std::string &body, const std::map<std::string, int> &structs) {
	int total = 0;
	size_t pos = 0;
	while (pos < body.size()) {
		size_t semi = body.find(';', pos);
		if (semi == std::string::npos) break;
		std::string decl = body.substr(pos, semi - pos);
		pos = semi + 1;
		auto ids = identifiers(decl);
		std::string type;
		size_t k = 0;
		for (; k < ids.size(); k++) {
			if (ids[k] == "layout" || isQualifier(ids[k]) || ids[k] == "in" || ids[k] == "out") continue;
			type = ids[k];
			break;
		}
		if (type.empty()) continue;
		int slots = typeSlots(type, structs);
		// count declarators (comma separated) and array sizes
		std::string rest = decl.substr(decl.find(type) + type.size());
		int declarators = 0;
		size_t p = 0;
		while (p < rest.size()) {
			size_t comma = rest.find(',', p);
			std::string d = rest.substr(p, comma == std::string::npos ? std::string::npos : comma - p);
			if (!identifiers(d).empty()) {
				int arr = 1;
				size_t lb = d.find('[');
				if (lb != std::string::npos) arr = std::max(1, atoi(d.c_str() + lb + 1));
				declarators += arr;
			}
			if (comma == std::string::npos) break;
			p = comma + 1;
		}
		total += slots * std::max(declarators, 1);
	}
	return total;
}

struct InterfaceDecl {
	size_t insertAt;
	std::string key;
	int slots;
	bool isOut;
	bool hasLocation;
};

std::vector<InterfaceDecl> findInterfaces(const std::string &src, std::map<std::string, int> &structs) {
	std::vector<InterfaceDecl> out;
	for (auto &st : topLevelStatements(src)) {
		std::string text = src.substr(st.begin, st.end - st.begin);
		auto ids = identifiers(text);
		if (ids.empty()) continue;

		if (ids[0] == "struct" && ids.size() > 1) {
			size_t lb = text.find('{'), rb = text.rfind('}');
			if (lb != std::string::npos && rb != std::string::npos)
				structs[ids[1]] = std::max(1, memberSlots(text.substr(lb + 1, rb - lb - 1), structs));
			continue;
		}

		// Qualifier prefix: layout(...) and storage/interpolation keywords before the type
		bool hasLocation = false, isIn = false, isOut = false;
		size_t k = 0;
		size_t cursor = 0;
		while (k < ids.size()) {
			const std::string &w = ids[k];
			if (w == "layout") {
				size_t lp = text.find('(', cursor);
				size_t rp = text.find(')', lp);
				if (lp == std::string::npos || rp == std::string::npos) break;
				std::string inner = text.substr(lp, rp - lp);
				if (inner.find("location") != std::string::npos) hasLocation = true;
				// skip identifiers inside the parentheses
				auto innerIds = identifiers(inner);
				k += 1 + innerIds.size();
				cursor = rp + 1;
				continue;
			}
			if (w == "in") { isIn = true; k++; continue; }
			if (w == "out") { isOut = true; k++; continue; }
			if (w == "inout" || w == "uniform" || w == "buffer" || w == "shared") { isIn = isOut = false; break; }
			if (isQualifier(w)) { k++; continue; }
			break;
		}
		if (!(isIn || isOut) || k >= ids.size()) continue;
		if (ids[k].rfind("gl_", 0) == 0) continue; // built-in block redeclarations
		// Function definitions or prototypes never start with in/out at global scope, but be safe
		if (text.find('(') != std::string::npos && text.find('{') == std::string::npos && text.find("layout") == std::string::npos)
			continue;

		InterfaceDecl d;
		d.insertAt = st.begin;
		d.isOut = isOut;
		d.hasLocation = hasLocation;
		size_t lb = text.find('{');
		if (lb != std::string::npos) {
			// interface block: key on the block name
			d.key = ids[k];
			size_t rb = text.rfind('}');
			d.slots = std::max(1, memberSlots(text.substr(lb + 1, rb - lb - 1), structs));
			// instance arrays (rare) multiply
			size_t arr = text.find('[', rb);
			if (arr != std::string::npos) d.slots *= std::max(1, atoi(text.c_str() + arr + 1));
		} else {
			std::string type = ids[k];
			if (k + 1 >= ids.size()) continue;
			d.key = ids[k + 1];
			d.slots = typeSlots(type, structs);
			size_t arr = text.find('[');
			if (arr != std::string::npos) d.slots *= std::max(1, atoi(text.c_str() + arr + 1));
		}
		out.push_back(d);
	}
	return out;
}

std::string insertLocations(const std::string &src, const std::vector<InterfaceDecl> &decls,
	const std::vector<int> &locations) {
	std::string out = src;
	for (int i = (int) decls.size() - 1; i >= 0; i--) {
		if (locations[i] < 0) continue;
		out.insert(decls[i].insertAt, "layout(location = " + std::to_string(locations[i]) + ") ");
	}
	return out;
}

// GL clip space has z in [-1, 1], Vulkan's in [0, 1]. Rename main and remap after it returns.
std::string wrapVertexMain(const std::string &src) {
	for (auto &st : topLevelStatements(src)) {
		std::string head = src.substr(st.begin, std::min<size_t>(st.end - st.begin, 64));
		auto ids = identifiers(head);
		if (ids.size() >= 2 && ids[0] == "void" && ids[1] == "main") {
			size_t pos = src.find("main", st.begin);
			std::string out = src;
			out.replace(pos, 4, "hdvk_main");
			out += "\nvoid main() {\n\thdvk_main();\n\tgl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;\n}\n";
			return out;
		}
	}
	return src;
}

shaderc_compiler_t compiler = nullptr;

bool preprocess(const std::string &src, shaderc_shader_kind kind, const std::string &name, std::string &out, std::string &error) {
	shaderc_compile_options_t opts = shaderc_compile_options_initialize();
	shaderc_compile_options_set_target_env(opts, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_1);
	shaderc_compilation_result_t res = shaderc_compile_into_preprocessed_text(
		compiler, src.data(), src.size(), kind, name.c_str(), "main", opts);
	bool ok = shaderc_result_get_compilation_status(res) == shaderc_compilation_status_success;
	if (ok) out.assign(shaderc_result_get_bytes(res), shaderc_result_get_length(res));
	else error = shaderc_result_get_error_message(res);
	shaderc_result_release(res);
	shaderc_compile_options_release(opts);
	return ok;
}

bool compileSpirv(const std::string &src, shaderc_shader_kind kind, const std::string &name, std::vector<uint32_t> &spirv,
	std::string &error, bool optimize = true) {
	shaderc_compile_options_t opts = shaderc_compile_options_initialize();
	shaderc_compile_options_set_target_env(opts, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_1);
	shaderc_compile_options_set_optimization_level(opts,
		optimize ? shaderc_optimization_level_performance : shaderc_optimization_level_zero);
	shaderc_compilation_result_t res = shaderc_compile_into_spv(
		compiler, src.data(), src.size(), kind, name.c_str(), "main", opts);
	bool ok = shaderc_result_get_compilation_status(res) == shaderc_compilation_status_success;
	if (ok) {
		size_t len = shaderc_result_get_length(res);
		spirv.resize(len / 4);
		memcpy(spirv.data(), shaderc_result_get_bytes(res), len);
	} else {
		error = shaderc_result_get_error_message(res);
	}
	shaderc_result_release(res);
	shaderc_compile_options_release(opts);
	if (!ok && optimize && error.find("failed to optimize") != std::string::npos) {
		// The SPIR-V optimizer can trip over valid glslang output (seen with 117 HD 1.6's scene
		// shader); the driver optimizes again anyway, so fall back to the unoptimized module
		LOGW("%s: SPIR-V optimizer failed, using the unoptimized module", name.c_str());
		error.clear();
		return compileSpirv(src, kind, name, spirv, error, false);
	}
	return ok;
}

// Bump when compile options or the source fixups change, so stale binaries are never reused
constexpr const char *SPIRV_CACHE_TAG = "hdvk-spirv-1";

uint64_t fnv1a(const std::string &s, uint64_t h = 1469598103934665603ull) {
	for (unsigned char c : s) h = (h ^ c) * 1099511628211ull;
	return h;
}

// Compiling (glslang + the SPIR-V optimizer) dominates program creation; the final GLSL fully
// determines the output, so key the binary on a hash of it.
bool compileSpirvCached(const std::string &src, shaderc_shader_kind kind, const std::string &name,
	std::vector<uint32_t> &spirv, std::string &error, bool &hit) {
	char file[64];
	uint64_t h = fnv1a(src, fnv1a(SPIRV_CACHE_TAG) ^ (uint64_t) kind);
	snprintf(file, sizeof(file), "spv-%016llx.bin", (unsigned long long) h);
	std::vector<uint8_t> bytes;
	if (readCacheFile(file, bytes) && bytes.size() >= 20 && bytes.size() % 4 == 0) {
		uint32_t magic;
		memcpy(&magic, bytes.data(), 4);
		if (magic == 0x07230203) {
			spirv.resize(bytes.size() / 4);
			memcpy(spirv.data(), bytes.data(), bytes.size());
			hit = true;
			return true;
		}
	}
	hit = false;
	if (!compileSpirv(src, kind, name, spirv, error)) return false;
	writeCacheFile(file, spirv.data(), spirv.size() * 4);
	return true;
}

VkShaderModule createModule(const std::vector<uint32_t> &spirv) {
	VkShaderModuleCreateInfo ci{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
	ci.codeSize = spirv.size() * 4;
	ci.pCode = spirv.data();
	VkShaderModule m = VK_NULL_HANDLE;
	VKCHECK(vkCreateShaderModule(ctx->device, &ci, nullptr, &m));
	return m;
}

} // namespace

int programCreate(const std::string &name, const std::string &vertex, const std::string &fragment, std::string &error) {
	if (!compiler) compiler = shaderc_compiler_initialize();

	std::string vs, fs;
	if (!preprocess(vertex, shaderc_vertex_shader, name + ".vert", vs, error)) return 0;
	if (!preprocess(fragment, shaderc_fragment_shader, name + ".frag", fs, error)) return 0;

	std::map<std::string, int> vsStructs, fsStructs;
	auto vsDecls = findInterfaces(vs, vsStructs);
	auto fsDecls = findInterfaces(fs, fsStructs);

	std::map<std::string, int> outputLocations;
	std::vector<int> vsLoc(vsDecls.size(), -1), fsLoc(fsDecls.size(), -1);
	int next = 0;
	for (size_t i = 0; i < vsDecls.size(); i++) {
		auto &d = vsDecls[i];
		if (!d.isOut || d.hasLocation) continue; // vertex inputs carry explicit locations
		vsLoc[i] = next;
		outputLocations[d.key] = next;
		next += d.slots;
	}
	int fragOut = 0;
	for (size_t i = 0; i < fsDecls.size(); i++) {
		auto &d = fsDecls[i];
		if (d.hasLocation) continue;
		if (d.isOut) {
			fsLoc[i] = fragOut;
			fragOut += d.slots;
			continue;
		}
		auto it = outputLocations.find(d.key);
		if (it != outputLocations.end()) {
			fsLoc[i] = it->second;
		} else {
			// Not written by the vertex stage; give it a slot that can't collide
			fsLoc[i] = next;
			next += d.slots;
		}
	}
	vs = wrapVertexMain(insertLocations(vs, vsDecls, vsLoc));
	fs = insertLocations(fs, fsDecls, fsLoc);
	if (!ctx->provokingVertexLast) {
		// Flat varyings come from the first vertex here; move the scene shader's face fetch there
		size_t at = vs.find("isProvoking = vertex == 2");
		if (at != std::string::npos) vs.replace(at, 25, "isProvoking = vertex == 0");
	}

	std::vector<uint32_t> vsSpirv, fsSpirv;
	bool vsHit, fsHit;
	if (!compileSpirvCached(vs, shaderc_vertex_shader, name + ".vert", vsSpirv, error, vsHit)) {
		error = "vertex: " + error;
		return 0;
	}
	if (!compileSpirvCached(fs, shaderc_fragment_shader, name + ".frag", fsSpirv, error, fsHit)) {
		error = "fragment: " + error;
		return 0;
	}
	auto *p = new Program();
	p->name = name;
	p->vertex = createModule(vsSpirv);
	p->fragment = createModule(fsSpirv);
	if (!p->vertex || !p->fragment) {
		destroyProgramNow(p);
		error = "vkCreateShaderModule failed";
		return 0;
	}
	std::lock_guard<std::mutex> lock(ctx->mutex);
	return ctx->programs.add(p);
}

void destroyProgramNow(Program *p) {
	if (!p) return;
	for (auto &kv : p->pipelines) vkDestroyPipeline(ctx->device, kv.second, nullptr);
	if (p->vertex) vkDestroyShaderModule(ctx->device, p->vertex, nullptr);
	if (p->fragment) vkDestroyShaderModule(ctx->device, p->fragment, nullptr);
	delete p;
}

void programDestroy(int handle) {
	std::lock_guard<std::mutex> lock(ctx->mutex);
	Program *p = ctx->programs.remove(handle);
	if (p) deferDestroy(PendingDestroy::PROGRAM, p);
	if (ctx->curProgram == handle) ctx->curProgram = 0;
}

// ---------------------------------------------------------------------------------------
// Vertex layouts (GL VAOs)
// ---------------------------------------------------------------------------------------

void VertexLayout::rehash() {
	uint64_t h = 1469598103934665603ull;
	for (auto &a : attribs) {
		uint64_t v = a.buffer ? ((uint64_t) a.format << 32 | (uint64_t) a.stride << 1 | a.perInstance) : 0;
		h = (h ^ v) * 1099511628211ull;
	}
	formatHash = h;
}

int layoutCreate() {
	std::lock_guard<std::mutex> lock(ctx->mutex);
	auto *l = new VertexLayout();
	l->rehash();
	return ctx->layouts.add(l);
}

void layoutDestroy(int handle) {
	std::lock_guard<std::mutex> lock(ctx->mutex);
	delete ctx->layouts.remove(handle);
	if (ctx->curLayout == handle) ctx->curLayout = 0;
}

// Java encodes (glType << 8) | (components << 4) | (integer ? 2 : 0) | (normalized ? 1 : 0)
VkFormat attribFormat(int fmt) {
	int type = fmt >> 8;
	int n = (fmt >> 4) & 0xF;
	bool integer = fmt & 2, norm = fmt & 1;
	n = std::max(1, std::min(4, n));
	switch (type) {
		case 0x1406: { // GL_FLOAT
			VkFormat f[] = { VK_FORMAT_R32_SFLOAT, VK_FORMAT_R32G32_SFLOAT, VK_FORMAT_R32G32B32_SFLOAT, VK_FORMAT_R32G32B32A32_SFLOAT };
			return f[n - 1];
		}
		case 0x140B: { // GL_HALF_FLOAT
			VkFormat f[] = { VK_FORMAT_R16_SFLOAT, VK_FORMAT_R16G16_SFLOAT, VK_FORMAT_R16G16B16_SFLOAT, VK_FORMAT_R16G16B16A16_SFLOAT };
			return f[n - 1];
		}
		case 0x1402: { // GL_SHORT
			VkFormat i[] = { VK_FORMAT_R16_SINT, VK_FORMAT_R16G16_SINT, VK_FORMAT_R16G16B16_SINT, VK_FORMAT_R16G16B16A16_SINT };
			VkFormat s[] = { VK_FORMAT_R16_SNORM, VK_FORMAT_R16G16_SNORM, VK_FORMAT_R16G16B16_SNORM, VK_FORMAT_R16G16B16A16_SNORM };
			VkFormat c[] = { VK_FORMAT_R16_SSCALED, VK_FORMAT_R16G16_SSCALED, VK_FORMAT_R16G16B16_SSCALED, VK_FORMAT_R16G16B16A16_SSCALED };
			return integer ? i[n - 1] : norm ? s[n - 1] : c[n - 1];
		}
		case 0x1403: { // GL_UNSIGNED_SHORT
			VkFormat i[] = { VK_FORMAT_R16_UINT, VK_FORMAT_R16G16_UINT, VK_FORMAT_R16G16B16_UINT, VK_FORMAT_R16G16B16A16_UINT };
			VkFormat s[] = { VK_FORMAT_R16_UNORM, VK_FORMAT_R16G16_UNORM, VK_FORMAT_R16G16B16_UNORM, VK_FORMAT_R16G16B16A16_UNORM };
			VkFormat c[] = { VK_FORMAT_R16_USCALED, VK_FORMAT_R16G16_USCALED, VK_FORMAT_R16G16B16_USCALED, VK_FORMAT_R16G16B16A16_USCALED };
			return integer ? i[n - 1] : norm ? s[n - 1] : c[n - 1];
		}
		case 0x1400: { // GL_BYTE
			VkFormat i[] = { VK_FORMAT_R8_SINT, VK_FORMAT_R8G8_SINT, VK_FORMAT_R8G8B8_SINT, VK_FORMAT_R8G8B8A8_SINT };
			VkFormat s[] = { VK_FORMAT_R8_SNORM, VK_FORMAT_R8G8_SNORM, VK_FORMAT_R8G8B8_SNORM, VK_FORMAT_R8G8B8A8_SNORM };
			VkFormat c[] = { VK_FORMAT_R8_SSCALED, VK_FORMAT_R8G8_SSCALED, VK_FORMAT_R8G8B8_SSCALED, VK_FORMAT_R8G8B8A8_SSCALED };
			return integer ? i[n - 1] : norm ? s[n - 1] : c[n - 1];
		}
		case 0x1401: { // GL_UNSIGNED_BYTE
			VkFormat i[] = { VK_FORMAT_R8_UINT, VK_FORMAT_R8G8_UINT, VK_FORMAT_R8G8B8_UINT, VK_FORMAT_R8G8B8A8_UINT };
			VkFormat s[] = { VK_FORMAT_R8_UNORM, VK_FORMAT_R8G8_UNORM, VK_FORMAT_R8G8B8_UNORM, VK_FORMAT_R8G8B8A8_UNORM };
			VkFormat c[] = { VK_FORMAT_R8_USCALED, VK_FORMAT_R8G8_USCALED, VK_FORMAT_R8G8B8_USCALED, VK_FORMAT_R8G8B8A8_USCALED };
			return integer ? i[n - 1] : norm ? s[n - 1] : c[n - 1];
		}
		case 0x1404: { // GL_INT
			VkFormat i[] = { VK_FORMAT_R32_SINT, VK_FORMAT_R32G32_SINT, VK_FORMAT_R32G32B32_SINT, VK_FORMAT_R32G32B32A32_SINT };
			return i[n - 1];
		}
		case 0x1405: { // GL_UNSIGNED_INT
			VkFormat i[] = { VK_FORMAT_R32_UINT, VK_FORMAT_R32G32_UINT, VK_FORMAT_R32G32B32_UINT, VK_FORMAT_R32G32B32A32_UINT };
			return i[n - 1];
		}
		default:
			LOGE("Unsupported vertex attribute type 0x%x", type);
			return VK_FORMAT_R32_SFLOAT;
	}
}

void layoutSetAttrib(int handle, int location, int buffer, int format, int offset, int stride, int perInstance) {
	VertexLayout *l = ctx->layouts.get(handle);
	if (!l || location < 0 || location >= MAX_VERTEX_ATTRIBS) return;
	VertexAttrib &a = l->attribs[location];
	a.buffer = buffer;
	a.format = buffer ? attribFormat(format) : VK_FORMAT_UNDEFINED;
	a.offset = (uint32_t) offset;
	a.stride = (uint32_t) stride;
	a.perInstance = (uint32_t) perInstance;
	if (buffer) {
		VkFormatProperties fp;
		vkGetPhysicalDeviceFormatProperties(ctx->physical, a.format, &fp);
		if (!(fp.bufferFeatures & VK_FORMAT_FEATURE_VERTEX_BUFFER_BIT)) {
			LOGE("Vertex format %d (GL 0x%x) is not supported by this device", a.format, format);
			ctx->lastError = "Unsupported vertex format";
		}
	}
	l->rehash();
	if (ctx->curLayout == handle) ctx->curLayout = -handle; // force re-bind
}

// ---------------------------------------------------------------------------------------
// Pipelines
// ---------------------------------------------------------------------------------------

VkPipeline getPipeline(Program *p, VertexLayout *layout, uint32_t state, uint64_t passCompat, bool flipY) {
	uint64_t key = layout->formatHash;
	key = (key ^ state) * 1099511628211ull;
	key = (key ^ passCompat) * 1099511628211ull;
	key = (key ^ (flipY ? 0x9E3779B97F4A7C15ull : 0)) * 1099511628211ull;
	auto it = p->pipelines.find(key);
	if (it != p->pipelines.end()) return it->second;

	VkVertexInputBindingDescription bindings[MAX_VERTEX_ATTRIBS];
	VkVertexInputAttributeDescription attribs[MAX_VERTEX_ATTRIBS];
	uint32_t n = 0;
	for (uint32_t i = 0; i < MAX_VERTEX_ATTRIBS; i++) {
		const VertexAttrib &a = layout->attribs[i];
		if (!a.buffer) continue;
		// One binding per attribute: the attribute's GL offset becomes the bind offset, which
		// keeps GL's per-attribute buffer/stride/divisor model intact.
		bindings[n] = { i, a.stride, a.perInstance ? VK_VERTEX_INPUT_RATE_INSTANCE : VK_VERTEX_INPUT_RATE_VERTEX };
		attribs[n] = { i, i, a.format, 0 };
		n++;
	}
	VkPipelineVertexInputStateCreateInfo vi{VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
	vi.vertexBindingDescriptionCount = n;
	vi.pVertexBindingDescriptions = bindings;
	vi.vertexAttributeDescriptionCount = n;
	vi.pVertexAttributeDescriptions = attribs;

	VkPipelineInputAssemblyStateCreateInfo ia{VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};
	ia.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;

	VkPipelineViewportStateCreateInfo vp{VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};
	vp.viewportCount = 1;
	vp.scissorCount = 1;

	VkPipelineRasterizationStateCreateInfo rs{VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};
	rs.polygonMode = VK_POLYGON_MODE_FILL;
	rs.cullMode = !(state & (1u << 17)) ? VK_CULL_MODE_NONE : (state & (1u << 28)) ? VK_CULL_MODE_FRONT_BIT : VK_CULL_MODE_BACK_BIT;
	// GL_POLYGON_OFFSET_FILL; the factors are dynamic (OP_DEPTH_BIAS)
	bool depthBias = (state & (1u << 27)) != 0;
	rs.depthBiasEnable = depthBias;
	// GL's counter-clockwise front faces become clockwise in an unflipped Vulkan framebuffer
	rs.frontFace = flipY ? VK_FRONT_FACE_COUNTER_CLOCKWISE : VK_FRONT_FACE_CLOCKWISE;
	rs.lineWidth = 1;
	VkPipelineRasterizationProvokingVertexStateCreateInfoEXT pv{
		VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_PROVOKING_VERTEX_STATE_CREATE_INFO_EXT};
	pv.provokingVertexMode = VK_PROVOKING_VERTEX_MODE_LAST_VERTEX_EXT;
	if (ctx->provokingVertexLast) rs.pNext = &pv;

	VkSampleCountFlagBits samples = ctx->passSamples;
	VkPipelineMultisampleStateCreateInfo ms{VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};
	ms.rasterizationSamples = samples;

	bool hasDepth = ctx->passDepthImage != 0;
	bool hasColor = ctx->passColorImage != 0;
	VkPipelineDepthStencilStateCreateInfo ds{VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO};
	bool depthTest = hasDepth && (state & (1u << 18));
	ds.depthTestEnable = depthTest;
	// GL never writes depth while the depth test is disabled
	ds.depthWriteEnable = depthTest && (state & (1u << 19));
	ds.depthCompareOp = (VkCompareOp) ((state >> 20) & 7);

	VkPipelineColorBlendAttachmentState cba{};
	cba.blendEnable = state & 1;
	cba.srcColorBlendFactor = (VkBlendFactor) ((state >> 1) & 0xF);
	cba.dstColorBlendFactor = (VkBlendFactor) ((state >> 5) & 0xF);
	cba.srcAlphaBlendFactor = (VkBlendFactor) ((state >> 9) & 0xF);
	cba.dstAlphaBlendFactor = (VkBlendFactor) ((state >> 13) & 0xF);
	cba.colorBlendOp = VK_BLEND_OP_ADD;
	cba.alphaBlendOp = VK_BLEND_OP_ADD;
	cba.colorWriteMask = (state >> 23) & 0xF;
	VkPipelineColorBlendStateCreateInfo cb{VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};
	cb.attachmentCount = hasColor ? 1 : 0;
	cb.pAttachments = &cba;

	VkDynamicState dyn[] = { VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR, VK_DYNAMIC_STATE_DEPTH_BIAS };
	VkPipelineDynamicStateCreateInfo dy{VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO};
	dy.dynamicStateCount = depthBias ? 3 : 2;
	dy.pDynamicStates = dyn;

	VkPipelineShaderStageCreateInfo stages[2]{};
	stages[0] = { VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO, nullptr, 0, VK_SHADER_STAGE_VERTEX_BIT, p->vertex, "main", nullptr };
	stages[1] = { VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO, nullptr, 0, VK_SHADER_STAGE_FRAGMENT_BIT, p->fragment, "main", nullptr };

	VkGraphicsPipelineCreateInfo gp{VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};
	gp.stageCount = 2;
	gp.pStages = stages;
	gp.pVertexInputState = &vi;
	gp.pInputAssemblyState = &ia;
	gp.pViewportState = &vp;
	gp.pRasterizationState = &rs;
	gp.pMultisampleState = &ms;
	gp.pDepthStencilState = &ds;
	gp.pColorBlendState = &cb;
	gp.pDynamicState = &dy;
	gp.layout = ctx->pipelineLayout;
	gp.renderPass = ctx->currentPass;
	gp.subpass = 0;

	VkPipeline pipeline = VK_NULL_HANDLE;
	VkResult r = vkCreateGraphicsPipelines(ctx->device, ctx->pipelineCache, 1, &gp, nullptr, &pipeline);
	if (r != VK_SUCCESS) {
		LOGE("vkCreateGraphicsPipelines(%s) failed: %d", p->name.c_str(), r);
		pipeline = VK_NULL_HANDLE;
	} else {
		ctx->pipelineCacheDirty = true;
		ctx->lastPipelineSerial = ctx->serial;
	}
	p->pipelines[key] = pipeline;
	return pipeline;
}

} // namespace hdvk
