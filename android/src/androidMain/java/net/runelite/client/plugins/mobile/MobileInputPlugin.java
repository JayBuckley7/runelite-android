package net.runelite.client.plugins.mobile;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Snapshots the visible RS widget tree once per frame and publishes the resulting flat
 * list of opaque rectangles to {@link MobileHitTest}, so the Android touch driver can
 * tell whether a gesture starts on a UI widget (BUTTON1 — inv slot, scrollbar) or on
 * the 3D scene (BUTTON2 — camera rotate) without ever touching the client-thread-only
 * Widget API from the touch handler.
 *
 * <p>Safe to load on desktop too; it just produces an unused snapshot.</p>
 */
@Slf4j
@PluginDescriptor(
	name = "Mobile Input Bridge",
	description = "Provides interface-vs-scene hit-testing for the Android touch driver",
	hidden = true
)
public class MobileInputPlugin extends Plugin
{
	/** Height of the root menu's "Choose Option" header in real (unstretched) pixels;
	 *  the first option row starts below it. */
	private static final int MENU_HEADER_HEIGHT = 19;

	@Inject private Client client;

	private long lastLogMs;
	private int lastLoggedCount = -1;

	@Override
	protected void shutDown()
	{
		MobileHitTest.setInterfaceRects(null);
		MobileHitTest.setMenuRect(null);
		MobileHitTest.setMenuOptions(0, null);
		MobileHitTest.setFixedMode(false);
		MobileHitTest.setViewportRect(null);
	}

	@Subscribe
	public void onBeforeRender(BeforeRender ev)
	{
		// The engine reports all rect/coord APIs (viewport, menu, widget bounds) in
		// "real" (internal) space — the same space TranslateMouseListener converts
		// incoming mouse events INTO. Our Android touch handler delivers canvas-local
		// pixels in "stretched" space (what the user actually sees on screen). Compute
		// the scale once and apply at every publish site so rect-in-rect checks against
		// touch coords just work. When stretched mode is off, the factor is 1 and the
		// scaleRect path becomes a no-op.
		double sx = 1.0;
		double sy = 1.0;
		if (client.isStretchedEnabled())
		{
			Dimension real = client.getRealDimensions();
			Dimension stretched = client.getStretchedDimensions();
			if (real != null && stretched != null
				&& real.width > 0 && real.height > 0)
			{
				sx = (double) stretched.width / real.width;
				sy = (double) stretched.height / real.height;
			}
		}

		// Mode + viewport: published every frame so MobileHitTest can short-circuit on
		// fixed mode (where the chrome layout is static and "is this on UI" reduces to
		// "is this outside the 3D viewport"). The widget walk below is still done so
		// resizable mode keeps working — and so fixed mode has a fallback for the first
		// frame after a mode-change before the engine has computed viewport bounds.
		boolean fixed = !client.isResized();
		MobileHitTest.setFixedMode(fixed);
		if (fixed)
		{
			int vw = client.getViewportWidth();
			int vh = client.getViewportHeight();
			if (vw > 0 && vh > 0)
			{
				MobileHitTest.setViewportRect(scaleRect(
					client.getViewportXOffset(), client.getViewportYOffset(), vw, vh, sx, sy));
			}
		}
		else
		{
			MobileHitTest.setViewportRect(null);
		}

		// Menu rect: published every frame so the touch driver knows whether a long-press
		// release lands on a menu entry (→ select that entry with a left-click) or in the
		// world (→ no click; the menu just stays open until dismissed).
		if (client.isMenuOpen())
		{
			MobileHitTest.setMenuRect(scaleRect(
				client.getMenuX(), client.getMenuY(),
				client.getMenuWidth(), client.getMenuHeight(),
				sx, sy));
			// Submenus open on hover over their parent row. Menu bounds are documented as
			// valid only while the menu is open, so a submenu with a size is taken as open.
			List<Rectangle> subs = new ArrayList<>(2);
			for (MenuEntry entry : client.getMenu().getMenuEntries())
			{
				Menu sub = entry.getSubMenu();
				if (sub != null && sub.getMenuWidth() > 0 && sub.getMenuHeight() > 0)
				{
					subs.add(scaleRect(sub.getMenuX(), sub.getMenuY(),
						sub.getMenuWidth(), sub.getMenuHeight(), sx, sy));
				}
			}
			MobileHitTest.setMenuOptions((int) Math.round(MENU_HEADER_HEIGHT * sy),
				subs.toArray(new Rectangle[0]));
		}
		else
		{
			MobileHitTest.setMenuRect(null);
			MobileHitTest.setMenuOptions(0, null);
		}

		if (client.getGameState() == GameState.LOGGED_IN) {
			Widget[] roots = client.getWidgetRoots();
			if (roots == null)
			{
				MobileHitTest.setInterfaceRects(null);
				return;
			}
			List<Rectangle> rects = new ArrayList<>(64);
			int canvasW = client.getCanvasWidth();
			int canvasH = client.getCanvasHeight();
			for (Widget root : roots)
			{
				collect(root, rects, canvasW, canvasH);
			}
			Rectangle[] arr;
			if (sx == 1.0 && sy == 1.0)
			{
				arr = rects.toArray(new Rectangle[0]);
			}
			else
			{
				arr = new Rectangle[rects.size()];
				for (int i = 0; i < arr.length; i++)
				{
					Rectangle r = rects.get(i);
					arr[i] = scaleRect(r.x, r.y, r.width, r.height, sx, sy);
				}
			}
			MobileHitTest.setInterfaceRects(arr);

			// Periodic visibility into what's being treated as "interface". Helps diagnose
			// when too many rects are flagged (scene-clicks getting BUTTON1 instead of
			// BUTTON2) vs too few (inv drag getting BUTTON2 instead of BUTTON1).
			long now = System.currentTimeMillis();
			if (arr.length != lastLoggedCount && now - lastLogMs > 3000)
			{
				lastLogMs = now;
				lastLoggedCount = arr.length;
				log.info("mobile hit-test snapshot: {} blocking rects", arr.length);
			}
		}
	}

