package net.runelite.client.plugins.gpugles;

import static android.opengl.GLES20.GL_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_BLEND;
import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_TEST;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_FRAGMENT_SHADER;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_ONE;
import static android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.GL_STATIC_DRAW;
import static android.opengl.GLES20.GL_STREAM_DRAW;
import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_TRIANGLE_FAN;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.GL_VERTEX_SHADER;
import static net.runelite.client.plugins.gpugles.Gfx.glActiveTexture;
import static net.runelite.client.plugins.gpugles.Gfx.glBindBuffer;
import static net.runelite.client.plugins.gpugles.Gfx.glBindTexture;
import static net.runelite.client.plugins.gpugles.Gfx.glBlendFunc;
import static net.runelite.client.plugins.gpugles.Gfx.glBufferData;
import static net.runelite.client.plugins.gpugles.Gfx.glClear;
import static net.runelite.client.plugins.gpugles.Gfx.glClearColor;
import static net.runelite.client.plugins.gpugles.Gfx.glDeleteProgram;
import static net.runelite.client.plugins.gpugles.Gfx.glDeleteTextures;
import static net.runelite.client.plugins.gpugles.Gfx.glDisable;
import static net.runelite.client.plugins.gpugles.Gfx.glDrawArrays;
import static net.runelite.client.plugins.gpugles.Gfx.glEnable;
import static net.runelite.client.plugins.gpugles.Gfx.glEnableVertexAttribArray;
import static net.runelite.client.plugins.gpugles.Gfx.glGenBuffers;
import static net.runelite.client.plugins.gpugles.Gfx.glGenTextures;
import static net.runelite.client.plugins.gpugles.Gfx.glGetString;
import static net.runelite.client.plugins.gpugles.Gfx.glGetUniformLocation;
import static net.runelite.client.plugins.gpugles.Gfx.glTexImage2D;
import static net.runelite.client.plugins.gpugles.Gfx.glTexParameteri;
import static net.runelite.client.plugins.gpugles.Gfx.glTexSubImage2D;
import static net.runelite.client.plugins.gpugles.Gfx.glUniform1i;
import static net.runelite.client.plugins.gpugles.Gfx.glUniform2i;
import static net.runelite.client.plugins.gpugles.Gfx.glUniform4f;
import static net.runelite.client.plugins.gpugles.Gfx.glUseProgram;
import static net.runelite.client.plugins.gpugles.Gfx.glVertexAttribPointer;
import static net.runelite.client.plugins.gpugles.Gfx.glViewport;
import static android.opengl.GLES30.GL_PIXEL_UNPACK_BUFFER;
import static net.runelite.client.plugins.gpugles.Gfx.glBindVertexArray;
import static net.runelite.client.plugins.gpugles.Gfx.glDeleteBuffers;
import static net.runelite.client.plugins.gpugles.Gfx.glDeleteVertexArrays;
import static net.runelite.client.plugins.gpugles.Gfx.glGenVertexArrays;
import static android.opengl.GLES20.GL_BLEND;
import static android.opengl.GLES20.GL_CULL_FACE;
import static android.opengl.GLES20.GL_ONE;
import static android.opengl.GLES20.GL_SRC_ALPHA;
import static net.runelite.client.plugins.gpugles.Gfx.glBlendFuncSeparate;
import static net.runelite.client.plugins.gpugles.Gfx.glClearDepthf;
import static net.runelite.client.plugins.gpugles.Gfx.glDepthFunc;
import static net.runelite.client.plugins.gpugles.Gfx.glUniform1f;
import static net.runelite.client.plugins.gpugles.Gfx.glUniform3i;
import static net.runelite.client.plugins.gpugles.Gfx.glUniform4i;
import static net.runelite.client.plugins.gpugles.Gfx.glUniformMatrix4fv;
import static android.opengl.GLES20.GL_LESS;
import static android.opengl.GLES30.GL_UNIFORM_BUFFER;
import static net.runelite.client.plugins.gpugles.Gfx.glBindBufferBase;
import static net.runelite.client.plugins.gpugles.Gfx.glUniformBlockBinding;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.util.Log;
import com.google.inject.Provides;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import javax.inject.Inject;
import net.runelite.api.BufferProvider;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.FloatProjection;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Projection;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.TextureProvider;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import rs117.hd.vulkan.Vk;
import rs117.hd.vulkan.VkNative;

/**
 * Android GLES port of the desktop GPU plugin. Replaces JOGL/LWJGL with EGL14 +
 * GLES31 bindings, otherwise mirrors the desktop plugin's structure 1:1.
 *
 * Step status as of initial scaffold:
 *   - EGL host + SurfaceView wiring: done (see {@link GlesHost})
 *   - DrawCallbacks registration: done
 *   - UI overlay pass: done (BufferProvider → texture → UI shader → backbuffer)
 *   - 3D scene pass: STUBBED — scene draw/upload not yet wired; the canvas hole
 *     under the Compose chrome will show a black GLES surface with the 2D UI
 *     blitted on top until the scene pipeline lands.
 *
 * Threading model: the patched client calls {@link #draw} on its client thread.
 * We {@link GlesHost#makeCurrent} on that thread so the GL context follows it,
 * and {@link GlesHost#swapBuffers} at the end of each frame to present.
 */
@PluginDescriptor(
	name = "GPU",
	description = "GPU renderer (OpenGL ES or Vulkan) — Android port of the desktop GPU plugin",
	tags = {"fog", "draw distance", "android", "gles", "vulkan"},
	loadInSafeMode = false
)
public class GpuGlesPlugin extends Plugin implements DrawCallbacks
{
	private static final String TAG = "GpuGlesPlugin";

