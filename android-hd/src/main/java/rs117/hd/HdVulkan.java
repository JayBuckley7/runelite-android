package rs117.hd;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import rs117.hd.utils.ColorUtils;
import rs117.hd.vulkan.Vk;
import rs117.hd.vulkan.VkNative;
import rs117.hd.vulkan.VkShaders;

import static org.lwjgl.opengl.GL33C.*;
import static rs117.hd.HdPlugin.*;
import static rs117.hd.utils.MathUtils.*;

/**
 * The Vulkan side of {@link HdPlugin}'s GPU resources: window, fullscreen geometry, UI
 * textures, scene / shadow / tiled-lighting render targets, the UI composite and screenshots.
 * Kept out of HdPlugin so the upstream file only gains short branches.
 */
@Slf4j
public final class HdVulkan {
	private HdVulkan() {}

	// Scene render target
	static int sceneColor, sceneResolve, sceneDepth;
	/** Single-sample image holding the finished scene, sampled by the swapchain composite. */
	public static int sceneReadImage;
	static int[] tiledLightingFbos = new int[0];
	private static int shadowDepth;
	private static int lastSurfaceWidth, lastSurfaceHeight;

	/** @return null if Vulkan is running, otherwise why it couldn't start */
	static String startUp() {
		if (AndroidSupport.host == null)
			return "no Android host";
		String err = Vk.init(new java.io.File(net.runelite.client.RuneLite.RUNELITE_DIR, "117hd/vulkan-cache"));
		if (err != null)
			return err;
		Vk.ACTIVE = true;
		AndroidSupport.host.setVulkanMode(true);
		// Texture units that may be sampled before anything real is bound to them
		Vk.setUnitDummy(TEXTURE_UNIT_GAME - GL_TEXTURE0, VkNative.DUMMY_2D_ARRAY);
		Vk.setUnitDummy(TEXTURE_UNIT_TILE_HEIGHT_MAP - GL_TEXTURE0, VkNative.DUMMY_3D_INT);
		Vk.setUnitDummy(TEXTURE_UNIT_TILED_LIGHTING_MAP - GL_TEXTURE0, VkNative.DUMMY_2D_ARRAY_UINT);
		Vk.setUnitDummy(TEXTURE_UNIT_NEBULA_MAP - GL_TEXTURE0, VkNative.DUMMY_CUBE);
		lastSurfaceWidth = lastSurfaceHeight = 0;
		// Handles belong to the previous device
		compositeProgram = 0;
		scenePending = false;
		log.info("117 HD using Vulkan: {}", Vk.deviceName());
		return null;
	}

	static void shutDown() {
		if (!Vk.ACTIVE)
			return;
		Vk.waitIdle();
		Vk.shutdown();
		if (AndroidSupport.host != null)
			AndroidSupport.host.setVulkanMode(false);
	}

	/** Attach the current window, if any. Frames still record and submit without one. */
	static void prepareFrame() {
		var host = AndroidSupport.host;
		Object surface = host.vulkanSurface();
		int w = host.width(), h = host.height();
		if (surface != null && (w != lastSurfaceWidth || h != lastSurfaceHeight)) {
			lastSurfaceWidth = w;
			lastSurfaceHeight = h;
			Vk.invalidateSwapchain();
		}
		Vk.setSurface(surface);
	}

	// ---------------------------------------------------------------------------------------
	// Fullscreen geometry
	// ---------------------------------------------------------------------------------------

	private static int vboQuad, vboTri, vboQuadList, vaoQuadList;

	/** The fullscreen quad as a 6-vertex triangle list (Vulkan has no triangle fans here). */
	public static int quadListLayout() {
		return vaoQuadList;
	}

	private static int fullscreenBuffer(float[] data) {
		int buf = Vk.bufferCreate(data.length * 4L, VkNative.BUF_VERTEX);
		Vk.bufferMap(buf, VkNative.MAP_PRESERVE).asFloatBuffer().put(data);
		return buf;
	}

	private static int fullscreenLayout(int buffer) {
		int vao = Vk.layoutCreate();
		Vk.layoutAttrib(vao, 0, buffer, 2, GL_FLOAT, false, false, 16, 0, 0);
		Vk.layoutAttrib(vao, 1, buffer, 2, GL_FLOAT, false, false, 16, 8, 0);
		return vao;
	}

