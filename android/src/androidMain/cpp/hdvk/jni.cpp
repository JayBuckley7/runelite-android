// JNI entry points for rs117.hd.vulkan.VkNative. All calls arrive on the client thread except where
// noted; resource tables are additionally guarded so job threads could create objects later.
#include "hdvk.h"

using namespace hdvk;

#define JNI_FN(ret, name) extern "C" JNIEXPORT ret JNICALL Java_rs117_hd_vulkan_VkNative_##name

static JavaVM *g_jvm = nullptr;

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
	g_jvm = vm;
	return JNI_VERSION_1_6;
}

static jstring str(JNIEnv *env, const std::string &s) {
	return env->NewStringUTF(s.c_str());
}

static std::string jstr(JNIEnv *env, jstring s) {
	if (!s) return {};
	const char *c = env->GetStringUTFChars(s, nullptr);
	std::string out(c);
	env->ReleaseStringUTFChars(s, c);
	return out;
}

static bool alive() { return ctx && ctx->device; }
JNI_FN(jstring, init)(JNIEnv *env, jclass, jstring cacheDir) {
	if (alive()) return nullptr;
	delete ctx;
	ctx = new Context();
	ctx->jvm = g_jvm;
	ctx->cacheDir = jstr(env, cacheDir);
	std::string error;
	if (!createInstanceAndDevice(error)) {
		LOGE("Vulkan init failed: %s", error.c_str());
		destroyAll();
		delete ctx;
		ctx = nullptr;
		return str(env, error);
	}
	ctx->dummyImages[0] = createDummyImage(0);
	ctx->dummyImages[1] = createDummyImage(1);
	ctx->dummyImages[2] = createDummyImage(2);
	ctx->dummyImages[3] = createDummyImage(3);
	for (int &u : ctx->unitDummy) u = ctx->dummyImages[0];
	ctx->dummyUbo = bufferCreate(256, BUF_UNIFORM);
	ctx->dummyStorage = bufferCreate(256, BUF_STORAGE);
	return nullptr;
}

JNI_FN(void, shutdown)(JNIEnv *env, jclass) {
	if (!ctx) return;
	destroyAll(); // destroys the VkSurface before the window it was created from goes
	if (ctx->window) {
		ANativeWindow_release(ctx->window);
		ctx->window = nullptr;
	}
	if (ctx->surfaceRef) env->DeleteGlobalRef(ctx->surfaceRef);
	delete ctx;
	ctx = nullptr;
}

JNI_FN(jstring, deviceName)(JNIEnv *env, jclass) {
	if (!alive()) return str(env, "none");
	char buf[512];
	const auto &p = ctx->properties;
	snprintf(buf, sizeof(buf), "%s (Vulkan %u.%u.%u, driver 0x%x, vendor 0x%x)", p.deviceName,
		VK_API_VERSION_MAJOR(p.apiVersion), VK_API_VERSION_MINOR(p.apiVersion), VK_API_VERSION_PATCH(p.apiVersion),
		p.driverVersion, p.vendorID);
	return str(env, buf);
}

JNI_FN(jint, limit)(JNIEnv *, jclass, jint which) {
	if (!alive()) return 0;
	const auto &l = ctx->properties.limits;
	switch (which) {
		case 0: {
			VkSampleCountFlags c = l.framebufferColorSampleCounts & l.framebufferDepthSampleCounts;
			for (int s = 16; s > 1; s >>= 1)
				if (c & s) return s;
			return 1;
		}
		case 1: return (jint) l.maxImageDimension2D;
		case 2: return (jint) l.maxImageArrayLayers;
		case 3: return (jint) l.minUniformBufferOffsetAlignment;
		case 4: return ctx->anisotropySupported ? (jint) ctx->maxAnisotropy : 0;
		case 5: return (jint) l.maxUniformBufferRange;
		case 6: return (jint) ctx->swapchain.extent.width;
		case 7: return (jint) ctx->swapchain.extent.height;
		case 8: return ctx->swapchain.swapchain ? 1 : 0;
		case 9: return (jint) (ctx->allocator.totalAllocated() >> 20);
		case 10: return (jint) l.maxStorageBufferRange;
		default: return 0;
	}
}

JNI_FN(jboolean, setSurface)(JNIEnv *env, jclass, jobject surface) {
	if (!alive()) return JNI_FALSE;
	return setSurface(env, surface) ? JNI_TRUE : JNI_FALSE;
}

JNI_FN(void, invalidateSwapchain)(JNIEnv *, jclass) {
	if (alive()) ctx->swapchain.dirty = true;
}

