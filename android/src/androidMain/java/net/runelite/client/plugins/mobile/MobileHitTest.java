package net.runelite.client.plugins.mobile;

import java.awt.Rectangle;

/**
 * Touch-thread-safe interface-vs-scene hit test. Used by the Android input bridge to
 * pick BUTTON1 (interface drag: inv slot, scrollbar, button) vs BUTTON2 (scene drag:
 * camera) at the moment a touch crosses the drag-slop threshold.
 *
 * <p>The OSRS {@code Widget} APIs are client-thread-only — calling them from the
 * Compose touch handler triggered {@code IllegalStateException: must be called on
 * client thread} and killed the gesture coroutine the first time the user touched.
 * Instead, {@link MobileInputPlugin} walks the widget tree on the client thread once
 * per frame and dumps the resulting opaque rectangles here as a flat array. The
 * touch handler just intersects against the snapshot — no client-thread calls.</p>
 */
public final class MobileHitTest
{
	private static final Rectangle[] EMPTY = new Rectangle[0];
	private static volatile Rectangle[] interfaceRects = EMPTY;
	/** Right-click menu rect in canvas space, or null when no menu is open. */
	private static volatile Rectangle menuRect;
	/** Height of the root menu's header row in canvas space. */
	private static volatile int menuHeaderHeight;
	/** Open submenu rects in canvas space; empty while none is open. */
	private static volatile Rectangle[] submenuRects = EMPTY;
	/** True iff the OSRS client is in fixed (765×503) mode this frame. Drives the
	 *  short-circuit in {@link #isInterfaceAt}: in fixed mode the chrome layout is
	 *  static, so "is this touch on UI" reduces to "is it outside the 3D viewport". */
	private static volatile boolean fixedMode = false;
	/** 3D viewport rect in canvas-local pixels (top-left + width/height). Published
	 *  every frame whenever fixed mode is active. Null otherwise. */
	private static volatile Rectangle viewportRect;

	private MobileHitTest() {}

	/** Called by {@link MobileInputPlugin} on the client thread once per frame. */
	static void setInterfaceRects(Rectangle[] rects)
	{
		interfaceRects = (rects == null || rects.length == 0) ? EMPTY : rects;
	}

	/** Called by {@link MobileInputPlugin} on the client thread once per frame. Null
	 *  while no right-click menu is open. */
	static void setMenuRect(Rectangle rect)
	{
		menuRect = rect;
	}

	/** Called by {@link MobileInputPlugin} on the client thread once per frame: the height
	 *  of the root menu's "Choose Option" header, and the rects of the open submenus. */
	static void setMenuOptions(int headerHeight, Rectangle[] submenus)
	{
		menuHeaderHeight = headerHeight;
		submenuRects = (submenus == null || submenus.length == 0) ? EMPTY : submenus;
	}

	/** True if (canvasX, canvasY) lies on an option of the open menu: a row of the root
	 *  menu below its header, or anywhere in an open submenu. The header is not an option;
	 *  clicking it closes the menu, so a long-press lifted there must leave the menu open. */
	public static boolean isMenuOptionAt(int canvasX, int canvasY)
	{
		Rectangle r = menuRect;
		if (r == null)
		{
			return false;
		}
		if (canvasX >= r.x && canvasY >= r.y + menuHeaderHeight
			&& canvasX < r.x + r.width && canvasY < r.y + r.height)
		{
			return true;
		}
		for (Rectangle s : submenuRects)
		{
			if (canvasX >= s.x && canvasY >= s.y
				&& canvasX < s.x + s.width && canvasY < s.y + s.height)
			{
				return true;
			}
		}
		return false;
	}

	/** Called by {@link MobileInputPlugin} on the client thread once per frame. */
	static void setFixedMode(boolean fixed)
	{
		fixedMode = fixed;
	}

	/** Called by {@link MobileInputPlugin} on the client thread once per frame.
	 *  Null when fixed mode isn't active or the engine isn't reporting viewport bounds. */
	static void setViewportRect(Rectangle rect)
	{
		viewportRect = rect;
	}

	/** True if a right-click menu is open AND (canvasX, canvasY) lies inside it. Used
	 *  by the long-press release path so a finger lift outside the menu cancels the
	 *  selection instead of firing a stray left-click on the world below. */
	public static boolean isMenuAt(int canvasX, int canvasY)
	{
		Rectangle r = menuRect;
		return r != null
			&& canvasX >= r.x && canvasY >= r.y
			&& canvasX < r.x + r.width && canvasY < r.y + r.height;
	}

	/**
	 * @param canvasX x in OSRS canvas space (pixels from the game canvas's top-left)
	 * @param canvasY y in OSRS canvas space
	 * @return true if any opaque widget covers (canvasX, canvasY) as of the most-recent
	 *   frame snapshot. Safe to call from any thread.
	 */
	public static boolean isInterfaceAt(int canvasX, int canvasY)
	{
		// Fixed-mode short-circuit. The fixed-size chrome (sidebar, inventory tabs,
		// prayer tabs, chat box, minimap) is a static layout around a fixed 3D viewport
		// rect — there's no widget walk that gets this right without false positives.
		// Anything OUTSIDE the viewport is by definition chrome; anything INSIDE is the
		// scene. Same conclusion the widget walk would arrive at on a good day, just
		// faster and without the over-flagging that routes scene drags to BUTTON1.
		if (fixedMode)
		{
			Rectangle v = viewportRect;
			if (v != null)
			{
				boolean inViewport = canvasX >= v.x && canvasY >= v.y
					&& canvasX < v.x + v.width && canvasY < v.y + v.height;
				if (!inViewport)
				{
					// Outside the viewport is always chrome (chat, tabs, minimap).
					return true;
				}
				// Inside the viewport — but a modal window (bank, GE, trade) may be
				// overlaying the viewport at this point. Fall through to the
				// interfaceRects check below so those overlays register correctly.
			}
			// Viewport rect not yet published — fall through to the widget walk so we
			// don't degrade to "everything is scene" before the first frame snapshot.
		}
		Rectangle[] snap = interfaceRects;
		for (int i = 0; i < snap.length; i++)
		{
			Rectangle r = snap[i];
			if (canvasX >= r.x && canvasY >= r.y
				&& canvasX < r.x + r.width && canvasY < r.y + r.height)
			{
				return true;
			}
		}
		return false;
	}
}
