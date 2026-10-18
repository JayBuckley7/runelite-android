package net.runelite.client.plugins.mobile;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.BufferProvider;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Runs after widget overlays, before menus: attached status bars scale with the inventory. */
public final class MobileInventoryOverlay extends Overlay
{
	private final Client client;
	private final MobileConfig config;
	private int[] scratch = new int[0];
	private static volatile InventoryScaleGeometry displayed;
	@Inject
	public MobileInventoryOverlay(Client client, MobileConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(10000f);

	}
	public static void clear() { displayed = null; }
	public void validateVisibility()
	{
		Widget panel = inventoryPanel();
		if (config.inventoryScale() <= 100 || !client.isGpu() || !client.isResized()
			|| panel == null) clear();
	}
	@Override
	public Dimension render(Graphics2D graphics)
	{
		validateVisibility();
		Widget panel = inventoryPanel();
		if (config.inventoryScale() <= 100 || !client.isGpu() || !client.isResized()
			|| panel == null) return null;
		BufferProvider buffer = client.getBufferProvider();
		Rectangle bounds = panel.getBounds();
		int w = buffer.getWidth(), h = buffer.getHeight();
		// Refuse partial/offscreen panels rather than sample unrelated UI or invalid memory.
		if (bounds.width <= 0 || bounds.height <= 0 || !new Rectangle(0, 0, w, h).contains(bounds))
		{ clear(); return null; }
		InventoryScaleGeometry transform = new InventoryScaleGeometry(bounds, config.inventoryScale(), w, h);
		int count = bounds.width * bounds.height;
		if (scratch.length < count) scratch = new int[count];
		transform.composite(buffer.getPixels(), scratch);
		displayed = transform;
		return null;
	}
	private Widget inventoryPanel()
	{
		Widget panel = client.getWidget(InterfaceID.ToplevelOsrsStretch.SIDE_MENU);
		if (panel == null || panel.isHidden()) panel = client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_CONTAINER);
		return panel == null || panel.isHidden() ? null : panel;
	}
	/** Canvas/stretched coordinates in, original canvas coordinates out. */
	public static int[] mapInput(int x, int y, int canvasWidth, int canvasHeight)
	{
		InventoryScaleGeometry g = displayed;
		if (g == null || canvasWidth <= 0 || canvasHeight <= 0
			|| MobileHitTest.isMenuAt(x, y) || MobileHitTest.isMenuOptionAt(x, y)) return new int[]{x, y};
		int px = (int) ((long) x * g.width / canvasWidth), py = (int) ((long) y * g.height / canvasHeight);
		if (!g.destination.contains(px, py)) return new int[]{x, y};
		return new int[]{(int) ((long) g.sourceX(px) * canvasWidth / g.width),
			(int) ((long) g.sourceY(py) * canvasHeight / g.height)};
	}
}