JNI_FN(jboolean, imageRead)(JNIEnv *env, jclass, jint img, jobject dst) {
	if (!alive() || !dst) return JNI_FALSE;
	void *ptr = env->GetDirectBufferAddress(dst);
	jlong cap = env->GetDirectBufferCapacity(dst);
	if (!ptr || cap <= 0) return JNI_FALSE;
	return readImage(img, ptr, (size_t) cap) ? JNI_TRUE : JNI_FALSE;
}

JNI_FN(void, setVsync)(JNIEnv *, jclass, jboolean vsync) {
	if (alive()) setVsync(vsync);
}

JNI_FN(jboolean, endFrame)(JNIEnv *, jclass, jboolean present) {
	if (!alive()) return JNI_FALSE;
	endFrame(present);
	return ctx->deviceLost ? JNI_FALSE : JNI_TRUE;
}

JNI_FN(void, waitIdle)(JNIEnv *, jclass) {
	if (alive()) waitIdle();
}

JNI_FN(jstring, lastError)(JNIEnv *env, jclass) {
	if (!ctx || ctx->lastError.empty()) return nullptr;
	jstring s = str(env, ctx->lastError);
	ctx->lastError.clear();
	return s;
}

// --- buffers ---

JNI_FN(jint, bufferCreate)(JNIEnv *, jclass, jlong size, jint usage) {
	return alive() ? bufferCreate((VkDeviceSize) size, (uint32_t) usage) : 0;
}

JNI_FN(void, bufferDestroy)(JNIEnv *, jclass, jint h) {
	if (alive()) bufferDestroy(h);
}

JNI_FN(jobject, bufferMap)(JNIEnv *env, jclass, jint h, jint mode) {
	return alive() ? bufferMap(env, h, mode) : nullptr;
}

JNI_FN(void, bufferResize)(JNIEnv *env, jclass, jint h, jlong size, jlong preserve) {
	if (alive()) bufferResize(env, h, (VkDeviceSize) size, (VkDeviceSize) preserve);
}

JNI_FN(void, bufferCopy)(JNIEnv *, jclass, jint src, jint dst, jlong so, jlong d, jlong n) {
	if (alive()) bufferCopy(src, dst, (VkDeviceSize) so, (VkDeviceSize) d, (VkDeviceSize) n);
}

// --- images ---

JNI_FN(jint, imageCreate)(JNIEnv *, jclass, jint type, jint glFormat, jint w, jint h, jint layers, jint mips, jint samples, jint usage) {
	return alive() ? imageCreate(type, glFormat, (uint32_t) w, (uint32_t) h, (uint32_t) layers, (uint32_t) mips,
		(uint32_t) samples, (uint32_t) usage) : 0;
}

JNI_FN(void, imageDestroy)(JNIEnv *, jclass, jint h) {
	if (alive()) imageDestroy(h);
}

JNI_FN(jboolean, imageUpload)(JNIEnv *env, jclass, jint img, jint level, jint x, jint y, jint layer, jint w, jint h, jint layers,
	jobject data, jint dataOffset, jint dataLength, jint buffer, jlong bufferOffset) {
	if (!alive()) return JNI_FALSE;
	const void *ptr = nullptr;
	if (data) {
		ptr = env->GetDirectBufferAddress(data);
		if (!ptr) {
			LOGE("imageUpload requires a direct buffer");
			return JNI_FALSE;
		}
		ptr = (const char *) ptr + dataOffset;
	}
	return imageUpload(img, (uint32_t) level, x, y, layer, (uint32_t) w, (uint32_t) h, (uint32_t) layers,
		ptr, (size_t) dataLength, buffer, (VkDeviceSize) bufferOffset, 0) ? JNI_TRUE : JNI_FALSE;
}

JNI_FN(jboolean, imageUploadInts)(JNIEnv *env, jclass, jint img, jint level, jint x, jint y, jint layer, jint w, jint h, jint layers,
	jintArray data, jint offsetInts, jint countInts) {
	if (!alive() || !data) return JNI_FALSE;
	void *ptr = env->GetPrimitiveArrayCritical(data, nullptr);
	if (!ptr) return JNI_FALSE;
	// Copy out of the critical region first; staging may allocate, and no JNI is allowed while held
	std::vector<uint8_t> copy((size_t) countInts * 4);
	memcpy(copy.data(), (int32_t *) ptr + offsetInts, copy.size());
	env->ReleasePrimitiveArrayCritical(data, ptr, JNI_ABORT);
	return imageUpload(img, (uint32_t) level, x, y, layer, (uint32_t) w, (uint32_t) h, (uint32_t) layers,
		copy.data(), copy.size(), 0, 0, 0) ? JNI_TRUE : JNI_FALSE;
}

