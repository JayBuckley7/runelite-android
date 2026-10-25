package javax.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.event.ChangeListener;

public class JViewport extends JComponent {
    private static final long serialVersionUID = 1L;

    public static final int BLIT_SCROLL_MODE = 1;
    public static final int BACKINGSTORE_SCROLL_MODE = 2;
    public static final int SIMPLE_SCROLL_MODE = 3;

    private Component view;
    private final java.util.List<ChangeListener> changeListeners = new java.util.ArrayList<>();
    private int scrollMode = BLIT_SCROLL_MODE;
    private final Point viewPosition = new Point();

    public JViewport() {}

    public Component getView() { return view; }
    public void setView(Component view) {
        if (this.view != null) super.remove(this.view);
        this.view = view;
        if (view != null) super.add(view);
        // Swing derives the position from the view's location, so a new view (at 0,0)
        // starts scrolled to the top.
        viewPosition.setLocation(0, 0);
        if (getWidth() > 0 && getHeight() > 0) doLayout();
        else fireStateChanged();
        invalidate();
    }
    public Dimension getViewSize() { return view == null ? new Dimension() : view.getSize(); }
    public void setViewSize(Dimension newSize) {
        if (view == null || newSize == null || newSize.equals(view.getSize())) return;
        view.setSize(newSize);
        viewPosition.setLocation(clampX(viewPosition.x), clampY(viewPosition.y));
        applyScroll(true);
    }
    public Point getViewPosition() { return new Point(viewPosition); }
    /** Like Swing, an unchanged position fires nothing — JScrollPane's scrollbar sync
     *  echoes positions back here and relies on that to settle. */
    public void setViewPosition(Point p) {
        if (p == null) return;
        int x = clampX(p.x), y = clampY(p.y);
        if (x == viewPosition.x && y == viewPosition.y) return;
        viewPosition.setLocation(x, y);
        applyScroll(true);
    }
    public Rectangle getViewRect() {
        return new Rectangle(viewPosition.x, viewPosition.y, getWidth(), getHeight());
    }
    public Dimension getExtentSize() { return getSize(); }

    /** Scroll by (dx, dy) in view-coords, clamped to the view's bounds. */
    public void scrollBy(int dx, int dy) {
        setViewPosition(new Point(viewPosition.x + dx, viewPosition.y + dy));
    }

    private int clampX(int x) {
        if (view == null) return 0;
        int max = Math.max(0, view.getWidth() - getWidth());
        return Math.max(0, Math.min(x, max));
    }
    private int clampY(int y) {
        if (view == null) return 0;
        int max = Math.max(0, view.getHeight() - getHeight());
        return Math.max(0, Math.min(y, max));
    }

    private void applyScroll(boolean fire) {
        if (view == null) return;
        // The viewport clips its children to its own bounds (see Window.hostPaint),
        // so positioning the view at negative coords slides the content under the
        // viewport without painting outside.
        view.setLocation(-viewPosition.x, -viewPosition.y);
        if (fire) fireStateChanged();
    }

    protected void fireStateChanged() {
        if (changeListeners.isEmpty()) return;
        javax.swing.event.ChangeEvent event = new javax.swing.event.ChangeEvent(this);
        for (ChangeListener listener : new java.util.ArrayList<>(changeListeners)) listener.stateChanged(event);
    }

    @Override
    public void doLayout() {
        if (view != null && getWidth() > 0 && getHeight() > 0) {
            // Size the view to its preferred height (or at least viewport height) so
            // scrollable content can grow; horizontal stays clamped to viewport.
            Dimension pref = view.getPreferredSize();
            int h = Math.max(getHeight(), pref.height);
            boolean wideTable = view instanceof JTable || view instanceof javax.swing.table.JTableHeader;
            view.setSize(wideTable ? Math.max(getWidth(), pref.width) : getWidth(), h);
            // Re-clamp + re-apply scroll in case the view shrunk. Always fires: the extent
            // or view size may have changed even when the position didn't, and the
            // scroll pane's scrollbars track all three.
            viewPosition.setLocation(clampX(viewPosition.x), clampY(viewPosition.y));
            applyScroll(true);
        }
    }
    /**
     * ViewportLayout's sizing: the view's preferred size (its Scrollable viewport size when it
     * has one). Without a layout manager this fell through to Component's "current size", so
     * a viewport kept whatever height it was last given — quest-helper's assist picker, laid
     * out at the quest list's height, reported that back as preferred and never shrank.
     */
    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        if (view == null) return new Dimension();
        if (view instanceof Scrollable) return ((Scrollable) view).getPreferredScrollableViewportSize();
        return view.getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) return super.getMinimumSize();
        return new Dimension(4, 4);
    }

    public void setExtentSize(Dimension newExtent) {}
    public int getScrollMode() { return scrollMode; }
    public void setScrollMode(int mode) { this.scrollMode = mode; }
    public void addChangeListener(ChangeListener l) { if (l != null) changeListeners.add(l); }
    public void removeChangeListener(ChangeListener l) { changeListeners.remove(l); }
    public ChangeListener[] getChangeListeners() { return changeListeners.toArray(new ChangeListener[0]); }

    /**
     * Swing's minimal scroll: contentRect is in this viewport's coordinates (JComponent's
     * walk-up adds the view's negative offset), and the view moves just far enough to show
     * it — or its leading edge when it is bigger than the extent. Does not propagate to an
     * enclosing viewport, matching the JDK.
     */
    @Override
    public void scrollRectToVisible(Rectangle contentRect) {
        if (view == null || contentRect == null) return;
        if (!view.isValid() && getWidth() > 0 && getHeight() > 0) {
            doLayout();
            view.validate();
        }
        int dx = positionAdjustment(getWidth(), contentRect.width, contentRect.x);
        int dy = positionAdjustment(getHeight(), contentRect.height, contentRect.y);
        if (dx == 0 && dy == 0) return;
        Point p = getViewPosition();
        Dimension viewSize = view.getSize();
        Dimension extent = getExtentSize();
        p.x -= dx;
        p.y -= dy;
        if (p.x + extent.width > viewSize.width) p.x = Math.max(0, viewSize.width - extent.width);
        else if (p.x < 0) p.x = 0;
        if (extent.height > viewSize.height) p.y = 0;
        else if (p.y + extent.height > viewSize.height) p.y = Math.max(0, viewSize.height - extent.height);
        else if (p.y < 0) p.y = 0;
        setViewPosition(p);
    }

    private static int positionAdjustment(int parentWidth, int childWidth, int childAt) {
        if (childAt >= 0 && childWidth + childAt <= parentWidth) return 0;
        if (childAt <= 0 && childWidth + childAt >= parentWidth) return 0;
        if (childAt > 0 && childWidth <= parentWidth) return -childAt + parentWidth - childWidth;
        if (childAt >= 0 && childWidth >= parentWidth) return -childAt;
        if (childAt <= 0 && childWidth <= parentWidth) return -childAt;
        if (childAt < 0 && childWidth >= parentWidth) return -childAt + parentWidth - childWidth;
        return 0;
    }
}