	static final int MAX_DISTANCE = 184;
	static final int MAX_FOG_DEPTH = 100;
	/** Offset for sxy → msxy in the extended scene grid (104→152 tile region). */
	public static final int SCENE_OFFSET = (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2;
	static final int NUM_ZONES = Constants.EXTENDED_SCENE_SIZE >> 3;
	static final int MAX_WORLDVIEWS = 4096;
	/** Upper bound on worker render threads, independent of what the config asks for.
	  * Beyond a handful the dynamic-object upload stops being the bottleneck and each
	  * extra thread is pure memory + scheduling cost on a phone. */
	static final int MAX_RENDER_THREADS = 4;

	private static final int UNIFORM_BUFFER_SIZE = 5 * Float.BYTES;

	// Attachment lists for glInvalidateFramebuffer. Preallocated because they are handed
	// to a GL call on every frame.
	private static final int[] INVALIDATE_DEPTH = { GLES30.GL_DEPTH_ATTACHMENT };
	private static final int[] INVALIDATE_COLOR = { GLES30.GL_COLOR_ATTACHMENT0 };

	/** Shared identity entity-projection. Only ever uploaded, never retained by a VAO
	  * range, so one instance is enough and it keeps a float[16] out of the frame loop. */
	private static final float[] IDENTITY = Mat4.identity();

	// Values for the UI program's `hasScene` uniform — see fragui.glsl.
	private static final int SCENE_NONE = 0;        // title/login: UI over black
	private static final int SCENE_TEXTURE = 1;     // composite sceneColorTex under the UI
	private static final int SCENE_DEFAULT_FB = 2;  // scene already in the default FB; blend

	/** Texture unit the UI program samples the scene from. Unit 1 is the scene program's
	  * texture array, so keep clear of it. */
	private static final int SCENE_TEX_UNIT = 2;

	private static final GlesShader PROGRAM = new GlesShader()
		.add(GL_VERTEX_SHADER, "vert.glsl")
		.add(GL_FRAGMENT_SHADER, "frag.glsl");

	private static final GlesShader UI_PROGRAM = new GlesShader()
		.add(GL_VERTEX_SHADER, "vertui.glsl")
		.add(GL_FRAGMENT_SHADER, "fragui.glsl");

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private GpuGlesPluginConfig config;
	@Inject private TextureManager textureManager;
	@Inject private RegionManager regionManager;
	@Inject private net.runelite.client.callback.RenderCallbackManager renderCallbackManager;

	private boolean glInitted;
	private int glProgram;
	private int glUiProgram;

	private int textureArrayId = -1;
	private int lastAnisotropicFilteringLevel = -1;

	// GlesHost.contextGeneration() captured the last time bringUpGl() built our GL
	// objects. If the live generation diverges, the EGL context was lost+recreated and
	// every GL handle we hold is stale — draw() rebuilds. -1 = nothing built yet.
	private int glContextGeneration = -1;

	// Set on a STARTING transition (logout/relog): textures may have reloaded, so the
	// texture array is freed + re-inited on the next draw(). Deferred to draw() because
	// freeTextureArray issues glDelete and the GL context is only current there.
	private volatile boolean freeTextureArrayPending = false;

	/** True for the current frame iff preSceneDraw fired (i.e. the client thinks
	 *  the 3D scene is being rendered). When false in draw(), we clear the EGL
	 *  surface ourselves so login/title-screen frames don't show stale pixels
	 *  from whatever the last logged-in frame left behind. */
	private boolean scenePaintedThisFrame;

	/**
	 * One of these per render thread. The engine can farm dynamic-object uploads out to
	 * N worker threads (see {@link #setupGpuFlags()}); each gets its own transient
	 * geometry pools and its own {@link ModelUploader} scratch so nothing is shared.
	 * rts[0] is the client/GL thread — it is the only one that may issue GL calls, and
	 * it is what drawTemp, the skybox and the zone alpha sorter use. Worker thread i
	 * lands in rts[i + 1].
	 */
	static class RenderThread
	{
		VAOList vaoO, vaoA;
		ModelUploader modelUploader;
	}

	private RenderThread[] rts = new RenderThread[0];

	private SceneUploader clientUploader, mapUploader;

	private int cameraYaw, cameraPitch;

	private GpuFloatBuffer uniformBuffer;

	// One context per WorldView (toplevel + sub-views). Zones live here keyed
	// by extended-scene (x, z) chunk coords. Mirrors desktop layout 1:1.
	static class SceneContext
	{
		final int sizeX, sizeZ;
		Zone[][] zones;

		// Entity projection captured at preSceneDraw. Used by drawDynamic/drawTemp
		// to seal each entity's VAO range under the right projection, and by the
		// PRE_PASS_ALPHA handler to re-arm uniEntityProj before static alpha
		// geometry draws. Identity for the toplevel scene (its geometry is already
		// in world coords); the WorldEntity-to-world matrix for a sub-WorldView.
		final float[] projection = Mat4.identity();

		int cameraX, cameraY, cameraZ;
		int minLevel, level, maxLevel;
		java.util.Set<Integer> hideRoofIds = java.util.Collections.emptySet();

		SceneContext(int sizeX, int sizeZ)
		{
			this.sizeX = sizeX;
			this.sizeZ = sizeZ;
			zones = new Zone[sizeX][sizeZ];
			for (int x = 0; x < sizeX; ++x)
				for (int z = 0; z < sizeZ; ++z)
					zones[x][z] = new Zone();
		}

		void free()
		{
			for (int x = 0; x < sizeX; ++x)
				for (int z = 0; z < sizeZ; ++z)
					zones[x][z].free();
		}
	}

	private SceneContext root;
	private SceneContext[] subs;

	SceneContext context(Scene scene)
	{
		int wvid = scene.getWorldViewId();
		if (wvid == WorldView.TOPLEVEL) return root;
		return subs[wvid];
	}

	SceneContext context(WorldView wv)
	{
		int wvid = wv.getId();
		if (wvid == WorldView.TOPLEVEL) return root;
		return subs[wvid];
	}

	private int vaoUiHandle;
	private int vboUiHandle;
	private static final int UI_QUAD_VERTS = 6;

	private int interfaceTexture;
	private int interfacePbo;

	private int lastCanvasWidth = -1;
	private int lastCanvasHeight = -1;

	/**
	 * Offscreen framebuffer for the 3D scene render. Sized at
	 * {@code surface * resolutionScale} so the heavy fragment work scales with the
	 * slider while the UI (composited from {@link #interfaceTexture} after the blit)
	 * always lands on the native-resolution default framebuffer. Recreated whenever
	 * the surface or scale changes — see {@link #ensureSceneFbo}.
	 */
	private int sceneFbo;
	private int sceneColorTex;
	private int sceneDepthRbo;
	private int sceneFboW;
	private int sceneFboH;

	/** Multisample scene path. When {@link #sceneSamples} >= 2, {@link #sceneFbo} renders
	 *  into a multisample color renderbuffer ({@link #sceneColorRbo}) + multisample depth
	 *  ({@link #sceneDepthRbo}); a same-size GL_NEAREST blit then resolves it into the
	 *  single-sample {@link #sceneColorTex} held by {@link #sceneResolveFbo}, from which the
	 *  existing scaled GL_LINEAR blit upsamples to the default framebuffer. GLES forbids a
	 *  multisample→single-sample blit that also scales, hence the separate resolve target.
	 *  When sceneSamples < 2 the resolve objects stay 0 and sceneColorTex is attached
	 *  directly to sceneFbo (the original single-sample path). */
	private int sceneColorRbo;
	private int sceneResolveFbo;
	private int sceneSamples;

	/** Cached direct ByteBuffer that prepareInterfaceTexture streams the BufferProvider
	 *  pixels through each frame. Was being [re]allocated per-frame (~3.7MB at 1280×720)
	 *  which produced 100s of MB/sec of LOS garbage at unlocked FPS — the Android log
	 *  shows the GC running every ~300ms with 185ms blocked-alloc pauses freeing ~49MB
	 *  of LOS each cycle. Keep one buffer per canvas size, reuse forever. */
	private ByteBuffer interfaceUploadBuf;
	private IntBuffer interfaceUploadBufAsInts;

	private final GlesBuffer glUniformBuffer = new GlesBuffer("uniform buffer");

	// Scene-shader uniform locations. uniBase / uniEntityTint / uniEntityProj are
	// static so Zone / VAO / draw helpers in the same package can reach them
	// without holding a plugin instance, matching desktop's pattern.
	private int uniUseFog, uniFogColor, uniFogDepth, uniDrawDistance;
	private int uniExpandedMapLoadingChunks, uniSmoothBanding;
	private int uniWorldProj;
	static int uniEntityProj;
	static int uniEntityTint;
	private int uniBrightness, uniTextures, uniTextureAnimations;
	private int uniBlockMain, uniTextureLightMode, uniTick;
	private int uniColorblindIntensity;
	static int uniBase;
	// UI-shader uniform locations
	private int uniTex, uniTexSourceDimensions, uniTexTargetDimensions;
	private int uniUiAlphaOverlay, uniUiColorblindIntensity;
	private int uniUiSceneTex, uniUiHasScene;

	private static Projection lastProjection;

	// Master switch for the per-frame/per-entity render instrumentation below. Flip to
	// true only when diagnosing scene-callback behaviour — these run in hot paths
	// (HashMap lookups, currentTimeMillis, string building, logcat) on every dynamic/
	// temp model and every sub-WorldView zone, so they must stay off in shipping builds.
	private static final boolean DEBUG = false;

	// TEMP DEBUG — once-per-second-per-sub-WV-per-callback throttle so we can see what fires.
	private static final java.util.HashMap<String, Long> debugSubWvLastLog = new java.util.HashMap<>();
	private static void debugSubWvCallback(String which, int wvId, int zx, int zz)
	{
		if (!DEBUG) return;
		String key = which + ":" + wvId;
		long now = System.currentTimeMillis();
		Long prev = debugSubWvLastLog.get(key);
		if (prev != null && now - prev < 1000) return;
		debugSubWvLastLog.put(key, now);
		Log.i(TAG, "SUBWV " + which + " wv=" + wvId + " zone=(" + zx + "," + zz + ")");
	}

	// TEMP DEBUG — track drawDynamic/drawTemp call volume + biggest model per second.
	private static long debugWindowStart = 0;
	private static int debugOpaqueDrawThrottle = 0;
	private static int debugDynamicCount = 0;
	private static int debugTempCount = 0;
	private static int debugBiggestFaces = 0;
	private static String debugBiggestInfo = "";

	private static void debugTrackDraw(String which, int faces, Model m, Scene scene,
		int x, int y, int z, int plane)
	{
		if (!DEBUG) return;
		long now = System.currentTimeMillis();
		if (debugWindowStart == 0) debugWindowStart = now;
		if ("drawDynamic".equals(which)) debugDynamicCount++;
		else debugTempCount++;
		// Log EVERY entity with > 1500 faces individually so we can see what's reaching
		// the callback. Throttle per-unique-position so we don't spam, but always log
		// at least once per second per unique entity.
		if (faces > 1500)
		{
			String posKey = which + ":" + x + "," + y + "," + z + "," + faces;
			Long prev = debugBigSeen.get(posKey);
			if (prev == null || now - prev >= 1500)
			{
				debugBigSeen.put(posKey, now);
				Log.i(TAG, "BIG " + which + " faces=" + faces + " verts=" + m.getVerticesCount()
					+ " vxNull=" + (m.getVerticesX() == null) + " idxNull=" + (m.getFaceIndices1() == null)
					+ " trans=" + (m.getFaceTransparencies() != null) + " wv=" + scene.getWorldViewId()
					+ " pos=(" + x + "," + y + "," + z + ") plane=" + plane);
			}
		}
		if (faces > debugBiggestFaces)
		{
			debugBiggestFaces = faces;
			debugBiggestInfo = which + " faces=" + faces + " verts=" + m.getVerticesCount()
				+ " trans=" + (m.getFaceTransparencies() != null) + " wv=" + scene.getWorldViewId()
				+ " pos=(" + x + "," + y + "," + z + ") plane=" + plane;
		}
		if (now - debugWindowStart >= 1000)
		{
			Log.i(TAG, "DRAW/s dyn=" + debugDynamicCount + " temp=" + debugTempCount
				+ " biggest{" + debugBiggestInfo + "}");
			debugWindowStart = now;
			debugDynamicCount = 0;
			debugTempCount = 0;
			debugBiggestFaces = 0;
			debugBiggestInfo = "";
			// Trim old entries
			debugBigSeen.entrySet().removeIf(e -> now - e.getValue() > 5000);
		}
	}
	private static final java.util.HashMap<String, Long> debugBigSeen = new java.util.HashMap<>();

	/** Update the entity-projection uniform if it changed. Static so VAO can call
	 *  during batched draws; mirrors desktop GpuPlugin.updateEntityProjection. */
	static void updateEntityProjection(Projection projection)
	{
		if (lastProjection != projection)
		{
			float[] p = projection instanceof FloatProjection
				? ((FloatProjection) projection).getProjection()
				: IDENTITY;
			glUniformMatrix4fv(uniEntityProj, 1, false, p, 0);
			lastProjection = projection;
		}
	}

	/**
	 * Upload an already-captured entity projection MATRIX (a SceneContext.projection
	 * snapshot) and invalidate the {@link #lastProjection} object-dedup cache. Used by
	 * {@link VAO#draw()} to replay each sealed range under the per-scene projection that
	 * was captured when the range was added — so deferred sub-WorldView entity geometry
	 * (e.g. the Gemstone Crab) is transformed by ITS scene's matrix, not whatever
	 * Projection object the shared instance was last mutated to. Mirrors desktop, which
	 * stores the float[] matrix per range.
	 */
	static void setEntityProjection(float[] matrix)
	{
		glUniformMatrix4fv(uniEntityProj, 1, false, matrix, 0);
		// We bypassed the object-keyed cache; force the next updateEntityProjection to
		// re-upload rather than skip on a stale identity match.
		lastProjection = null;
	}

	/** Set once the GLES path is actually taking over rendering (after first transition
	 *  out of {@link GameState#STARTING}). Guards [@link #engage()] against being run
	 *  twice and tells [@link #shutDown()] whether teardown work is needed. */
	private volatile boolean engaged;

	@Override
	protected void startUp()
	{
		// Cheap CPU-side init only. The GLES handoff is deferred to [#engage()] so the
		// boot sequence (splash → "Loading..." → title screen) renders through the SW
		// path. We've seen GLES half-init artifacts when engaging during STARTING:
		// SurfaceView attaches before the engine has reasonable Canvas dimensions, the
		// first EGL surface comes up at the wrong size, and the chrome bitmap composite
		// races the punched alpha hole. Holding engagement until LOGIN_SCREEN avoids
		// all of that — by then the engine has settled and the first frame after engage
		// is steady-state.
		root = new SceneContext(NUM_ZONES, NUM_ZONES);
		subs = new SceneContext[MAX_WORLDVIEWS];
		allocRenderThreads();
		clientUploader = new SceneUploader(renderCallbackManager);
		mapUploader = new SceneUploader(renderCallbackManager);

		// If the plugin is enabled mid-game (already past STARTING), engage immediately
		// — onGameStateChanged only fires on transitions, so there's nothing to wait
		// for. Reading getGameState happens on the client thread for the same reason
		// the existing engage block did.
		clientThread.invoke(() -> {
			if (client.getGameState() != GameState.STARTING)
			{
				engage();
			}
			return true;
		});
	}

	/**
	 * (Re)build the per-render-thread state. rts[0] is the client/GL thread; the rest
	 * shadow the worker threads the engine spawns for {@link DrawCallbacks#RENDER_THREADS}.
	 * The VAO pools are owned by the GL context and so are created in bringUpGl().
	 */
	private void allocRenderThreads()
	{
		int n = renderThreadCount() + 1;
		rts = new RenderThread[n];
		for (int i = 0; i < n; ++i)
		{
			RenderThread rt = rts[i] = new RenderThread();
			rt.modelUploader = new ModelUploader();
		}
	}

	/**
	 * Worker render threads to ask the engine for. Clamped to cores-1 so we never take
	 * the core the client thread is on, and to MAX_RENDER_THREADS because each thread
	 * costs a ModelUploader's scratch arrays (~2.6 MB) plus its own VAO pools — on a
	 * phone that adds up a lot faster than it does on a desktop.
	 */
	private int renderThreadCount()
	{
		int cpus = Runtime.getRuntime().availableProcessors();
		return Math.max(0, Math.min(cpus - 1, Math.min(config.numThreads(), MAX_RENDER_THREADS)));
	}

	/**
	 * Advertise the render-thread count from {@code rts.length}, never straight from the
	 * config: the engine indexes us by thread id, so telling it about a thread we have no
	 * {@link RenderThread} for is an out-of-bounds waiting to happen.
	 */
	private void setupGpuFlags()
	{
		int threads = Math.max(0, rts.length - 1);
		Log.i(TAG, "Using " + threads + " render threads");
		client.setGpuFlags(DrawCallbacks.GPU
			| (config.removeVertexSnapping() ? DrawCallbacks.NO_VERTEX_SNAPPING : 0)
			| DrawCallbacks.ZBUF
			| DrawCallbacks.RENDER_THREADS(threads));
	}

	/**
	 * Activate the GLES rendering path. Idempotent — safe to call multiple times.
	 * Always invoked on the client thread, either from the deferred startUp() check
	 * above or from onGameStateChanged() on the first transition out of STARTING.
	 *
	 * Order matters: flip [@link Canvas#setRenderedByGles] BEFORE bringUpGl so the
	 * first frame after engage already has the alpha-hole punched in the chrome
	 * bitmap (otherwise that frame paints the SW canvas through the bitmap and the
	 * SurfaceView's first EGL frame appears underneath an opaque overlay).
	 */
	private void engage()
	{
		if (engaged) return;
		engaged = true;
		try
		{
			java.awt.Canvas.setRenderedByGles(true);

			// Apply mobile render-scale + MSAA *before* bringUpGl: the MSAA value is
			// baked into the EGL config at context creation, and a fixed-size request
			// before the EGL surface exists means the first surfaceChanged callback
			// already arrives at the scaled size (avoids a one-frame native-res render).
			GlesHost.get().setRequestedMsaa(config.msaaSamples().samples);
			GlesHost.get().setResolutionScale(config.resolutionScale() / 100f);

			if (config.vulkanRenderer() && !Gfx.vulkan)
			{
				String err = Gfx.startVulkan();
				if (err != null)
				{
					Log.e(TAG, "Vulkan unavailable (" + err + "); using GLES");
				}
			}

			try
			{
				bringUpGl();
			}
			catch (Throwable t)
			{
				if (!Gfx.vulkan) throw t;
				// Everything bringUpGl made lives on the Vulkan device, which goes with it
				Log.e(TAG, "Vulkan bring-up failed; falling back to GLES", t);
				Gfx.stopVulkan();
				glInitted = false;
				bringUpGl();
			}

			client.setDrawCallbacks(this);
			setupGpuFlags();
			client.setExpandedMapLoading(config.expandedMapLoadingZones());
			applyFpsConfig();

			// Force the BufferProvider rebuild so it gives us an RGBA-with-alpha
			// pixel buffer — the desktop plugin does the same. The UI shader
			// alpha-blends the overlay onto the GL scene below, so non-zero
			// alpha matters.
			client.resizeCanvas();

			// If we engaged while already logged in (plugin toggled on mid-session), the
			// engine won't re-issue loadScene/swapScene until the next map rebuild, so seed
			// the current scene now. At LOGIN_SCREEN there's no world to load — the engine
			// drives the upload normally once the player logs in.
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				startupWorldLoad();
			}
		}
		catch (Throwable t)
		{
			Log.e(TAG, "engage failed", t);
			engaged = false;
			java.awt.Canvas.setRenderedByGles(false);
			try { shutDown(); } catch (Throwable ignored) {}
		}
	}

