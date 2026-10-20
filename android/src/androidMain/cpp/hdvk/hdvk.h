// Vulkan backend for 117 HD on Android.
//
// The Java side (rs117.hd.vulkan.VK) drives this through a small explicit API: buffers,
// images, framebuffers, programs and vertex layouts are created up front and referred to by
// integer handles; a frame is a sequence of explicit render passes, transfers and a
// present. Draw-time state (program, vertex layout, fixed-function bits, bindings, draws)
// arrives in batches through a command stream so a whole pass costs a handful of JNI calls.
//
// Conventions shared with the GL renderer so the 117 HD shaders and matrices work unchanged:
//  - Clip-space depth is GL's [-1, 1]; every vertex shader is wrapped to remap it to [0, 1].
//  - Offscreen targets are rendered with a positive viewport height, which keeps their memory
//    layout identical to GL's (row 0 is NDC y = -1, and gl_FragCoord matches). Front faces are
//    therefore clockwise in framebuffer space. Only the swapchain is rendered flipped (negative
//    viewport height, VK_KHR_maintenance1), so the presented image is upright.
#pragma once

#include <vulkan/vulkan.h>
#include <android/log.h>
#include <android/native_window.h>
#include <jni.h>

#include <cstdint>
#include <cstring>
#include <map>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#define HDVK_TAG "hdvk"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, HDVK_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, HDVK_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, HDVK_TAG, __VA_ARGS__)

#define VKCHECK(expr) do { VkResult r_ = (expr); if (r_ != VK_SUCCESS) { LOGE("%s failed: %d (%s:%d)", #expr, r_, __FILE__, __LINE__); } } while (0)

