package javax.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

public class JMenuBar extends JComponent {
    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_HEIGHT = 28;
    private SingleSelectionModel selectionModel = new DefaultSingleSelectionModel();
    private boolean borderPainted = true;
    private java.awt.Insets margin = new java.awt.Insets(0, 0, 0, 0);

    public JMenuBar() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setOpaque(true);
        setBackground(new Color(0x3A3D43));
        setPreferredSize(new Dimension(0, DEFAULT_HEIGHT));
        setMinimumSize(new Dimension(0, DEFAULT_HEIGHT));
    }

    public JMenu add(JMenu c) {
        if (c != null) super.add(c);
        return c;
    }

    public JMenu getMenu(int index) {
        Component c = index >= 0 && getComponentCount() > index ? getComponent(index) : null;
        return c instanceof JMenu ? (JMenu) c : null;
    }

    public int getMenuCount() { return getComponentCount(); }
    public SingleSelectionModel getSelectionModel() { return selectionModel; }
    public void setSelectionModel(SingleSelectionModel model) {
        if (model == null) throw new IllegalArgumentException("selection model");
        SingleSelectionModel old = selectionModel; selectionModel = model;
        firePropertyChange("selectionModel", old, model); repaint();
    }
    public void setSelected(Component sel) {
        int index = -1;
        for (int i = 0; i < getComponentCount(); i++) if (getComponent(i) == sel) { index = i; break; }
        selectionModel.setSelectedIndex(index);
    }
    public boolean isSelected() { return selectionModel.isSelected(); }
    public void remove(int index) {
        int selected = selectionModel.getSelectedIndex(); super.remove(index);
        if (selected == index) selectionModel.clearSelection();
        else if (selected > index) selectionModel.setSelectedIndex(selected - 1);
    }
    public void remove(Component component) { for (int i = 0; i < getComponentCount(); i++) if (getComponent(i) == component) { remove(i); return; } }
    public void removeAll() { super.removeAll(); selectionModel.clearSelection(); }
    public void setBorderPainted(boolean b) { boolean old=borderPainted; borderPainted=b; firePropertyChange("borderPainted",old,b); revalidate(); repaint(); }
    public boolean isBorderPainted() { return borderPainted; }
    protected void paintBorder(java.awt.Graphics graphics) { if (borderPainted) super.paintBorder(graphics); }
    public boolean isManagingFocus() { return false; }
    public java.awt.Insets getMargin() { return (java.awt.Insets) margin.clone(); }
    public void setMargin(java.awt.Insets m) { java.awt.Insets old=margin; margin=m==null?new java.awt.Insets(0,0,0,0):(java.awt.Insets)m.clone(); firePropertyChange("margin",old,margin); revalidate(); repaint(); }
    public boolean isFloatable() { return false; }
}
