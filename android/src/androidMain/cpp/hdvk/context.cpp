#include "hdvk.h"

#include <vulkan/vulkan_android.h>
#include <android/native_window_jni.h>

#include <algorithm>

namespace hdvk {

Context *ctx = nullptr;

// ---------------------------------------------------------------------------------------
// Allocator
// ---------------------------------------------------------------------------------------

static constexpr VkDeviceSize BLOCK_SIZE = 32ull << 20;
static constexpr VkDeviceSize DEDICATED_THRESHOLD = 8ull << 20;

void Allocator::init(VkPhysicalDevice phys, VkDevice dev) {
	device = dev;
	vkGetPhysicalDeviceMemoryProperties(phys, &props);
}

void Allocator::destroy() {
	std::lock_guard<std::mutex> lock(mutex);
	for (auto &b : blocks) {
		if (b.memory != VK_NULL_HANDLE) {
			if (b.mapped) vkUnmapMemory(device, b.memory);
			vkFreeMemory(device, b.memory, nullptr);
		}
	}
	blocks.clear();
	total = 0;
}

int Allocator::findType(uint32_t bits, int kind) {
	// Preference lists per kind; the first type matching every required flag wins.
	VkMemoryPropertyFlags required, preferred;
	switch (kind) {
		case 0: // host-visible buffers; on unified-memory mobile GPUs this is also device local
			required = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
			preferred = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
			break;
		case 2: // transient attachments
			required = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
			preferred = VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT;
			break;
		default:
			required = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
			preferred = 0;
			break;
	}
	int fallback = -1;
	for (uint32_t i = 0; i < props.memoryTypeCount; i++) {
		if (!(bits & (1u << i))) continue;
		VkMemoryPropertyFlags f = props.memoryTypes[i].propertyFlags;
		if ((f & required) != required) continue;
		// Never pick lazily allocated memory for anything but transient attachments
		if (kind != 2 && (f & VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT)) continue;
		if ((f & preferred) == preferred) return (int) i;
		if (fallback < 0) fallback = (int) i;
	}
	if (fallback < 0 && kind != 0) {
		// Last resort for images: anything allowed by the requirements
		for (uint32_t i = 0; i < props.memoryTypeCount; i++)
			if (bits & (1u << i)) return (int) i;
	}
	return fallback;
}

bool Allocator::allocate(const VkMemoryRequirements &req, int kind, Allocation &out) {
	int type = findType(req.memoryTypeBits, kind);
	if (type < 0) {
		LOGE("No memory type for bits 0x%x kind %d", req.memoryTypeBits, kind);
		return false;
	}
	bool host = (props.memoryTypes[type].propertyFlags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0 && kind == 0;

	std::lock_guard<std::mutex> lock(mutex);

	if (req.size >= DEDICATED_THRESHOLD || kind == 2) {
		VkMemoryAllocateInfo info{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
		info.allocationSize = req.size;
		info.memoryTypeIndex = (uint32_t) type;
		VkDeviceMemory mem;
		if (vkAllocateMemory(device, &info, nullptr, &mem) != VK_SUCCESS) {
			LOGE("vkAllocateMemory(%llu) failed", (unsigned long long) req.size);
			return false;
		}
		out.memory = mem;
		out.offset = 0;
		out.size = req.size;
		out.block = -1;
		out.mapped = nullptr;
		if (host && vkMapMemory(device, mem, 0, VK_WHOLE_SIZE, 0, &out.mapped) != VK_SUCCESS) {
			vkFreeMemory(device, mem, nullptr);
			return false;
		}
		total += req.size;
		return true;
	}

	VkDeviceSize align = std::max<VkDeviceSize>(req.alignment, 256);
	for (int bi = 0; bi < (int) blocks.size(); bi++) {
		Block &b = blocks[bi];
		if (b.memory == VK_NULL_HANDLE || b.typeIndex != (uint32_t) type || b.kind != kind) continue;
		for (auto it = b.freeRanges.begin(); it != b.freeRanges.end(); ++it) {
			VkDeviceSize start = (it->first + align - 1) & ~(align - 1);
			VkDeviceSize end = it->first + it->second;
			if (start + req.size > end) continue;
			VkDeviceSize rangeOffset = it->first;
			b.freeRanges.erase(it);
			if (start > rangeOffset) b.freeRanges[rangeOffset] = start - rangeOffset;
			if (start + req.size < end) b.freeRanges[start + req.size] = end - (start + req.size);
			out.memory = b.memory;
			out.offset = start;
			out.size = req.size;
			out.block = bi;
			out.mapped = b.mapped ? (char *) b.mapped + start : nullptr;
			return true;
		}
	}

	Block b{};
	VkMemoryAllocateInfo info{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
	info.allocationSize = BLOCK_SIZE;
	info.memoryTypeIndex = (uint32_t) type;
	if (vkAllocateMemory(device, &info, nullptr, &b.memory) != VK_SUCCESS) {
		LOGE("vkAllocateMemory(block) failed");
		return false;
	}
	b.size = BLOCK_SIZE;
	b.typeIndex = (uint32_t) type;
	b.kind = kind;
	b.mapped = nullptr;
	if (host && vkMapMemory(device, b.memory, 0, VK_WHOLE_SIZE, 0, &b.mapped) != VK_SUCCESS) {
		vkFreeMemory(device, b.memory, nullptr);
		return false;
	}
	total += BLOCK_SIZE;
	if (req.size < BLOCK_SIZE) b.freeRanges[req.size] = BLOCK_SIZE - req.size;
	int bi = -1;
	for (int i = 0; i < (int) blocks.size(); i++)
		if (blocks[i].memory == VK_NULL_HANDLE) { bi = i; break; }
	if (bi < 0) {
		bi = (int) blocks.size();
		blocks.push_back(b);
	} else {
		blocks[bi] = b;
	}
	out.memory = b.memory;
	out.offset = 0;
	out.size = req.size;
	out.block = bi;
	out.mapped = b.mapped;
	return true;
}

void Allocator::free(Allocation &a) {
	if (a.memory == VK_NULL_HANDLE) return;
	std::lock_guard<std::mutex> lock(mutex);
	if (a.block < 0) {
		vkFreeMemory(device, a.memory, nullptr); // implicitly unmaps
		total -= a.size;
	} else {
		Block &b = blocks[a.block];
		VkDeviceSize off = a.offset, size = a.size;
		auto next = b.freeRanges.lower_bound(off);
		if (next != b.freeRanges.end() && off + size == next->first) {
			size += next->second;
			next = b.freeRanges.erase(next);
		}
		if (next != b.freeRanges.begin()) {
			auto prev = std::prev(next);
			if (prev->first + prev->second == off) {
				off = prev->first;
				size += prev->second;
				b.freeRanges.erase(prev);
			}
		}
		b.freeRanges[off] = size;
		// Release empty blocks, keeping one of each kind around to avoid churn
		if (off == 0 && size == b.size) {
			int sameKind = 0;
			for (auto &o : blocks)
				if (o.memory != VK_NULL_HANDLE && o.typeIndex == b.typeIndex && o.kind == b.kind) sameKind++;
			if (sameKind > 1) {
				vkFreeMemory(device, b.memory, nullptr);
				total -= b.size;
				b.memory = VK_NULL_HANDLE;
				b.mapped = nullptr;
				b.freeRanges.clear();
			}
		}
	}
	a = Allocation{};
}

// ---------------------------------------------------------------------------------------
// Instance & device
// ---------------------------------------------------------------------------------------

static bool hasExtension(const std::vector<VkExtensionProperties> &list, const char *name) {
	for (auto &e : list)
		if (strcmp(e.extensionName, name) == 0) return true;
	return false;
}

// ---------------------------------------------------------------------------------------
// On-disk caches
// ---------------------------------------------------------------------------------------

bool readCacheFile(const std::string &name, std::vector<uint8_t> &out) {
	if (ctx->cacheDir.empty()) return false;
	FILE *f = fopen((ctx->cacheDir + "/" + name).c_str(), "rb");
	if (!f) return false;
	fseek(f, 0, SEEK_END);
	long len = ftell(f);
	fseek(f, 0, SEEK_SET);
	bool ok = len > 0;
	if (ok) {
		out.resize((size_t) len);
		ok = fread(out.data(), 1, out.size(), f) == out.size();
	}
	fclose(f);
	return ok;
}

void writeCacheFile(const std::string &name, const void *data, size_t size) {
	if (ctx->cacheDir.empty() || !size) return;
	// Write-then-rename, so a process killed mid-write never leaves a truncated file behind
	std::string path = ctx->cacheDir + "/" + name, tmp = path + ".tmp";
	FILE *f = fopen(tmp.c_str(), "wb");
	if (!f) return;
	bool ok = fwrite(data, 1, size, f) == size;
	ok = fclose(f) == 0 && ok;
	if (ok) rename(tmp.c_str(), path.c_str());
	else remove(tmp.c_str());
}

void savePipelineCache() {
	if (!ctx->pipelineCache || !ctx->pipelineCacheDirty) return;
	size_t size = 0;
	if (vkGetPipelineCacheData(ctx->device, ctx->pipelineCache, &size, nullptr) != VK_SUCCESS || !size) return;
	std::vector<uint8_t> data(size);
	if (vkGetPipelineCacheData(ctx->device, ctx->pipelineCache, &size, data.data()) != VK_SUCCESS) return;
	writeCacheFile("pipelines.bin", data.data(), size);
	ctx->pipelineCacheDirty = false;
	LOGI("Pipeline cache: %zu bytes saved", size);
}

bool createInstanceAndDevice(std::string &error) {
	uint32_t apiVersion = VK_API_VERSION_1_0;
	auto enumerateVersion = (PFN_vkEnumerateInstanceVersion) vkGetInstanceProcAddr(nullptr, "vkEnumerateInstanceVersion");
	if (enumerateVersion) enumerateVersion(&apiVersion);
	if (apiVersion < VK_API_VERSION_1_1) {
		error = "Vulkan 1.1 is required (loader reports " + std::to_string(VK_API_VERSION_MAJOR(apiVersion)) + "." +
			std::to_string(VK_API_VERSION_MINOR(apiVersion)) + ")";
		return false;
	}

	VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
	app.pApplicationName = "117 HD";
	app.applicationVersion = 1;
	app.pEngineName = "hdvk";
	app.apiVersion = VK_API_VERSION_1_1;

	const char *instanceExtensions[] = { VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME };
	VkInstanceCreateInfo ici{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
	ici.pApplicationInfo = &app;
	ici.enabledExtensionCount = 2;
	ici.ppEnabledExtensionNames = instanceExtensions;

	// Enable validation when the layer is packaged (debug builds only ship it on request)
	uint32_t layerCount = 0;
	vkEnumerateInstanceLayerProperties(&layerCount, nullptr);
	std::vector<VkLayerProperties> layers(layerCount);
	vkEnumerateInstanceLayerProperties(&layerCount, layers.data());
	const char *validation = "VK_LAYER_KHRONOS_validation";
	for (auto &l : layers) {
		if (strcmp(l.layerName, validation) == 0) {
			ici.enabledLayerCount = 1;
			ici.ppEnabledLayerNames = &validation;
			LOGI("Enabling %s", validation);
			break;
		}
	}

	VkResult r = vkCreateInstance(&ici, nullptr, &ctx->instance);
	if (r != VK_SUCCESS) {
		error = "vkCreateInstance failed: " + std::to_string(r);
		return false;
	}

	uint32_t count = 0;
	vkEnumeratePhysicalDevices(ctx->instance, &count, nullptr);
	if (count == 0) {
		error = "No Vulkan physical devices";
		return false;
	}
	std::vector<VkPhysicalDevice> devices(count);
	vkEnumeratePhysicalDevices(ctx->instance, &count, devices.data());

	for (VkPhysicalDevice pd : devices) {
		VkPhysicalDeviceProperties p;
		vkGetPhysicalDeviceProperties(pd, &p);
		if (p.apiVersion < VK_API_VERSION_1_1) continue;
		uint32_t qfCount = 0;
		vkGetPhysicalDeviceQueueFamilyProperties(pd, &qfCount, nullptr);
		std::vector<VkQueueFamilyProperties> qfs(qfCount);
		vkGetPhysicalDeviceQueueFamilyProperties(pd, &qfCount, qfs.data());
		for (uint32_t i = 0; i < qfCount; i++) {
			if (qfs[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) {
				ctx->physical = pd;
				ctx->queueFamily = i;
				ctx->properties = p;
				break;
			}
		}
		if (ctx->physical) break;
	}
	if (!ctx->physical) {
		error = "No Vulkan 1.1 device with a graphics queue";
		return false;
	}

	vkGetPhysicalDeviceFeatures(ctx->physical, &ctx->features);

	uint32_t extCount = 0;
	vkEnumerateDeviceExtensionProperties(ctx->physical, nullptr, &extCount, nullptr);
	std::vector<VkExtensionProperties> exts(extCount);
	vkEnumerateDeviceExtensionProperties(ctx->physical, nullptr, &extCount, exts.data());
	if (!hasExtension(exts, VK_KHR_SWAPCHAIN_EXTENSION_NAME)) {
		error = "Device lacks VK_KHR_swapchain";
		return false;
	}
	std::vector<const char *> deviceExtensions = { VK_KHR_SWAPCHAIN_EXTENSION_NAME };

	// GL takes flat varyings from a triangle's last vertex, Vulkan from its first. 117 HD's
	// scene shader only fetches face data on GL's provoking vertex, so match GL when possible.
	VkPhysicalDeviceProvokingVertexFeaturesEXT provoking{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROVOKING_VERTEX_FEATURES_EXT};
	if (hasExtension(exts, VK_EXT_PROVOKING_VERTEX_EXTENSION_NAME)) {
		VkPhysicalDeviceFeatures2 f2{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
		f2.pNext = &provoking;
		// Vulkan 1.1 entry point; the NDK's loader stub for older API levels doesn't export it
		auto getFeatures2 = (PFN_vkGetPhysicalDeviceFeatures2) vkGetInstanceProcAddr(ctx->instance, "vkGetPhysicalDeviceFeatures2");
		if (getFeatures2) getFeatures2(ctx->physical, &f2);
		if (provoking.provokingVertexLast) {
			deviceExtensions.push_back(VK_EXT_PROVOKING_VERTEX_EXTENSION_NAME);
			ctx->provokingVertexLast = true;
		}
	}
	provoking.transformFeedbackPreservesProvokingVertex = VK_FALSE;
	LOGI("Last-vertex provoking: %s", ctx->provokingVertexLast ? "yes" : "no (shader fallback)");

	float priority = 1;
	VkDeviceQueueCreateInfo qci{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
	qci.queueFamilyIndex = ctx->queueFamily;
	qci.queueCount = 1;
	qci.pQueuePriorities = &priority;

	VkPhysicalDeviceFeatures enabled{};
	enabled.samplerAnisotropy = ctx->features.samplerAnisotropy;
	enabled.fragmentStoresAndAtomics = ctx->features.fragmentStoresAndAtomics;
	enabled.independentBlend = ctx->features.independentBlend;
	ctx->anisotropySupported = ctx->features.samplerAnisotropy;
	ctx->maxAnisotropy = ctx->properties.limits.maxSamplerAnisotropy;

	VkDeviceCreateInfo dci{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
	dci.queueCreateInfoCount = 1;
	dci.pQueueCreateInfos = &qci;
	dci.enabledExtensionCount = (uint32_t) deviceExtensions.size();
	dci.ppEnabledExtensionNames = deviceExtensions.data();
	dci.pEnabledFeatures = &enabled;
	if (ctx->provokingVertexLast) dci.pNext = &provoking;
	r = vkCreateDevice(ctx->physical, &dci, nullptr, &ctx->device);
	if (r != VK_SUCCESS) {
		error = "vkCreateDevice failed: " + std::to_string(r);
		return false;
	}
	vkGetDeviceQueue(ctx->device, ctx->queueFamily, 0, &ctx->queue);

	ctx->allocator.init(ctx->physical, ctx->device);

	if (!createPipelineLayout()) {
		error = "Failed to create the pipeline layout";
		return false;
	}

	// The driver validates the header (vendor, device, driver UUID) and ignores a stale cache
	std::vector<uint8_t> cached;
	VkPipelineCacheCreateInfo pcci{VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO};
	if (readCacheFile("pipelines.bin", cached) && cached.size() > 32) {
		pcci.initialDataSize = cached.size();
		pcci.pInitialData = cached.data();
	}
	if (vkCreatePipelineCache(ctx->device, &pcci, nullptr, &ctx->pipelineCache) != VK_SUCCESS) {
		pcci.initialDataSize = 0;
		pcci.pInitialData = nullptr;
		vkCreatePipelineCache(ctx->device, &pcci, nullptr, &ctx->pipelineCache);
	}
	LOGI("Pipeline cache: %zu bytes loaded", (size_t) pcci.initialDataSize);

	for (auto &f : ctx->frames) {
		VkCommandPoolCreateInfo cpci{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
		cpci.flags = VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
		cpci.queueFamilyIndex = ctx->queueFamily;
		VKCHECK(vkCreateCommandPool(ctx->device, &cpci, nullptr, &f.pool));
		VkCommandBufferAllocateInfo cbai{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
		cbai.commandPool = f.pool;
		cbai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
		cbai.commandBufferCount = 1;
		VKCHECK(vkAllocateCommandBuffers(ctx->device, &cbai, &f.cmd));
		VkFenceCreateInfo fci{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
		fci.flags = VK_FENCE_CREATE_SIGNALED_BIT;
		VKCHECK(vkCreateFence(ctx->device, &fci, nullptr, &f.fence));
		VkSemaphoreCreateInfo sci{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
		VKCHECK(vkCreateSemaphore(ctx->device, &sci, nullptr, &f.acquire));

		VkDescriptorPoolSize sizes[] = {
			{ VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 4096 * NUM_UBO_BINDINGS },
			{ VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 4096 * NUM_TEXTURE_UNITS },
		};
		VkDescriptorPoolCreateInfo dpci{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
		dpci.maxSets = 8192;
		dpci.poolSizeCount = 2;
		dpci.pPoolSizes = sizes;
		VKCHECK(vkCreateDescriptorPool(ctx->device, &dpci, nullptr, &f.descriptors));
	}

	VkDescriptorPoolSize storageSize = { VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 16384 };
	VkDescriptorPoolCreateInfo dpci{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
	dpci.flags = VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
	dpci.maxSets = 16384;
	dpci.poolSizeCount = 1;
	dpci.pPoolSizes = &storageSize;
	VKCHECK(vkCreateDescriptorPool(ctx->device, &dpci, nullptr, &ctx->persistentPool));

	LOGI("Vulkan device: %s (API %u.%u.%u, driver 0x%x)", ctx->properties.deviceName,
		VK_API_VERSION_MAJOR(ctx->properties.apiVersion), VK_API_VERSION_MINOR(ctx->properties.apiVersion),
		VK_API_VERSION_PATCH(ctx->properties.apiVersion), ctx->properties.driverVersion);
	return true;
}

static void destroySwapchain(bool keepSurface);

void destroyAll() {
	if (!ctx) return;
	if (ctx->device) {
		vkDeviceWaitIdle(ctx->device);
		ctx->completedSerial = ctx->serial;
		ctx->recording = false;

		ctx->programs.forEach([](int h, Program *p) { destroyProgramNow(p); });
		ctx->framebuffers.forEach([](int h, Framebuffer *fb) { destroyFramebufferNow(fb); });
		ctx->images.forEach([](int h, Image *img) { destroyImageNow(img); });
		ctx->buffers.forEach([](int h, Buffer *b) {
			for (auto *a : b->allocs) destroyBufferAlloc(a);
			delete b;
		});
		ctx->layouts.forEach([](int h, VertexLayout *l) { delete l; });
		collectGarbage();
		for (auto &p : ctx->pendingDestroy) {
			// Anything left is safe to free now that the device is idle
			switch (p.kind) {
				case PendingDestroy::BUFFER_ALLOC: destroyBufferAlloc((BufferAlloc *) p.ptr); break;
				case PendingDestroy::IMAGE: destroyImageNow((Image *) p.ptr); break;
				case PendingDestroy::FRAMEBUFFER: destroyFramebufferNow((Framebuffer *) p.ptr); break;
				case PendingDestroy::PROGRAM: destroyProgramNow((Program *) p.ptr); break;
				case PendingDestroy::STAGING: break;
			}
		}
		ctx->pendingDestroy.clear();

		for (auto &kv : ctx->renderPasses) vkDestroyRenderPass(ctx->device, kv.second, nullptr);
		ctx->renderPasses.clear();
		for (auto &s : ctx->samplers) vkDestroySampler(ctx->device, s.second, nullptr);
		ctx->samplers.clear();

		destroySwapchain(false);

		for (auto &f : ctx->frames) {
			if (f.staging) {
				vkDestroyBuffer(ctx->device, f.staging, nullptr);
				ctx->allocator.free(f.stagingMemory);
			}
			vkDestroyDescriptorPool(ctx->device, f.descriptors, nullptr);			vkDestroySemaphore(ctx->device, f.acquire, nullptr);
			vkDestroyFence(ctx->device, f.fence, nullptr);
			vkDestroyCommandPool(ctx->device, f.pool, nullptr);
			f = FrameSlot{};
		}
		if (ctx->persistentPool) vkDestroyDescriptorPool(ctx->device, ctx->persistentPool, nullptr);
		savePipelineCache();
		if (ctx->pipelineCache) vkDestroyPipelineCache(ctx->device, ctx->pipelineCache, nullptr);
		vkDestroyPipelineLayout(ctx->device, ctx->pipelineLayout, nullptr);
		for (auto &l : ctx->setLayouts)
			if (l) vkDestroyDescriptorSetLayout(ctx->device, l, nullptr);
		ctx->allocator.destroy();
		vkDestroyDevice(ctx->device, nullptr);
	}
	if (ctx->instance) vkDestroyInstance(ctx->instance, nullptr);
}

// ---------------------------------------------------------------------------------------
// Swapchain
// ---------------------------------------------------------------------------------------

static void destroySwapchain(bool keepSurface) {
	Swapchain &sc = ctx->swapchain;
	if (!ctx->device) return;
	if (sc.swapchain || sc.surface) vkQueueWaitIdle(ctx->queue);
	for (auto fb : sc.framebuffers) vkDestroyFramebuffer(ctx->device, fb, nullptr);
	for (auto v : sc.views) vkDestroyImageView(ctx->device, v, nullptr);
	for (auto s : sc.renderDone) vkDestroySemaphore(ctx->device, s, nullptr);
	sc.framebuffers.clear();
	sc.views.clear();
	sc.renderDone.clear();
	sc.images.clear();
	sc.layouts.clear();
	if (sc.swapchain) vkDestroySwapchainKHR(ctx->device, sc.swapchain, nullptr);
	sc.swapchain = VK_NULL_HANDLE;
	if (!keepSurface && sc.surface) {
		vkDestroySurfaceKHR(ctx->instance, sc.surface, nullptr);
		sc.surface = VK_NULL_HANDLE;
	}
	ctx->swapchainImage = -1;
}

static bool createSwapchain() {
	Swapchain &sc = ctx->swapchain;
	if (!sc.surface) return false;

	VkBool32 supported = VK_FALSE;
	vkGetPhysicalDeviceSurfaceSupportKHR(ctx->physical, ctx->queueFamily, sc.surface, &supported);
	if (!supported) {
		LOGE("Queue family %u cannot present to the surface", ctx->queueFamily);
		return false;
	}

	VkSurfaceCapabilitiesKHR caps;
	VKCHECK(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(ctx->physical, sc.surface, &caps));
	// Android reports the size of the window's last queued buffer, which still holds the old
	// size right after a resize; the window itself already knows the new one.
	if (ctx->window) {
		int32_t ww = ANativeWindow_getWidth(ctx->window), wh = ANativeWindow_getHeight(ctx->window);
		if (ww > 0 && wh > 0) {
			caps.currentExtent.width = std::min(std::max((uint32_t) ww, caps.minImageExtent.width), caps.maxImageExtent.width);
			caps.currentExtent.height = std::min(std::max((uint32_t) wh, caps.minImageExtent.height), caps.maxImageExtent.height);
		}
	}
	if (caps.currentExtent.width == 0 || caps.currentExtent.height == 0) return false;

	uint32_t formatCount = 0;
	vkGetPhysicalDeviceSurfaceFormatsKHR(ctx->physical, sc.surface, &formatCount, nullptr);
	std::vector<VkSurfaceFormatKHR> formats(formatCount);
	vkGetPhysicalDeviceSurfaceFormatsKHR(ctx->physical, sc.surface, &formatCount, formats.data());
	// The GL renderer writes gamma-encoded values straight to the window, so a UNORM
	// (non-sRGB) swapchain keeps the colors identical.
	VkSurfaceFormatKHR chosen = formats.empty() ? VkSurfaceFormatKHR{VK_FORMAT_R8G8B8A8_UNORM, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR} : formats[0];
	for (auto &f : formats) {
		if (f.format == VK_FORMAT_R8G8B8A8_UNORM || f.format == VK_FORMAT_B8G8R8A8_UNORM) {
			chosen = f;
			break;
		}
	}

	uint32_t modeCount = 0;
	vkGetPhysicalDeviceSurfacePresentModesKHR(ctx->physical, sc.surface, &modeCount, nullptr);
	std::vector<VkPresentModeKHR> modes(modeCount);
	vkGetPhysicalDeviceSurfacePresentModesKHR(ctx->physical, sc.surface, &modeCount, modes.data());
	VkPresentModeKHR mode = VK_PRESENT_MODE_FIFO_KHR;
	if (!sc.vsync) {
		for (auto m : modes) if (m == VK_PRESENT_MODE_MAILBOX_KHR) mode = m;
		if (mode == VK_PRESENT_MODE_FIFO_KHR)
			for (auto m : modes) if (m == VK_PRESENT_MODE_IMMEDIATE_KHR) mode = m;
	}

	uint32_t imageCount = std::max<uint32_t>(caps.minImageCount, 3);
	if (caps.maxImageCount > 0) imageCount = std::min(imageCount, caps.maxImageCount);

	VkCompositeAlphaFlagBitsKHR alpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
	if (!(caps.supportedCompositeAlpha & alpha)) {
		if (caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR) alpha = VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
		else if (caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR) alpha = VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR;
		else alpha = VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR;
	}

	VkFormatProperties fp;
	vkGetPhysicalDeviceFormatProperties(ctx->physical, chosen.format, &fp);
	VkImageUsageFlags usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
	sc.supportsBlit = (caps.supportedUsageFlags & VK_IMAGE_USAGE_TRANSFER_DST_BIT) &&
		(fp.optimalTilingFeatures & VK_FORMAT_FEATURE_BLIT_DST_BIT);
	if (caps.supportedUsageFlags & VK_IMAGE_USAGE_TRANSFER_DST_BIT) usage |= VK_IMAGE_USAGE_TRANSFER_DST_BIT;
	if (caps.supportedUsageFlags & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) usage |= VK_IMAGE_USAGE_TRANSFER_SRC_BIT;

	VkSwapchainCreateInfoKHR sci{VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};
	sci.surface = sc.surface;
	sci.minImageCount = imageCount;
	sci.imageFormat = chosen.format;
	sci.imageColorSpace = chosen.colorSpace;
	sci.imageExtent = caps.currentExtent;
	sci.imageArrayLayers = 1;
	sci.imageUsage = usage;
	sci.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
	// Identity: SurfaceFlinger applies any display rotation. Pre-rotating would require
	// rotating every render target, which the GL path doesn't do either.
	sci.preTransform = (caps.supportedTransforms & VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR) ?
		VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR : caps.currentTransform;
	sci.compositeAlpha = alpha;
	sci.presentMode = mode;
	sci.clipped = VK_TRUE;
	VkResult r = vkCreateSwapchainKHR(ctx->device, &sci, nullptr, &sc.swapchain);
	if (r != VK_SUCCESS) {
		LOGE("vkCreateSwapchainKHR failed: %d", r);
		sc.swapchain = VK_NULL_HANDLE;
		return false;
	}
	sc.format = chosen.format;
	sc.extent = caps.currentExtent;

	uint32_t n = 0;
	vkGetSwapchainImagesKHR(ctx->device, sc.swapchain, &n, nullptr);
	sc.images.resize(n);
	vkGetSwapchainImagesKHR(ctx->device, sc.swapchain, &n, sc.images.data());
	sc.views.resize(n);
	sc.renderDone.resize(n);
	sc.layouts.assign(n, VK_IMAGE_LAYOUT_UNDEFINED);

	RenderPassKey key{};
	key.color = sc.format;
	key.samples = VK_SAMPLE_COUNT_1_BIT;
	key.colorLoad = VK_ATTACHMENT_LOAD_OP_CLEAR;
	key.colorFinal = 2;
	sc.compatPass = getRenderPass(key);

	sc.framebuffers.resize(n);
	for (uint32_t i = 0; i < n; i++) {
		VkImageViewCreateInfo vci{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
		vci.image = sc.images[i];
		vci.viewType = VK_IMAGE_VIEW_TYPE_2D;
		vci.format = sc.format;
		vci.subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 };
		VKCHECK(vkCreateImageView(ctx->device, &vci, nullptr, &sc.views[i]));
		VkFramebufferCreateInfo fci{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
		fci.renderPass = sc.compatPass;
		fci.attachmentCount = 1;
		fci.pAttachments = &sc.views[i];
		fci.width = sc.extent.width;
		fci.height = sc.extent.height;
		fci.layers = 1;
		VKCHECK(vkCreateFramebuffer(ctx->device, &fci, nullptr, &sc.framebuffers[i]));
		VkSemaphoreCreateInfo semci{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
		VKCHECK(vkCreateSemaphore(ctx->device, &semci, nullptr, &sc.renderDone[i]));
	}
	sc.dirty = false;
	LOGI("Swapchain %ux%u, %u images, format %d, present mode %d, blit %d", sc.extent.width, sc.extent.height, n,
		sc.format, mode, sc.supportsBlit);
	return true;
}

bool setSurface(JNIEnv *env, jobject surface) {
	bool same = surface && ctx->surfaceRef && env->IsSameObject(surface, ctx->surfaceRef);
	if (same && ctx->swapchain.swapchain && !ctx->swapchain.dirty) return true;
	if (!surface && !ctx->surfaceRef) return true;
	// Changing the swapchain: flush everything recorded so far; the swapchain image (if any)
	// is abandoned.
	if (ctx->recording) endFrame(false);

	if (!same) {
		destroySwapchain(false);
		if (ctx->window) ANativeWindow_release(ctx->window);
		ctx->window = nullptr;
		if (ctx->surfaceRef) env->DeleteGlobalRef(ctx->surfaceRef);
		ctx->surfaceRef = nullptr;
		if (!surface) return true;

		ctx->window = ANativeWindow_fromSurface(env, surface);
		if (!ctx->window) {
			LOGE("ANativeWindow_fromSurface failed");
			return false;
		}
		ctx->surfaceRef = env->NewGlobalRef(surface);

		VkAndroidSurfaceCreateInfoKHR asci{VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR};
		asci.window = ctx->window;
		VkResult r = vkCreateAndroidSurfaceKHR(ctx->instance, &asci, nullptr, &ctx->swapchain.surface);
		if (r != VK_SUCCESS) {
			// VK_ERROR_NATIVE_WINDOW_IN_USE_KHR here means EGL still owns the window
			LOGE("vkCreateAndroidSurfaceKHR failed: %d", r);
			ctx->swapchain.surface = VK_NULL_HANDLE;
			return false;
		}
	} else {
		destroySwapchain(true);
	}
	return createSwapchain();
}

void setVsync(bool vsync) {
	if (ctx->swapchain.vsync == vsync) return;
	ctx->swapchain.vsync = vsync;
	ctx->swapchain.dirty = true;
}

bool acquireSwapchainImage() {
	Swapchain &sc = ctx->swapchain;
	if (ctx->swapchainImage >= 0) return true;
	if (sc.dirty && sc.surface) {
		if (ctx->recording) endFrame(false);
		destroySwapchain(true);
		createSwapchain();
	}
	if (!sc.swapchain) return false;
	cmd(); // make sure the frame is open before tying the acquire semaphore to it
	uint32_t index = 0;
	VkResult r = vkAcquireNextImageKHR(ctx->device, sc.swapchain, 1000000000ull, frame().acquire, VK_NULL_HANDLE, &index);
	if (r == VK_ERROR_OUT_OF_DATE_KHR) {
		sc.dirty = true;
		return false;
	}
	if (r != VK_SUCCESS && r != VK_SUBOPTIMAL_KHR) {
		LOGW("vkAcquireNextImageKHR: %d", r);
		if (r == VK_ERROR_SURFACE_LOST_KHR) sc.dirty = true;
		return false;
	}
	ctx->swapchainImage = (int) index;
	sc.layouts[index] = VK_IMAGE_LAYOUT_UNDEFINED;
	return true;
}

// ---------------------------------------------------------------------------------------
// Frames
// ---------------------------------------------------------------------------------------

FrameSlot &frame() { return ctx->frames[ctx->frameIndex]; }

VkCommandBuffer cmd() {
	FrameSlot &f = frame();
	if (!ctx->recording) {
		// The slot's fence was waited on when the previous frame ended
		vkResetDescriptorPool(ctx->device, f.descriptors, 0);
		vkResetCommandPool(ctx->device, f.pool, 0);
		VkCommandBufferBeginInfo bi{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
		bi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
		VKCHECK(vkBeginCommandBuffer(f.cmd, &bi));
		f.stagingUsed = 0;
		// Loading textures can grow a ring far past what steady-state frames need; this
		// slot's previous submission has finished, so give the memory back.
		if (f.stagingSize > (32ull << 20)) {
			vkDestroyBuffer(ctx->device, f.staging, nullptr);
			ctx->allocator.free(f.stagingMemory);
			f.staging = VK_NULL_HANDLE;
			f.stagingSize = 0;
		}
		ctx->recording = true;
		resetDrawState();
	}
	return f.cmd;
}

void endFrame(bool present) {
	if (ctx->inPass) endPass();
	if (!ctx->recording && !(present && ctx->swapchainImage >= 0)) return;
	VkCommandBuffer cb = cmd();
	FrameSlot &f = frame();
	Swapchain &sc = ctx->swapchain;

	int image = ctx->swapchainImage;
	if (image >= 0 && sc.layouts[image] != VK_IMAGE_LAYOUT_PRESENT_SRC_KHR) {
		rawImageBarrier(cb, sc.images[image], VK_IMAGE_ASPECT_COLOR_BIT, sc.layouts[image], VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
		sc.layouts[image] = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
	}

	VKCHECK(vkEndCommandBuffer(cb));
	ctx->recording = false;

	VkSubmitInfo si{VK_STRUCTURE_TYPE_SUBMIT_INFO};
	VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT;
	si.commandBufferCount = 1;
	si.pCommandBuffers = &cb;
	if (image >= 0) {
		si.waitSemaphoreCount = 1;
		si.pWaitSemaphores = &f.acquire;
		si.pWaitDstStageMask = &waitStage;
		if (present) {
			si.signalSemaphoreCount = 1;
			si.pSignalSemaphores = &sc.renderDone[image];
		}
	}
	vkResetFences(ctx->device, 1, &f.fence);
	VkResult r = vkQueueSubmit(ctx->queue, 1, &si, f.fence);
	if (r != VK_SUCCESS) {
		LOGE("vkQueueSubmit failed: %d", r);
		if (r == VK_ERROR_DEVICE_LOST) ctx->deviceLost = true;
	}
	f.serial = ctx->serial;

	if (image >= 0 && present) {
		VkPresentInfoKHR pi{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};
		pi.waitSemaphoreCount = 1;
		pi.pWaitSemaphores = &sc.renderDone[image];
		pi.swapchainCount = 1;
		pi.pSwapchains = &sc.swapchain;
		uint32_t idx = (uint32_t) image;
		pi.pImageIndices = &idx;
		r = vkQueuePresentKHR(ctx->queue, &pi);
		// SUBOPTIMAL is expected on Android whenever the display is rotated relative to the
		// identity pre-transform; only OUT_OF_DATE requires a new swapchain.
		if (r == VK_ERROR_OUT_OF_DATE_KHR || r == VK_ERROR_SURFACE_LOST_KHR) sc.dirty = true;
		else if (r != VK_SUCCESS && r != VK_SUBOPTIMAL_KHR) LOGW("vkQueuePresentKHR: %d", r);
	} else if (image >= 0) {
		// Acquired but not presented (surface changing): the image is still owned by us.
		// Recreating the swapchain is the only way to hand it back.
		sc.dirty = true;
	}
	ctx->swapchainImage = -1;

	ctx->serial++;
	ctx->frameIndex = (ctx->frameIndex + 1) % FRAMES_IN_FLIGHT;

	// Wait for the slot we're about to record into, so CPU writes this frame never race the GPU
	FrameSlot &next = frame();
	if (next.serial != 0) {
		r = vkWaitForFences(ctx->device, 1, &next.fence, VK_TRUE, 5000000000ull);
		if (r == VK_ERROR_DEVICE_LOST) ctx->deviceLost = true;
		ctx->completedSerial = std::max(ctx->completedSerial, next.serial);
	}
	// Save new pipelines once creating them has settled (scene load, a new area), since the
	// process can be killed at any time without a clean shutdown
	if (ctx->pipelineCacheDirty && ctx->serial - ctx->lastPipelineSerial > 600) savePipelineCache();
	// Opportunistically retire older slots too
	for (auto &s : ctx->frames) {
		if (s.serial > ctx->completedSerial && vkGetFenceStatus(ctx->device, s.fence) == VK_SUCCESS)
			ctx->completedSerial = std::max(ctx->completedSerial, s.serial);
	}
	collectGarbage();
}

void waitIdle() {
	if (!ctx || !ctx->device) return;
	if (ctx->recording) endFrame(false);
	vkQueueWaitIdle(ctx->queue);
	ctx->completedSerial = ctx->serial - 1;
	collectGarbage();
}

void deferDestroy(PendingDestroy::Kind kind, void *ptr) {
	ctx->pendingDestroy.push_back({ ctx->serial, kind, ptr });
}

void collectGarbage() {
	auto &list = ctx->pendingDestroy;
	size_t w = 0;
	for (size_t i = 0; i < list.size(); i++) {
		PendingDestroy &p = list[i];
		// Resources queued during the frame being recorded carry that frame's serial, so they
		// only go once its submission has completed.
		bool done = p.serial <= ctx->completedSerial || (p.serial < ctx->serial && !ctx->recording && p.serial <= ctx->completedSerial);
		if (!done) {
			list[w++] = p;
			continue;
		}
		switch (p.kind) {
			case PendingDestroy::BUFFER_ALLOC: destroyBufferAlloc((BufferAlloc *) p.ptr); break;
			case PendingDestroy::IMAGE: destroyImageNow((Image *) p.ptr); break;
			case PendingDestroy::FRAMEBUFFER: destroyFramebufferNow((Framebuffer *) p.ptr); break;
			case PendingDestroy::PROGRAM: destroyProgramNow((Program *) p.ptr); break;
			case PendingDestroy::STAGING: {
				auto *s = (std::pair<VkBuffer, Allocation> *) p.ptr;
				vkDestroyBuffer(ctx->device, s->first, nullptr);
				ctx->allocator.free(s->second);
				delete s;
				break;
			}
		}
	}
	list.resize(w);
}

bool stagingAlloc(VkDeviceSize size, VkDeviceSize align, VkBuffer &buf, VkDeviceSize &offset, void *&mapped) {
	cmd();
	// Bulk uploads (texture array loads) submit in chunks rather than staging everything at once
	if (frame().stagingUsed > 0 && frame().stagingUsed + size > (48ull << 20) && ctx->swapchainImage < 0 && !ctx->inPass) {
		endFrame(false);
		cmd();
	}
	FrameSlot &f = frame();
	VkDeviceSize start = (f.stagingUsed + align - 1) & ~(align - 1);
	if (!f.staging || start + size > f.stagingSize) {
		VkDeviceSize newSize = std::max<VkDeviceSize>(std::max<VkDeviceSize>(f.stagingSize * 2, 4ull << 20), size + align);
		if (f.staging) {
			// Commands recorded earlier this frame still read the old ring
			deferDestroy(PendingDestroy::STAGING, new std::pair<VkBuffer, Allocation>(f.staging, f.stagingMemory));
		}
		VkBufferCreateInfo bci{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
		bci.size = newSize;
		bci.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
		VKCHECK(vkCreateBuffer(ctx->device, &bci, nullptr, &f.staging));
		VkMemoryRequirements req;
		vkGetBufferMemoryRequirements(ctx->device, f.staging, &req);
		if (!ctx->allocator.allocate(req, 0, f.stagingMemory) || !f.stagingMemory.mapped) {
			vkDestroyBuffer(ctx->device, f.staging, nullptr);
			f.staging = VK_NULL_HANDLE;
			f.stagingSize = 0;
			return false;
		}
		vkBindBufferMemory(ctx->device, f.staging, f.stagingMemory.memory, f.stagingMemory.offset);
		f.stagingSize = newSize;
		start = 0;
	}
	buf = f.staging;
	offset = start;
	mapped = (char *) f.stagingMemory.mapped + start;
	f.stagingUsed = start + size;
	return true;
}

// ---------------------------------------------------------------------------------------
// Barriers
// ---------------------------------------------------------------------------------------

static VkAccessFlags accessFor(VkImageLayout layout) {
	switch (layout) {
		case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL: return VK_ACCESS_TRANSFER_WRITE_BIT;
		case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL: return VK_ACCESS_TRANSFER_READ_BIT;
		case VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL: return VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
		case VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL: return VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
		case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL: return VK_ACCESS_SHADER_READ_BIT;
		case VK_IMAGE_LAYOUT_GENERAL: return VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT |
			VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT;
		case VK_IMAGE_LAYOUT_PRESENT_SRC_KHR: return 0;
		default: return 0;
	}
}

void rawImageBarrier(VkCommandBuffer cb, VkImage image, VkImageAspectFlags aspect, VkImageLayout from, VkImageLayout to) {
	VkImageMemoryBarrier b{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
	b.srcAccessMask = accessFor(from) | (from == VK_IMAGE_LAYOUT_UNDEFINED ? 0 : VK_ACCESS_MEMORY_WRITE_BIT);
	b.dstAccessMask = accessFor(to) | VK_ACCESS_MEMORY_READ_BIT;
	b.oldLayout = from;
	b.newLayout = to;
	b.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
	b.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
	b.image = image;
	b.subresourceRange = { aspect, 0, VK_REMAINING_MIP_LEVELS, 0, VK_REMAINING_ARRAY_LAYERS };
	// Transfers and layout changes outside render passes are rare (uploads, the UI copy, the
	// scene blit), so a full barrier keeps this simple without costing anything measurable.
	vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0,
		0, nullptr, 0, nullptr, 1, &b);
}

void imageBarrier(VkCommandBuffer cb, Image *img, VkImageLayout from, VkImageLayout to,
	uint32_t baseLayer, uint32_t layerCount, uint32_t baseMip, uint32_t mipCount) {
	VkImageMemoryBarrier b{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
	b.srcAccessMask = accessFor(from) | (from == VK_IMAGE_LAYOUT_UNDEFINED ? 0 : VK_ACCESS_MEMORY_WRITE_BIT);
	b.dstAccessMask = accessFor(to) | VK_ACCESS_MEMORY_READ_BIT;
	b.oldLayout = from;
	b.newLayout = to;
	b.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
	b.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
	b.image = img->image;
	b.subresourceRange = { (VkImageAspectFlags) (img->isDepth ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT),
		baseMip, mipCount, baseLayer, layerCount };
	vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0,
		0, nullptr, 0, nullptr, 1, &b);
}

} // namespace hdvk