	/**
	 * Push {@link GpuGlesPluginConfig#unlockFps} + {@link GpuGlesPluginConfig#fpsTarget}
	 * down to the engine, the same way the desktop {@code GpuPlugin} does in its
	 * {@code setupSyncMode}. Without this the patched client keeps its 50-cycle cap
	 * regardless of what the config says.
	 *
	 * Also relaxes the EGL swap interval to 0 when uncapping, so the on-screen surface
	 * doesn't hard-pin to the display's vsync once the engine is willing to deliver
	 * more frames. Re-pinned to 1 when the unlock is turned back off.
	 */
	private void applyFpsConfig()
	{
		boolean unlock = config.unlockFps();
		int target = config.fpsTarget();
		client.setUnlockedFps(unlock);
		client.setUnlockedFpsTarget(unlock ? target : 0);
		// Drop vsync only when uncapping. With vsync on, the engine's uncapped scene
		// rate still gets latched to the display refresh — fine on a 120Hz device, but
		// users see ~60 on 60Hz screens when they asked for higher.
		Gfx.setSwapInterval(unlock ? 0 : 1);
	}

	@Subscribe
	public void onConfigChanged(net.runelite.client.events.ConfigChanged ev)
	{
		if (!ev.getGroup().equals(GpuGlesPluginConfig.GROUP)) return;
		if ("vulkanRenderer".equals(ev.getKey()))
		{
			clientThread.invokeLater(this::restartRenderer);
		}
		else if ("unlockFps".equals(ev.getKey()) || "fpsTarget".equals(ev.getKey()))
		{
			clientThread.invoke(() -> { applyFpsConfig(); return true; });
		}
		else if ("resolutionScale".equals(ev.getKey()))
		{
			GlesHost.get().setResolutionScale(config.resolutionScale() / 100f);
		}
		else if ("removeVertexSnapping".equals(ev.getKey()))
		{
			clientThread.invokeLater(this::setupGpuFlags);
		}
		else if ("numThreads".equals(ev.getKey()))
		{
			clientThread.invokeLater(() ->
			{
				// Rebuilding the render threads throws away their VAO pools, which means glDelete
				// calls — only legal with our EGL context current, and only safe between frames.
				boolean gl = glInitted && Gfx.makeCurrent();
				if (gl)
				{
					for (int i = 0; i < rts.length; ++i) // NOPMD: ForLoopCanBeForeach
					{
						if (rts[i].vaoO != null) rts[i].vaoO.free();
						if (rts[i].vaoA != null) rts[i].vaoA.free();
					}
				}

				// If GL wasn't available we couldn't free the old pools, so keep the old rts and
				// let the next bringUpGl()/toggle pick the new count up — resizing rts without
				// live pools would hand the engine thread ids we can't service.
				if (!gl)
				{
					return;
				}

				allocRenderThreads();
				for (int i = 0; i < rts.length; ++i)
				{
					rts[i].vaoO = new VAOList(i > 0);
					rts[i].vaoA = new VAOList(i > 0);
				}

				setupGpuFlags();
			});
		}
		// MSAA changes require an EGL context rebuild — we surface that requirement in
		// the config item description rather than try to re-init mid-frame.
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged ev)
	{
		GameState s = ev.getGameState();
		// First transition out of STARTING is our cue to take over rendering. The OSRS
		// engine has settled by then — Canvas dims are stable, the lobby world view is
		// being prepped, the SW rasterizer was already given a chance to paint the
		// title screen cleanly. engage() is idempotent so a redundant call is fine.
		if (s != GameState.STARTING && !engaged)
		{
			engage();
		}
		// Force a scene rebuild whenever the game transitions back to LOGGED_IN so
		// freshly-loaded zones get uploaded. The actual upload is driven by the
		// client calling loadScene/swapScene; this just makes sure we're listening.
		if (s == GameState.LOADING)
		{
			// Mark all zones as needing rebuild on next swapScene.
			if (root != null)
			{
				for (int x = 0; x < root.sizeX; ++x)
					for (int z = 0; z < root.sizeZ; ++z)
						root.zones[x][z].invalidate = true;
			}
		}
		else if (s == GameState.STARTING)
		{
			// Logout/relog can reload the texture set. Flag the texture array for rebuild;
			// draw() frees + re-inits it once the GL context is current (matches upstream
			// GpuPlugin, which frees it on STARTING). Without this the lazy re-init guard
			// never re-fires and stale textures persist after relog.
			freeTextureArrayPending = true;
		}
	}

	@Override
	protected void shutDown()
	{
		// Clear engage state so the next startUp will re-arm via onGameStateChanged
		// (or via the immediate-engage check if the plugin is toggled back on mid-game).
		engaged = false;

		clientThread.invoke(() -> {
			client.setGpuFlags(0);
			client.setDrawCallbacks(null);
			client.setUnlockedFps(false);
			client.setExpandedMapLoading(0);

			if (glInitted)
			{
				// GL teardown is best-effort: it must NEVER abort the lambda before the
				// client.resizeCanvas() rebuild below, or te (MainBufferProvider) keeps its
				// GPU-era alpha ColorModel and software rendering stays blank after GPU off.
				try
				{
				// Free the largest GL allocation first and reset the id so draw()'s
				// `textureArrayId == -1` re-init guard fires again after a re-enable.
				if (textureArrayId != -1)
				{
					textureManager.freeTextureArray(textureArrayId);
					textureArrayId = -1;
				}
				lastAnisotropicFilteringLevel = -1;

				// Free per-WorldView static-scene zone VBOs/VAOs (root + sub-views).
				// startUp() re-allocates these on the next enable, so null them out.
				if (root != null)
				{
					root.free();
					root = null;
				}
				if (subs != null)
				{
					for (int i = 0; i < subs.length; ++i)
					{
						if (subs[i] != null)
						{
							subs[i].free();
							subs[i] = null;
						}
					}
				}

				shutdownSceneFbo();
				shutdownInterfaceTexture();
				shutdownProgram();
				shutdownVao();

				// Element buffer + the growable transient-geometry VAO pools.
				// bringUpGl() re-creates them on the next enable.
				Zone.freeBuffer();
				for (int i = 0; i < rts.length; ++i) // NOPMD: ForLoopCanBeForeach
				{
					if (rts[i].vaoO != null)
					{
						rts[i].vaoO.free();
						rts[i].vaoO = null;
					}
					if (rts[i].vaoA != null)
					{
						rts[i].vaoA.free();
						rts[i].vaoA = null;
					}
				}

				shutdownBuffers();
				}
				catch (Throwable t)
				{
					Log.e(TAG, "GL teardown failed during shutDown; continuing to buffer-provider rebuild", t);
				}
				glInitted = false;
			}
			// After every object above is freed; hands the window back to EGL
			try { Gfx.stopVulkan(); }
			catch (Throwable t) { Log.e(TAG, "Vulkan shutdown failed", t); }

			// ALWAYS rebuild the buffer provider — even if GL teardown above threw.
			// resizeCanvas() reconstructs the MainBufferProvider with isGpu()==false now,
			// which gives it BOTH the software (no-alpha) ColorModel AND — critically — the
			// software depth buffer (so.ax_fld → Rasterizer3D.az_fld). Without that z-buffer
			// the per-frame backend select (fk.ag) picks the non-writing rasterizer and the
			// software 3D scene is never drawn at all => permanently black game viewport
			// after GPU has been on once. (The alpha ColorModel alone would only blank the
			// present; the missing z-buffer blanks the actual rasterization.)
			Log.i(TAG, "shutDown: rebuilding buffer provider for software path");
			client.resizeCanvas();

			// Flip the host to the software compositing path LAST — only now that a valid
			// software buffer provider (z-buffer + opaque ColorModel) exists. Doing this
			// earlier (before the lambda) let the Compose host react to the flag and trigger
			// an AWT relayout that rebuilt the buffer provider while isGpu() was still true
			// (queued setGpuFlags(0) hadn't run) — leaving a null software z-buffer that
			// nothing self-corrects → the "black only after GPU was on once" bug.
			java.awt.Canvas.setRenderedByGles(false);
			return true;
		});
	}

	@Provides
	GpuGlesPluginConfig provideConfig(ConfigManager cm) { return cm.getConfig(GpuGlesPluginConfig.class); }

	// -----------------------------------------------------------------------
	// GL bring-up — runs once on the first draw() the EGL surface is ready.
	// We can't init eagerly because the Compose SurfaceView may not have
	// produced an Android Surface yet when startUp() runs.
	// -----------------------------------------------------------------------

	private void bringUpGl() throws ShaderException
	{
		if (glInitted) return;
		if (!Gfx.makeCurrent())
		{
			Log.d(TAG, "EGL not ready yet, deferring GL init");
			return;
		}

		Log.i(TAG, "GLES vendor=" + glGetString(GLES20.GL_VENDOR)
			+ " renderer=" + glGetString(GLES20.GL_RENDERER)
			+ " version=" + glGetString(GLES20.GL_VERSION));

		initBuffers();
		initVao();
		initProgram();
		initInterfaceTexture();

		Zone.initBuffer();

		uniformBuffer = new GpuFloatBuffer(UNIFORM_BUFFER_SIZE);
		for (int i = 0; i < rts.length; ++i)
		{
			rts[i].vaoO = new VAOList(i > 0);
			rts[i].vaoA = new VAOList(i > 0);
		}

		glInitted = true;
		glContextGeneration = Gfx.contextGeneration();
		Log.i(TAG, "GLES bring-up complete (context generation " + glContextGeneration + ")");
	}

	/**
	 * The EGL context was lost and a fresh one created — every GL object we held died
	 * with it. Drop our bookkeeping WITHOUT issuing glDelete (those names are invalid in
	 * the new context) so the subsequent bringUpGl() rebuilds the per-context objects and
	 * the lazy scene-FBO / texture-array / zone-geometry paths re-fire. bringUpGl()
	 * re-creates the uniform/UI buffers, VAOs, program, interface texture, element buffer
	 * and the vaoO/A/PO pools; only the lazily-built state below needs resetting here.
	 */
	private void resetGlStateForContextLoss()
	{
		glInitted = false;

		// Texture array + anisotropic level: force the lazy re-init in draw().
		textureArrayId = -1;
		lastAnisotropicFilteringLevel = -1;

		// Scene FBO is lazily (re)created by ensureSceneFbo; zero the handles + cached
		// size so it rebuilds rather than binding a dead framebuffer.
		sceneFbo = sceneResolveFbo = sceneColorTex = sceneColorRbo = sceneDepthRbo = 0;
		sceneFboW = sceneFboH = 0;
		sceneSamples = 0;

		// Static-scene geometry lives in per-zone VBOs/VAOs that are now invalid. Mark
		// every zone dirty so it re-uploads into fresh buffers on the next rebuild.
		invalidateAllZones();
	}

	/** Mark every zone of the toplevel scene and all sub-WorldViews as needing re-upload. */
	private void invalidateAllZones()
	{
		invalidateScene(root);
		if (subs != null)
		{
			for (SceneContext s : subs)
			{
				invalidateScene(s);
			}
		}
	}

	/** Mark every zone of a single scene context as needing re-upload (no-op if null). */
	private static void invalidateScene(SceneContext ctx)
	{
		if (ctx == null) return;
		for (int x = 0; x < ctx.sizeX; ++x)
			for (int z = 0; z < ctx.sizeZ; ++z)
				ctx.zones[x][z].invalidate = true;
	}

