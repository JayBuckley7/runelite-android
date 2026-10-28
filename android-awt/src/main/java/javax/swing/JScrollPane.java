package javax.swing;

import java.awt.Component;
import javax.swing.border.Border;

public class JScrollPane extends JComponent implements ScrollPaneConstants {
    private static final long serialVersionUID = 1L;

    protected JViewport viewport;
    protected JScrollBar verticalScrollBar;
    protected JScrollBar horizontalScrollBar;
    protected int verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED;
    protected int horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_AS_NEEDED;
    private Border viewportBorder;
    private JViewport columnHeader;

    public JScrollPane() { init(); }
    public JScrollPane(Component view) { init(); setViewportView(view); }
    public JScrollPane(int vsbPolicy, int hsbPolicy) {
        this.verticalScrollBarPolicy = vsbPolicy;
        this.horizontalScrollBarPolicy = hsbPolicy;
        init();
    }
    public JScrollPane(Component view, int vsbPolicy, int hsbPolicy) {
        this(vsbPolicy, hsbPolicy);
        setViewportView(view);
    }

    /**
     * Swing's ScrollPane scrollbar: its unit/block increments come from a Scrollable view
     * (or the viewport extent for blocks) until set explicitly.
     */
    protected class ScrollBar extends JScrollBar implements javax.swing.plaf.UIResource {
        private static final long serialVersionUID = 1L;
        private boolean unitIncrementSet;
        private boolean blockIncrementSet;

        public ScrollBar(int orientation) { super(orientation); }

        @Override public void setUnitIncrement(int unitIncrement) {
            unitIncrementSet = true;
            super.setUnitIncrement(unitIncrement);
        }

        @Override public int getUnitIncrement(int direction) {
            JViewport vp = getViewport();
            if (!unitIncrementSet && vp != null && vp.getView() instanceof Scrollable) {
                return ((Scrollable) vp.getView()).getScrollableUnitIncrement(vp.getViewRect(), getOrientation(), direction);
            }
            return super.getUnitIncrement(direction);
        }

        @Override public void setBlockIncrement(int blockIncrement) {
            blockIncrementSet = true;
            super.setBlockIncrement(blockIncrement);
        }

        @Override public int getBlockIncrement(int direction) {
            JViewport vp = getViewport();
            if (blockIncrementSet || vp == null) return super.getBlockIncrement(direction);
            if (vp.getView() instanceof Scrollable) {
                return ((Scrollable) vp.getView()).getScrollableBlockIncrement(vp.getViewRect(), getOrientation(), direction);
            }
            return getOrientation() == VERTICAL ? vp.getExtentSize().height : vp.getExtentSize().width;
        }

        /**
         * On the desktop a revalidate is queued on the EDT ahead of the plugin's own
         * invokeLater, so `invokeLater(() -> bar.setValue(saved))` after rebuilding the
         * content sees the new range. Our layout pump runs on its own schedule, so lay the
         * pane out first (it is a validate root in Swing too) or the value clamps to the
         * stale range — quest-helper restores its scroll offset exactly this way.
         */
        @Override public void setValue(int value) {
            if (!JScrollPane.this.isValid() && JScrollPane.this.getWidth() > 0 && JScrollPane.this.getHeight() > 0) {
                try { JScrollPane.this.validate(); } catch (Throwable ignored) {}
            }
            super.setValue(value);
        }
    }

    public JScrollBar createVerticalScrollBar() { return new ScrollBar(JScrollBar.VERTICAL); }
    public JScrollBar createHorizontalScrollBar() { return new ScrollBar(JScrollBar.HORIZONTAL); }
    protected JViewport createViewport() { return new JViewport(); }

    // Viewport -> scrollbars (BasicScrollPaneUI.syncScrollPaneWithViewport) and back.
    // `syncing` stops the model events we raise from steering the viewport again.
    private boolean syncing;
    private final javax.swing.event.ChangeListener viewportListener = event -> {
        JViewport vp = viewport;
        if (vp == null) return;
        if (columnHeader != null) columnHeader.setViewPosition(new java.awt.Point(vp.getViewPosition().x, 0));
        syncScrollBarsWithViewport();
    };
    private final java.awt.event.AdjustmentListener verticalListener = e -> {
        JViewport vp = viewport;
        if (syncing || vp == null || verticalScrollBar == null) return;
        java.awt.Point p = vp.getViewPosition();
        p.y = verticalScrollBar.getValue();
        vp.setViewPosition(p);
    };
    private final java.awt.event.AdjustmentListener horizontalListener = e -> {
        JViewport vp = viewport;
        if (syncing || vp == null || horizontalScrollBar == null) return;
        java.awt.Point p = vp.getViewPosition();
        p.x = horizontalScrollBar.getValue();
        vp.setViewPosition(p);
    };

    private void syncScrollBarsWithViewport() {
        JViewport vp = viewport;
        if (vp == null || syncing) return;
        syncing = true;
        try {
            java.awt.Dimension extent = vp.getExtentSize();
            java.awt.Dimension viewSize = vp.getViewSize();
            java.awt.Point pos = vp.getViewPosition();
            if (verticalScrollBar != null) {
                int ext = extent.height, max = viewSize.height;
                verticalScrollBar.setValues(Math.max(0, Math.min(pos.y, max - ext)), ext, 0, max);
            }
            if (horizontalScrollBar != null) {
                int ext = extent.width, max = viewSize.width;
                horizontalScrollBar.setValues(Math.max(0, Math.min(pos.x, max - ext)), ext, 0, max);
            }
        } finally {
            syncing = false;
        }
    }

