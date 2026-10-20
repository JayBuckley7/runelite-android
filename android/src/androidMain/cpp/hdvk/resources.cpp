#include "hdvk.h"

#include <algorithm>

namespace hdvk {

static JNIEnv *jniEnv() {
	JNIEnv *env = nullptr;
	if (ctx->jvm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK)
		ctx->jvm->AttachCurrentThread(&env, nullptr);
	return env;
}

// ---------------------------------------------------------------------------------------
// Buffers
// ---------------------------------------------------------------------------------------

static VkBufferUsageFlags bufferUsageFlags(uint32_t usage) {
	VkBufferUsageFlags f = VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
	if (usage & BUF_VERTEX) f |= VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
	if (usage & BUF_INDEX) f |= VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
	if (usage & BUF_UNIFORM) f |= VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
	if (usage & BUF_STORAGE) f |= VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
	if (usage & BUF_INDIRECT) f |= VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
	return f;
}

static BufferAlloc *createBufferAlloc(VkDeviceSize size, uint32_t usage) {
	auto *a = new BufferAlloc();
	VkBufferCreateInfo bci{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
	bci.size = std::max<VkDeviceSize>(size, 16);
	bci.usage = bufferUsageFlags(usage);
	if (vkCreateBuffer(ctx->device, &bci, nullptr, &a->buffer) != VK_SUCCESS) {
		delete a;
		return nullptr;
	}
	VkMemoryRequirements req;
	vkGetBufferMemoryRequirements(ctx->device, a->buffer, &req);
	if (usage & BUF_UNIFORM)
		req.alignment = std::max(req.alignment, ctx->properties.limits.minUniformBufferOffsetAlignment);
	if (usage & BUF_STORAGE)
		req.alignment = std::max(req.alignment, ctx->properties.limits.minStorageBufferOffsetAlignment);
	if (!ctx->allocator.allocate(req, 0, a->memory) || !a->memory.mapped) {
		LOGE("Failed to allocate %llu bytes of host-visible memory", (unsigned long long) size);
		vkDestroyBuffer(ctx->device, a->buffer, nullptr);
		ctx->allocator.free(a->memory);
		delete a;
		return nullptr;
	}
	vkBindBufferMemory(ctx->device, a->buffer, a->memory.memory, a->memory.offset);
	// Fresh GL buffers read back as zero; keep that so partially written buffers stay harmless
	memset(a->memory.mapped, 0, (size_t) bci.size);

	JNIEnv *env = jniEnv();
	jobject local = env->NewDirectByteBuffer(a->memory.mapped, (jlong) bci.size);
	a->byteBuffer = env->NewGlobalRef(local);
	env->DeleteLocalRef(local);
	return a;
}

void destroyBufferAlloc(BufferAlloc *a) {
	if (!a) return;
	if (a->byteBuffer) jniEnv()->DeleteGlobalRef(a->byteBuffer);
	if (a->storageSet) vkFreeDescriptorSets(ctx->device, ctx->persistentPool, 1, &a->storageSet);
	if (a->buffer) vkDestroyBuffer(ctx->device, a->buffer, nullptr);
	ctx->allocator.free(a->memory);
	delete a;
}

static inline bool inFlight(const BufferAlloc *a) {
	return a->lastUse > ctx->completedSerial;
}

int bufferCreate(VkDeviceSize size, uint32_t usage) {
	std::lock_guard<std::mutex> lock(ctx->mutex);
	auto *b = new Buffer();
	b->size = std::max<VkDeviceSize>(size, 16);
	b->usage = usage;
	b->current = createBufferAlloc(b->size, usage);
	if (!b->current) {
		delete b;
		return 0;
	}
	b->allocs.push_back(b->current);
	return ctx->buffers.add(b);
}

void bufferDestroy(int handle) {
	std::lock_guard<std::mutex> lock(ctx->mutex);
	Buffer *b = ctx->buffers.remove(handle);
	if (!b) return;
	for (auto *a : b->allocs) deferDestroy(PendingDestroy::BUFFER_ALLOC, a);
	delete b;
}

// Switch to an allocation the GPU isn't using. Mirrors glBufferData orphaning.
static BufferAlloc *rename(Buffer *b, bool preserve) {
	BufferAlloc *next = nullptr;
	for (auto *a : b->allocs) {
		if (a != b->current && !inFlight(a)) {
			next = a;
			break;
		}
	}
	if (!next) {
		next = createBufferAlloc(b->size, b->usage);
		if (!next) return b->current;
		b->allocs.push_back(next);
		if (b->allocs.size() > 8)
			LOGW("Buffer with %zu allocations (%llu bytes each) - frequent rewrites", b->allocs.size(), (unsigned long long) b->size);
	}
	if (preserve)
		memcpy(next->memory.mapped, b->current->memory.mapped, (size_t) b->size);
	b->current = next;
	return next;
}

// mode: 0 = preserve contents (GL map without invalidate), 1 = discard contents (invalidate /
// orphan), 2 = unsynchronized (write into whatever is current, the caller handles hazards)
jobject bufferMap(JNIEnv *env, int handle, int mode) {
	std::lock_guard<std::mutex> lock(ctx->mutex);
	Buffer *b = ctx->buffers.get(handle);
	if (!b) return nullptr;
	if (mode != 2 && inFlight(b->current))
		rename(b, mode == 0);
	return env->NewLocalRef(b->current->byteBuffer);
}

void bufferResize(JNIEnv *env, int handle, VkDeviceSize size, VkDeviceSize preserve) {
	std::lock_guard<std::mutex> lock(ctx->mutex);
	Buffer *b = ctx->buffers.get(handle);
	if (!b) return;
	size = std::max<VkDeviceSize>(size, 16);
	BufferAlloc *fresh = createBufferAlloc(size, b->usage);
	if (!fresh) return;
	preserve = std::min(std::min(preserve, b->size), size);
	if (preserve > 0)
		memcpy(fresh->memory.mapped, b->current->memory.mapped, (size_t) preserve);
	for (auto *a : b->allocs) deferDestroy(PendingDestroy::BUFFER_ALLOC, a);
	b->allocs.clear();
	b->allocs.push_back(fresh);
	b->current = fresh;
	b->size = size;
}

void bufferCopy(int src, int dst, VkDeviceSize srcOffset, VkDeviceSize dstOffset, VkDeviceSize size) {
	std::lock_guard<std::mutex> lock(ctx->mutex);
	Buffer *s = ctx->buffers.get(src);
	Buffer *d = ctx->buffers.get(dst);
	if (!s || !d || size == 0) return;
	if (srcOffset + size > s->size || dstOffset + size > d->size) {
		LOGE("bufferCopy out of range");
		return;
	}
	// GL orders buffer copies after previously issued draws; with host-visible memory the
	// equivalent is never writing into an allocation the GPU may still read.
	if (inFlight(d->current)) rename(d, true);
	memmove((char *) d->current->memory.mapped + dstOffset, (char *) s->current->memory.mapped + srcOffset, (size_t) size);
}

BufferAlloc *bufferUse(int handle) {
	Buffer *b = ctx->buffers.get(handle);
	if (!b) return nullptr;
	b->current->lastUse = ctx->serial;
	return b->current;
}

// ---------------------------------------------------------------------------------------
// Images
// ---------------------------------------------------------------------------------------

static bool formatSupports(VkFormat f, VkFormatFeatureFlags features) {
	VkFormatProperties p;
	vkGetPhysicalDeviceFormatProperties(ctx->physical, f, &p);
	return (p.optimalTilingFeatures & features) == features;
}

static VkFormat chooseDepthFormat(bool sampled) {
	VkFormatFeatureFlags need = VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT | (sampled ? VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT : 0);
	VkFormat candidates[] = { VK_FORMAT_D32_SFLOAT, VK_FORMAT_X8_D24_UNORM_PACK32, VK_FORMAT_D24_UNORM_S8_UINT, VK_FORMAT_D16_UNORM };
	for (VkFormat f : candidates)
		if (formatSupports(f, need)) return f;
	return VK_FORMAT_D16_UNORM;
}

// GL internal formats used by 117 HD
static VkFormat mapFormat(int gl, bool sampledDepth, bool &isDepth) {
	isDepth = false;
	switch (gl) {
		case 0x1908: // GL_RGBA
		case 0x8058: // GL_RGBA8
		case 0x8051: // GL_RGB8
		case 0x1907: // GL_RGB
			return VK_FORMAT_R8G8B8A8_UNORM;
		case 0x8C43: // GL_SRGB8_ALPHA8
		case 0x8C41: // GL_SRGB8
			return VK_FORMAT_R8G8B8A8_SRGB;
		case 0x8D76: // GL_RGBA16UI
			return VK_FORMAT_R16G16B16A16_UINT;
		case 0x8233: // GL_R16I
			return VK_FORMAT_R16_SINT;
		case 0x881A: // GL_RGBA16F
			return VK_FORMAT_R16G16B16A16_SFLOAT;
		case 0x81A5: // GL_DEPTH_COMPONENT16
			// Linear filtering (terrain shadows' sampler2DShadow) is guaranteed for D16, not for D32F
			isDepth = true;
			if (formatSupports(VK_FORMAT_D16_UNORM, VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT |
					(sampledDepth ? VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT : 0)))
				return VK_FORMAT_D16_UNORM;
			return chooseDepthFormat(sampledDepth);
		case 0x8CAC: // GL_DEPTH_COMPONENT32F
		case 0x81A6: // GL_DEPTH_COMPONENT24
		case 0x1902: // GL_DEPTH_COMPONENT
			isDepth = true;
			return chooseDepthFormat(sampledDepth);
		default:
			LOGW("Unknown GL format 0x%x, using RGBA8", gl);
			return VK_FORMAT_R8G8B8A8_UNORM;
	}
}

static uint32_t bytesPerPixel(VkFormat f) {
	switch (f) {
		case VK_FORMAT_R16G16B16A16_UINT: return 8;
		case VK_FORMAT_R16G16B16A16_SFLOAT: return 8;
		case VK_FORMAT_R16_SINT: return 2;
		case VK_FORMAT_D16_UNORM: return 2;
		default: return 4;
	}
}

static VkImageLayout restingLayout(const Image *img) {
	if (img->usage & IMG_GENERAL) return VK_IMAGE_LAYOUT_GENERAL;
	if (img->usage & IMG_TRANSIENT) return VK_IMAGE_LAYOUT_UNDEFINED;
	if (img->usage & IMG_SAMPLED) return VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
	if (img->isDepth) return VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
	return VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
}

int imageCreate(int type, int formatGl, uint32_t w, uint32_t h, uint32_t layers, uint32_t mips, uint32_t samples, uint32_t usage) {
	std::lock_guard<std::mutex> lock(ctx->mutex);
	auto *img = new Image();
	img->type = (ImageType) type;
	img->usage = usage;
	img->format = mapFormat(formatGl, (usage & IMG_SAMPLED) != 0, img->isDepth);
	img->width = std::max<uint32_t>(w, 1);
	img->height = std::max<uint32_t>(h, 1);
	img->layers = type == IMG_2D ? 1 : type == IMG_CUBE ? 6 : std::max<uint32_t>(layers, 1);
	img->depth = type == IMG_3D ? img->layers : 1;
	if (type == IMG_3D) img->layers = 1;
	img->mips = std::max<uint32_t>(mips, 1);
	img->samples = samples >= 8 ? VK_SAMPLE_COUNT_8_BIT : samples >= 4 ? VK_SAMPLE_COUNT_4_BIT :
		samples >= 2 ? VK_SAMPLE_COUNT_2_BIT : VK_SAMPLE_COUNT_1_BIT;

	bool transient = (usage & IMG_TRANSIENT) != 0;
	VkImageUsageFlags vu = 0;
	if (usage & IMG_SAMPLED) vu |= VK_IMAGE_USAGE_SAMPLED_BIT;
	if (usage & IMG_COLOR_ATTACHMENT) vu |= VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
	if (usage & IMG_DEPTH_ATTACHMENT) vu |= VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
	if (transient) {
		vu |= VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT;
	} else {
		if (usage & IMG_SAMPLED) vu |= VK_IMAGE_USAGE_TRANSFER_DST_BIT;
		if (!img->isDepth) vu |= VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
	}
	if (usage & IMG_TRANSFER_SRC) vu |= VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
	if (usage & IMG_TRANSFER_DST) vu |= VK_IMAGE_USAGE_TRANSFER_DST_BIT;

	VkImageCreateInfo ici{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
	if (type == IMG_CUBE) ici.flags = VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT;
	ici.imageType = type == IMG_3D ? VK_IMAGE_TYPE_3D : VK_IMAGE_TYPE_2D;
	ici.format = img->format;
	ici.extent = { img->width, img->height, img->depth };
	ici.mipLevels = img->mips;
	ici.arrayLayers = img->layers;
	ici.samples = img->samples;
	ici.tiling = VK_IMAGE_TILING_OPTIMAL;
	ici.usage = vu;
	ici.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
	ici.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
	VkResult r = vkCreateImage(ctx->device, &ici, nullptr, &img->image);
	if (r != VK_SUCCESS && transient) {
		// Some drivers reject transient usage for certain formats; drop the hint
		ici.usage &= ~VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT;
		transient = false;
		r = vkCreateImage(ctx->device, &ici, nullptr, &img->image);
	}
	if (r != VK_SUCCESS) {
		LOGE("vkCreateImage(%ux%ux%u fmt %d) failed: %d", img->width, img->height, img->layers, img->format, r);
		delete img;
		return 0;
	}
	VkMemoryRequirements req;
	vkGetImageMemoryRequirements(ctx->device, img->image, &req);
	if (!ctx->allocator.allocate(req, transient ? 2 : 1, img->memory)) {
		vkDestroyImage(ctx->device, img->image, nullptr);
		delete img;
		return 0;
	}
	vkBindImageMemory(ctx->device, img->image, img->memory.memory, img->memory.offset);

	VkImageAspectFlags aspect = img->isDepth ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT;
	VkImageViewCreateInfo vci{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
	vci.image = img->image;
	vci.viewType = type == IMG_3D ? VK_IMAGE_VIEW_TYPE_3D : type == IMG_2D_ARRAY ? VK_IMAGE_VIEW_TYPE_2D_ARRAY :
		type == IMG_CUBE ? VK_IMAGE_VIEW_TYPE_CUBE : VK_IMAGE_VIEW_TYPE_2D;
	vci.format = img->format;
	vci.subresourceRange = { aspect, 0, img->mips, 0, img->layers };
	VKCHECK(vkCreateImageView(ctx->device, &vci, nullptr, &img->view));

	if (usage & (IMG_COLOR_ATTACHMENT | IMG_DEPTH_ATTACHMENT)) {
		img->layerViews.resize(img->layers);
		for (uint32_t l = 0; l < img->layers; l++) {
			VkImageViewCreateInfo lv = vci;
			lv.viewType = VK_IMAGE_VIEW_TYPE_2D;
			lv.subresourceRange = { aspect, 0, 1, l, 1 };
			VKCHECK(vkCreateImageView(ctx->device, &lv, nullptr, &img->layerViews[l]));
		}
	}

	if (!img->isDepth && (img->format == VK_FORMAT_R16G16B16A16_UINT || img->format == VK_FORMAT_R16_SINT)) {
		img->samplerState.minFilter = img->samplerState.magFilter = 0; // integer formats can't be filtered
	}
	img->sampler = getSampler(img->samplerState);

	// Bring the image into its resting layout right away, so it can be sampled before its first
	// upload or render, as GL allows.
	img->layout = restingLayout(img);
	if (img->layout != VK_IMAGE_LAYOUT_UNDEFINED) {
		if (ctx->inPass) endPass();
		VkCommandBuffer cb = cmd();
		if (!img->isDepth && !transient) {
			// New memory may hold a freed render target's pixels; GL drivers hand out zeroed
			// textures, and partial uploads (the UI during a resize) rely on that
			imageBarrier(cb, img, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
			VkClearColorValue zero{};
			VkImageSubresourceRange range{ VK_IMAGE_ASPECT_COLOR_BIT, 0, img->mips, 0, img->layers };
			vkCmdClearColorImage(cb, img->image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &zero, 1, &range);
			imageBarrier(cb, img, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, img->layout);
		} else {
			imageBarrier(cb, img, VK_IMAGE_LAYOUT_UNDEFINED, img->layout);
		}
	}
	return ctx->images.add(img);
}

void destroyImageNow(Image *img) {
	if (!img) return;
	for (auto v : img->layerViews) vkDestroyImageView(ctx->device, v, nullptr);
	if (img->view) vkDestroyImageView(ctx->device, img->view, nullptr);
	if (img->image) vkDestroyImage(ctx->device, img->image, nullptr);
	ctx->allocator.free(img->memory);
	delete img;
}

void imageDestroy(int handle) {
	std::lock_guard<std::mutex> lock(ctx->mutex);
	Image *img = ctx->images.remove(handle);
	if (!img) return;
	for (int &u : ctx->textureUnits)
		if (u == handle) u = 0;
	ctx->texturesDirty = true;
	deferDestroy(PendingDestroy::IMAGE, img);
}

Image *imageUse(int handle) {
	Image *img = ctx->images.get(handle);
	if (img) img->lastUse = ctx->serial;
	return img;
}

bool imageUpload(int handle, uint32_t level, int x, int y, int layer, uint32_t w, uint32_t h, uint32_t layerCount,
	const void *data, size_t size, int srcBuffer, VkDeviceSize srcOffset, int formatHint) {
	Image *img = imageUse(handle);
	if (!img || w == 0 || h == 0) return false;
	if (ctx->inPass) endPass();
	layerCount = std::max<uint32_t>(layerCount, 1);
	uint32_t mw = std::max<uint32_t>(img->width >> level, 1), mh = std::max<uint32_t>(img->height >> level, 1);
	if (x < 0 || y < 0 || x + w > mw || y + h > mh) {
		// Out-of-bounds copies are undefined in Vulkan (drivers may drop them); GL errors instead
		LOGE("imageUpload %d: %ux%u at %d,%d exceeds %ux%u", handle, w, h, x, y, mw, mh);
		return false;
	}

	VkDeviceSize bytes = (VkDeviceSize) w * h * layerCount * bytesPerPixel(img->format);
	VkBuffer src = VK_NULL_HANDLE;
	VkDeviceSize offset = 0;
	if (srcBuffer) {
		BufferAlloc *a = bufferUse(srcBuffer);
		if (!a) return false;
		src = a->buffer;
		offset = srcOffset;
	} else {
		if (!data || size < bytes) {
			LOGE("imageUpload: %zu bytes provided, %llu needed", size, (unsigned long long) bytes);
			return false;
		}
		void *mapped;
		if (!stagingAlloc(bytes, 16, src, offset, mapped)) return false;
		memcpy(mapped, data, (size_t) bytes);
	}

	VkCommandBuffer cb = cmd();
	VkImageLayout resting = img->layout == VK_IMAGE_LAYOUT_UNDEFINED ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : img->layout;
	bool general = resting == VK_IMAGE_LAYOUT_GENERAL;
	uint32_t baseLayer = img->type == IMG_3D ? 0 : (uint32_t) layer;
	uint32_t layers = img->type == IMG_3D ? 1 : layerCount;
	if (!general)
		imageBarrier(cb, img, img->layout, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, baseLayer, layers, level, 1);

	VkBufferImageCopy region{};
	region.bufferOffset = offset;
	region.imageSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, level, baseLayer, layers };
	region.imageOffset = { x, y, img->type == IMG_3D ? layer : 0 };
	region.imageExtent = { w, h, img->type == IMG_3D ? layerCount : 1 };
	vkCmdCopyBufferToImage(cb, src, img->image, general ? VK_IMAGE_LAYOUT_GENERAL : VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);

	if (!general)
		imageBarrier(cb, img, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, resting, baseLayer, layers, level, 1);
	img->layout = resting;
	return true;
}

void imageGenerateMips(int handle) {
	Image *img = imageUse(handle);
	if (!img || img->mips <= 1) return;
	if (ctx->inPass) endPass();
	VkCommandBuffer cb = cmd();
	VkImageLayout resting = img->layout;
	bool linear = formatSupports(img->format, VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT);

	imageBarrier(cb, img, resting, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, 0, img->layers, 0, 1);
	for (uint32_t i = 1; i < img->mips; i++) {
		imageBarrier(cb, img, resting, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 0, img->layers, i, 1);
		VkImageBlit blit{};
		blit.srcSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, i - 1, 0, img->layers };
		blit.srcOffsets[1] = { (int32_t) std::max<uint32_t>(img->width >> (i - 1), 1), (int32_t) std::max<uint32_t>(img->height >> (i - 1), 1), 1 };
		blit.dstSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, i, 0, img->layers };
		blit.dstOffsets[1] = { (int32_t) std::max<uint32_t>(img->width >> i, 1), (int32_t) std::max<uint32_t>(img->height >> i, 1), 1 };
		vkCmdBlitImage(cb, img->image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, img->image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
			1, &blit, linear ? VK_FILTER_LINEAR : VK_FILTER_NEAREST);
		imageBarrier(cb, img, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, 0, img->layers, i, 1);
	}
	imageBarrier(cb, img, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, resting, 0, img->layers, 0, img->mips);
}

VkSampler getSampler(const SamplerState &s) {
	for (auto &kv : ctx->samplers)
		if (kv.first == s) return kv.second;

	auto wrap = [](uint8_t w) {
		switch (w) {
			case 1: return VK_SAMPLER_ADDRESS_MODE_REPEAT;
			case 2: return VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER;
			case 3: return VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
			default: return VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
		}
	};
	VkSamplerCreateInfo sci{VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};
	sci.magFilter = s.magFilter ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
	sci.minFilter = s.minFilter ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
	sci.mipmapMode = s.mipmap == 2 ? VK_SAMPLER_MIPMAP_MODE_LINEAR : VK_SAMPLER_MIPMAP_MODE_NEAREST;
	sci.addressModeU = wrap(s.wrapS);
	sci.addressModeV = wrap(s.wrapT);
	sci.addressModeW = wrap(s.wrapR);
	sci.minLod = 0;
	sci.maxLod = s.mipmap ? VK_LOD_CLAMP_NONE : 0.25f; // GL: no mipmap filter = base level only
	sci.borderColor = VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE;
	if (s.compare) {
		sci.compareEnable = VK_TRUE;
		sci.compareOp = (VkCompareOp) (s.compare - 1);
	}
	if (ctx->anisotropySupported && s.anisotropy > 1 && s.mipmap) {
		sci.anisotropyEnable = VK_TRUE;
		sci.maxAnisotropy = std::min(s.anisotropy, ctx->maxAnisotropy);
	}
	VkSampler sampler;
	VKCHECK(vkCreateSampler(ctx->device, &sci, nullptr, &sampler));
	ctx->samplers.emplace_back(s, sampler);
	return sampler;
}

void imageSetSampler(int handle, const SamplerState &state) {
	Image *img = ctx->images.get(handle);
	if (!img) return;
	SamplerState s = state;
	if (img->format == VK_FORMAT_R16G16B16A16_UINT || img->format == VK_FORMAT_R16_SINT ||
		(s.minFilter || s.magFilter) && !formatSupports(img->format, VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT)) {
		s.minFilter = s.magFilter = 0;
		s.mipmap = s.mipmap ? 1 : 0;
	}
	if (s == img->samplerState) return;
	img->samplerState = s;
	img->sampler = getSampler(s);
	for (int u : ctx->textureUnits)
		if (u == handle) ctx->texturesDirty = true;
}

bool readImage(int handle, void *dst, size_t size) {
	Image *img = imageUse(handle);
	if (!img || img->isDepth || img->samples != VK_SAMPLE_COUNT_1_BIT) return false;
	if (ctx->inPass) endPass();
	VkDeviceSize bytes = (VkDeviceSize) img->width * img->height * bytesPerPixel(img->format);
	if (size < bytes) return false;

	VkBufferCreateInfo bci{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
	bci.size = bytes;
	bci.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
	VkBuffer buf;
	if (vkCreateBuffer(ctx->device, &bci, nullptr, &buf) != VK_SUCCESS) return false;
	VkMemoryRequirements req;
	vkGetBufferMemoryRequirements(ctx->device, buf, &req);
	Allocation mem;
	if (!ctx->allocator.allocate(req, 0, mem) || !mem.mapped) {
		vkDestroyBuffer(ctx->device, buf, nullptr);
		return false;
	}
	vkBindBufferMemory(ctx->device, buf, mem.memory, mem.offset);

	VkCommandBuffer cb = cmd();
	imageBarrier(cb, img, img->layout, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, 0, 1, 0, 1);
	VkBufferImageCopy region{};
	region.imageSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1 };
	region.imageExtent = { img->width, img->height, 1 };
	vkCmdCopyImageToBuffer(cb, img->image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buf, 1, &region);
	imageBarrier(cb, img, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, img->layout, 0, 1, 0, 1);
	waitIdle();
	memcpy(dst, mem.mapped, (size_t) bytes);
	vkDestroyBuffer(ctx->device, buf, nullptr);
	ctx->allocator.free(mem);
	return true;
}

int createDummyImage(int kind) {
	static const uint8_t zero[64] = {};
	int h;
	switch (kind) {
		case 1: h = imageCreate(IMG_2D_ARRAY, 0x8058, 1, 1, 1, 1, 1, IMG_SAMPLED); break;
		case 2: h = imageCreate(IMG_2D_ARRAY, 0x8D76, 1, 1, 1, 1, 1, IMG_SAMPLED); break;
		case 3: h = imageCreate(IMG_3D, 0x8233, 1, 1, 1, 1, 1, IMG_SAMPLED); break;
		case 4: return imageCreate(IMG_CUBE, 0x8058, 1, 1, 6, 1, 1, IMG_SAMPLED); // zero-cleared on creation
		default: h = imageCreate(IMG_2D, 0x8058, 1, 1, 1, 1, 1, IMG_SAMPLED); break;
	}
	if (h) imageUpload(h, 0, 0, 0, 0, 1, 1, 1, zero, sizeof(zero), 0, 0, 0);
	return h;
}

// ---------------------------------------------------------------------------------------
// Render passes & framebuffers
// ---------------------------------------------------------------------------------------

static uint64_t hashKey(const RenderPassKey &k) {
	const auto *p = (const uint8_t *) &k;
	uint64_t h = 1469598103934665603ull;
	for (size_t i = 0; i < sizeof(k); i++) h = (h ^ p[i]) * 1099511628211ull;
	return h;
}

VkRenderPass getRenderPass(const RenderPassKey &key) {
	uint64_t h = hashKey(key);
	auto it = ctx->renderPasses.find(h);
	if (it != ctx->renderPasses.end()) return it->second;

	VkAttachmentDescription att[3]{};
	VkAttachmentReference colorRef{}, depthRef{}, resolveRef{};
	uint32_t n = 0;
	bool general = key.colorInitial == 2 || key.colorFinal == 1;

	if (key.color != VK_FORMAT_UNDEFINED) {
		VkAttachmentDescription &a = att[n];
		a.format = key.color;
		a.samples = key.samples;
		a.loadOp = (VkAttachmentLoadOp) key.colorLoad;
		a.storeOp = key.hasResolve ? VK_ATTACHMENT_STORE_OP_DONT_CARE : VK_ATTACHMENT_STORE_OP_STORE;
		a.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
		a.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
		switch (key.colorInitial) {
			case 1: a.initialLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL; break;
			case 2: a.initialLayout = VK_IMAGE_LAYOUT_GENERAL; break;
			case 3: a.initialLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; break;
			default: a.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED; break;
		}
		if (a.loadOp == VK_ATTACHMENT_LOAD_OP_LOAD && a.initialLayout == VK_IMAGE_LAYOUT_UNDEFINED && key.colorFinal != 2)
			a.initialLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
		switch (key.colorFinal) {
			case 1: a.finalLayout = VK_IMAGE_LAYOUT_GENERAL; break;
			case 2: a.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR; break;
			case 3: a.finalLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL; break;
			default: a.finalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL; break;
		}
		colorRef = { n, general ? VK_IMAGE_LAYOUT_GENERAL : VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL };
		n++;
	}
	if (key.depth != VK_FORMAT_UNDEFINED) {
		VkAttachmentDescription &a = att[n];
		a.format = key.depth;
		a.samples = key.samples;
		a.loadOp = (VkAttachmentLoadOp) key.depthLoad;
		a.storeOp = key.depthStore ? VK_ATTACHMENT_STORE_OP_STORE : VK_ATTACHMENT_STORE_OP_DONT_CARE;
		a.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
		a.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
		a.initialLayout = a.loadOp == VK_ATTACHMENT_LOAD_OP_LOAD ?
			(key.depthFinal ? VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL : VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) :
			VK_IMAGE_LAYOUT_UNDEFINED;
		a.finalLayout = key.depthFinal ? VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL : VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
		depthRef = { n, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL };
		n++;
	}
	if (key.hasResolve) {
		VkAttachmentDescription &a = att[n];
		a.format = key.color;
		a.samples = VK_SAMPLE_COUNT_1_BIT;
		a.loadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
		a.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
		a.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
		a.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
		a.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
		a.finalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
		resolveRef = { n, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL };
		n++;
	}

	VkSubpassDescription sub{};
	sub.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
	if (key.color != VK_FORMAT_UNDEFINED) {
		sub.colorAttachmentCount = 1;
		sub.pColorAttachments = &colorRef;
		if (key.hasResolve) sub.pResolveAttachments = &resolveRef;
	}
	if (key.depth != VK_FORMAT_UNDEFINED) sub.pDepthStencilAttachment = &depthRef;

	// Earlier passes and transfers write what this pass samples (shadow map, tiled lighting,
	// uploaded textures); later passes and transfers read what this pass writes.
	VkSubpassDependency deps[2]{};
	deps[0].srcSubpass = VK_SUBPASS_EXTERNAL;
	deps[0].dstSubpass = 0;
	deps[0].srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT |
		VK_PIPELINE_STAGE_TRANSFER_BIT;
	deps[0].srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT |
		VK_ACCESS_TRANSFER_WRITE_BIT;
	deps[0].dstStageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT |
		VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
	deps[0].dstAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT |
		VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
	deps[1].srcSubpass = 0;
	deps[1].dstSubpass = VK_SUBPASS_EXTERNAL;
	deps[1].srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
	deps[1].srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
	deps[1].dstStageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT;
	deps[1].dstAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_TRANSFER_READ_BIT;

	VkRenderPassCreateInfo rpci{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
	rpci.attachmentCount = n;
	rpci.pAttachments = att;
	rpci.subpassCount = 1;
	rpci.pSubpasses = &sub;
	rpci.dependencyCount = 2;
	rpci.pDependencies = deps;
	VkRenderPass pass = VK_NULL_HANDLE;
	VKCHECK(vkCreateRenderPass(ctx->device, &rpci, nullptr, &pass));
	ctx->renderPasses[h] = pass;
	return pass;
}

int framebufferCreate(int color, int colorLayer, int depth, int resolve) {
	std::lock_guard<std::mutex> lock(ctx->mutex);
	Image *c = ctx->images.get(color);
	Image *d = ctx->images.get(depth);
	Image *r = ctx->images.get(resolve);
	if (!c && !d) return 0;

	auto *fb = new Framebuffer();
	fb->colorImage = c ? color : 0;
	fb->colorLayer = colorLayer;
	fb->depthImage = d ? depth : 0;
	fb->resolveImage = r ? resolve : 0;
	Image *any = c ? c : d;
	fb->width = any->width;
	fb->height = any->height;
	fb->baseKey.color = c ? c->format : VK_FORMAT_UNDEFINED;
	fb->baseKey.depth = d ? d->format : VK_FORMAT_UNDEFINED;
	fb->baseKey.samples = any->samples;
	fb->baseKey.hasResolve = r ? 1 : 0;

	RenderPassKey compat = fb->baseKey;
	compat.colorLoad = VK_ATTACHMENT_LOAD_OP_CLEAR;
	compat.depthLoad = VK_ATTACHMENT_LOAD_OP_CLEAR;
	VkRenderPass pass = getRenderPass(compat);

	VkImageView views[3];
	uint32_t n = 0;
	if (c) views[n++] = c->layerViews.empty() ? c->view : c->layerViews[std::min<uint32_t>(colorLayer, c->layers - 1)];
	if (d) views[n++] = d->layerViews.empty() ? d->view : d->layerViews[0];
	if (r) views[n++] = r->layerViews.empty() ? r->view : r->layerViews[0];

	VkFramebufferCreateInfo fci{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
	fci.renderPass = pass;
	fci.attachmentCount = n;
	fci.pAttachments = views;
	fci.width = fb->width;
	fci.height = fb->height;
	fci.layers = 1;
	if (vkCreateFramebuffer(ctx->device, &fci, nullptr, &fb->framebuffer) != VK_SUCCESS) {
		delete fb;
		return 0;
	}
	return ctx->framebuffers.add(fb);
}

void destroyFramebufferNow(Framebuffer *fb) {
	if (!fb) return;
	if (fb->framebuffer) vkDestroyFramebuffer(ctx->device, fb->framebuffer, nullptr);
	delete fb;
}

void framebufferDestroy(int handle) {
	std::lock_guard<std::mutex> lock(ctx->mutex);
	Framebuffer *fb = ctx->framebuffers.remove(handle);
	if (fb) deferDestroy(PendingDestroy::FRAMEBUFFER, fb);
}

} // namespace hdvk