namespace hdvk {

constexpr int FRAMES_IN_FLIGHT = 3;
constexpr int NUM_UBO_BINDINGS = 8;
constexpr int NUM_TEXTURE_UNITS = 8;
constexpr int PUSH_CONSTANT_SIZE = 128;
constexpr int MAX_VERTEX_ATTRIBS = 8;
// Handle 0 is never valid. Swapchain pseudo-framebuffer:
constexpr int SWAPCHAIN_FRAMEBUFFER = -1;

// ---------------------------------------------------------------------------------------
// Memory
// ---------------------------------------------------------------------------------------

struct Allocation {
	VkDeviceMemory memory = VK_NULL_HANDLE;
	VkDeviceSize offset = 0;
	VkDeviceSize size = 0;
	void *mapped = nullptr; // already offset
	int block = -1; // -1 = dedicated
};

class Allocator {
public:
	void init(VkPhysicalDevice phys, VkDevice dev);
	void destroy();
	// kind: 0 = host-visible (mapped), 1 = device-local image, 2 = transient (lazily allocated)
	bool allocate(const VkMemoryRequirements &req, int kind, Allocation &out);
	void free(Allocation &a);
	VkDeviceSize totalAllocated() const { return total; }

private:
	struct Block {
		VkDeviceMemory memory;
		VkDeviceSize size;
		void *mapped;
		uint32_t typeIndex;
		int kind;
		std::map<VkDeviceSize, VkDeviceSize> freeRanges; // offset -> size
	};
	int findType(uint32_t bits, int kind);
	VkDevice device = VK_NULL_HANDLE;
	VkPhysicalDeviceMemoryProperties props{};
	std::vector<Block> blocks;
	VkDeviceSize total = 0;
	std::mutex mutex;
};

// ---------------------------------------------------------------------------------------
// Resources
// ---------------------------------------------------------------------------------------

enum BufferUsage : uint32_t {
	BUF_VERTEX = 1,
	BUF_INDEX = 2,
	BUF_UNIFORM = 4,
	BUF_STORAGE = 8,
	BUF_TRANSFER = 16, // staging / pixel unpack
	BUF_INDIRECT = 32,
};

struct BufferAlloc {
	VkBuffer buffer = VK_NULL_HANDLE;
	Allocation memory;
	uint64_t lastUse = 0; // frame serial of the last recorded use
	jobject byteBuffer = nullptr; // global ref to a direct ByteBuffer over the mapping
	VkDescriptorSet storageSet = VK_NULL_HANDLE; // set 2, for BUF_STORAGE buffers
};

// A logical buffer. Its contents live in one of several allocations; writing to a buffer the
// GPU may still read switches to an idle allocation (GL's buffer orphaning), so the CPU never
// waits on the GPU and never scribbles over data an in-flight frame is reading.
struct Buffer {
	VkDeviceSize size = 0;
	uint32_t usage = 0;
	std::vector<BufferAlloc *> allocs;
	BufferAlloc *current = nullptr;
};

enum ImageType { IMG_2D = 0, IMG_2D_ARRAY = 1, IMG_3D = 2 };

enum ImageUsage : uint32_t {
	IMG_SAMPLED = 1,
	IMG_COLOR_ATTACHMENT = 2,
	IMG_DEPTH_ATTACHMENT = 4,
	IMG_TRANSIENT = 8, // only ever an attachment within a pass (MSAA color/depth)
	IMG_GENERAL = 16, // read and written by the same passes (tiled lighting layers)
	IMG_TRANSFER_SRC = 32,
	IMG_TRANSFER_DST = 64,
};

struct SamplerState {
	uint8_t minFilter = 1, magFilter = 1; // 0 nearest, 1 linear
	uint8_t mipmap = 0; // 0 none, 1 nearest, 2 linear
	uint8_t wrapS = 0, wrapT = 0, wrapR = 0; // 0 clamp edge, 1 repeat, 2 clamp border (white), 3 mirrored repeat
	uint8_t pad = 0;
	float anisotropy = 1;
	bool operator==(const SamplerState &o) const { return memcmp(this, &o, sizeof(*this)) == 0; }
};

struct Image {
	VkImage image = VK_NULL_HANDLE;
	Allocation memory;
	VkImageView view = VK_NULL_HANDLE; // all layers / mips, typed for sampling
	std::vector<VkImageView> layerViews; // single-layer 2D views for attachments
	VkFormat format = VK_FORMAT_UNDEFINED;
	ImageType type = IMG_2D;
	uint32_t width = 0, height = 0, depth = 1, layers = 1, mips = 1;
	VkSampleCountFlagBits samples = VK_SAMPLE_COUNT_1_BIT;
	uint32_t usage = 0;
	bool isDepth = false;
	VkImageLayout layout = VK_IMAGE_LAYOUT_UNDEFINED; // resting layout between passes
	SamplerState samplerState;
	VkSampler sampler = VK_NULL_HANDLE;
	uint64_t lastUse = 0;
};

struct RenderPassKey {
	VkFormat color = VK_FORMAT_UNDEFINED;
	VkFormat depth = VK_FORMAT_UNDEFINED;
	VkSampleCountFlagBits samples = VK_SAMPLE_COUNT_1_BIT;
	uint8_t hasResolve = 0;
	uint8_t colorLoad = 0; // VkAttachmentLoadOp
	uint8_t depthLoad = 0;
	uint8_t depthStore = 0; // 1 = store
	uint8_t colorFinal = 0; // 0 = shader read, 1 = general, 2 = present, 3 = color attachment (transient)
	uint8_t colorInitial = 0; // 0 undefined, 1 shader read, 2 general, 3 transfer dst
	uint8_t depthFinal = 0; // 0 = shader read, 1 = attachment (transient)
	uint8_t pad = 0;
	bool operator==(const RenderPassKey &o) const { return memcmp(this, &o, sizeof(*this)) == 0; }
	// Formats + samples + resolve are all that matter for pipeline / framebuffer compatibility
	uint64_t compatHash() const {
		return (uint64_t) color * 1000003ull ^ (uint64_t) depth * 7919ull ^ (uint64_t) samples << 48 ^ (uint64_t) hasResolve << 56;
	}
};

struct Framebuffer {
	int colorImage = 0, colorLayer = 0;
	int depthImage = 0;
	int resolveImage = 0;
	uint32_t width = 0, height = 0;
	RenderPassKey baseKey; // formats/samples; load/store chosen per beginPass
	VkFramebuffer framebuffer = VK_NULL_HANDLE;
};

struct VertexAttrib {
	int buffer = 0; // 0 = disabled
	VkFormat format = VK_FORMAT_UNDEFINED;
	uint32_t offset = 0;
	uint32_t stride = 0;
	uint32_t perInstance = 0;
};

struct VertexLayout {
	VertexAttrib attribs[MAX_VERTEX_ATTRIBS];
	uint64_t formatHash = 0; // excludes buffers; identifies the pipeline vertex input state
	void rehash();
};

struct Program {
	VkShaderModule vertex = VK_NULL_HANDLE;
	VkShaderModule fragment = VK_NULL_HANDLE;
	std::string name;
	std::unordered_map<uint64_t, VkPipeline> pipelines;
};

// ---------------------------------------------------------------------------------------
// Handle tables
// ---------------------------------------------------------------------------------------

template<typename T>
class Table {
public:
	int add(T *obj) {
		int id;
		if (!freeIds.empty()) {
			id = freeIds.back();
			freeIds.pop_back();
			items[id] = obj;
		} else {
			id = (int) items.size();
			items.push_back(obj);
		}
		return id + 1;
	}
	T *get(int handle) const {
		int id = handle - 1;
		if (id < 0 || id >= (int) items.size()) return nullptr;
		return items[id];
	}
	T *remove(int handle) {
		int id = handle - 1;
		if (id < 0 || id >= (int) items.size() || !items[id]) return nullptr;
		T *obj = items[id];
		items[id] = nullptr;
		freeIds.push_back(id);
		return obj;
	}
	template<typename F> void forEach(F fn) {
		for (size_t i = 0; i < items.size(); i++)
			if (items[i]) fn((int) i + 1, items[i]);
	}

private:
	std::vector<T *> items;
	std::vector<int> freeIds;
};

// ---------------------------------------------------------------------------------------
// Device / frame context
// ---------------------------------------------------------------------------------------

struct FrameSlot {
	VkCommandPool pool = VK_NULL_HANDLE;
	VkCommandBuffer cmd = VK_NULL_HANDLE;
	VkFence fence = VK_NULL_HANDLE;
	VkSemaphore acquire = VK_NULL_HANDLE;
	VkDescriptorPool descriptors = VK_NULL_HANDLE;
	uint64_t serial = 0; // serial of the submission currently using this slot
	// Staging ring for image uploads recorded during this frame
	VkBuffer staging = VK_NULL_HANDLE;
	Allocation stagingMemory;
	VkDeviceSize stagingSize = 0, stagingUsed = 0;
};

struct PendingDestroy {
	uint64_t serial;
	enum Kind { BUFFER_ALLOC, IMAGE, FRAMEBUFFER, PROGRAM, STAGING } kind;
	void *ptr;
};

struct Swapchain {
	VkSurfaceKHR surface = VK_NULL_HANDLE;
	VkSwapchainKHR swapchain = VK_NULL_HANDLE;
	VkFormat format = VK_FORMAT_UNDEFINED;
	VkExtent2D extent{};
	std::vector<VkImage> images;
	std::vector<VkImageView> views;
	std::vector<VkFramebuffer> framebuffers; // created per render pass compat (color only)
	std::vector<VkSemaphore> renderDone; // per image
	std::vector<VkImageLayout> layouts; // per image, this frame
	VkRenderPass compatPass = VK_NULL_HANDLE;
	bool supportsBlit = false;
	bool vsync = true;
	bool dirty = false;
};

struct StateBits {
	// Packed fixed-function state, see VK.java STATE_* for the layout.
	uint32_t bits = 0;
};

struct Context {
	JavaVM *jvm = nullptr;
	jclass byteBufferClass = nullptr;
	// On-disk caches (SPIR-V per shader, the driver's pipeline cache); empty disables them
	std::string cacheDir;
	bool pipelineCacheDirty = false;
	uint64_t lastPipelineSerial = 0;