	/** Multiply a rect's origin + size by the real→stretched scale factor. Identity when
	 *  the factor is 1 (stretched mode off). Allocates a fresh Rectangle either way. */
	private static Rectangle scaleRect(int x, int y, int w, int h, double sx, double sy)
	{
		if (sx == 1.0 && sy == 1.0) return new Rectangle(x, y, w, h);
		return new Rectangle(
			(int) Math.round(x * sx), (int) Math.round(y * sy),
			(int) Math.round(w * sx), (int) Math.round(h * sy));
	}

	/**
	 * Depth-first walk. A widget contributes to the click-blocking set when it
	 * intercepts clicks or drags (buttons, inventory slots, bank slider, scrollbars)
	 * while excluding full-screen transparent viewport containers so 3D scene camera
	 * rotation works smoothly.
	 */
	private static void collect(Widget w, List<Rectangle> out, int canvasW, int canvasH)
	{
		// isHidden also covers a hidden ancestor, so a hidden subtree is skipped whole.
		if (w == null || w.isHidden())
		{
			return;
		}
		Widget[] dyn = w.getDynamicChildren();
		if (dyn != null)
		{
			for (Widget c : dyn) collect(c, out, canvasW, canvasH);
		}
		Widget[] stat = w.getStaticChildren();
		if (stat != null)
		{
			for (Widget c : stat) collect(c, out, canvasW, canvasH);
		}
		Widget[] nest = w.getNestedChildren();
		if (nest != null)
		{
			for (Widget c : nest) collect(c, out, canvasW, canvasH);
		}
		if (isClickBlocking(w, canvasW, canvasH))
		{
			Rectangle b = w.getBounds();
			if (b != null && b.width > 0 && b.height > 0)
			{
				out.add(b);
			}
		}
	}