	/**
	 * Seed the scene when we engage while already in-game (plugin toggled on mid-session).
	 * The engine only issues loadScene/swapScene on a map rebuild, so without this the GL
	 * scene stays empty (UI only) until the player walks far enough to force a reload.
	 * Mirrors desktop {@code GpuPlugin.startupWorldLoad()} using the port's callbacks, with
	 * the port-specific twist that freshly-created zones default to not-invalidated — so we
	 * explicitly invalidate before swapScene so rebuild() actually uploads them. If the EGL
	 * context isn't up yet, swapScene no-ops but the invalidate flags persist and the next
	 * onPostClientTick flushes them.
	 */
	private void startupWorldLoad()
	{
		WorldView top = client.getTopLevelWorldView();
		if (top == null) return;

		Scene topScene = top.getScene();
		if (topScene != null)
		{
			loadScene(top, topScene);
			invalidateScene(context(topScene));
			swapScene(topScene);
		}

		for (net.runelite.api.WorldEntity we : top.worldEntities())
		{
			WorldView sub = we.getWorldView();
			if (sub == null) continue;
			Scene subScene = sub.getScene();
			if (subScene == null) continue;
			loadScene(sub, subScene);
			invalidateScene(context(subScene));
			swapScene(subScene);
		}
	}

	int getDrawDistance()
	{
		final int limit = MAX_DISTANCE;
		return Math.max(0, Math.min(config.drawDistance(), limit));
	}

	private void initBuffers()
	{
		int[] ids = new int[1];
		Gfx.glGenBuffers(1, ids, 0);
		glUniformBuffer.glBufferId = ids[0];
	}

	private void shutdownBuffers()
	{
		if (glUniformBuffer.glBufferId != 0)
		{
			glDeleteBuffers(1, new int[]{ glUniformBuffer.glBufferId }, 0);
			glUniformBuffer.glBufferId = 0;
		}
	}