	VkInstance instance = VK_NULL_HANDLE;
	VkPhysicalDevice physical = VK_NULL_HANDLE;
	VkDevice device = VK_NULL_HANDLE;
	VkQueue queue = VK_NULL_HANDLE;
	uint32_t queueFamily = 0;
	VkPhysicalDeviceProperties properties{};
	VkPhysicalDeviceFeatures features{};
	bool anisotropySupported = false;
	bool provokingVertexLast = false;
	float maxAnisotropy = 1;

	Allocator allocator;

	VkDescriptorSetLayout setLayouts[3]{};
	VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
	VkDescriptorPool persistentPool = VK_NULL_HANDLE; // storage-buffer sets live here
	VkPipelineCache pipelineCache = VK_NULL_HANDLE;

	FrameSlot frames[FRAMES_IN_FLIGHT];
	int frameIndex = 0;
	uint64_t serial = 1; // serial of the frame being recorded
	uint64_t completedSerial = 0;
	bool recording = false;

	Swapchain swapchain;
	ANativeWindow *window = nullptr;
	jobject surfaceRef = nullptr;
	int swapchainImage = -1; // acquired image this frame, or -1

	Table<Buffer> buffers;
	Table<Image> images;
	Table<Framebuffer> framebuffers;
	Table<VertexLayout> layouts;
	Table<Program> programs;