	/**
	 * What counts as "interface that swallows a drag":
	 * <ul>
	 *   <li>Full-screen / viewport-sized transparent containers → never block
	 *       (preserves 3D scene touch-hold camera rotation in resizable mode).</li>
	 *   <li>LAYER widgets only block if they have {@code noClickThrough} or scrollable
	 *       content ({@code scrollHeight > height}).</li>
	 *   <li>Non-LAYER widgets with any interactive property → block: {@code actions},
	 *       {@code dragParent}, {@code hasListener}, {@code scrollHeight > height},
	 *       {@code noClickThrough}, {@code clickMask}.</li>
	 * </ul>
	 */
	private static boolean isClickBlocking(Widget w, int canvasW, int canvasH)
	{
		if (w == null)
		{
			return false;
		}

		int width = w.getWidth();
		int height = w.getHeight();
		if (width <= 0 || height <= 0)
		{
			return false;
		}

		boolean isFullScreen = canvasW > 0 && canvasH > 0
			&& width >= canvasW - 10 && height >= canvasH - 10;

		// LAYER widgets are RS's transparent group containers (HUD trim, viewport
		// background, etc.) and draw nothing themselves; whatever is visible in them is
		// a child, collected on its own. Only a genuinely scrollable one (e.g. the bank
		// item container) blocks, so a drag in it scrolls instead of turning the camera.
		// noClickThrough alone doesn't count: in resizable mode such layers span empty
		// screen, and a drag there is meant to turn the camera.
		if (w.getType() == WidgetType.LAYER)
		{
			return !isFullScreen && height > 0 && w.getScrollHeight() > height;
		}

		// Full-screen non-layer containers → never block (preserves camera)
		if (isFullScreen)
		{
			return false;
		}

		// Nothing drawn here → nothing the user can see to grab. Invisible hotspots
		// (listeners or actions on an empty widget) would otherwise turn a camera drag
		// over empty scene into a BUTTON1 click-drag.
		if (!drawsSomething(w) && !(height > 0 && w.getScrollHeight() > height))
		{
			return false;
		}

		// --- Non-LAYER, non-full-screen widgets below ---
		// These are the real interactive UI elements: buttons, inventory slots,
		// scrollbar thumbs, bank items, etc.

		if (w.getNoClickThrough())
		{
			return true;
		}

		String[] actions = w.getActions();
		if (actions != null)
		{
			for (String a : actions)
			{
				if (a != null && !a.isEmpty())
				{
					return true;
				}
			}
		}

		if (w.getDragParent() != null)
		{
			return true;
		}

		if (height > 0 && w.getScrollHeight() > height)
		{
			return true;
		}

		// hasListener() catches scrollbar thumbs, arrows, and similar small interactive
		// components. Guard with a size check: only block when at least one dimension is
		// smaller than half the canvas, so large viewport-covering widgets (which may
		// have listeners for scene rendering callbacks) don't suppress camera rotation.
		if (w.hasListener()
			&& (canvasW <= 0 || canvasH <= 0
				|| width < canvasW / 2 || height < canvasH / 2))
		{
			return true;
		}

		return w.getClickMask() != 0;
	}

	/** True if the widget itself puts pixels on screen: a sprite or item icon, non-empty
	 *  text, a rectangle that isn't fully transparent (opacity 255), a model or a line. */
	private static boolean drawsSomething(Widget w)
	{
		switch (w.getType())
		{
			case WidgetType.GRAPHIC:
				return (w.getSpriteId() >= 0 || w.getItemId() >= 0) && w.getOpacity() < 255;
			case WidgetType.TEXT:
				String text = w.getText();
				return text != null && !text.isEmpty() && w.getOpacity() < 255;
			case WidgetType.RECTANGLE:
				return w.getOpacity() < 255;
			case WidgetType.MODEL:
				return w.getModelId() >= 0 || w.getModelType() != 1;
			default:
				return true;
		}
	}
}