	static void initializeVaos(HdPlugin p) {
		vboQuad = fullscreenBuffer(new float[] {
			1, 1, 1, 1,
			-1, 1, 0, 1,
			-1, -1, 0, 0,
			1, -1, 1, 0
		});
		p.vaoQuad = fullscreenLayout(vboQuad);
		vboTri = fullscreenBuffer(new float[] {
			-1, -1, 0, 0,
			3, -1, 2, 0,
			-1, 3, 0, 2
		});
		p.vaoTri = fullscreenLayout(vboTri);
		vboQuadList = fullscreenBuffer(new float[] {
			1, 1, 1, 1,
			-1, 1, 0, 1,
			-1, -1, 0, 0,
			1, 1, 1, 1,
			-1, -1, 0, 0,
			1, -1, 1, 0
		});
		vaoQuadList = fullscreenLayout(vboQuadList);
	}

	static void destroyVaos(HdPlugin p) {
		Vk.layoutDestroy(p.vaoQuad);
		Vk.layoutDestroy(p.vaoTri);
		Vk.layoutDestroy(vaoQuadList);
		Vk.bufferDestroy(vboQuad);
		Vk.bufferDestroy(vboTri);
		Vk.bufferDestroy(vboQuadList);
		p.vaoQuad = p.vaoTri = vaoQuadList = 0;
		vboQuad = vboTri = vboQuadList = 0;
	}

	// ---------------------------------------------------------------------------------------
	// UI
	// ---------------------------------------------------------------------------------------

	static void resizeUiTextures(HdPlugin p, int width, int height) {
		for (int i = 0; i < p.texUi.length; i++) {
			Vk.imageDestroy(p.texUi[i]);
			p.texUi[i] = Vk.imageCreate(VkNative.IMG_2D, GL_RGBA8, width, height, 1, 1, 1, VkNative.IMG_SAMPLED);
			Vk.imageSampler(p.texUi[i], GL_LINEAR, GL_LINEAR, GL_CLAMP_TO_EDGE, GL_CLAMP_TO_EDGE, 1);
		}
	}

	static void destroyUiTextures(HdPlugin p) {
		for (int i = 0; i < p.texUi.length; i++) {
			Vk.imageDestroy(p.texUi[i]);
			p.texUi[i] = 0;
		}
	}

	/** Uploads the UI copied into the frame's staging buffer; must run outside a render pass. */
	static void uploadUi(HdPlugin p, int pbo, int width, int height) {
		Vk.imageUploadFromBuffer(p.texUi[p.texUiCurrent], 0, 0, width, height, pbo, 0);
	}

	/** Draw the scene (if any) and the UI in one swapchain pass. */
	static void drawUi(HdPlugin p, int overlayColor, int samplingFunction) {
		beginSwapchainPass(p);
		Vk.viewport(0, 0, p.actualUiResolution[0], p.actualUiResolution[1]);
		Vk.disable(GL_DEPTH_TEST);
		Vk.disable(GL_CULL_FACE);

		// Debug overlays never write alpha, as in the GL path
		Vk.colorMask(true, true, true, false);
		p.tiledLightingOverlay.render();

		p.uiProgram.use();
		p.uboUi.sourceDimensions.set(p.getUiResolution());
		p.uboUi.targetDimensions.set(p.actualUiResolution);
		p.uboUi.alphaOverlay.set(ColorUtils.srgba(overlayColor));
		p.uboUi.upload();

		int tex = p.texUi[p.texUiCurrent];
		Vk.imageSampler(tex, samplingFunction, samplingFunction, GL_CLAMP_TO_EDGE, GL_CLAMP_TO_EDGE, 1);
		Vk.bindTexture(TEXTURE_UNIT_UI - GL_TEXTURE0, tex);

		Vk.disable(GL_DEPTH_TEST);
		Vk.disable(GL_CULL_FACE);
		Vk.enable(GL_BLEND);
		Vk.blendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
		Vk.colorMask(true, true, true, true);
		Vk.bindVertexLayout(p.vaoTri, 0);
		Vk.drawArrays(0, 3);

		Vk.colorMask(true, true, true, false);
		p.shadowMapOverlay.render();
		p.gammaCalibrationOverlay.render();

		Vk.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
		Vk.disable(GL_BLEND);
		Vk.colorMask(true, true, true, true);
		Vk.endPass();
	}

	// The scene is drawn into the swapchain pass as a fullscreen triangle rather than blitted in
	// beforehand: a blit is a full-screen write the following LOAD pass reads straight back into
	// tile memory, while drawing it inside the pass costs neither (and needs no blit support).
	private static boolean scenePending, sceneLinear, swapchainPassDone;
	private static int compositeProgram;