	private void initVao()
	{
		int[] arr = new int[1];

		glGenVertexArrays(1, arr, 0);
		vaoUiHandle = arr[0];

		Gfx.glGenBuffers(1, arr, 0);
		vboUiHandle = arr[0];

		glBindVertexArray(vaoUiHandle);
		glBindBuffer(GL_ARRAY_BUFFER, vboUiHandle);

		// Fullscreen quad as two triangles — 6 verts × (vec3 pos + vec2 uv). A triangle list
		// rather than a fan, since Vulkan has no fans.
		FloatBuffer vboBuf = ByteBuffer.allocateDirect(5 * UI_QUAD_VERTS * Float.BYTES)
			.order(ByteOrder.nativeOrder()).asFloatBuffer();
		vboBuf.put(new float[] {
			 1f,  1f, 0f, 1f, 0f, // top right
			 1f, -1f, 0f, 1f, 1f, // bottom right
			-1f, -1f, 0f, 0f, 1f, // bottom left
			 1f,  1f, 0f, 1f, 0f, // top right
			-1f, -1f, 0f, 0f, 1f, // bottom left
			-1f,  1f, 0f, 0f, 0f, // top left
		});
		vboBuf.rewind();
		glBufferData(GL_ARRAY_BUFFER, 5 * UI_QUAD_VERTS * Float.BYTES, vboBuf, GL_STATIC_DRAW);

		glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
		glEnableVertexAttribArray(1);

		glBindVertexArray(0);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	private void shutdownVao()
	{
		if (vboUiHandle != 0) glDeleteBuffers(1, new int[]{ vboUiHandle }, 0);
		if (vaoUiHandle != 0) glDeleteVertexArrays(1, new int[]{ vaoUiHandle }, 0);
		vboUiHandle = vaoUiHandle = 0;
	}

	private void initProgram() throws ShaderException
	{
		// Some GL implementations require a VAO bound before any program validates,
		// so bind the UI VAO during compile — matches the desktop plugin's quirk.
		glBindVertexArray(vaoUiHandle);

		GlesTemplate template = createTemplate();
		glProgram = PROGRAM.compile(template);
		glUiProgram = UI_PROGRAM.compile(template);

		glBindVertexArray(0);

		initUniforms();
	}

	private GlesTemplate createTemplate()
	{
		// Inline tokens — `#include foo` (no quotes) maps to one of these constants;
		// `#include "foo.glsl"` resolves through the classloader.
		GlesTemplate t = new GlesTemplate();
		t.add(key -> {
			switch (key)
			{
				case "texture_config":  return "#define TEXTURE_COUNT 256\n";
				case "sampling_mode":   return "#define SAMPLING_MODE 1\n"; // LINEAR
				case "colorblind_mode": return "#define COLORBLIND_MODE 0\n";
				default: return null;
			}
		});
		t.addInclude(GpuGlesPlugin.class);
		return t;
	}

	private void initUniforms()
	{
		uniWorldProj = glGetUniformLocation(glProgram, "worldProj");
		uniEntityProj = glGetUniformLocation(glProgram, "entityProj");
		uniEntityTint = glGetUniformLocation(glProgram, "entityTint");
		uniSmoothBanding = glGetUniformLocation(glProgram, "smoothBanding");
		uniBrightness = glGetUniformLocation(glProgram, "brightness");
		uniUseFog = glGetUniformLocation(glProgram, "useFog");
		uniFogColor = glGetUniformLocation(glProgram, "fogColor");
		uniFogDepth = glGetUniformLocation(glProgram, "fogDepth");
		uniDrawDistance = glGetUniformLocation(glProgram, "drawDistance");
		uniExpandedMapLoadingChunks = glGetUniformLocation(glProgram, "expandedMapLoadingChunks");
		uniTextureLightMode = glGetUniformLocation(glProgram, "textureLightMode");
		uniTick = glGetUniformLocation(glProgram, "tick");
		uniBlockMain = Gfx.glGetUniformBlockIndex(glProgram, "uniforms");
		uniTextures = glGetUniformLocation(glProgram, "textures");
		uniTextureAnimations = glGetUniformLocation(glProgram, "textureAnimations");
		uniBase = glGetUniformLocation(glProgram, "base");
		uniColorblindIntensity = glGetUniformLocation(glProgram, "colorblindIntensity");

		uniTex = glGetUniformLocation(glUiProgram, "tex");
		uniTexTargetDimensions = glGetUniformLocation(glUiProgram, "targetDimensions");
		uniTexSourceDimensions = glGetUniformLocation(glUiProgram, "sourceDimensions");
		uniUiAlphaOverlay = glGetUniformLocation(glUiProgram, "alphaOverlay");
		uniUiColorblindIntensity = glGetUniformLocation(glUiProgram, "colorblindIntensity");
		uniUiSceneTex = glGetUniformLocation(glUiProgram, "sceneTex");
		uniUiHasScene = glGetUniformLocation(glUiProgram, "hasScene");
	}

	private void shutdownProgram()
	{
		if (glProgram != 0)   glDeleteProgram(glProgram);
		if (glUiProgram != 0) glDeleteProgram(glUiProgram);
		glProgram = glUiProgram = 0;
	}

	private void initInterfaceTexture()
	{
		int[] arr = new int[1];
		Gfx.glGenBuffers(1, arr, 0);
		interfacePbo = arr[0];

		glGenTextures(1, arr, 0);
		interfaceTexture = arr[0];
		// A new texture has no storage yet; make prepareInterfaceTexture allocate it even when
		// the canvas is the size it was before (a renderer restart, e.g. switching to Vulkan)
		lastCanvasWidth = lastCanvasHeight = -1;
		glBindTexture(GL_TEXTURE_2D, interfaceTexture);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glBindTexture(GL_TEXTURE_2D, 0);
	}

	private void shutdownInterfaceTexture()
	{
		if (interfacePbo != 0)     glDeleteBuffers(1, new int[]{ interfacePbo }, 0);
		if (interfaceTexture != 0) glDeleteTextures(1, new int[]{ interfaceTexture }, 0);
		interfacePbo = interfaceTexture = 0;
	}

	/**
	 * (Re)allocate the scene FBO + its colour/depth attachments so they match the
	 * requested scaled dimensions. No-op if size hasn't changed. Called from
	 * preSceneDrawToplevel each frame — cheap when stable.
	 */
	private boolean ensureSceneFbo(int targetW, int targetH)
	{
		if (targetW <= 0 || targetH <= 0) return false;

		// Resolve the requested MSAA level against what the driver supports. Desktop GpuPlugin
		// puts the scene in a multisample renderbuffer; the EGL surface's own MSAA can't help us
		// here because the scene is composited via glBlitFramebuffer out of this offscreen FBO.
		int wantSamples = config.msaaSamples().samples;
		if (wantSamples >= 2)
		{
			int[] maxS = new int[1];
			Gfx.glGetIntegerv(GLES30.GL_MAX_SAMPLES, maxS, 0);
			wantSamples = Math.min(wantSamples, Math.max(1, maxS[0]));
			if (wantSamples < 2) wantSamples = 0;
		}
		else
		{
			wantSamples = 0;
		}

		// Reuse iff both the size and the sample count still match.
		if (sceneFbo != 0 && targetW == sceneFboW && targetH == sceneFboH && wantSamples == sceneSamples)
		{
			return true;
		}

		// Tear down anything stale before re-creating.
		shutdownSceneFbo();

		if (Gfx.vulkan)
		{
			return createSceneTargetsVk(targetW, targetH, wantSamples);
		}

		int[] arr = new int[1];

		// Single-sample resolve/present texture. In the non-MSAA path it is the scene FBO's
		// own color attachment; in the MSAA path it is the resolve target sampled by the
		// upscale blit. LINEAR so the blit-back to the default FB gives a smooth upscale
		// rather than hard nearest-neighbour blockiness when scale < 1.
		glGenTextures(1, arr, 0);
		sceneColorTex = arr[0];
		glBindTexture(GL_TEXTURE_2D, sceneColorTex);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, targetW, targetH, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glBindTexture(GL_TEXTURE_2D, 0);

		GLES30.glGenFramebuffers(1, arr, 0);
		sceneFbo = arr[0];

		GLES30.glGenRenderbuffers(1, arr, 0);
		sceneDepthRbo = arr[0];
		GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, sceneDepthRbo);
		if (wantSamples >= 2)
		{
			GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, wantSamples,
				GLES30.GL_DEPTH_COMPONENT24, targetW, targetH);
		}
		else
		{
			GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, targetW, targetH);
		}
		GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0);

		Gfx.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFbo);
		if (wantSamples >= 2)
		{
			// Multisample color renderbuffer (resolved into sceneColorTex below).
			GLES30.glGenRenderbuffers(1, arr, 0);
			sceneColorRbo = arr[0];
			GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, sceneColorRbo);
			GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, wantSamples,
				GLES30.GL_RGBA8, targetW, targetH);
			GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0);
			GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
				GLES30.GL_RENDERBUFFER, sceneColorRbo);
		}
		else
		{
			GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
				GL_TEXTURE_2D, sceneColorTex, 0);
		}
		GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
			GLES30.GL_RENDERBUFFER, sceneDepthRbo);

		int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
		Gfx.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

		if (status != GLES30.GL_FRAMEBUFFER_COMPLETE)
		{
			Log.e(TAG, "scene FBO incomplete: 0x" + Integer.toHexString(status));
			shutdownSceneFbo();
			return false;
		}

		// MSAA path needs a second, single-sample FBO whose color attachment is sceneColorTex,
		// so the same-size resolve blit (multisample sceneFbo -> sceneResolveFbo) lands in a
		// texture the scaled upscale blit can read.
		if (wantSamples >= 2)
		{
			GLES30.glGenFramebuffers(1, arr, 0);
			sceneResolveFbo = arr[0];
			Gfx.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneResolveFbo);
			GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
				GL_TEXTURE_2D, sceneColorTex, 0);
			int rstatus = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
			Gfx.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
			if (rstatus != GLES30.GL_FRAMEBUFFER_COMPLETE)
			{
				Log.e(TAG, "scene resolve FBO incomplete: 0x" + Integer.toHexString(rstatus));
				shutdownSceneFbo();
				return false;
			}
		}

		sceneFboW = targetW;
		sceneFboH = targetH;
		sceneSamples = wantSamples;
		Log.i(TAG, "scene FBO (re)created " + targetW + "x" + targetH
			+ (wantSamples >= 2 ? " MSAA x" + wantSamples : " (no MSAA)"));
		return true;
	}

	/**
	 * Vulkan scene targets. The multisample color and the depth only ever live in tile memory
	 * (transient attachments); MSAA resolves into the sampled image inside the render pass, so
	 * there is no resolve framebuffer or blit, and sceneResolveFbo stays 0.
	 */
	private boolean createSceneTargetsVk(int w, int h, int samples)
	{
		int s = Math.max(1, samples);
		int color = VkNative.IMG_COLOR_ATTACHMENT;
		int sampled;
		if (s > 1)
		{
			vkSceneColor = Vk.imageCreate(VkNative.IMG_2D, GLES30.GL_RGBA8, w, h, 1, 1, s, color | VkNative.IMG_TRANSIENT);
			vkSceneResolve = Vk.imageCreate(VkNative.IMG_2D, GLES30.GL_RGBA8, w, h, 1, 1, 1, color | VkNative.IMG_SAMPLED);
			sampled = vkSceneResolve;
		}
		else
		{
			vkSceneColor = Vk.imageCreate(VkNative.IMG_2D, GLES30.GL_RGBA8, w, h, 1, 1, 1, color | VkNative.IMG_SAMPLED);
			vkSceneResolve = 0;
			sampled = vkSceneColor;
		}
		vkSceneDepth = Vk.imageCreate(VkNative.IMG_2D, GLES30.GL_DEPTH_COMPONENT24, w, h, 1, 1, s,
			VkNative.IMG_DEPTH_ATTACHMENT | VkNative.IMG_TRANSIENT);
		sceneFbo = Vk.framebufferCreate(vkSceneColor, 0, vkSceneDepth, vkSceneResolve);
		if (sceneFbo == 0 || sampled == 0)
		{
			Log.e(TAG, "Vulkan scene targets " + w + "x" + h + " (MSAA " + s + ") could not be created");
			shutdownSceneFbo();
			return false;
		}
		sceneColorTex = Gfx.wrapImage(sampled, w, h);
		sceneFboW = w;
		sceneFboH = h;
		sceneSamples = samples;
		Log.i(TAG, "Vulkan scene targets (re)created " + w + "x" + h + (s > 1 ? " MSAA x" + s : " (no MSAA)"));
		return true;
	}

	private int vkSceneColor, vkSceneResolve, vkSceneDepth;

	private void shutdownSceneFbo()
	{
		if (Gfx.vulkan)
		{
			// The scene texture id only wraps vkSceneColor/vkSceneResolve; deleting it frees nothing
			if (sceneColorTex != 0) Gfx.glDeleteTextures(1, new int[]{ sceneColorTex }, 0);
			Vk.framebufferDestroy(sceneFbo);
			Vk.imageDestroy(vkSceneColor);
			Vk.imageDestroy(vkSceneResolve);
			Vk.imageDestroy(vkSceneDepth);
			vkSceneColor = vkSceneResolve = vkSceneDepth = 0;
			sceneFbo = sceneResolveFbo = sceneColorTex = sceneColorRbo = sceneDepthRbo = 0;
			sceneFboW = sceneFboH = 0;
			sceneSamples = 0;
			return;
		}
		if (sceneFbo != 0)        GLES30.glDeleteFramebuffers(1, new int[]{ sceneFbo }, 0);
		if (sceneResolveFbo != 0) GLES30.glDeleteFramebuffers(1, new int[]{ sceneResolveFbo }, 0);
		if (sceneColorTex != 0)   glDeleteTextures(1, new int[]{ sceneColorTex }, 0);
		if (sceneColorRbo != 0)   GLES30.glDeleteRenderbuffers(1, new int[]{ sceneColorRbo }, 0);
		if (sceneDepthRbo != 0)   GLES30.glDeleteRenderbuffers(1, new int[]{ sceneDepthRbo }, 0);
		sceneFbo = sceneResolveFbo = sceneColorTex = sceneColorRbo = sceneDepthRbo = 0;
		sceneFboW = sceneFboH = 0;
		sceneSamples = 0;
	}

	// -----------------------------------------------------------------------
	// DrawCallbacks — invoked by the patched client. Only `draw(int)` and
	// `swapScene` are non-default in the interface. The other zone/scene/dynamic
	// callbacks will be filled in when the scene-rendering pass lands.
	// -----------------------------------------------------------------------

	@Override
	public void draw(int overlayColor)
	{
		GameState gameState = client.getGameState();
		if (gameState == GameState.STARTING) return;

		// EGL is created lazily — first draw() after the SurfaceView is alive
		// brings the context up.
		if (!glInitted)
		{
			try { bringUpGl(); }
			catch (Throwable t) { Log.e(TAG, "deferred GL init failed", t); return; }
			if (!glInitted) return;
		}
		else
		{
			if (!Gfx.makeCurrent())
			{
				return;
			}
			// Dormant in steady state (generation only moves on an actual context loss).
			// If the EGL context was lost and rebuilt — app backgrounded, device rotated,
			// GPU reset — every GL object we hold is invalid; rebuild before drawing.
			if (Gfx.contextGeneration() != glContextGeneration)
			{
				Log.w(TAG, "EGL context changed (generation " + glContextGeneration + " -> "
					+ Gfx.contextGeneration() + "); rebuilding GL state");
				resetGlStateForContextLoss();
				try { bringUpGl(); }
				catch (Throwable t) { Log.e(TAG, "GL re-init after context loss failed", t); return; }
				if (!glInitted) return;
			}
		}
		Gfx.attachSurface();

		// A STARTING transition (logout/relog) flagged the texture array for rebuild;
		// the GL context is current now, so free it here and let the lazy init below
		// re-fire with the freshly-loaded textures.
		if (freeTextureArrayPending)
		{
			freeTextureArrayPending = false;
			if (textureArrayId != -1)
			{
				textureManager.freeTextureArray(textureArrayId);
				textureArrayId = -1;
				lastAnisotropicFilteringLevel = -1;
			}
		}

		// Lazy texture-array init — textures load asynchronously, so retry
		// each frame until initTextureArray returns a real id.
		TextureProvider tp = client.getTextureProvider();
		if (textureArrayId == -1 && tp != null)
		{
			textureArrayId = textureManager.initTextureArray(tp);
			if (textureArrayId > -1)
			{
				float[] texAnims = textureManager.computeTextureAnimations(tp);
				glUseProgram(glProgram);
				Gfx.glUniform2fv(uniTextureAnimations, texAnims.length / 2, texAnims, 0);
				glUseProgram(0);
			}
		}

		int canvasWidth = client.getCanvasWidth();
		int canvasHeight = client.getCanvasHeight();
		if (canvasWidth <= 0 || canvasHeight <= 0) return;

		prepareInterfaceTexture(canvasWidth, canvasHeight);

		// Present. The scene lives in sceneFbo at surface * resolutionScale; the UI lives in
		// interfaceTexture at canvas size. Both are handed to the UI program, which upscales
		// the scene and composites the UI over it in a single fullscreen pass.
		//
		// This used to be a glBlitFramebuffer upscale into the default framebuffer followed by
		// a blended UI quad. On a tile-based GPU that is two full-screen passes: the blit writes
		// the whole framebuffer out to memory, and the blended quad then has to load all of it
		// back in to have a destination to blend against. Doing the composite in the shader
		// removes one full-screen write and one full-screen read per frame, and lets the UI
		// pass run with blending off so its tile buffer never needs loading at all.

		// Tile-based GPUs write every attachment back to main memory when a render pass ends,
		// and the scene's depth buffer is dead the moment the scene is done — only colour is
		// read from here on. Discarding it while the scene FBO is still the bound draw target
		// skips that writeback: ~8 MB per frame at 1080p, four times that with 4x MSAA. Adreno,
		// Mali and PowerVR all honour this; a driver that ignores it just does what it did before.
		if (scenePaintedThisFrame && sceneFbo != 0)
		{
			Gfx.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, INVALIDATE_DEPTH, 0);
		}

		// SCENE_* mirror the fragui `hasScene` uniform.
		int sceneMode = SCENE_NONE;
		if (scenePaintedThisFrame)
		{
			if (sceneFbo != 0 && sceneColorTex != 0)
			{
				sceneMode = SCENE_TEXTURE;
				// MSAA still needs its own resolve: a multisample renderbuffer can't be sampled,
				// so blit it same-size into sceneColorTex first. Non-MSAA renders straight into
				// sceneColorTex, so there is nothing to do.
				if (sceneSamples >= 2 && sceneResolveFbo != 0)
				{
					Gfx.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, sceneFbo);
					Gfx.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, sceneResolveFbo);
					Gfx.glBlitFramebuffer(
						0, 0, sceneFboW, sceneFboH,
						0, 0, sceneFboW, sceneFboH,
						GL_COLOR_BUFFER_BIT, GLES20.GL_NEAREST);
					// The multisample colour has been resolved; nothing reads it again.
					Gfx.glInvalidateFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 1, INVALIDATE_COLOR, 0);
				}
			}
			else
			{
				// ensureSceneFbo failed earlier, so preSceneDrawToplevel fell back to rendering
				// the scene directly into the default framebuffer. It is already where it needs
				// to be — blend the UI over it instead of compositing a texture.
				sceneMode = SCENE_DEFAULT_FB;
			}
		}
		scenePaintedThisFrame = false;

		Gfx.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

		drawUi(overlayColor, canvasWidth, canvasHeight, sceneMode);

		if (!Gfx.swapBuffers())
		{
			Log.e(TAG, "Vulkan device lost; restarting the GPU plugin");
			clientThread.invokeLater(this::restartRenderer);
		}
	}

	/** Tear the renderer down and bring it back up, e.g. to switch between GLES and Vulkan. */
	private void restartRenderer()
	{
		shutDown();
		startUp();
	}

	@Override
	public void swapScene(Scene scene)
	{
		// Rebuild any zones the client has marked dirty since the last swap.
		// This is where SceneUploader actually walks tile/object geometry and
		// fills a VBO with per-vertex (pos, abhsl, uv) — the GL work that makes
		// drawZoneOpaque/Alpha later actually have data to draw.
		if (!glInitted) return;
		if (!Gfx.makeCurrent()) return;
		rebuild(scene);
	}

	private void rebuild(Scene scene)
	{
		SceneContext ctx = context(scene);
		if (ctx == null) return;

		// Clamp to the actual scene tile-array dims. The root SceneContext is sized to
		// NUM_ZONES (23, sized for an EXTENDED 184-tile toplevel scene), but the
		// meteor-mp client serves instance toplevel scenes as plain 104-tile arrays.
		// Iterating to NUM_ZONES would AIOOBE inside SceneUploader.zoneSize for every
		// zone past index 12 — the surrounding try/catch keeps the render thread
		// alive but leaves those zones un-built, so all static geometry past x=12
		// (and any dynamic entity hosted on top of it, e.g. the Giant Gemstone Crab)
		// has no zone backing and never renders.
		final Tile[][][] tiles = scene.getExtendedTiles();
		final int maxX = Math.min(ctx.sizeX, tiles[0].length >> 3);
		final int maxZ = Math.min(ctx.sizeZ, tiles[0][0].length >> 3);

		for (int x = 0; x < maxX; ++x)
		{
			for (int z = 0; z < maxZ; ++z)
			{
				Zone zone = ctx.zones[x][z];
				if (!zone.invalidate) continue;

				// Defense in depth: an exception inside one zone's upload (oversize
				// model, GL buffer alloc failure, anything) used to kill the entire
				// rebuild loop — every remaining zone in the scene was then left
				// invalidated-but-unbuilt until the next swapScene. A user reported
				// "crash on region reload"; this catch keeps the rest of the scene
				// building so one bad object can only blank one zone.
				try
				{
					rebuildOneZone(scene, ctx, x, z);
				}
				catch (Throwable t)
				{
					Log.w(TAG, "zone rebuild failed at (" + x + "," + z + "): " + t, t);
					// Re-arm invalidate so a subsequent swapScene can retry; the
					// zone slot now contains whatever partial state we left it in,
					// but free() at the top of the next attempt will clean up.
					ctx.zones[x][z].invalidate = true;
				}
			}
		}
	}

	private void rebuildOneZone(Scene scene, SceneContext ctx, int x, int z)
	{
		Zone zone = ctx.zones[x][z];
		zone.free();
		zone = ctx.zones[x][z] = new Zone();

		clientUploader.zoneSize(scene, zone, x, z);

		VBO o = null, a = null;
		int sz = zone.sizeO * Zone.VERT_SIZE * 3;
		if (sz > 0)
		{
			o = new VBO(sz);
			o.init(GLES20.GL_STATIC_DRAW);
			o.map();
		}

		sz = zone.sizeA * Zone.VERT_SIZE * 3;
		if (sz > 0)
		{
			a = new VBO(sz);
			a.init(GLES20.GL_STATIC_DRAW);
			a.map();
		}

		zone.init(o, a);

		clientUploader.uploadZone(scene, zone, x, z);

		zone.unmap();
		zone.initialized = true;
		zone.invalidate = false;
		zone.dirty = true;
	}

	@Override
	public void loadScene(Scene scene)
	{
		// Hook for scene-load notifications. Zone (re)builds happen lazily in
		// preSceneDraw via the invalidate flag.
		SceneContext ctx = context(scene);
		if (ctx == null) return;
		final Tile[][][] tiles = scene.getExtendedTiles();
		final int maxX = Math.min(ctx.sizeX, tiles[0].length >> 3);
		final int maxZ = Math.min(ctx.sizeZ, tiles[0][0].length >> 3);
		for (int x = 0; x < maxX; ++x)
			for (int z = 0; z < maxZ; ++z)
				ctx.zones[x][z].invalidate = true;
	}

	@Subscribe
	public void onWorldEntitySpawned(net.runelite.api.events.WorldEntitySpawned ev)
	{
		net.runelite.api.WorldEntity we = ev.getWorldEntity();
		net.runelite.api.WorldView wv = we.getWorldView();
		Log.i(TAG, "WorldEntitySpawned id=" + (we != null ? "yes" : "null")
			+ " wv=" + (wv != null ? wv.getId() : "null")
			+ " wvSizeX=" + (wv != null ? wv.getSizeX() : -1)
			+ " wvSizeY=" + (wv != null ? wv.getSizeY() : -1));
	}

	@Subscribe
	public void onWorldEntityDespawned(net.runelite.api.events.WorldEntityDespawned ev)
	{
		Log.i(TAG, "WorldEntityDespawned");
	}

	@Subscribe
	public void onWorldViewLoaded(net.runelite.api.events.WorldViewLoaded ev)
	{
		net.runelite.api.WorldView wv = ev.getWorldView();
		Log.i(TAG, "WorldViewLoaded id=" + (wv != null ? wv.getId() : "null")
			+ " sizeX=" + (wv != null ? wv.getSizeX() : -1)
			+ " sizeY=" + (wv != null ? wv.getSizeY() : -1));
	}

	@Subscribe
	public void onWorldViewUnloaded(net.runelite.api.events.WorldViewUnloaded ev)
	{
		Log.i(TAG, "WorldViewUnloaded");
	}

	@Override
	public void loadScene(WorldView wv, Scene scene)
	{
		int id = wv.getId();
		Log.i(TAG, "loadScene(WV) id=" + id + " sizeX=" + wv.getSizeX() + " sizeY=" + wv.getSizeY()
			+ " sceneWvId=" + scene.getWorldViewId());
		if (id != WorldView.TOPLEVEL && subs[id] == null)
		{
			subs[id] = new SceneContext(wv.getSizeX() >> 3, wv.getSizeY() >> 3);
		}
		loadScene(scene);
	}

	/**
	 * Engine callback fired when an object inside a zone changes state — gate opens,
	 * door swings, lever flips, plant grows, etc. Without this hook, the static zone
	 * VBO holds the pose captured at the last scene load forever, so a gate the player
	 * just opened still renders closed even though the game's interaction layer treats
	 * it as open. Mirrors GpuPlugin.invalidateZone — flagging the zone here, with the
	 * actual rebuild deferred to {@link #onPostClientTick} so a tick that invalidates
	 * many zones at once doesn't stall the render thread mid-frame.
	 */
	@Override
	public void invalidateZone(Scene scene, int zx, int zz)
	{
		SceneContext ctx = context(scene);
		if (ctx == null) return;
		if (zx < 0 || zz < 0 || zx >= ctx.sizeX || zz >= ctx.sizeZ) return;
		Zone z = ctx.zones[zx][zz];
		if (!z.invalidate)
		{
			z.invalidate = true;
		}
	}

	/**
	 * After every client tick the engine settles all the per-tick state (animations,
	 * gate openings, etc.) and fires {@link net.runelite.api.events.PostClientTick}.
	 * That's our window to flush any zones flagged dirty by {@link #invalidateZone}
	 * during the tick — once we hit GL on the client thread, the rebuilt VBO replaces
	 * the stale pose and the next frame paints the object in its current state. Doing
	 * this here (instead of inside the draw callback) keeps the rebuild off the hot
	 * draw path, matching how GpuPlugin handles it on desktop.
	 */
	@Subscribe
	public void onPostClientTick(net.runelite.api.events.PostClientTick ev)
	{
		if (!glInitted) return;
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null) return;
		if (!Gfx.makeCurrent()) return;
		rebuildDirty(wv.getScene());
		for (net.runelite.api.WorldEntity we : wv.worldEntities())
		{
			WorldView sub = we.getWorldView();
			if (sub != null) rebuildDirty(sub.getScene());
		}
	}

	private void rebuildDirty(Scene scene)
	{
		SceneContext ctx = context(scene);
		if (ctx == null) return;
		// Cheap fast path: bail out of the EGL/GL section entirely when nothing changed
		// this tick. Clamped to the scene's real tile dims so we don't pick up stale
		// invalidate=true on zones beyond the scene bounds (otherwise we'd trip the
		// dirty check every tick and re-enter rebuild() which would just skip them
		// again — wasted GL section every tick).
		final Tile[][][] tiles = scene.getExtendedTiles();
		final int maxX = Math.min(ctx.sizeX, tiles[0].length >> 3);
		final int maxZ = Math.min(ctx.sizeZ, tiles[0][0].length >> 3);
		boolean anyDirty = false;
		outer:
		for (int x = 0; x < maxX; ++x)
		{
			for (int z = 0; z < maxZ; ++z)
			{
				if (ctx.zones[x][z].invalidate) { anyDirty = true; break outer; }
			}
		}
		if (!anyDirty) return;
		rebuild(scene);
	}

	@Override
	public void despawnWorldView(WorldView wv)
	{
		int id = wv.getId();
		if (id != WorldView.TOPLEVEL && subs[id] != null)
		{
			subs[id].free();
			subs[id] = null;
		}
	}

	@Override
	public void preSceneDraw(Scene scene, Projection entityProjection,
		float cameraX, float cameraY, float cameraZ, float cameraPitch, float cameraYaw,
		int minLevel, int level, int maxLevel, java.util.Set<Integer> hideRoofIds)
	{
		// The GL objects these scene callbacks use — chiefly the vaoO/A/PO pools — only
		// exist while glInitted. The engine can still fire them before bring-up finishes,
		// or after shutDown()/a context-loss reset has nulled the pools, so every
		// GL-touching scene callback must bail when GL isn't up (else NPE in drawTemp etc).
		if (!glInitted) return;

		SceneContext ctx = context(scene);
		if (ctx != null)
		{
			ctx.cameraX = (int) cameraX;
			ctx.cameraY = (int) cameraY;
			ctx.cameraZ = (int) cameraZ;
			ctx.minLevel = minLevel;
			ctx.level = level;
			ctx.maxLevel = maxLevel;
			ctx.hideRoofIds = hideRoofIds;
		}

		if (scene.getWorldViewId() == WorldView.TOPLEVEL)
		{
			// Map every pool up front. Worker render threads can't map (that's a GL call), so
			// their buffers have to already be writable by the time drawDynamic reaches them —
			// and this is also where an rt pool that ran out of room last frame grows.
			for (int i = 0; i < rts.length; ++i) // NOPMD: ForLoopCanBeForeach
			{
				rts[i].vaoO.map();
				rts[i].vaoA.map();
			}

			this.cameraYaw = client.getCameraYaw();
			this.cameraPitch = client.getCameraPitch();
			preSceneDrawToplevel(scene, cameraX, cameraY, cameraZ, cameraPitch, cameraYaw);
			scenePaintedThisFrame = true;
		}
		else
		{
			if (DEBUG) Log.i(TAG, "preSceneDraw SUB-WV wvId=" + scene.getWorldViewId() + " ctxNull=" + (ctx == null)
				+ " entProj=" + (entityProjection != null ? entityProjection.getClass().getSimpleName() : "null"));
			if (ctx != null)
			{
				// Capture the WorldEntity's entity-to-world matrix so drawDynamic/drawTemp
				// can seal each sub-WV entity's VAO range under it, drawZone* sees the
				// right uniEntityProj for static alpha, and PRE_PASS_ALPHA can re-arm it
				// before static alpha geometry draws.
				if (entityProjection instanceof FloatProjection)
				{
					System.arraycopy(((FloatProjection) entityProjection).getProjection(), 0, ctx.projection, 0, 16);
				}
				else
				{
					System.arraycopy(IDENTITY, 0, ctx.projection, 0, 16);
				}
				glUniformMatrix4fv(uniEntityProj, 1, false, ctx.projection, 0);
				lastProjection = entityProjection;
				glUniform4i(uniEntityTint, scene.getOverrideHue(), scene.getOverrideSaturation(),
					scene.getOverrideLuminance(), scene.getOverrideAmount());
			}
		}
	}

	private void preSceneDrawToplevel(Scene scene,
		float cameraX, float cameraY, float cameraZ, float cameraPitch, float cameraYaw)
	{
		scene.setDrawDistance(getDrawDistance());

		// UBO update — camera position + angles. The scene vertex shader reads
		// these from std140 binding 0.
		uniformBuffer.clear();
		uniformBuffer.put(cameraYaw).put(cameraPitch).put(cameraX).put(cameraY).put(cameraZ);
		uniformBuffer.flip();

		Gfx.glBindBuffer(GL_UNIFORM_BUFFER, glUniformBuffer.glBufferId);
		Gfx.glBufferData(GL_UNIFORM_BUFFER, UNIFORM_BUFFER_SIZE, uniformBuffer.getBuffer(), GLES20.GL_DYNAMIC_DRAW);
		Gfx.glBindBuffer(GL_UNIFORM_BUFFER, 0);
		uniformBuffer.clear();

		glBindBufferBase(GL_UNIFORM_BUFFER, 0, glUniformBuffer.glBufferId);

		// Viewport — render to the scene FBO, sized at (surface * resolutionScale).
		// The default framebuffer (EGL surface) stays at native canvas resolution so the
		// UI compositing later in draw() can hit native pixels exactly. Picking the FBO
		// size here also gates whether we have a usable FBO at all — if allocation fails
		// we skip the scene draw rather than render to the default FB at scaled coords
		// (which would tile the scene into the wrong screen region).
		int viewportWidth = client.getViewportWidth();
		int viewportHeight = client.getViewportHeight();
		int canvasHeight = client.getCanvasHeight();
		int viewportXOff = client.getViewportXOffset();
		int viewportYOff = client.getViewportYOffset();

		int surfW = GlesHost.get().getWidth();
		int surfH = GlesHost.get().getHeight();
		float resScale = GlesHost.get().getResolutionScale();
		int targetFboW = Math.max(1, Math.round(surfW * resScale));
		int targetFboH = Math.max(1, Math.round(surfH * resScale));
		if (!ensureSceneFbo(targetFboW, targetFboH))
		{
			// FBO unavailable — fall back to the default framebuffer so we at least
			// render something this frame, even if the scaling slider has no effect.
			Gfx.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
		}
		else
		{
			Gfx.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFbo);
		}

		// Map the client-relative viewport into FBO space. The FBO covers the full
		// canvas at scaled size, so the conversion is canvas-pixel * (fbo/canvas).
		// Floor the left/top edge and ceil the right/bottom edge so the FBO viewport
		// always *fully covers* the canvas-viewport region after glBlitFramebuffer's
		// linear upscale to the default framebuffer. Otherwise integer truncation can
		// leave a 1–2px sliver of FBO-clear pixels (skybox blue) inside the canvas
		// viewport rect on fractional scales — visible as a thin column on the right
		// edge of the scene.
		float sx = sceneFboW > 0 ? (float) sceneFboW / client.getCanvasWidth() : 1f;
		float sy = sceneFboH > 0 ? (float) sceneFboH / client.getCanvasHeight() : 1f;
		int vx = (int) Math.floor(viewportXOff * sx);
		int vy = (int) Math.floor((canvasHeight - viewportHeight - viewportYOff) * sy);
		int rightFbo = (int) Math.ceil((viewportXOff + viewportWidth) * sx);
		int topFbo = (int) Math.ceil((canvasHeight - viewportYOff) * sy);
		int vw = Math.max(1, rightFbo - vx);
		int vh = Math.max(1, topFbo - vy);
		Gfx.glViewport(vx, vy, vw, vh);

		// Sky colour is read here but the clear itself happens in drawSkybox() at the end
		// of this method — a model skybox has to be drawn with the scene program bound and
		// all its uniforms already set.
		int sky = client.getSkyboxColor();

		// Anisotropic filter — only apply when the texture array exists.
		final int anisotropic = config.anisotropicFilteringLevel();
		if (textureArrayId != -1 && lastAnisotropicFilteringLevel != anisotropic)
		{
			textureManager.setAnisotropicFilteringLevel(textureArrayId, anisotropic);
			lastAnisotropicFilteringLevel = anisotropic;
		}

		glUseProgram(glProgram);

		// Scalar uniforms.
		final int drawDistance = getDrawDistance();
		final int fogDepth = config.fogDepth();
		glUniform1i(uniUseFog, fogDepth > 0 ? 1 : 0);
		glUniform4f(uniFogColor, ((sky >> 16) & 0xFF) / 255f, ((sky >> 8) & 0xFF) / 255f, (sky & 0xFF) / 255f, 1f);
		glUniform1i(uniFogDepth, fogDepth);
		glUniform1i(uniDrawDistance, drawDistance * Perspective.LOCAL_TILE_SIZE);
		glUniform1i(uniExpandedMapLoadingChunks, client.getExpandedMapLoading());
		glUniform1f(uniColorblindIntensity, config.colorBlindIntensity());

		TextureProvider textureProvider = client.getTextureProvider();
		glUniform1f(uniBrightness, textureProvider != null ? (float) textureProvider.getBrightness() : 0.5f);
		glUniform1f(uniSmoothBanding, config.smoothBanding() ? 0f : 1f);
		glUniform1f(uniTextureLightMode, config.brightTextures() ? 1f : 0f);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			glUniform1i(uniTick, client.getGameCycle() & 127);
		}

		// World projection matrix (column-major, glUniformMatrix4fv pushes as-is).
		float[] proj = Mat4.scale(client.getScale(), client.getScale(), 1);
		Mat4.mul(proj, Mat4.projection(viewportWidth, viewportHeight, 50));
		Mat4.mul(proj, Mat4.rotateX(cameraPitch));
		Mat4.mul(proj, Mat4.rotateY(cameraYaw));
		Mat4.mul(proj, Mat4.translate(-cameraX, -cameraY, -cameraZ));
		glUniformMatrix4fv(uniWorldProj, 1, false, proj, 0);

		glUniformMatrix4fv(uniEntityProj, 1, false, IDENTITY, 0);
		lastProjection = null;

		glUniform4i(uniEntityTint, 0, 0, 0, 0);

		glUniformBlockBinding(glProgram, uniBlockMain, 0);
		glUniform1i(uniTextures, 1); // sampler array bound to texture unit 1

		glEnable(GL_CULL_FACE);
		glEnable(GL_BLEND);
		glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE);

		// Standard depth convention: GL_LESS + clear=1.0. Mat4.projection now
		// produces z_ndc ∈ [-1, 1] (mapped to window-z ∈ [0, 1]) instead of the
		// reverse-Z [0, 1] desktop config — without glClipControl on GLES we'd
		// lose half the depth precision otherwise, which is what produced the
		// "layered cake" artifact on tree leaves.
		glDepthFunc(GL_LESS);
		glEnable(GLES20.GL_DEPTH_TEST);

		drawSkybox(scene, sky, cameraX, cameraY, cameraZ);
	}

	/**
	 * Clear the scene framebuffer, and — when the scene supplies a skybox model — draw it
	 * as the backdrop. Ported from upstream gpu's drawSkybox; the only differences are
	 * the GLES array-form uniform calls and the standard (non reverse-Z) depth clear.
	 */
	private void drawSkybox(Scene scene, int sky, float cameraX, float cameraY, float cameraZ)
	{
		Model skybox = scene.getSkybox();
		if (skybox == null)
		{
			Gfx.glClearColor(((sky >> 16) & 0xFF) / 255f, ((sky >> 8) & 0xFF) / 255f, (sky & 0xFF) / 255f, 1f);
			glClearDepthf(1f);
			Gfx.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
			return;
		}

		Gfx.glClearColor(0f, 0f, 0f, 1f);
		glClearDepthf(1f);
		Gfx.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

		RenderThread rt = rts[0];
		int size = skybox.getFaceCount() * 3 * VAO.VERT_SIZE;
		VAO o = rt.vaoO.get(size);
		if (o == null)
		{
			return;
		}

		try
		{
			rt.modelUploader.uploadTempModel(skybox, 0, 0, 0, 0, o.vbo.vb);
		}
		catch (Throwable t)
		{
			Log.w(TAG, "skybox upload failed: " + t, t);
			return;
		}

		// Translate-only projection: the skybox is modelled around the origin and follows
		// the camera. UNSORTED_NO_DEPTH so it never writes depth and everything draws over it.
		float[] skyboxProjection = Mat4.translate(cameraX, cameraY, cameraZ);
		o.addRange(skyboxProjection, scene, Renderable.RENDERMODE_UNSORTED_NO_DEPTH);

		rt.vaoO.draw();

		setEntityProjection(IDENTITY);
	}

	@Override
	public void postSceneDraw(Scene scene)
	{
		if (!glInitted) return;

		if (scene.getWorldViewId() == WorldView.TOPLEVEL)
		{
			glDisable(GL_BLEND);
			Gfx.glDisable(GL_CULL_FACE);
			Gfx.glDisable(GLES20.GL_DEPTH_TEST);
		}
		else
		{
			glUniform4i(uniEntityTint, 0, 0, 0, 0);
		}
	}

	@Override
	public void drawZoneOpaque(Projection entityProjection, Scene scene, int zx, int zz)
	{
		if (!glInitted) return;

		updateEntityProjection(entityProjection);

		SceneContext ctx = context(scene);
		if (ctx == null) return;

		if (scene.getWorldViewId() != WorldView.TOPLEVEL)
		{
			debugSubWvCallback("drawZoneOpaque", scene.getWorldViewId(), zx, zz);
		}

		Zone z = ctx.zones[zx][zz];
		if (!z.initialized) return;

		int offset = scene.getWorldViewId() == WorldView.TOPLEVEL ? (SCENE_OFFSET >> 3) : 0;
		z.renderOpaque(zx - offset, zz - offset, ctx.minLevel, ctx.level, ctx.maxLevel, ctx.hideRoofIds);
	}

	private static final int ALPHA_ZSORT_CLOSE = 2048;

	@Override
	public void drawZoneAlpha(Projection entityProjection, Scene scene, int level, int zx, int zz)
	{
		if (!glInitted) return;

		SceneContext ctx = context(scene);
		if (ctx == null) return;

		if (scene.getWorldViewId() != WorldView.TOPLEVEL)
		{
			debugSubWvCallback("drawZoneAlpha", scene.getWorldViewId(), zx, zz);
		}

		Zone z = ctx.zones[zx][zz];
		if (!z.initialized) return;

		updateEntityProjection(entityProjection);
		glUniform4i(uniEntityTint, scene.getOverrideHue(), scene.getOverrideSaturation(),
			scene.getOverrideLuminance(), scene.getOverrideAmount());

		int offset = scene.getWorldViewId() == WorldView.TOPLEVEL ? (SCENE_OFFSET >> 3) : 0;
		int dx = ctx.cameraX - ((zx - offset) << 10);
		int dz = ctx.cameraZ - ((zz - offset) << 10);
		boolean close = dx * dx + dz * dz < ALPHA_ZSORT_CLOSE * ALPHA_ZSORT_CLOSE;

		if (level == 0)
		{
			z.alphaSort(zx - offset, zz - offset, ctx.cameraX, ctx.cameraY, ctx.cameraZ);
			z.multizoneLocs(scene, zx - offset, zz - offset, ctx.cameraX, ctx.cameraZ, ctx.zones);
		}

		z.renderAlpha(rts[0].modelUploader, zx - offset, zz - offset, cameraYaw, cameraPitch,
			ctx.minLevel, ctx.level, ctx.maxLevel, level, ctx.hideRoofIds,
			!close || (scene.getOverrideAmount() > 0));
	}

	@Override
	public void drawPass(Projection projection, Scene scene, int pass)
	{
		if (!glInitted) return;

		SceneContext ctx = context(scene);
		if (ctx == null) return;

		if (scene.getWorldViewId() != WorldView.TOPLEVEL)
		{
			debugSubWvCallback("drawPass" + pass, scene.getWorldViewId(), 0, 0);
		}

		updateEntityProjection(projection);

		if (pass == DrawCallbacks.PASS_OPAQUE)
		{
			// Seal whatever the last model left unranged in each pool under this scene's
			// projection. A no-op when the pool's trailing range already covers everything,
			// which is the normal case since drawDynamic/drawTemp range as they go.
			for (int i = 0; i < rts.length; ++i) // NOPMD: ForLoopCanBeForeach
			{
				rts[i].vaoO.addRange(ctx.projection, scene, 0);
			}

			if (scene.getWorldViewId() == WorldView.TOPLEVEL)
			{
				if (DEBUG && (debugOpaqueDrawThrottle++ % 60) == 0)
				{
					int total = 0, mapped = 0, biggestRange = 0, vaoCount = 0;
					for (int i = 0; i < rts.length; ++i) // NOPMD: ForLoopCanBeForeach
					{
						for (VAO v : rts[i].vaoO.vaos)
						{
							vaoCount++;
							if (v.vbo.mapped) { total += v.vbo.vb.position(); mapped++; }
							for (int r = 0; r < v.off; ++r)
							{
								biggestRange = Math.max(biggestRange,
									v.ranges[r].endpos - (r > 0 ? v.ranges[r - 1].endpos : 0));
							}
						}
					}
					Log.i(TAG, "OPAQUE-DRAW totalInts=" + total + " mappedVaos=" + mapped
						+ " vaoCount=" + vaoCount + " biggestRange=" + biggestRange);
				}

				for (int i = 0; i < rts.length; ++i) // NOPMD: ForLoopCanBeForeach
				{
					rts[i].vaoO.draw();
				}
			}
		}
		else if (pass == DrawCallbacks.PRE_PASS_ALPHA)
		{
			// PRE_PASS_ALPHA fires once per scene right before the alpha zone loop.
			//
			// The alpha pools are unmapped, never addRange'd and never drawn as a pool: their
			// contents are replayed by Zone.renderAlpha, which indexes them directly by the
			// (start, end) positions recorded in addTempAlphaModel, in back-to-front order.
			//
			// Unmapping here rather than in drawZoneAlpha (upstream 098b40c4c6): drawZoneAlpha
			// is not guaranteed to run at all — if no zone is visible the pools stayed mapped
			// and the next frame's map() threw. This callback always fires.
			for (int i = 0; i < rts.length; ++i) // NOPMD: ForLoopCanBeForeach
			{
				rts[i].vaoA.unmap();
			}

			// Re-arm uniEntityProj so static alpha geometry (drawn by Zone.renderAlpha via
			// the static elementBuffer) gets the right entity transform.
			glUniformMatrix4fv(uniEntityProj, 1, false, ctx.projection, 0);
			lastProjection = projection;
			glUniform4i(uniEntityTint, scene.getOverrideHue(), scene.getOverrideSaturation(),
				scene.getOverrideLuminance(), scene.getOverrideAmount());
		}
		else // PASS_ALPHA
		{
			// CRITICAL: clean up temp alpha models stashed across zones by multizoneLocs
			// during the alpha pass. Without this, every frame allocates new AlphaModel
			// instances that never get reclaimed → heap exhaustion in seconds (OOM in
			// multizoneLocs).
			for (int x = 0; x < ctx.sizeX; ++x)
			{
				for (int z = 0; z < ctx.sizeZ; ++z)
				{
					ctx.zones[x][z].removeTemp();
				}
			}
		}
	}

	@Override
	public void drawDynamic(int renderThreadId, Projection worldProjection, Scene scene, TileObject tileObject,
		Renderable r, Model m, int orient, int x, int y, int z)
	{
		if (!glInitted) return;

		// -1 is the client thread, worker i is rts[i + 1]. Nothing below this point issues a
		// GL call: worker threads only fill an already-mapped VBO, seal a range on their own
		// VAO, and register into the (synchronized) zone alpha list.
		final RenderThread rt = rts[Math.max(0, Math.min(renderThreadId + 1, rts.length - 1))];

		SceneContext ctx = context(scene);
		if (ctx == null) return;
		if (m == null) return;
		final int faceCount = m.getFaceCount();
		if (faceCount <= 0) return;

		debugTrackDraw("drawDynamic", faceCount, m, scene, x, y, z, tileObject.getPlane());

		if (!renderCallbackManager.drawObject(scene, tileObject))
		{
			if (DEBUG && faceCount >= 1500)
			{
				Log.i(TAG, "CB-DROP drawDynamic faces=" + faceCount + " pos=(" + x + "," + y + "," + z + ")");
			}
			return;
		}

		final int size = faceCount * 3 * VAO.VERT_SIZE;
		if (m.getFaceTransparencies() == null)
		{
			// Opaque-only fast path: uploadTempModel has no near-clip (p[2] < 50)
			// or MAX_DIAMETER bail, so large NPCs like the Giant Gemstone Crab
			// render even when the camera is close to or inside the bounding
			// cylinder. Routing everything through uploadSortedModel like before
			// silently dropped these.
			VAO o = rt.vaoO.get(size);
			if (o == null) return; // worker pool out of room; it grows at the next preSceneDraw
			try { rt.modelUploader.uploadTempModel(m, orient, x, y, z, o.vbo.vb); }
			catch (Throwable t) { Log.w(TAG, "uploadTempModel opaque(drawDynamic) faces=" + faceCount
				+ " vxNull=" + (m.getVerticesX() == null) + " idxNull=" + (m.getFaceIndices1() == null)
				+ " wv=" + scene.getWorldViewId() + " failed: " + t, t); }
			// Seal this entity's range under THIS scene's captured projection matrix so
			// the later (toplevel) vaoO.draw() transforms it correctly. ctx.projection is
			// a per-SceneContext snapshot, so a sub-WorldView entity (the crab) keeps its
			// own matrix instead of being drawn under the shared Projection object after
			// it's been mutated to the enclosing toplevel transform — which sent its
			// entity-local vertices off-screen.
			o.addRange(ctx.projection, scene, 0);
			return;
		}

		VAO o = rt.vaoO.get(size);
		VAO a = rt.vaoA.get(size);
		if (o == null || a == null) return;
		int aStart = a.vbo.vb.position();
		try
		{
			m.calculateBoundsCylinder();
			rt.modelUploader.uploadSortedModel(worldProjection, m, orient, x, y, z,
				o.vbo.vb, a.vbo.vb, false);
		}
		catch (Throwable t)
		{
			// Match desktop: log + skip. Keeps a single bad model from killing
			// the render thread. WARN (not DEBUG) — filtered-out DEBUG used to
			// hide "why doesn't NPC X render" investigations.
			Log.w(TAG, "uploadSortedModel failed: " + t, t);
		}

		o.addRange(ctx.projection, scene, 0);

		int aEnd = a.vbo.vb.position();
		if (aEnd > aStart)
		{
			int offset = scene.getWorldViewId() == WorldView.TOPLEVEL ? (SCENE_OFFSET >> 3) : 0;
			int zx = (x >> 10) + offset;
			int zz = (z >> 10) + offset;
			if (zx >= 0 && zx < ctx.sizeX && zz >= 0 && zz < ctx.sizeZ)
			{
				Zone zone = ctx.zones[zx][zz];
				int plane = Math.min(ctx.maxLevel, tileObject.getPlane());
				zone.addTempAlphaModel(a.vao, aStart, aEnd, plane, x & 1023, y, z & 1023);
			}
		}
	}

	@Override
	public void drawTemp(Projection worldProjection, Scene scene, GameObject gameObject,
		Model m, int orient, int x, int y, int z)
	{
		if (!glInitted) return;

		SceneContext ctx = context(scene);
		if (ctx == null) return;
		if (m == null) return;
		final int faceCount = m.getFaceCount();
		if (faceCount <= 0) return;

		debugTrackDraw("drawTemp", faceCount, m, scene, x, y, z, gameObject.getPlane());

		if (!renderCallbackManager.drawObject(scene, gameObject))
		{
			if (DEBUG && faceCount >= 1500)
			{
				Log.i(TAG, "CB-DROP drawTemp faces=" + faceCount + " pos=(" + x + "," + y + "," + z + ")");
			}
			return;
		}

		final Renderable renderable = gameObject.getRenderable();
		final int renderMode = renderable.getRenderMode();
		final int size = faceCount * 3 * VAO.VERT_SIZE;

		// drawTemp is always the client thread.
		final RenderThread rt = rts[0];

		// getTransparency() is the model-wide transparency; a model can be see-through with
		// no per-face transparencies at all, and that has to take the sorted path too.
		if (renderMode != Renderable.RENDERMODE_SORTED_NO_DEPTH
			&& m.getFaceTransparencies() == null && m.getTransparency() == 0)
		{
			VAO o = rt.vaoO.get(size);
			if (o == null) return;
			try { rt.modelUploader.uploadTempModel(m, orient, x, y, z, o.vbo.vb); }
			catch (Throwable t) { Log.w(TAG, "uploadTempModel opaque(drawTemp) faces=" + faceCount
				+ " vxNull=" + (m.getVerticesX() == null) + " idxNull=" + (m.getFaceIndices1() == null)
				+ " wv=" + scene.getWorldViewId() + " failed: " + t, t); }
			o.addRange(ctx.projection, scene, 0);
			return;
		}

		VAO o = rt.vaoO.get(size);
		VAO a = rt.vaoA.get(size);
		if (o == null || a == null) return;
		int aStart = a.vbo.vb.position();
		int oStart = o.vbo.vb.position();
		try
		{
			m.calculateBoundsCylinder();
			rt.modelUploader.uploadSortedModel(worldProjection, m, orient, x, y, z,
				o.vbo.vb, a.vbo.vb, renderMode == Renderable.RENDERMODE_SORTED_NO_DEPTH);
		}
		catch (Throwable t)
		{
			Log.w(TAG, "uploadSortedModel failed: " + t, t);
		}

		o.addRange(ctx.projection, scene,
			renderMode == Renderable.RENDERMODE_SORTED_NO_DEPTH ? renderMode : 0);

		int aEnd = a.vbo.vb.position();
		if (aEnd > aStart)
		{
			int offset = scene.getWorldViewId() == WorldView.TOPLEVEL ? (SCENE_OFFSET >> 3) : 0;
			int zx = (gameObject.getX() >> 10) + offset;
			int zz = (gameObject.getY() >> 10) + offset;
			boolean inBounds = zx >= 0 && zx < ctx.sizeX && zz >= 0 && zz < ctx.sizeZ;
			if (DEBUG && faceCount > 1500)
			{
				Log.i(TAG, "SORTED faces=" + faceCount + " oBytes=" + (o.vbo.vb.position() - oStart)
					+ " aBytes=" + (aEnd - aStart) + " gobjXY=(" + gameObject.getX() + "," + gameObject.getY()
					+ ") zx=" + zx + " zz=" + zz + " sizeXZ=(" + ctx.sizeX + "," + ctx.sizeZ + ") inBounds=" + inBounds
					+ " mh=" + renderable.getModelHeight() + " maxLevel=" + ctx.maxLevel + " gobjPlane=" + gameObject.getPlane());
			}
			if (inBounds)
			{
				Zone zone = ctx.zones[zx][zz];
				int plane = Math.min(ctx.maxLevel, gameObject.getPlane());
				// y is offset by getModelHeight so players draw on top of locs (matches desktop).
				zone.addTempAlphaModel(a.vao, aStart, aEnd, plane, x & 1023, y - renderable.getModelHeight(), z & 1023);
			}
		}
		else if (DEBUG && faceCount > 1500)
		{
			Log.i(TAG, "SORTED faces=" + faceCount + " oBytes=" + (o.vbo.vb.position() - oStart)
				+ " aBytes=0 (NO ALPHA FACES — all opaque via vaoO)");
		}
	}

	private void prepareInterfaceTexture(int canvasWidth, int canvasHeight)
	{
		if (canvasWidth != lastCanvasWidth || canvasHeight != lastCanvasHeight)
		{
			lastCanvasWidth = canvasWidth;
			lastCanvasHeight = canvasHeight;
			glBindTexture(GL_TEXTURE_2D, interfaceTexture);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, canvasWidth, canvasHeight, 0,
				GL_RGBA, GL_UNSIGNED_BYTE, null);
			glBindTexture(GL_TEXTURE_2D, 0);
		}

		BufferProvider bp = client.getBufferProvider();
		if (bp == null) return;
		int[] pixels = bp.getPixels();
		int w = bp.getWidth();
		int h = bp.getHeight();
		if (pixels == null || w <= 0 || h <= 0) return;

		// Reuse a per-canvas-size direct ByteBuffer for the pixel upload. Allocating one
		// every frame was producing ~3.7MB of LOS garbage per frame at 1280×720, which
		// under Unlock FPS = constant 185ms blocking-GC pauses (see logcat:
		// "Background concurrent mark compact GC ... 49MB LOS objects ... 185ms total"
		// followed by "WaitForGcToComplete blocked Alloc on Background for 121ms").
		// The IntBuffer view is also cached because asIntBuffer() returns a fresh wrapper
		// each call and that was an even smaller but still per-frame allocation.
		int needBytes = w * h * 4;
		if (interfaceUploadBuf == null || interfaceUploadBuf.capacity() < needBytes)
		{
			interfaceUploadBuf = ByteBuffer.allocateDirect(needBytes).order(ByteOrder.nativeOrder());
			interfaceUploadBufAsInts = interfaceUploadBuf.asIntBuffer();
		}
		interfaceUploadBufAsInts.clear();
		interfaceUploadBufAsInts.put(pixels, 0, w * h);
		interfaceUploadBuf.position(0).limit(needBytes);
		glBindTexture(GL_TEXTURE_2D, interfaceTexture);
		glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, interfaceUploadBuf);
		glBindTexture(GL_TEXTURE_2D, 0);
	}

	/**
	 * Fullscreen composite: upscale the offscreen scene and lay the UI over it, in one pass.
	 *
	 * @param sceneMode one of SCENE_NONE / SCENE_TEXTURE / SCENE_DEFAULT_FB
	 */
	private void drawUi(int overlayColor, int canvasWidth, int canvasHeight, int sceneMode)
	{
		int viewW = GlesHost.get().getWidth();
		int viewH = GlesHost.get().getHeight();
		if (viewW <= 0 || viewH <= 0) { viewW = canvasWidth; viewH = canvasHeight; }
		glViewport(0, 0, viewW, viewH);

		// Blending is only needed for the fallback where the scene is already sitting in the
		// default framebuffer. In the normal path the shader does the compositing and this
		// quad covers every pixel, so the tiler never has to load the framebuffer in.
		if (sceneMode == SCENE_DEFAULT_FB)
		{
			glEnable(GL_BLEND);
			glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
		}
		else
		{
			glDisable(GL_BLEND);
		}

		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, interfaceTexture);
		glActiveTexture(GL_TEXTURE0 + SCENE_TEX_UNIT);
		glBindTexture(GL_TEXTURE_2D, sceneMode == SCENE_TEXTURE ? sceneColorTex : 0);
		glActiveTexture(GL_TEXTURE0);

		glUseProgram(glUiProgram);
		glUniform1i(uniTex, 0);
		glUniform1i(uniUiSceneTex, SCENE_TEX_UNIT);
		glUniform1i(uniUiHasScene, sceneMode);
		glUniform2i(uniTexSourceDimensions, canvasWidth, canvasHeight);
		glUniform2i(uniTexTargetDimensions, viewW, viewH);
		glUniform4f(uniUiAlphaOverlay,
			((overlayColor >> 16) & 0xFF) / 255f,
			((overlayColor >> 8) & 0xFF) / 255f,
			(overlayColor & 0xFF) / 255f,
			((overlayColor >>> 24) & 0xFF) / 255f
		);

		glBindVertexArray(vaoUiHandle);
		glDrawArrays(GLES20.GL_TRIANGLES, 0, UI_QUAD_VERTS);
		glBindVertexArray(0);

		glActiveTexture(GL_TEXTURE0 + SCENE_TEX_UNIT);
		glBindTexture(GL_TEXTURE_2D, 0);
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, 0);
		glUseProgram(0);

		glDisable(GL_BLEND);
	}
}
