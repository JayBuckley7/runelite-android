package javax.swing;

import java.util.ArrayList;
import java.util.Vector;
import javax.swing.event.ListSelectionListener;

public class JList<E> extends JComponent {
    private static final long serialVersionUID = -1697842831055920921L;

    public static final int VERTICAL = 0;
    public static final int VERTICAL_WRAP = 1;
    public static final int HORIZONTAL_WRAP = 2;

    private ListModel<E> model = new DefaultListModel<>();
    private ListCellRenderer<? super E> renderer = new DefaultListCellRenderer();
    private int selectionMode = 0;
    private int selectedIndex = -1;
    private int layoutOrientation = VERTICAL;
    private int visibleRowCount = 8;
    private int fixedCellWidth = -1, fixedCellHeight = -1;

    public JList() {}
    public JList(ListModel<E> model) { this.model = model; }
    public JList(E[] listData) {
        DefaultListModel<E> m = new DefaultListModel<>();
        for (E e : listData) m.addElement(e);
        this.model = m;
    }
    public JList(Vector<? extends E> listData) {
        DefaultListModel<E> m = new DefaultListModel<>();
        for (E e : listData) m.addElement(e);
        this.model = m;
    }

    public ListModel<E> getModel() { return model; }
    public void setModel(ListModel<E> model) { this.model = model; invalidate(); }
    public void setListData(E[] listData) {
        DefaultListModel<E> m = new DefaultListModel<>();
        for (E e : listData) m.addElement(e);
        this.model = m;
    }
    public void setListData(Vector<? extends E> listData) {
        DefaultListModel<E> m = new DefaultListModel<>();
        for (E e : listData) m.addElement(e);
        this.model = m;
    }

    public ListCellRenderer<? super E> getCellRenderer() { return renderer; }
    public void setCellRenderer(ListCellRenderer<? super E> r) { this.renderer = r; }

    public int getSelectedIndex() { return selectedIndex; }
    public void setSelectedIndex(int index) {
        if (index == selectedIndex) return;
        int old = selectedIndex;
        selectedIndex = index;
        fireSelectionChanged(Math.min(old, index), Math.max(old, index));
    }
    public void setSelectedIndices(int[] indices) {
        // We track a single index — pick the first.
        setSelectedIndex(indices == null || indices.length == 0 ? -1 : indices[0]);
    }
    public int[] getSelectedIndices() { return selectedIndex < 0 ? new int[0] : new int[]{selectedIndex}; }
    public E getSelectedValue() { return selectedIndex < 0 ? null : model.getElementAt(selectedIndex); }
    public void setSelectedValue(Object o, boolean scroll) {
        if (model == null) return;
        for (int i = 0; i < model.getSize(); i++) {
            if (java.util.Objects.equals(model.getElementAt(i), o)) { setSelectedIndex(i); return; }
        }
        setSelectedIndex(-1);
    }
    public java.util.List<E> getSelectedValuesList() {
        ArrayList<E> r = new ArrayList<>();
        if (selectedIndex >= 0) r.add(model.getElementAt(selectedIndex));
        return r;
    }

    public int getSelectionMode() { return selectionMode; }
    public void setSelectionMode(int mode) { this.selectionMode = mode; }
    public int getLayoutOrientation() { return layoutOrientation; }
    public void setLayoutOrientation(int o) { this.layoutOrientation = o; }
    public int getVisibleRowCount() { return visibleRowCount; }
    public void setVisibleRowCount(int n) { this.visibleRowCount = n; }
    public int getFixedCellWidth() { return fixedCellWidth; }
    public void setFixedCellWidth(int w) { this.fixedCellWidth = w; }
    public int getFixedCellHeight() { return fixedCellHeight; }
    public void setFixedCellHeight(int h) { this.fixedCellHeight = h; }

    private final java.util.List<ListSelectionListener> selectionListeners = new java.util.ArrayList<>();
    public void addListSelectionListener(ListSelectionListener l) { if (l != null) selectionListeners.add(l); }
    public void removeListSelectionListener(ListSelectionListener l) { selectionListeners.remove(l); }
    public ListSelectionListener[] getListSelectionListeners() { return selectionListeners.toArray(new ListSelectionListener[0]); }

    private void fireSelectionChanged(int firstIndex, int lastIndex) {
        javax.swing.event.ListSelectionEvent ev = new javax.swing.event.ListSelectionEvent(
            this, Math.max(0, firstIndex), Math.max(0, lastIndex), false);
        for (ListSelectionListener l : new java.util.ArrayList<>(selectionListeners)) {
            try { l.valueChanged(ev); } catch (Throwable ignored) {}
        }
    }