	private static final String COMPOSITE_VERT = VkShaders.HEADER
		+ "layout(location = 0) in vec2 vPos;\nlayout(location = 1) in vec2 vUv;\nout vec2 fUv;\n"
		+ "void main() { fUv = vUv; gl_Position = vec4(vPos, 0.0, 1.0); }\n";
	private static final String COMPOSITE_FRAG = VkShaders.HEADER
		+ "layout(set = 1, binding = 0) uniform sampler2D sceneTex;\nin vec2 fUv;\nout vec4 FragColor;\n"
		+ "void main() { FragColor = vec4(texture(sceneTex, fUv).rgb, 1.0); }\n";

	/** Queue the scene (scaled into its viewport) for this frame's swapchain pass; black when there's none. */
	public static void presentScene(HdPlugin p, boolean drawScene, boolean linear) {
		swapchainPassDone = false;
		scenePending = drawScene && sceneReadImage != 0;
		sceneLinear = linear;
	}

	private static void beginSwapchainPass(HdPlugin p) {
		swapchainPassDone = true;
		Vk.beginPass(VkNative.SWAPCHAIN, VkNative.CLEAR, 0, 0, 0, 1, VkNative.DONT_CARE, 0, false);
		if (!scenePending)
			return;
		scenePending = false;
		if (compositeProgram == 0) {
			compositeProgram = Vk.programCreate("hdvk_composite", COMPOSITE_VERT, COMPOSITE_FRAG);
			if (compositeProgram == 0) {
				log.error("Scene composite shader failed: {}", Vk.programError());
				return;
			}
		}
		int[] vp = p.sceneViewport;
		int filter = sceneLinear ? GL_LINEAR : GL_NEAREST;
		Vk.imageSampler(sceneReadImage, filter, filter, GL_CLAMP_TO_EDGE, GL_CLAMP_TO_EDGE, 1);
		Vk.viewport(vp[0], vp[1], vp[2], vp[3]);
		Vk.disable(GL_BLEND);
		Vk.disable(GL_DEPTH_TEST);
		Vk.disable(GL_CULL_FACE);
		Vk.colorMask(true, true, true, true);
		Vk.useProgram(compositeProgram, null);
		Vk.bindTexture(0, sceneReadImage);
		Vk.bindVertexLayout(p.vaoTri, 0);
		Vk.drawArrays(0, 3);
	}

	/** Make sure the frame reached the swapchain even when the UI was skipped (hidden UI). */
	public static void finishFrame(HdPlugin p) {
		if (swapchainPassDone)
			return;
		beginSwapchainPass(p);
		Vk.endPass();
	}

	// ---------------------------------------------------------------------------------------
	// Render targets
	// ---------------------------------------------------------------------------------------

	static void createSceneTargets(HdPlugin p) {
		int maxSamples = Vk.limit(VkNative.LIMIT_MAX_SAMPLES);
		p.msaaSamples = max(1, min(p.config.antiAliasingMode().getSamples(), maxSamples));
		int w = p.sceneResolution[0], h = p.sceneResolution[1];
		int color = VkNative.IMG_COLOR_ATTACHMENT;
		if (p.msaaSamples > 1) {
			// Samples live in tile memory and are resolved there; only the resolve hits memory
			sceneColor = Vk.imageCreate(VkNative.IMG_2D, GL_RGBA8, w, h, 1, 1, p.msaaSamples, color | VkNative.IMG_TRANSIENT);
			sceneResolve = Vk.imageCreate(VkNative.IMG_2D, GL_RGBA8, w, h, 1, 1, 1, color | VkNative.IMG_SAMPLED);
			sceneReadImage = sceneResolve;
		} else {
			sceneColor = Vk.imageCreate(VkNative.IMG_2D, GL_RGBA8, w, h, 1, 1, 1, color | VkNative.IMG_SAMPLED);
			sceneResolve = 0;
			sceneReadImage = sceneColor;
		}
		sceneDepth = Vk.imageCreate(VkNative.IMG_2D, GL_DEPTH_COMPONENT32F, w, h, 1, 1, p.msaaSamples,
			VkNative.IMG_DEPTH_ATTACHMENT | VkNative.IMG_TRANSIENT);
		p.fboScene = Vk.framebufferCreate(sceneColor, 0, sceneDepth, sceneResolve);
		if (p.fboScene == 0)
			throw new RuntimeException("Unable to create the " + w + "x" + h + " scene framebuffer (MSAA " + p.msaaSamples + ")");
		p.fboSceneResolve = 0;
		p.sceneTileResolved = true;
	}

