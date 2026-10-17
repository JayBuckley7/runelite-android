package net.runelite.client.plugins.gpugles;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

/**
 * Lifetime owner of the EGL14 display/context + the GLES surface tied to the
 * Compose-hosted {@link SurfaceView}. One static instance shared between the
 * Compose layer (which feeds us the SurfaceView via {@link #attachSurfaceView}) and
 * {@link GpuGlesPlugin} (which calls {@link #makeCurrent} + {@link #swapBuffers}
 * from the patched client's draw thread).
 *
 * The plugin's GL calls have to run on the same thread that holds the context, so
 * we don't spin up our own render thread — we make-current on whichever thread the
 * client calls into us on. SurfaceHolder callbacks fire on the main thread; EGL
 * surface (re)creation is deferred to the first {@link #makeCurrent} call from the
 * client thread so the context never gets bound on the wrong thread.
 */
public final class GlesHost
{
	private static final String TAG = "GlesHost";

	/** EGL 1.1 error code; android.opengl.EGL14 doesn't expose it as a constant. Returned
	 *  by eglMakeCurrent/eglSwapBuffers when the context is lost (GPU reset, driver
	 *  eviction). */
	private static final int EGL_CONTEXT_LOST = 0x300E;

	/** MSAA samples requested for the on-screen surface. Falls back to no MSAA if
	 *  the GPU/driver doesn't expose a matching config. Mobile-friendly default is
	 *  0; users can opt in via config. The number is read once at context creation
	 *  because EGL configs are immutable on a live context — flipping the toggle
	 *  requires a plugin restart so we can rebuild the EGL stack. */
	private int requestedMsaa = 0;

	/** 3D scene render-target scale (0..1). 1 = render the scene FBO at the SurfaceView's
	 *  native pixel size. Lower = smaller scene FBO; {@link GpuGlesPlugin} blits the FBO
	 *  back to the native-resolution EGL surface and then composites the UI texture on
	 *  top at native res. Critical that only the 3D scene scales — using
	 *  {@link SurfaceHolder#setFixedSize} (our previous approach) would scale the entire
	 *  EGL surface including the UI, which SurfaceFlinger then bilinearly upscales,
	 *  blurring text. Default 0.75 so first-launch users get a noticeable boost. */
	private float requestedScale = 0.75f;

	private static final GlesHost INSTANCE = new GlesHost();
	public static GlesHost get() { return INSTANCE; }

	private final Object lock = new Object();

	private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
	private EGLConfig config;
	private EGLContext context = EGL14.EGL_NO_CONTEXT;
	private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
	private Surface androidSurface;
	private SurfaceView surfaceView;
	private int surfaceWidth;
	private int surfaceHeight;
	private volatile boolean surfaceDirty = false;

	/** The callback currently registered on {@link #surfaceView}'s holder, so we can
	 *  remove it when a new SurfaceView is attached. */
	private SurfaceHolder.Callback currentCallback;

	/** Bumped each time a fresh EGL context is created (initial bring-up + every
	 *  context-loss rebuild). See {@link #contextGeneration()}. */
	private int contextGeneration = 0;

	private GlesHost() {}