    /** Geometry is shared by painting and hit testing, including variable-height renderers. */
    private java.awt.Rectangle[] cellBounds() {
        int count = model == null ? 0 : model.getSize();
        java.awt.Rectangle[] cells = new java.awt.Rectangle[count];
        java.awt.Insets in = getInsets();
        int cw = Math.max(1, fixedCellWidth), ch = Math.max(1, fixedCellHeight);
        int[] heights = new int[count];
        for (int i = 0; i < count; i++) {
            java.awt.Component c = renderer == null ? null : renderer.getListCellRendererComponent(
                this, model.getElementAt(i), i, i == selectedIndex, false);
            java.awt.Dimension d = c == null ? new java.awt.Dimension(0, 16) : c.getPreferredSize();
            if (fixedCellWidth < 0) cw = Math.max(cw, d.width);
            heights[i] = fixedCellHeight >= 0 ? fixedCellHeight : Math.max(1, d.height);
            ch = Math.max(ch, heights[i]);
        }
        if (layoutOrientation == VERTICAL) {
            int y = in.top;
            int w = Math.max(cw, getWidth() - in.left - in.right);
            for (int i = 0; i < count; i++) {
                cells[i] = new java.awt.Rectangle(in.left, y, w, heights[i]);
                y += heights[i];
            }
        } else {
            int rows, columns;
            if (visibleRowCount > 0) {
                rows = visibleRowCount;
                columns = Math.max(1, (count + rows - 1) / rows);
            } else if (layoutOrientation == HORIZONTAL_WRAP) {
                columns = Math.max(1, (getWidth() - in.left - in.right) / cw);
                rows = Math.max(1, (count + columns - 1) / columns);
            } else {
                rows = Math.max(1, (getHeight() - in.top - in.bottom) / ch);
                columns = Math.max(1, (count + rows - 1) / rows);
            }
            for (int i = 0; i < count; i++) {
                int col = layoutOrientation == HORIZONTAL_WRAP ? i % columns : i / rows;
                int row = layoutOrientation == HORIZONTAL_WRAP ? i / columns : i % rows;
                cells[i] = new java.awt.Rectangle(in.left + col * cw, in.top + row * ch, cw, ch);
            }
        }
        return cells;
    }

    public java.awt.Rectangle getCellBounds(int index0, int index1) {
        java.awt.Rectangle[] cells = cellBounds();
        int first = Math.min(index0, index1), last = Math.max(index0, index1);
        if (first < 0 || first >= cells.length) return null;
        java.awt.Rectangle bounds = new java.awt.Rectangle(cells[first]);
        for (int i = first + 1; i <= Math.min(last, cells.length - 1); i++) bounds = bounds.union(cells[i]);
        return bounds;
    }
    public int locationToIndex(java.awt.Point point) {
        java.util.Objects.requireNonNull(point);
        java.awt.Rectangle[] cells = cellBounds();
        int nearest = -1;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < cells.length; i++) {
            java.awt.Rectangle b = cells[i];
            double dx = Math.max(b.x - point.x, Math.max(0, point.x - (b.x + b.width - 1)));
            double dy = Math.max(b.y - point.y, Math.max(0, point.y - (b.y + b.height - 1)));
            double d = dx * dx + dy * dy;
            if (d < distance) { distance = d; nearest = i; }
        }
        return nearest;
    }
    @Override public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        java.awt.Rectangle[] cells = cellBounds();
        java.awt.Insets in = getInsets();
        int w = in.left, h = in.top;
        for (java.awt.Rectangle b : cells) { w = Math.max(w, b.x + b.width); h = Math.max(h, b.y + b.height); }
        return new java.awt.Dimension(w + in.right, h + in.bottom);
    }
    @Override protected void paintComponent(java.awt.Graphics g) {
        if (isOpaque()) { g.setColor(getBackground()); g.fillRect(0, 0, getWidth(), getHeight()); }
        if (renderer == null) return;
        java.awt.Rectangle[] cells = cellBounds();
        java.awt.Rectangle clip = g.getClipBounds();
        for (int i = 0; i < cells.length; i++) {
            java.awt.Rectangle b = cells[i];
            if (clip != null && !clip.intersects(b)) continue;
            java.awt.Component c = renderer.getListCellRendererComponent(this, model.getElementAt(i), i, i == selectedIndex, false);
            if (c == null) continue;
            c.setBounds(0, 0, b.width, b.height);
            if (c instanceof java.awt.Container) ((java.awt.Container) c).validate();
            java.awt.Graphics child = g.create(b.x, b.y, b.width, b.height);
            try { c.paint(child); } finally { child.dispose(); }
        }
    }

    public void clearSelection() { setSelectedIndex(-1); }
    public void ensureIndexIsVisible(int index) {}
}