	std::unordered_map<uint64_t, VkRenderPass> renderPasses;
	std::vector<std::pair<SamplerState, VkSampler>> samplers;
	std::vector<PendingDestroy> pendingDestroy;

	// Global bindings (persist across frames, like GL's bind points)
	int uboBindings[NUM_UBO_BINDINGS]{};
	int textureUnits[NUM_TEXTURE_UNITS]{};
	int dummyImages[4]{}; // by kind: 2D rgba8, 2D array rgba8, 2D array rgba16ui, 3D r16i
	int unitDummy[NUM_TEXTURE_UNITS]{}; // dummy image bound to each unit while nothing else is
	int dummyUbo = 0;
	int dummyStorage = 0;
	VkBuffer setUboBuffers[NUM_UBO_BINDINGS]{}; // what the current set 0 was written with
	VkPipeline lastPipeline = VK_NULL_HANDLE;
	int lastPipelineProgram = 0;
	uint64_t lastPipelineLayoutHash = 0;
	uint32_t lastPipelineState = 0;
	uint64_t lastPipelinePass = 0;

	// Pass state
	bool inPass = false;
	bool passSkipped = false;
	bool passFlipY = false;
	VkRenderPass currentPass = VK_NULL_HANDLE;
	uint64_t currentPassCompat = 0;
	VkSampleCountFlagBits passSamples = VK_SAMPLE_COUNT_1_BIT;
	uint32_t passWidth = 0, passHeight = 0;
	int passColorImage = 0, passDepthImage = 0, passResolveImage = 0;
	bool passGeneral = false;

	// Draw state
	int curProgram = 0;
	int curLayout = 0;
	int curIndexBuffer = 0;
	uint32_t curState = 0;
	VkPipeline boundPipeline = VK_NULL_HANDLE;
	VkBuffer boundVertexBuffers[MAX_VERTEX_ATTRIBS]{};
	VkDeviceSize boundVertexOffsets[MAX_VERTEX_ATTRIBS]{};
	VkBuffer boundIndexBuffer = VK_NULL_HANDLE;
	bool uboDirty = true, texturesDirty = true;
	// Push constants are command-buffer state; replayed if a mid-frame submit starts a new one
	uint32_t pushShadow[PUSH_CONSTANT_SIZE / 4] = {};
	bool pushReplay = false;	int curStorage = 0;
	VkBuffer boundStorage = VK_NULL_HANDLE;
	int viewport[4]{};
	bool viewportDirty = true;