	/** Compose hands the SurfaceView over once it's been created. Subsequent calls
	 *  with the same view are no-ops; passing a different view rebinds the holder
	 *  callbacks and discards the old EGL surface on next makeCurrent. */
	public void attachSurfaceView(final SurfaceView view)
	{
		final SurfaceView oldView;
		final SurfaceHolder.Callback oldCallback;
		synchronized (lock)
		{
			if (this.surfaceView == view) return;
			oldView = this.surfaceView;
			oldCallback = this.currentCallback;
			this.surfaceView = view;
			androidSurface = null;
			surfaceDirty = true;
		}

		// GlesHost is a process-wide singleton, but every GPU re-enable mounts a BRAND-NEW
		// SurfaceView (Compose remounts the AndroidView when glesActive flips). Without
		// removing the previous view's callback, its async surfaceDestroyed — delivered by
		// SurfaceFlinger AFTER the new view's surfaceCreated — would clobber androidSurface
		// back to null, wedging makeCurrent() forever (it early-returns on androidSurface ==
		// null) so GL never presents and the transparent hole over the opaque window stays
		// black permanently. Remove the stale callback AND guard each callback below so it
		// only mutates shared state for the currently-attached view.
		if (oldView != null && oldCallback != null)
		{
			try { oldView.getHolder().removeCallback(oldCallback); }
			catch (Throwable ignored) { /* old holder may already be torn down */ }
		}

		SurfaceHolder.Callback cb = new SurfaceHolder.Callback()
		{
			@Override public void surfaceCreated(SurfaceHolder holder)
			{
				synchronized (lock)
				{
					if (surfaceView != view) return;
					androidSurface = holder.getSurface();
					surfaceDirty = true;
				}
				Log.i(TAG, "surfaceCreated");
			}

			@Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
			{
				synchronized (lock)
				{
					if (surfaceView != view) return;
					if (width != surfaceWidth || height != surfaceHeight)
					{
						surfaceWidth = width;
						surfaceHeight = height;
						surfaceDirty = true;
					}
					androidSurface = holder.getSurface();
				}
				Log.i(TAG, "surfaceChanged " + width + "x" + height + " fmt=" + format);
			}

			@Override public void surfaceDestroyed(SurfaceHolder holder)
			{
				synchronized (lock)
				{
					// Only the CURRENTLY-attached view may null the live surface; a late
					// destroy from a replaced view must be ignored.
					if (surfaceView != view) return;
					androidSurface = null;
					surfaceDirty = true;
				}
				Log.i(TAG, "surfaceDestroyed");
			}
		};
		synchronized (lock) { this.currentCallback = cb; }
		view.getHolder().addCallback(cb);
	}

	public int getWidth() { synchronized (lock) { return surfaceWidth; } }
	public int getHeight() { synchronized (lock) { return surfaceHeight; } }

	/** Set the 3D-scene render scale (0.25..1.0). Just stores the value; the plugin
	 *  reads {@link #getResolutionScale} each frame to size its scene FBO. Safe to call
	 *  from any thread. */
	public void setResolutionScale(float scale)
	{
		float clamped = Math.max(0.25f, Math.min(1.0f, scale));
		synchronized (lock) { requestedScale = clamped; }
	}

	public float getResolutionScale()
	{
		synchronized (lock) { return requestedScale; }
	}

	/** Set the MSAA sample count to request when the EGL context is next created.
	 *  Has no effect on an already-running context — the plugin reads this on startUp
	 *  via {@link #setRequestedMsaa(int)} and shutdown/restart cycles tear the context
	 *  down so the next init() picks the new value up. */
	public void setRequestedMsaa(int samples)
	{
		int n = (samples == 2 || samples == 4) ? samples : 0;
		synchronized (lock) { requestedMsaa = n; }
	}

	/** True once {@link #makeCurrent} has bound a context to the current thread.
	 *  Cheap to call from a render loop — only reads volatile state. */
	public boolean hasContext() { return context != EGL14.EGL_NO_CONTEXT && eglSurface != EGL14.EGL_NO_SURFACE; }

	/** EGL context generation. Incremented every time a fresh context is created (initial
	 *  bring-up and after a context-loss rebuild). {@link GpuGlesPlugin} compares this
	 *  against the generation it last built GL objects under; a change means the context
	 *  was recreated and every GL object (textures, buffers, VAOs, programs, FBOs) it
	 *  holds is now invalid and must be rebuilt. */
	public int contextGeneration() { synchronized (lock) { return contextGeneration; } }

	/** Tear down the EGL context and its window surface. Caller must hold {@link #lock}.
	 *  The next {@link #makeCurrent} recreates both (bumping {@link #contextGeneration}).
	 *  Invoked when EGL reports the context was lost. */
	private void destroyContextLocked()
	{
		destroyEglSurfaceLocked();
		if (context != EGL14.EGL_NO_CONTEXT)
		{
			EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
			EGL14.eglDestroyContext(display, context);
			context = EGL14.EGL_NO_CONTEXT;
		}
	}

