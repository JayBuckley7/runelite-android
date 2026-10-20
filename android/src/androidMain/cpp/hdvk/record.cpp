#include "hdvk.h"

#include <algorithm>

namespace hdvk {

enum Op {
	OP_PROGRAM = 1,
	OP_LAYOUT = 2,
	OP_STATE = 3,
	OP_VIEWPORT = 4,
	OP_TEXTURE = 5,
	OP_STORAGE = 6,
	OP_UBO = 7,
	OP_DRAW = 8,
	OP_DRAW_INDEXED = 9,
	OP_MULTI_DRAW = 10,
	OP_PUSH = 11,
	OP_DRAW_INSTANCED = 12,
	OP_DEPTH_BIAS = 13,
};

void resetDrawState() {
	ctx->boundPipeline = VK_NULL_HANDLE;
	ctx->lastPipeline = VK_NULL_HANDLE;
	memset(ctx->boundVertexBuffers, 0, sizeof(ctx->boundVertexBuffers));
	memset(ctx->boundVertexOffsets, 0, sizeof(ctx->boundVertexOffsets));
	ctx->boundIndexBuffer = VK_NULL_HANDLE;
	ctx->boundStorage = VK_NULL_HANDLE;
	ctx->depthBiasDirty = true;
	ctx->uboDirty = true;
	ctx->pushReplay = true;
	ctx->texturesDirty = true;
	ctx->viewportDirty = true;
}

// ---------------------------------------------------------------------------------------
// Passes
// ---------------------------------------------------------------------------------------

// flags: bit 0 = store depth
bool beginPass(int framebuffer, int colorLoad, const float clearColor[4], int depthLoad, float clearDepth, int flags) {
	if (ctx->inPass) endPass();
	ctx->inPass = true;
	ctx->passSkipped = true;

	VkClearValue clears[3]{};
	uint32_t clearCount = 0;
	RenderPassKey key{};
	VkFramebuffer vkfb;
	uint32_t w, h;

	if (framebuffer == SWAPCHAIN_FRAMEBUFFER) {
		if (!acquireSwapchainImage()) return false;
		Swapchain &sc = ctx->swapchain;
		int img = ctx->swapchainImage;
		key.color = sc.format;
		key.samples = VK_SAMPLE_COUNT_1_BIT;
		VkImageLayout cur = sc.layouts[img];
		// Loading an image nothing was written to this frame is GL's undefined contents
		if (colorLoad == VK_ATTACHMENT_LOAD_OP_LOAD && cur == VK_IMAGE_LAYOUT_UNDEFINED)
			colorLoad = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
		key.colorLoad = (uint8_t) colorLoad;
		key.colorFinal = 2;
		key.colorInitial = colorLoad != VK_ATTACHMENT_LOAD_OP_LOAD ? 0 :
			cur == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL ? 3 : 0;
		if (colorLoad == VK_ATTACHMENT_LOAD_OP_LOAD && cur == VK_IMAGE_LAYOUT_PRESENT_SRC_KHR) {
			// Loaded from a previous pass this frame: present layout -> transfer dst keeps contents
			rawImageBarrier(cmd(), sc.images[img], VK_IMAGE_ASPECT_COLOR_BIT, cur, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
			key.colorInitial = 3;
		}
		vkfb = sc.framebuffers[img];
		w = sc.extent.width;
		h = sc.extent.height;
		memcpy(clears[0].color.float32, clearColor, sizeof(float) * 4);
		clearCount = 1;
		sc.layouts[img] = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
		ctx->passColorImage = SWAPCHAIN_FRAMEBUFFER;
		ctx->passDepthImage = 0;
		ctx->passResolveImage = 0;
		ctx->passFlipY = true;
		ctx->passGeneral = false;
	} else {
		Framebuffer *fb = ctx->framebuffers.get(framebuffer);
		if (!fb) {
			LOGE("beginPass: invalid framebuffer %d", framebuffer);
			return false;
		}
		key = fb->baseKey;
		Image *color = imageUse(fb->colorImage);
		Image *depth = imageUse(fb->depthImage);
		Image *resolve = imageUse(fb->resolveImage);
		ctx->passGeneral = false;
		if (color) {
			key.colorLoad = (uint8_t) colorLoad;
			if (color->usage & IMG_GENERAL) {
				key.colorInitial = 2;
				key.colorFinal = 1;
				ctx->passGeneral = true;
			} else if (color->usage & IMG_TRANSIENT) {
				key.colorInitial = 0;
				key.colorFinal = 3;
			} else {
				key.colorInitial = colorLoad == VK_ATTACHMENT_LOAD_OP_LOAD ? 1 : 0;
				key.colorFinal = 0;
			}
			memcpy(clears[clearCount].color.float32, clearColor, sizeof(float) * 4);
			clearCount++;
		}
		if (depth) {
			key.depthLoad = (uint8_t) depthLoad;
			key.depthStore = (flags & 1) ? 1 : 0;
			key.depthFinal = (depth->usage & IMG_SAMPLED) ? 0 : 1;
			clears[clearCount].depthStencil = { clearDepth, 0 };
			clearCount++;
		}
		if (resolve) clearCount++;
		vkfb = fb->framebuffer;
		w = fb->width;
		h = fb->height;
		if (color && !(color->usage & IMG_TRANSIENT))
			color->layout = (color->usage & IMG_GENERAL) ? VK_IMAGE_LAYOUT_GENERAL : VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
		if (resolve) resolve->layout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
		ctx->passColorImage = fb->colorImage;
		ctx->passDepthImage = fb->depthImage;
		ctx->passResolveImage = fb->resolveImage;
		ctx->passFlipY = false;
	}

	VkRenderPass pass = getRenderPass(key);
	VkRenderPassBeginInfo bi{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};
	bi.renderPass = pass;
	bi.framebuffer = vkfb;
	bi.renderArea = { { 0, 0 }, { w, h } };
	bi.clearValueCount = clearCount;
	bi.pClearValues = clears;
	vkCmdBeginRenderPass(cmd(), &bi, VK_SUBPASS_CONTENTS_INLINE);

	ctx->currentPass = pass;
	ctx->currentPassCompat = key.compatHash() ^ (ctx->passFlipY ? 1 : 0);
	ctx->passSamples = key.samples;
	ctx->passWidth = w;
	ctx->passHeight = h;
	ctx->passSkipped = false;
	ctx->viewport[0] = 0;
	ctx->viewport[1] = 0;
	ctx->viewport[2] = (int) w;
	ctx->viewport[3] = (int) h;
	ctx->viewportDirty = true;
	ctx->boundPipeline = VK_NULL_HANDLE;
	ctx->lastPipeline = VK_NULL_HANDLE;
	return true;
}

void endPass() {
	if (!ctx->inPass) return;
	if (!ctx->passSkipped) vkCmdEndRenderPass(cmd());
	ctx->inPass = false;
	ctx->passSkipped = false;
	ctx->currentPass = VK_NULL_HANDLE;
}

// ---------------------------------------------------------------------------------------
// Descriptors
// ---------------------------------------------------------------------------------------

static VkDescriptorSet allocateSet(VkDescriptorSetLayout layout) {
	VkDescriptorSetAllocateInfo ai{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
	ai.descriptorPool = frame().descriptors;
	ai.descriptorSetCount = 1;
	ai.pSetLayouts = &layout;
	VkDescriptorSet set = VK_NULL_HANDLE;
	if (vkAllocateDescriptorSets(ctx->device, &ai, &set) != VK_SUCCESS) {
		LOGE("Per-frame descriptor pool exhausted");
		return VK_NULL_HANDLE;
	}
	return set;
}

static void bindUbos(VkCommandBuffer cb) {
	// A UBO that was re-uploaded since the set was written now lives in another allocation
	if (!ctx->uboDirty) {
		for (int i = 0; i < NUM_UBO_BINDINGS; i++) {
			Buffer *b = ctx->buffers.get(ctx->uboBindings[i] ? ctx->uboBindings[i] : ctx->dummyUbo);
			if (b && b->current->buffer != ctx->setUboBuffers[i]) {
				ctx->uboDirty = true;
				break;
			}
		}
		if (!ctx->uboDirty) return;
	}
	VkDescriptorSet set = allocateSet(ctx->setLayouts[0]);
	if (!set) return;
	VkDescriptorBufferInfo infos[NUM_UBO_BINDINGS];
	VkWriteDescriptorSet writes[NUM_UBO_BINDINGS];
	for (int i = 0; i < NUM_UBO_BINDINGS; i++) {
		int handle = ctx->uboBindings[i];
		BufferAlloc *a = handle ? bufferUse(handle) : nullptr;
		if (!a) a = bufferUse(ctx->dummyUbo);
		Buffer *b = ctx->buffers.get(handle && ctx->buffers.get(handle) ? handle : ctx->dummyUbo);
		VkDeviceSize range = std::min<VkDeviceSize>(b ? b->size : 256, ctx->properties.limits.maxUniformBufferRange);
		infos[i] = { a->buffer, 0, range };
		ctx->setUboBuffers[i] = a->buffer;
		writes[i] = { VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET, nullptr, set, (uint32_t) i, 0, 1,
			VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, nullptr, &infos[i], nullptr };
	}
	vkUpdateDescriptorSets(ctx->device, NUM_UBO_BINDINGS, writes, 0, nullptr);
	vkCmdBindDescriptorSets(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, ctx->pipelineLayout, 0, 1, &set, 0, nullptr);
	ctx->uboDirty = false;
}

static void bindTextures(VkCommandBuffer cb) {
	if (!ctx->texturesDirty) return;
	VkDescriptorSet set = allocateSet(ctx->setLayouts[1]);
	if (!set) return;
	VkDescriptorImageInfo infos[NUM_TEXTURE_UNITS];
	VkWriteDescriptorSet writes[NUM_TEXTURE_UNITS];
	for (int i = 0; i < NUM_TEXTURE_UNITS; i++) {
		Image *img = imageUse(ctx->textureUnits[i]);
		if (!img) img = imageUse(ctx->unitDummy[i] ? ctx->unitDummy[i] : ctx->dummyImages[0]);
		VkImageLayout layout = img->layout == VK_IMAGE_LAYOUT_GENERAL ? VK_IMAGE_LAYOUT_GENERAL : VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
		infos[i] = { img->sampler, img->view, layout };
		writes[i] = { VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET, nullptr, set, (uint32_t) i, 0, 1,
			VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, &infos[i], nullptr, nullptr };
	}
	vkUpdateDescriptorSets(ctx->device, NUM_TEXTURE_UNITS, writes, 0, nullptr);
	vkCmdBindDescriptorSets(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, ctx->pipelineLayout, 1, 1, &set, 0, nullptr);
	ctx->texturesDirty = false;
}

static void bindStorage(VkCommandBuffer cb) {
	int handle = ctx->curStorage ? ctx->curStorage : ctx->dummyStorage;
	BufferAlloc *a = bufferUse(handle);
	if (!a) return;
	if (a->buffer == ctx->boundStorage) return;
	if (!a->storageSet) {
		VkDescriptorSetAllocateInfo ai{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
		ai.descriptorPool = ctx->persistentPool;
		ai.descriptorSetCount = 1;
		ai.pSetLayouts = &ctx->setLayouts[2];
		if (vkAllocateDescriptorSets(ctx->device, &ai, &a->storageSet) != VK_SUCCESS) {
			LOGE("Storage descriptor pool exhausted");
			a->storageSet = VK_NULL_HANDLE;
			return;
		}
		VkDescriptorBufferInfo info = { a->buffer, 0, VK_WHOLE_SIZE };
		VkWriteDescriptorSet w = { VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET, nullptr, a->storageSet, 0, 0, 1,
			VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, nullptr, &info, nullptr };
		vkUpdateDescriptorSets(ctx->device, 1, &w, 0, nullptr);
	}
	vkCmdBindDescriptorSets(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, ctx->pipelineLayout, 2, 1, &a->storageSet, 0, nullptr);
	ctx->boundStorage = a->buffer;
}

// ---------------------------------------------------------------------------------------
// Draws
// ---------------------------------------------------------------------------------------

static bool prepareDraw(VkCommandBuffer cb, bool indexed) {
	if (!ctx->inPass || ctx->passSkipped) return false;
	Program *p = ctx->programs.get(ctx->curProgram);
	int layoutHandle = ctx->curLayout < 0 ? -ctx->curLayout : ctx->curLayout;
	VertexLayout *layout = ctx->layouts.get(layoutHandle);
	if (!p || !layout) return false;

	if (ctx->lastPipeline == VK_NULL_HANDLE || ctx->lastPipelineProgram != ctx->curProgram ||
		ctx->lastPipelineLayoutHash != layout->formatHash || ctx->lastPipelineState != ctx->curState ||
		ctx->lastPipelinePass != ctx->currentPassCompat) {
		ctx->lastPipeline = getPipeline(p, layout, ctx->curState, ctx->currentPassCompat, ctx->passFlipY);
		ctx->lastPipelineProgram = ctx->curProgram;
		ctx->lastPipelineLayoutHash = layout->formatHash;
		ctx->lastPipelineState = ctx->curState;
		ctx->lastPipelinePass = ctx->currentPassCompat;
	}
	if (!ctx->lastPipeline) return false;
	if (ctx->boundPipeline != ctx->lastPipeline) {
		vkCmdBindPipeline(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, ctx->lastPipeline);
		ctx->boundPipeline = ctx->lastPipeline;
		ctx->depthBiasDirty = true;
	}
	// Pipelines with GL_POLYGON_OFFSET_FILL take the offset as dynamic state
	if ((ctx->curState & (1u << 27)) && ctx->depthBiasDirty) {
		vkCmdSetDepthBias(cb, ctx->depthBiasConstant, 0, ctx->depthBiasSlope);
		ctx->depthBiasDirty = false;
	}

	if (ctx->viewportDirty) {
		int x = ctx->viewport[0], y = ctx->viewport[1], w = ctx->viewport[2], h = ctx->viewport[3];
		VkViewport vp;
		VkRect2D sc;
		if (ctx->passFlipY) {
			int H = (int) ctx->passHeight;
			vp = { (float) x, (float) (H - y), (float) w, (float) -h, 0, 1 };
			sc = { { x, H - y - h }, { (uint32_t) w, (uint32_t) h } };
		} else {
			vp = { (float) x, (float) y, (float) w, (float) h, 0, 1 };
			sc = { { x, y }, { (uint32_t) w, (uint32_t) h } };
		}
		if (sc.offset.x < 0) { sc.extent.width = (uint32_t) std::max(0, (int) sc.extent.width + sc.offset.x); sc.offset.x = 0; }
		if (sc.offset.y < 0) { sc.extent.height = (uint32_t) std::max(0, (int) sc.extent.height + sc.offset.y); sc.offset.y = 0; }
		vkCmdSetViewport(cb, 0, 1, &vp);
		vkCmdSetScissor(cb, 0, 1, &sc);
		ctx->viewportDirty = false;
	}

	if (ctx->pushReplay) {
		vkCmdPushConstants(cb, ctx->pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
			0, PUSH_CONSTANT_SIZE, ctx->pushShadow);
		ctx->pushReplay = false;
	}

	bindUbos(cb);
	bindTextures(cb);
	bindStorage(cb);

	for (uint32_t i = 0; i < MAX_VERTEX_ATTRIBS; i++) {
		const VertexAttrib &a = layout->attribs[i];
		if (!a.buffer) continue;
		BufferAlloc *ba = bufferUse(a.buffer);
		if (!ba) return false;
		if (ctx->boundVertexBuffers[i] != ba->buffer || ctx->boundVertexOffsets[i] != a.offset) {
			VkDeviceSize off = a.offset;
			vkCmdBindVertexBuffers(cb, i, 1, &ba->buffer, &off);
			ctx->boundVertexBuffers[i] = ba->buffer;
			ctx->boundVertexOffsets[i] = a.offset;
		}
	}

	if (indexed) {
		BufferAlloc *ib = bufferUse(ctx->curIndexBuffer);
		if (!ib) return false;
		if (ctx->boundIndexBuffer != ib->buffer) {
			vkCmdBindIndexBuffer(cb, ib->buffer, 0, VK_INDEX_TYPE_UINT32);
			ctx->boundIndexBuffer = ib->buffer;
		}
	}
	return true;
}

void executeStream(const int32_t *ops, int count) {
	VkCommandBuffer cb = cmd();
	int i = 0;
	while (i < count) {
		int op = ops[i++];
		switch (op) {
			case OP_PROGRAM:
				ctx->curProgram = ops[i++];
				break;
			case OP_LAYOUT: {
				int layout = ops[i++];
				int ebo = ops[i++];
				ctx->curLayout = layout;
				if (ebo) ctx->curIndexBuffer = ebo;
				break;
			}
			case OP_STATE:
				ctx->curState = (uint32_t) ops[i++];
				break;
			case OP_VIEWPORT: {
				const int32_t *v = ops + i;
				i += 4;
				if (memcmp(v, ctx->viewport, sizeof(ctx->viewport)) != 0) {
					memcpy(ctx->viewport, v, sizeof(ctx->viewport));
					ctx->viewportDirty = true;
				}
				break;
			}
			case OP_TEXTURE: {
				int unit = ops[i++];
				int image = ops[i++];
				if (unit >= 0 && unit < NUM_TEXTURE_UNITS && ctx->textureUnits[unit] != image) {
					ctx->textureUnits[unit] = image;
					ctx->texturesDirty = true;
				}
				break;
			}
			case OP_STORAGE:
				ctx->curStorage = ops[i++];
				break;
			case OP_UBO: {
				int binding = ops[i++];
				int buffer = ops[i++];
				if (binding >= 0 && binding < NUM_UBO_BINDINGS && ctx->uboBindings[binding] != buffer) {
					ctx->uboBindings[binding] = buffer;
					ctx->uboDirty = true;
				}
				break;
			}
			case OP_DRAW: {
				int first = ops[i++];
				int n = ops[i++];
				if (n > 0 && prepareDraw(cb, false)) vkCmdDraw(cb, (uint32_t) n, 1, (uint32_t) first, 0);
				break;
			}
			case OP_DRAW_INSTANCED: {
				int first = ops[i++];
				int n = ops[i++];
				int instances = ops[i++];
				int firstInstance = ops[i++];
				if (n > 0 && instances > 0 && prepareDraw(cb, false))
					vkCmdDraw(cb, (uint32_t) n, (uint32_t) instances, (uint32_t) first, (uint32_t) firstInstance);
				break;
			}
			case OP_DRAW_INDEXED: {
				int n = ops[i++];
				int firstIndex = ops[i++];
				if (n > 0 && prepareDraw(cb, true)) vkCmdDrawIndexed(cb, (uint32_t) n, 1, (uint32_t) firstIndex, 0, 0);
				break;
			}
			case OP_MULTI_DRAW: {
				int n = ops[i++];
				bool ok = n > 0 && prepareDraw(cb, false);
				for (int d = 0; d < n; d++) {
					int first = ops[i++];
					int c = ops[i++];
					if (ok && c > 0) vkCmdDraw(cb, (uint32_t) c, 1, (uint32_t) first, 0);
				}
				break;
			}
			case OP_DEPTH_BIAS: {
				// glPolygonOffset(factor, units): units scale the format's minimum resolvable depth
				// difference exactly as Vulkan's constant factor does
				memcpy(&ctx->depthBiasConstant, ops + i, 4);
				memcpy(&ctx->depthBiasSlope, ops + i + 1, 4);
				i += 2;
				ctx->depthBiasDirty = true;
				break;
			}
			case OP_PUSH: {
				int offset = ops[i++];
				int words = ops[i++];
				if (offset >= 0 && (offset & 3) == 0 && words > 0 && offset + words * 4 <= PUSH_CONSTANT_SIZE) {
					memcpy((uint8_t *) ctx->pushShadow + offset, ops + i, (size_t) words * 4);
					// Replayed in full before the next draw, which also covers a fresh command buffer
					ctx->pushReplay = true;
				}
				i += words;
				break;
			}
			default:
				LOGE("Unknown stream op %d at %d", op, i - 1);
				return;
		}
	}
}

// ---------------------------------------------------------------------------------------
// Transfers to the swapchain / between images
// ---------------------------------------------------------------------------------------

bool blit(int srcHandle, const int src[4], int dstHandle, const int dst[4], bool linear) {
	if (ctx->inPass) endPass();
	Image *s = imageUse(srcHandle);
	if (!s) return false;
	VkCommandBuffer cb;
	VkImage dstImage;
	VkImageLayout dstFrom;
	Image *d = nullptr;
	int32_t dy0 = dst[1], dy1 = dst[3];

	if (dstHandle == SWAPCHAIN_FRAMEBUFFER) {
		if (!acquireSwapchainImage() || !ctx->swapchain.supportsBlit) return false;
		cb = cmd();
		int img = ctx->swapchainImage;
		dstImage = ctx->swapchain.images[img];
		dstFrom = ctx->swapchain.layouts[img];
		// Flip: GL window rows count up from the bottom, swapchain rows from the top
		int32_t H = (int32_t) ctx->swapchain.extent.height;
		dy0 = H - dst[1];
		dy1 = H - dst[3];
		if (dstFrom != VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
			rawImageBarrier(cb, dstImage, VK_IMAGE_ASPECT_COLOR_BIT, dstFrom, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
		ctx->swapchain.layouts[img] = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
	} else {
		d = imageUse(dstHandle);
		if (!d) return false;
		cb = cmd();
		dstImage = d->image;
		dstFrom = d->layout;
		imageBarrier(cb, d, dstFrom, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
	}

	imageBarrier(cb, s, s->layout, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
	VkImageBlit region{};
	VkImageAspectFlags aspect = s->isDepth ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT;
	region.srcSubresource = { aspect, 0, 0, 1 };
	region.srcOffsets[0] = { src[0], src[1], 0 };
	region.srcOffsets[1] = { src[2], src[3], 1 };
	region.dstSubresource = { aspect, 0, 0, 1 };
	region.dstOffsets[0] = { dst[0], dy0, 0 };
	region.dstOffsets[1] = { dst[2], dy1, 1 };
	vkCmdBlitImage(cb, s->image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, dstImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
		1, &region, linear && !s->isDepth ? VK_FILTER_LINEAR : VK_FILTER_NEAREST);
	imageBarrier(cb, s, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, s->layout);
	if (d) imageBarrier(cb, d, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, d->layout);
	return true;
}

void clearSwapchain(float r, float g, float b, float a) {
	if (ctx->inPass) endPass();
	if (!acquireSwapchainImage()) return;
	VkCommandBuffer cb = cmd();
	int img = ctx->swapchainImage;
	VkImage image = ctx->swapchain.images[img];
	if (ctx->swapchain.layouts[img] != VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
		rawImageBarrier(cb, image, VK_IMAGE_ASPECT_COLOR_BIT, ctx->swapchain.layouts[img], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
	ctx->swapchain.layouts[img] = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
	VkClearColorValue c = { { r, g, b, a } };
	VkImageSubresourceRange range = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 };
	vkCmdClearColorImage(cb, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &c, 1, &range);
}

} // namespace hdvk