	static void destroySceneTargets(HdPlugin p) {
		Vk.framebufferDestroy(p.fboScene);
		Vk.imageDestroy(sceneColor);
		Vk.imageDestroy(sceneResolve);
		Vk.imageDestroy(sceneDepth);
		p.fboScene = 0;
		sceneColor = sceneResolve = sceneDepth = sceneReadImage = 0;
	}

	static void createShadowMap(HdPlugin p, int resolution) {
		int maxResolution = Vk.limit(VkNative.LIMIT_MAX_IMAGE_2D);
		if (resolution > maxResolution) {
			log.info("Capping shadow resolution from {} to {}", resolution, maxResolution);
			resolution = maxResolution;
		}
		p.shadowMapResolution = resolution;
		shadowDepth = Vk.imageCreate(VkNative.IMG_2D, GL_DEPTH_COMPONENT24, resolution, resolution, 1, 1, 1,
			VkNative.IMG_DEPTH_ATTACHMENT | VkNative.IMG_SAMPLED);
		Vk.imageSampler(shadowDepth, GL_NEAREST, GL_NEAREST, GL_CLAMP_TO_BORDER, GL_CLAMP_TO_BORDER, 1);
		p.fboShadowMap = Vk.framebufferCreate(0, 0, shadowDepth, 0);
		Vk.bindTexture(TEXTURE_UNIT_SHADOW_MAP - GL_TEXTURE0, shadowDepth);

		if (p.configTerrainShadows) {
			p.terrainShadowMapResolution = resolution;
			terrainShadowDepth = Vk.imageCreate(VkNative.IMG_2D, GL_DEPTH_COMPONENT16, resolution, resolution, 1, 1, 1,
				VkNative.IMG_DEPTH_ATTACHMENT | VkNative.IMG_SAMPLED);
			Vk.imageSampler(terrainShadowDepth, GL_LINEAR, GL_LINEAR, GL_CLAMP_TO_BORDER, GL_CLAMP_TO_BORDER, 1,
				terrainShadowCompare(p));
			p.fboTerrainShadowMap = Vk.framebufferCreate(0, 0, terrainShadowDepth, 0);
			Vk.bindTexture(TEXTURE_UNIT_TERRAIN_SHADOW_MAP - GL_TEXTURE0, terrainShadowDepth);
		} else {
			createDummyTerrainShadowMap(p);
		}
	}

	/** Terrain shadows sample through a sampler2DShadow, except PCSS which needs the raw depths. */
	private static int terrainShadowCompare(HdPlugin p) {
		return p.config.shadowFiltering() == rs117.hd.config.ShadowFiltering.PCSS ? GL_NONE : GL_GREATER;
	}

	/** A 1x1 depth image cleared to 1 ("fully lit", like GL's white border) for unused shadow units. */
	private static int dummyDepth(int glFormat, int minFilter, int compare) {
		int image = Vk.imageCreate(VkNative.IMG_2D, glFormat, 1, 1, 1, 1, 1,
			VkNative.IMG_DEPTH_ATTACHMENT | VkNative.IMG_SAMPLED);
		Vk.imageSampler(image, minFilter, minFilter, GL_CLAMP_TO_BORDER, GL_CLAMP_TO_BORDER, 1, compare);
		int fb = Vk.framebufferCreate(0, 0, image, 0);
		Vk.beginPass(fb, VkNative.DONT_CARE, 0, 0, 0, 0, VkNative.CLEAR, 1, true);
		Vk.endPass();
		Vk.framebufferDestroy(fb);
		return image;
	}

	private static void createDummyTerrainShadowMap(HdPlugin p) {
		terrainShadowDepth = dummyDepth(GL_DEPTH_COMPONENT16, GL_NEAREST, terrainShadowCompare(p));
		Vk.bindTexture(TEXTURE_UNIT_TERRAIN_SHADOW_MAP - GL_TEXTURE0, terrainShadowDepth);
	}

	static void createDummyShadowMaps(HdPlugin p) {
		shadowDepth = dummyDepth(GL_DEPTH_COMPONENT24, GL_NEAREST, GL_NONE);
		Vk.bindTexture(TEXTURE_UNIT_SHADOW_MAP - GL_TEXTURE0, shadowDepth);
		createDummyTerrainShadowMap(p);
	}

	static void destroyShadowMap(HdPlugin p) {
		Vk.framebufferDestroy(p.fboShadowMap);
		Vk.framebufferDestroy(p.fboTerrainShadowMap);
		Vk.imageDestroy(shadowDepth);
		Vk.imageDestroy(terrainShadowDepth);
		p.fboShadowMap = p.fboTerrainShadowMap = 0;
		shadowDepth = terrainShadowDepth = 0;
	}