	std::mutex mutex; // resource tables; recording is single-threaded (client thread)

	std::string lastError;
	bool deviceLost = false;
};

extern Context *ctx;

// context.cpp
bool createInstanceAndDevice(std::string &error);
bool readCacheFile(const std::string &name, std::vector<uint8_t> &out);
void writeCacheFile(const std::string &name, const void *data, size_t size);
void savePipelineCache();void destroyAll();
bool setSurface(JNIEnv *env, jobject surface);
void setVsync(bool vsync);
VkCommandBuffer cmd(); // the current frame's command buffer, begun lazily
FrameSlot &frame();
void endFrame(bool present);void waitIdle();
bool acquireSwapchainImage();
void deferDestroy(PendingDestroy::Kind kind, void *ptr);
void collectGarbage();
bool stagingAlloc(VkDeviceSize size, VkDeviceSize align, VkBuffer &buf, VkDeviceSize &offset, void *&mapped);
void imageBarrier(VkCommandBuffer cb, Image *img, VkImageLayout from, VkImageLayout to,
	uint32_t baseLayer = 0, uint32_t layerCount = VK_REMAINING_ARRAY_LAYERS,
	uint32_t baseMip = 0, uint32_t mipCount = VK_REMAINING_MIP_LEVELS);
void rawImageBarrier(VkCommandBuffer cb, VkImage image, VkImageAspectFlags aspect, VkImageLayout from, VkImageLayout to);

// resources.cpp
int bufferCreate(VkDeviceSize size, uint32_t usage);
void bufferDestroy(int handle);
jobject bufferMap(JNIEnv *env, int handle, int mode);
void bufferResize(JNIEnv *env, int handle, VkDeviceSize size, VkDeviceSize preserve);
void bufferCopy(int src, int dst, VkDeviceSize srcOffset, VkDeviceSize dstOffset, VkDeviceSize size);
BufferAlloc *bufferUse(int handle); // marks the current allocation used by this frame
void destroyBufferAlloc(BufferAlloc *a);

int imageCreate(int type, int glFormat, uint32_t w, uint32_t h, uint32_t layers, uint32_t mips, uint32_t samples, uint32_t usage);
void imageDestroy(int handle);
void destroyImageNow(Image *img);
bool imageUpload(int handle, uint32_t level, int x, int y, int layer, uint32_t w, uint32_t h, uint32_t layerCount,
	const void *data, size_t size, int srcBuffer, VkDeviceSize srcOffset, int formatHint);
void imageGenerateMips(int handle);
void imageSetSampler(int handle, const SamplerState &state);
VkSampler getSampler(const SamplerState &state);
Image *imageUse(int handle);
int createDummyImage(int kind);
bool readImage(int handle, void *dst, size_t size);

int framebufferCreate(int color, int colorLayer, int depth, int resolve);
void framebufferDestroy(int handle);
void destroyFramebufferNow(Framebuffer *fb);
VkRenderPass getRenderPass(const RenderPassKey &key);

// pipeline.cpp
bool createPipelineLayout();
int programCreate(const std::string &name, const std::string &vertex, const std::string &fragment, std::string &error);
void programDestroy(int handle);
void destroyProgramNow(Program *p);
int layoutCreate();
void layoutDestroy(int handle);
void layoutSetAttrib(int handle, int location, int buffer, int format, int offset, int stride, int perInstance);
VkPipeline getPipeline(Program *p, VertexLayout *layout, uint32_t state, uint64_t passCompat, bool flipY);
VkFormat attribFormat(int fmt);

// record.cpp
bool beginPass(int framebuffer, int colorLoad, const float clearColor[4], int depthLoad, float clearDepth, int flags);
void endPass();
void executeStream(const int32_t *ops, int count);
bool blit(int srcImage, const int src[4], int dstImage, const int dst[4], bool linear);
void clearSwapchain(float r, float g, float b, float a);
void resetDrawState();

} // namespace hdvk