    private void init() {
        setOpaque(true);
        setViewport(createViewport());
        setVerticalScrollBar(createVerticalScrollBar());
        setHorizontalScrollBar(createHorizontalScrollBar());
        // Real Swing installs a MouseWheelListener that maps wheel rotations into
        // viewport scroll offsets. Mirror that here so dispatching a MOUSE_WHEEL
        // event on the scrollpane (or anywhere in its subtree, via the walk-up in
        // Compose's input bridge) actually scrolls the content.
        addMouseWheelListener(e -> {
            if (e == null || viewport == null) return;
            // Use the view's preferred row height (≈ a JLabel line) as the unit, or the
            // scrollbar's increment when the pane asks for more (RuneLite sets 16+).
            int unit = Math.max(16, getFont() == null ? 16 : getFontMetrics(getFont()).getHeight());
            if (verticalScrollBar != null) {
                try { unit = Math.max(unit, verticalScrollBar.getUnitIncrement(e.getWheelRotation() < 0 ? -1 : 1)); }
                catch (Throwable ignored) {}
            }
            int amount = e.getWheelRotation() * unit * 3;
            viewport.scrollBy(0, amount);
        });
    }

    @Override
    public void doLayout() {
        if (viewport != null && getWidth() > 0 && getHeight() > 0) {
            int headerHeight = columnHeader == null || columnHeader.getView() == null ? 0
                : Math.min(getHeight(), columnHeader.getView().getPreferredSize().height);
            if (columnHeader != null) columnHeader.setBounds(0, 0, getWidth(), headerHeight);
            viewport.setBounds(0, headerHeight, getWidth(), Math.max(0, getHeight() - headerHeight));
        }
    }

    /** ScrollPaneLayout's sizing: insets + the viewport's preferred size + the column header. */
    @Override
    public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        java.awt.Insets in = getInsets();
        java.awt.Dimension d = viewport == null ? new java.awt.Dimension() : viewport.getPreferredSize();
        int w = d.width, h = d.height;
        if (columnHeader != null && columnHeader.getView() != null) {
            java.awt.Dimension hd = columnHeader.getView().getPreferredSize();
            h += hd.height;
            w = Math.max(w, hd.width);
        }
        return new java.awt.Dimension(w + in.left + in.right, h + in.top + in.bottom);
    }

    @Override
    public java.awt.Dimension getMinimumSize() {
        if (isMinimumSizeSet()) return super.getMinimumSize();
        java.awt.Insets in = getInsets();
        java.awt.Dimension d = viewport == null ? new java.awt.Dimension() : viewport.getMinimumSize();
        return new java.awt.Dimension(d.width + in.left + in.right, d.height + in.top + in.bottom);
    }

    public JViewport getViewport() { return viewport; }
    public void setViewport(JViewport viewport) {
        JViewport old = this.viewport;
        if (old == viewport) return;
        if (old != null) {
            old.removeChangeListener(viewportListener);
            remove(old);
        }
        this.viewport = viewport;
        if (viewport != null) {
            add(viewport, 0);
            viewport.addChangeListener(viewportListener);
        }
        syncScrollBarsWithViewport();
        revalidate();
    }
    public void setViewportView(Component view) {
        if (viewport == null) setViewport(createViewport());
        viewport.setView(view);
        if (view instanceof JTable) {
            JViewport header = new JViewport();
            header.setView(((JTable) view).getTableHeader());
            setColumnHeader(header);
        } else setColumnHeader(null);
    }
    public JScrollBar getVerticalScrollBar() { return verticalScrollBar; }
    public void setVerticalScrollBar(JScrollBar sb) {
        if (verticalScrollBar == sb) return;
        if (verticalScrollBar != null) verticalScrollBar.removeAdjustmentListener(verticalListener);
        verticalScrollBar = sb;
        if (sb != null) sb.addAdjustmentListener(verticalListener);
        syncScrollBarsWithViewport();
    }
    public JScrollBar getHorizontalScrollBar() { return horizontalScrollBar; }
    public void setHorizontalScrollBar(JScrollBar sb) {
        if (horizontalScrollBar == sb) return;
        if (horizontalScrollBar != null) horizontalScrollBar.removeAdjustmentListener(horizontalListener);
        horizontalScrollBar = sb;
        if (sb != null) sb.addAdjustmentListener(horizontalListener);
        syncScrollBarsWithViewport();
    }
    public int getVerticalScrollBarPolicy() { return verticalScrollBarPolicy; }
    public void setVerticalScrollBarPolicy(int policy) { this.verticalScrollBarPolicy = policy; }
    public int getHorizontalScrollBarPolicy() { return horizontalScrollBarPolicy; }
    public void setHorizontalScrollBarPolicy(int policy) { this.horizontalScrollBarPolicy = policy; }
    public Border getViewportBorder() { return viewportBorder; }
    public void setViewportBorder(Border viewportBorder) { this.viewportBorder = viewportBorder; }
    public JViewport getRowHeader() { return null; }
    public void setRowHeader(JViewport rowHeader) {}
    public void setColumnHeaderView(Component view) {
        if (columnHeader == null) setColumnHeader(new JViewport());
        columnHeader.setView(view); revalidate(); repaint();
    }
    public JViewport getColumnHeader() { return columnHeader; }
    public void setColumnHeader(JViewport header) {
        if (columnHeader != null) remove(columnHeader);
        columnHeader = header;
        if (header != null) add(header);
        revalidate(); repaint();
    }
    public Component getCorner(String key) { return null; }
    public void setCorner(String key, Component corner) {}
    public void setWheelScrollingEnabled(boolean enable) {}
    public boolean isWheelScrollingEnabled() { return true; }
}