	private static int terrainShadowDepth;

	static void createTiledLighting(HdPlugin p) {
		int w = p.tiledLightingResolution[0], h = p.tiledLightingResolution[1], layers = p.tiledLightingLayerCount;
		p.texTiledLighting = Vk.imageCreate(VkNative.IMG_2D_ARRAY, GL_RGBA16UI, w, h, layers, 1, 1,
			VkNative.IMG_COLOR_ATTACHMENT | VkNative.IMG_SAMPLED | VkNative.IMG_GENERAL);
		tiledLightingFbos = new int[layers];
		for (int i = 0; i < layers; i++)
			tiledLightingFbos[i] = Vk.framebufferCreate(p.texTiledLighting, i, 0, 0);
		p.fboTiledLighting = tiledLightingFbos.length > 0 ? tiledLightingFbos[0] : 0;
		Vk.bindTexture(TEXTURE_UNIT_TILED_LIGHTING_MAP - GL_TEXTURE0, p.texTiledLighting);
	}

	static void destroyTiledLighting(HdPlugin p) {
		for (int fb : tiledLightingFbos)
			Vk.framebufferDestroy(fb);
		tiledLightingFbos = new int[0];
		Vk.imageDestroy(p.texTiledLighting);
		p.texTiledLighting = 0;
		p.fboTiledLighting = 0;
	}

	public static int tiledLightingFramebuffer(int layer) {
		return layer < tiledLightingFbos.length ? tiledLightingFbos[layer] : 0;
	}

	// ---------------------------------------------------------------------------------------
	// Screenshots
	// ---------------------------------------------------------------------------------------

	/**
	 * The swapchain image is gone once presented, so redo the last composite into an offscreen
	 * image and read that back. The scene image and UI texture still hold the last frame.
	 */
	static Image screenshot(HdPlugin p, boolean hasScene, boolean linear) {
		int width = p.actualUiResolution[0];
		int height = p.actualUiResolution[1];
		if (width <= 0 || height <= 0)
			return null;

		int target = Vk.imageCreate(VkNative.IMG_2D, GL_RGBA8, width, height, 1, 1, 1,
			VkNative.IMG_COLOR_ATTACHMENT | VkNative.IMG_SAMPLED);
		int fb = Vk.framebufferCreate(target, 0, 0, 0);
		try {
			Vk.beginPass(fb, VkNative.CLEAR, 0, 0, 0, 1, VkNative.DONT_CARE, 0, false);
			Vk.endPass();
			if (hasScene && sceneReadImage != 0) {
				int[] res = p.sceneResolution;
				int[] vp = p.sceneViewport;
				Vk.blit(sceneReadImage, 0, 0, res[0], res[1], target, vp[0], vp[1], vp[0] + vp[2], vp[1] + vp[3], linear);
			}
			Vk.beginPass(fb, VkNative.LOAD, 0, 0, 0, 1, VkNative.DONT_CARE, 0, false);
			Vk.viewport(0, 0, width, height);
			p.uiProgram.use();
			Vk.bindTexture(TEXTURE_UNIT_UI - GL_TEXTURE0, p.texUi[p.texUiCurrent]);
			Vk.disable(GL_DEPTH_TEST);
			Vk.disable(GL_CULL_FACE);
			Vk.enable(GL_BLEND);
			Vk.blendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
			Vk.colorMask(true, true, true, true);
			Vk.bindVertexLayout(p.vaoTri, 0);
			Vk.drawArrays(0, 3);
			Vk.disable(GL_BLEND);
			Vk.endPass();

			ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
			if (!Vk.imageRead(target, buffer))
				return null;

			BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
			// The offscreen image has GL's row order (bottom row first), like glReadPixels
			for (int y = 0; y < height; ++y) {
				for (int x = 0; x < width; ++x) {
					int r = buffer.get() & 0xff;
					int g = buffer.get() & 0xff;
					int b = buffer.get() & 0xff;
					buffer.get();
					pixels[(height - y - 1) * width + x] = (r << 16) | (g << 8) | b;
				}
			}
			return image;
		} finally {
			Vk.framebufferDestroy(fb);
			Vk.imageDestroy(target);
		}
	}

	static boolean shouldHideOverlayOnLogin(HdPlugin p) {
		return p.client.getGameState().getState() < GameState.LOADING.getState();
	}

	static boolean sameSize(int[] a, int[] b) {
		return Arrays.equals(a, b);
	}
}