	/**
	 * Bind the EGL context + window surface to the calling thread. Creates the
	 * display/context on first call and the window surface lazily once the Android
	 * Surface is available. Returns true when the caller can issue GL.
	 *
	 * Called from the client/draw thread; the SurfaceView callbacks set
	 * {@code surfaceDirty} when the underlying Android Surface changes, and we
	 * tear down and recreate the EGL surface on the next makeCurrent so the
	 * driver picks up the new backing buffer.
	 */
	public boolean makeCurrent()
	{
		synchronized (lock)
		{
			if (display == EGL14.EGL_NO_DISPLAY && !initDisplayLocked()) return false;
			if (context == EGL14.EGL_NO_CONTEXT && !initContextLocked()) return false;
			if (androidSurface == null) return false;
			if (surfaceDirty)
			{
				destroyEglSurfaceLocked();
				surfaceDirty = false;
			}
			if (eglSurface == EGL14.EGL_NO_SURFACE && !initSurfaceLocked()) return false;
			if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context))
			{
				int err = EGL14.eglGetError();
				Log.e(TAG, "eglMakeCurrent failed: 0x" + Integer.toHexString(err));
				if (err == EGL_CONTEXT_LOST)
				{
					// GPU reset / driver eviction. Drop the context so the next makeCurrent
					// rebuilds a fresh one (bumping contextGeneration), which signals the
					// plugin to recreate every GL object it holds.
					destroyContextLocked();
				}
				return false;
			}
			return true;
		}
	}

	/**
	 * Like {@link #makeCurrent}, but while there's no window surface (app in the background,
	 * or between surfaceDestroyed and surfaceCreated) binds the context surfaceless
	 * (EGL_KHR_surfaceless_context) instead of failing. GL objects stay usable, so a renderer
	 * that maps buffers early in a frame still unmaps them later in the same frame; only
	 * drawing to framebuffer 0 and {@link #swapBuffers} do nothing until the surface returns.
	 *
	 * 117 HD needs this: a frame interrupted between mapping and unmapping its buffers left
	 * them mapped, so every map after resume failed and the plugin shut down.
	 */
	public boolean makeCurrentAllowSurfaceless()
	{
		synchronized (lock)
		{
			if (makeCurrent()) return true;
			if (display == EGL14.EGL_NO_DISPLAY || context == EGL14.EGL_NO_CONTEXT) return false;
			if (androidSurface != null && androidSurface.isValid()) return false; // a real failure
			if (!surfacelessSupported()) return false;
			// Drop the window surface for the destroyed Android surface while keeping the
			// context, then bind the context without any surface.
			if (eglSurface != EGL14.EGL_NO_SURFACE)
			{
				EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, context);
				EGL14.eglDestroySurface(display, eglSurface);
				eglSurface = EGL14.EGL_NO_SURFACE;
				surfaceDirty = false;
			}
			return EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, context);
		}
	}

	private Boolean surfacelessSupported;

	private boolean surfacelessSupported()
	{
		if (surfacelessSupported == null)
		{
			String ext = EGL14.eglQueryString(display, EGL14.EGL_EXTENSIONS);
			surfacelessSupported = ext != null && ext.contains("EGL_KHR_surfaceless_context");
			Log.i(TAG, "EGL_KHR_surfaceless_context: " + surfacelessSupported);
		}
		return surfacelessSupported;
	}

	/** Present the current frame. Must be called after the caller has finished
	 *  GLES draws on the current thread. */
	public boolean swapBuffers()
	{
		synchronized (lock)
		{
			if (eglSurface == EGL14.EGL_NO_SURFACE) return false;
			if (!EGL14.eglSwapBuffers(display, eglSurface))
			{
				int err = EGL14.eglGetError();
				Log.w(TAG, "eglSwapBuffers failed: 0x" + Integer.toHexString(err));
				if (err == EGL_CONTEXT_LOST)
				{
					// Context died during present — tear it down so the next makeCurrent
					// rebuilds it and the plugin re-creates its GL objects.
					destroyContextLocked();
				}
				return false;
			}
			return true;
		}
	}

	/** EGL swap interval. 0 = present as fast as possible (no vsync), 1 = vsync at
	 *  the surface's refresh rate. Driver may clamp values it doesn't support — eg.
	 *  many Android implementations will silently treat anything &gt; 1 as 1. The
	 *  GpuGlesPlugin calls this when {@code unlockFps} flips on/off so the engine's
	 *  uncapped scene rate isn't hard-pinned to the display's vsync. */
	public boolean setSwapInterval(int interval)
	{
		synchronized (lock)
		{
			if (display == EGL14.EGL_NO_DISPLAY) return false;
			if (!EGL14.eglSwapInterval(display, interval))
			{
				Log.w(TAG, "eglSwapInterval(" + interval + ") failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
				return false;
			}
			return true;
		}
	}


	private boolean initDisplayLocked()
	{
		display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
		if (display == EGL14.EGL_NO_DISPLAY)
		{
			Log.e(TAG, "eglGetDisplay failed");
			return false;
		}
		int[] version = new int[2];
		if (!EGL14.eglInitialize(display, version, 0, version, 1))
		{
			Log.e(TAG, "eglInitialize failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
			display = EGL14.EGL_NO_DISPLAY;
			return false;
		}
		Log.i(TAG, "EGL " + version[0] + "." + version[1] + " initialized");
		return true;
	}

	private boolean initContextLocked()
	{
		EGLConfig chosen = null;
		if (requestedMsaa >= 2)
		{
			chosen = chooseConfig(requestedMsaa);
			if (chosen == null)
			{
				Log.w(TAG, requestedMsaa + "× MSAA config unavailable, falling back to single-sample");
			}
		}
		if (chosen == null)
		{
			chosen = chooseConfig(0);
		}
		if (chosen == null)
		{
			Log.e(TAG, "no suitable EGL config");
			return false;
		}
		config = chosen;

		int[] ctxAttrs = { EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE };
		context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttrs, 0);
		if (context == EGL14.EGL_NO_CONTEXT)
		{
			Log.e(TAG, "eglCreateContext failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
			return false;
		}
		contextGeneration++;
		Log.i(TAG, "EGL context created (generation " + contextGeneration + ")");
		return true;
	}

	private EGLConfig chooseConfig(int msaaSamples)
	{
		int[] attrs;
		if (msaaSamples >= 2)
		{
			attrs = new int[] {
				EGL14.EGL_RED_SIZE, 8,
				EGL14.EGL_GREEN_SIZE, 8,
				EGL14.EGL_BLUE_SIZE, 8,
				EGL14.EGL_ALPHA_SIZE, 8,
				EGL14.EGL_DEPTH_SIZE, 24,
				EGL14.EGL_STENCIL_SIZE, 0,
				EGL14.EGL_SAMPLE_BUFFERS, 1,
				EGL14.EGL_SAMPLES, msaaSamples,
				EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
				EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
				EGL14.EGL_NONE
			};
		}
		else
		{
			attrs = new int[] {
				EGL14.EGL_RED_SIZE, 8,
				EGL14.EGL_GREEN_SIZE, 8,
				EGL14.EGL_BLUE_SIZE, 8,
				EGL14.EGL_ALPHA_SIZE, 8,
				EGL14.EGL_DEPTH_SIZE, 24,
				EGL14.EGL_STENCIL_SIZE, 0,
				EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
				EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
				EGL14.EGL_NONE
			};
		}
		EGLConfig[] configs = new EGLConfig[1];
		int[] numConfigs = new int[1];
		if (!EGL14.eglChooseConfig(display, attrs, 0, configs, 0, configs.length, numConfigs, 0))
		{
			int error = EGL14.eglGetError();

			String msg = "eglChooseConfig failed: 0x" + Integer.toHexString(error);

			Log.e(TAG, msg);

			FirebaseCrashlytics.getInstance().log(msg);
			FirebaseCrashlytics.getInstance().recordException(
					new RuntimeException(msg));
			return null;
		}

		Log.d(TAG, "eglChooseConfig returned " + numConfigs[0] + " configs");
		if (numConfigs[0] == 0) return null;
		return configs[0];
	}

	private boolean initSurfaceLocked()
	{
		if (androidSurface == null || !androidSurface.isValid())
		{
			return false;
		}
		int[] attrs = { EGL14.EGL_NONE };
		eglSurface = EGL14.eglCreateWindowSurface(display, config, androidSurface, attrs, 0);
		if (eglSurface == EGL14.EGL_NO_SURFACE)
		{
			Log.e(TAG, "eglCreateWindowSurface failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
			return false;
		}
		return true;
	}

	private void destroyEglSurfaceLocked()
	{
		if (eglSurface != EGL14.EGL_NO_SURFACE)
		{
			EGL14.eglMakeCurrent(
					display,
					EGL14.EGL_NO_SURFACE,
					EGL14.EGL_NO_SURFACE,
					EGL14.EGL_NO_CONTEXT);

			EGL14.eglDestroySurface(display, eglSurface);
			eglSurface = EGL14.EGL_NO_SURFACE;
		}
	}
}