JNI_FN(void, imageGenerateMips)(JNIEnv *, jclass, jint img) {
	if (alive()) imageGenerateMips(img);
}

JNI_FN(void, imageSampler)(JNIEnv *, jclass, jint img, jint minFilter, jint magFilter, jint mipmap, jint wrapS, jint wrapT, jint wrapR, jfloat aniso) {
	if (!alive()) return;
	SamplerState s;
	s.minFilter = (uint8_t) minFilter;
	s.magFilter = (uint8_t) magFilter;
	s.mipmap = (uint8_t) mipmap;
	s.wrapS = (uint8_t) wrapS;
	s.wrapT = (uint8_t) wrapT;
	s.wrapR = (uint8_t) wrapR;
	s.anisotropy = aniso;
	imageSetSampler(img, s);
}

JNI_FN(void, setUnitDummy)(JNIEnv *, jclass, jint unit, jint kind) {
	if (!alive() || unit < 0 || unit >= NUM_TEXTURE_UNITS || kind < 0 || kind > 3) return;
	ctx->unitDummy[unit] = ctx->dummyImages[kind];
	ctx->texturesDirty = true;
}

JNI_FN(jint, framebufferCreate)(JNIEnv *, jclass, jint color, jint layer, jint depth, jint resolve) {
	return alive() ? framebufferCreate(color, layer, depth, resolve) : 0;
}

JNI_FN(void, framebufferDestroy)(JNIEnv *, jclass, jint h) {
	if (alive()) framebufferDestroy(h);
}

// --- programs & vertex layouts ---

static std::string g_programError;

JNI_FN(jint, programCreate)(JNIEnv *env, jclass, jstring name, jstring vertex, jstring fragment) {
	if (!alive()) return 0;
	g_programError.clear();
	int h = programCreate(jstr(env, name), jstr(env, vertex), jstr(env, fragment), g_programError);
	if (!h) LOGE("Program %s failed: %s", jstr(env, name).c_str(), g_programError.c_str());
	return h;
}

JNI_FN(jstring, programError)(JNIEnv *env, jclass) {
	return str(env, g_programError);
}

JNI_FN(void, programDestroy)(JNIEnv *, jclass, jint h) {
	if (alive()) programDestroy(h);
}

JNI_FN(jint, layoutCreate)(JNIEnv *, jclass) {
	return alive() ? layoutCreate() : 0;
}

JNI_FN(void, layoutDestroy)(JNIEnv *, jclass, jint h) {
	if (alive()) layoutDestroy(h);
}

JNI_FN(void, layoutAttrib)(JNIEnv *, jclass, jint layout, jint location, jint buffer, jint format, jint offset, jint stride, jint perInstance) {
	if (alive()) layoutSetAttrib(layout, location, buffer, format, offset, stride, perInstance);
}

// --- recording ---

JNI_FN(jboolean, beginPass)(JNIEnv *, jclass, jint fb, jint colorLoad, jfloat r, jfloat g, jfloat b, jfloat a,
	jint depthLoad, jfloat depth, jint flags) {
	if (!alive()) return JNI_FALSE;
	float clear[4] = { r, g, b, a };
	return beginPass(fb, colorLoad, clear, depthLoad, depth, flags) ? JNI_TRUE : JNI_FALSE;
}

JNI_FN(void, endPass)(JNIEnv *, jclass) {
	if (alive()) endPass();
}

JNI_FN(void, execute)(JNIEnv *env, jclass, jobject stream, jint count) {
	if (!alive() || count <= 0) return;
	auto *ops = (const int32_t *) env->GetDirectBufferAddress(stream);
	if (ops) executeStream(ops, count);
}

JNI_FN(jboolean, blit)(JNIEnv *, jclass, jint src, jint sx0, jint sy0, jint sx1, jint sy1,
	jint dst, jint dx0, jint dy0, jint dx1, jint dy1, jboolean linear) {
	if (!alive()) return JNI_FALSE;
	int s[4] = { sx0, sy0, sx1, sy1 };
	int d[4] = { dx0, dy0, dx1, dy1 };
	return blit(src, s, dst, d, linear) ? JNI_TRUE : JNI_FALSE;
}

JNI_FN(void, clearSwapchain)(JNIEnv *, jclass, jfloat r, jfloat g, jfloat b, jfloat a) {
	if (alive()) clearSwapchain(r, g, b, a);
}
