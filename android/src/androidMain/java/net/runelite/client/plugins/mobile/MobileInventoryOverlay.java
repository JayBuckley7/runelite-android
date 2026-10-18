package net.runelite.client.plugins.mobile;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.BufferProvider;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Composites independently sized panels after widget overlays and before menus. */
public final class MobileInventoryOverlay extends Overlay
{
    private static final String[] KEYS = {"inventoryScale", "chatScale", "minimapScale", "staticTabsScale", "movableTabsScale"};
    private static final int[][] IDS = {
        {InterfaceID.ToplevelOsrsStretch.SIDE_MENU, InterfaceID.ToplevelPreEoc.SIDE_CONTAINER},
        {InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER, InterfaceID.ToplevelPreEoc.CHAT_CONTAINER},
        {InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER, InterfaceID.ToplevelPreEoc.MAP_CONTAINER},
        {InterfaceID.ToplevelPreEoc.SIDE_STATIC_LAYER},
        {InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_LAYER}
    };
    private final Client client;
    private final MobileConfig config;
    private final ClientThread clientThread;
    private final ConfigManager configManager;
    private final int[][] scratch = {new int[0], new int[0], new int[0], new int[0], new int[0]};
    private static volatile List<Panel> displayed = java.util.Collections.emptyList();
    private static volatile Resize gesture;

    private static final class Panel
    {
        final int id, percent, bufferIndex;
        final InventoryScaleGeometry geometry;
        final MobileInventoryOverlay owner;
        Panel(int id, int percent, InventoryScaleGeometry geometry, MobileInventoryOverlay owner, int bufferIndex)
        { this.id = id; this.percent = percent; this.geometry = geometry; this.owner = owner; this.bufferIndex = bufferIndex; }
    }

    /** A gesture latches one panel; moving fingers never switches the target. */
    public static final class Resize
    {
        private final Panel panel;
        private volatile int percent;
        private boolean ended;
        private Resize(Panel panel) { this.panel = panel; percent = panel.percent; }
        public void update(float ratio)
        {
            if (!ended && gesture == this && Float.isFinite(ratio))
                percent = Math.max(100, Math.min(200, Math.round(panel.percent * ratio)));
        }
        public void end()
        {
            if (ended) return;
            ended = true;
            if (gesture != this) return;
            final int saved = percent;
            panel.owner.clientThread.invokeLater(() -> {
                panel.owner.configManager.setConfiguration(MobileConfig.GROUP, KEYS[panel.id], saved);
                if (gesture == this) gesture = null;
            });
        }
    }

    @Inject
    public MobileInventoryOverlay(Client client, MobileConfig config, ClientThread clientThread, ConfigManager configManager)
    {
        this.client = client;
        this.config = config;
        this.clientThread = clientThread;
        this.configManager = configManager;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(10000f);
    }
    public static void clear() { displayed = java.util.Collections.emptyList(); gesture = null; }
    public void validateVisibility()
    {
        if (!client.isGpu() || !client.isResized()) { clear(); return; }
        // Remove hidden panels before input can use the preceding frame's geometry.
        List<Panel> visible = new ArrayList<>();
        for (Panel panel : displayed) if (widget(panel.id) != null) visible.add(panel);
        displayed = visible;
        Resize current = gesture;
        if (current != null && widget(current.panel.id) == null) current.end();
    }
    @Override
    public Dimension render(Graphics2D graphics)
    {
        validateVisibility();
        if (!client.isGpu() || !client.isResized()) return null;
        BufferProvider buffer = client.getBufferProvider();
        int w = buffer.getWidth(), h = buffer.getHeight();
        int[] scales = {config.inventoryScale(), config.chatScale(), config.minimapScale(), config.staticTabsScale(), config.movableTabsScale()};
        Resize current = gesture;
        if (current != null) scales[current.panel.id] = current.percent;
        List<Panel> panels = new ArrayList<>();
        for (int id = 0; id < IDS.length; id++)
        {
            Widget widget = widget(id);
            if (widget == null) continue;
            Rectangle bounds = widget.getBounds();
            if (bounds.width <= 0 || bounds.height <= 0 || !new Rectangle(0, 0, w, h).contains(bounds)) continue;
            panels.add(new Panel(id, scales[id], new InventoryScaleGeometry(bounds, scales[id], w, h), this, id));
        }
        // Capture every piece before clearing any source, including detached tab rows.
        for (Panel panel : panels)
        {
            int count = panel.geometry.source.width * panel.geometry.source.height;
            if (scratch[panel.bufferIndex].length < count) scratch[panel.bufferIndex] = new int[count];
            panel.geometry.capture(buffer.getPixels(), scratch[panel.bufferIndex]);
        }
        for (Panel panel : panels) panel.geometry.clearSource(buffer.getPixels());
        for (Panel panel : panels) panel.geometry.paint(buffer.getPixels(), scratch[panel.bufferIndex]);
        displayed = panels;
        return null;
    }
    private Widget widget(int id)
    {
        // Each piece has its own visibility. Tab rows remain scalable when the inventory is closed.
        for (int widgetId : IDS[id])
        {
            Widget panel = client.getWidget(widgetId);
            if (panel != null && !panel.isHidden()) return panel;
        }
        return null;
    }
    private static Panel hit(int x, int y, int canvasWidth, int canvasHeight)
    {
        if (canvasWidth <= 0 || canvasHeight <= 0 || MobileHitTest.isMenuAt(x, y)
            || MobileHitTest.isMenuOptionAt(x, y)) return null;
        List<Panel> panels = displayed;
        for (int i = panels.size() - 1; i >= 0; i--)
        {
            Panel panel = panels.get(i);
            InventoryScaleGeometry g = panel.geometry;
            if (g.destination.contains((int)((long)x * g.width / canvasWidth),
                (int)((long)y * g.height / canvasHeight))) return panel;
        }
        return null;
    }
    public static Resize beginResize(int x, int y, int canvasWidth, int canvasHeight)
    {
        Panel panel = hit(x, y, canvasWidth, canvasHeight);
        if (panel == null) return null;
        Resize resize = new Resize(panel);
        gesture = resize;
        return resize;
    }
    /** Canvas/stretched coordinates in, original canvas coordinates out. */
    public static int[] mapInput(int x, int y, int canvasWidth, int canvasHeight)
    {
        Panel panel = hit(x, y, canvasWidth, canvasHeight);
        if (panel == null)
        {
            // Relocated pieces must not leave invisible clickable tabs at the old position.
            if (canvasWidth > 0 && canvasHeight > 0 && !MobileHitTest.isMenuAt(x, y) && !MobileHitTest.isMenuOptionAt(x, y))
                for (Panel old : displayed)
                    if (old.geometry.source.contains((int)((long)x * old.geometry.width / canvasWidth),
                        (int)((long)y * old.geometry.height / canvasHeight))) return new int[]{-1, -1};
            return new int[]{x, y};
        }
        InventoryScaleGeometry g = panel.geometry;
        int px = (int)((long)x * g.width / canvasWidth), py = (int)((long)y * g.height / canvasHeight);
        return new int[]{(int)((long)g.sourceX(px) * canvasWidth / g.width),
            (int)((long)g.sourceY(py) * canvasHeight / g.height)};
    }
}
