package javax.swing;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import javax.swing.border.Border;
import javax.swing.event.AncestorListener;

public class JComponent extends Container {
    private static final long serialVersionUID = 8202245103466131933L;

    public static final int WHEN_FOCUSED = 0;
    public static final int WHEN_ANCESTOR_OF_FOCUSED_COMPONENT = 1;
    public static final int WHEN_IN_FOCUSED_WINDOW = 2;
    public static final int UNDEFINED_CONDITION = -1;

    public static final String TOOL_TIP_TEXT_KEY = "ToolTipText";

    protected javax.swing.plaf.ComponentUI ui;

    private Border border;
    private String toolTipText;
    private boolean doubleBuffered = true;
    private boolean opaque;
    private float alignmentX = 0.5f;
    private float alignmentY = 0.5f;

    public JComponent() {}

    public Border getBorder() { return border; }
    // Border drives getInsets(), which every layout manager reads — a border change is a
    // layout change.
    public void setBorder(Border border) { this.border = border; invalidate(); }

    public String getToolTipText() { return toolTipText; }
    public void setToolTipText(String text) { this.toolTipText = text; }

    // Sizing is inherited from Component — JComponent used to keep parallel `preferred` /
    // `minimum` / `maximum` fields, but that made `isPreferredSizeSet()` (which reads
    // Component.preferredSize) return false even after JComponent.setPreferredSize. That
    // caused AbstractButton / JLabel to skip the explicit override and fall through to
    // their icon-based fallback — e.g. config + toggle in PluginListItem reported 50px
    // wide despite both being set to 25, eating into the plugin name label. Removing the
    // shadows fixes that for every JComponent subclass.

    /**
     * Swing's resolution when nothing was set explicitly: a container answers from its
     * layout manager (even 0x0, as the JDK does), and a leaf from its preferred size — the
     * ComponentUI default that the label/list/combo delegates inherit. Component's "current
     * size" fallback made BoxLayout's min-driven shrinking feed on its own output.
     */
    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) return super.getMinimumSize();
        java.awt.LayoutManager lm = getLayout();
        if (lm != null) {
            Dimension d = lm.minimumLayoutSize(this);
            if (d != null) return d;
        }
        return getPreferredSize();
    }

    /** Container.getMaximumSize: a LayoutManager2 answers, otherwise Component's
     *  Short.MAX_VALUE square. Labels and buttons override this with their preferred size. */
    @Override
    public Dimension getMaximumSize() {
        if (isMaximumSizeSet()) return super.getMaximumSize();
        java.awt.LayoutManager lm = getLayout();
        if (lm instanceof java.awt.LayoutManager2) {
            Dimension d = ((java.awt.LayoutManager2) lm).maximumLayoutSize(this);
            if (d != null) return d;
        }
        return new Dimension(Short.MAX_VALUE, Short.MAX_VALUE);
    }

    public boolean isDoubleBuffered() { return doubleBuffered; }
    public void setDoubleBuffered(boolean d) { this.doubleBuffered = d; }
    public boolean isOpaque() { return opaque; }
    public void setOpaque(boolean o) { this.opaque = o; }

    public float getAlignmentX() { return alignmentX; }
    public void setAlignmentX(float f) { this.alignmentX = f; }
    public float getAlignmentY() { return alignmentY; }
    public void setAlignmentY(float f) { this.alignmentY = f; }

    public void addPropertyChangeListener(PropertyChangeListener l) {}
    public void removePropertyChangeListener(PropertyChangeListener l) {}
    public void addPropertyChangeListener(String name, PropertyChangeListener l) {}
    public void removePropertyChangeListener(String name, PropertyChangeListener l) {}
    public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}

    public void addAncestorListener(AncestorListener l) {}
    public void removeAncestorListener(AncestorListener l) {}

    public javax.swing.plaf.ComponentUI getUI() { return ui; }
    public void setUI(javax.swing.plaf.ComponentUI ui) { this.ui = ui; }
    public String getUIClassID() { return "ComponentUI"; }

    public void updateUI() {}

    public void repaint(java.awt.Rectangle r) {
        repaint(0, r.x, r.y, r.width, r.height);
    }

    public ActionMap getActionMap() { return new ActionMap(); }
    public void setActionMap(ActionMap am) {}
    public InputMap getInputMap() { return new InputMap(); }
    public InputMap getInputMap(int condition) { return new InputMap(); }
    public void setInputMap(int condition, InputMap map) {}

    public void registerKeyboardAction(ActionListener anAction, KeyStroke aKeyStroke, int aCondition) {}
    public void registerKeyboardAction(ActionListener anAction, String aCommand, KeyStroke aKeyStroke, int aCondition) {}
    public void unregisterKeyboardAction(KeyStroke aKeyStroke) {}

    private java.util.Map<Object, Object> clientProperties;
    public void putClientProperty(Object key, Object value) {
        if (clientProperties == null) clientProperties = new java.util.HashMap<>();
        if (value == null) clientProperties.remove(key);
        else clientProperties.put(key, value);
        onClientPropertySet(key, value);
    }
    public Object getClientProperty(Object key) {
        return clientProperties != null ? clientProperties.get(key) : null;
    }
    /** Subclass hook. Called whenever a client property is set, after the value is stored. */
    protected void onClientPropertySet(Object key, Object value) {}

    /**
     * Swing forwards to the nearest JComponent ancestor in its coordinates; JViewport
     * overrides this to do the actual scrolling.
     */
    public void scrollRectToVisible(java.awt.Rectangle aRect) {
        if (aRect == null) return;
        int dx = getX(), dy = getY();
        Container parent = getParent();
        while (parent != null && !(parent instanceof JComponent)) {
            dx += parent.getX();
            dy += parent.getY();
            parent = parent.getParent();
        }
        if (parent != null) {
            aRect.x += dx;
            aRect.y += dy;
            ((JComponent) parent).scrollRectToVisible(aRect);
            aRect.x -= dx;
            aRect.y -= dy;
        }
    }

    /** Swing's visible rect: our bounds intersected with each ancestor's, in our coordinates. */
    public java.awt.Rectangle getVisibleRect() {
        java.awt.Rectangle r = new java.awt.Rectangle(0, 0, getWidth(), getHeight());
        int ox = 0, oy = 0;
        java.awt.Component c = this;
        Container p = getParent();
        while (p != null && !(p instanceof java.awt.Window)) {
            ox -= c.getX();
            oy -= c.getY();
            r = r.intersection(new java.awt.Rectangle(ox, oy, p.getWidth(), p.getHeight()));
            c = p;
            p = p.getParent();
        }
        if (r.width < 0 || r.height < 0) r.setSize(0, 0);
        return r;
    }

    public boolean requestFocusInWindow() { return false; }
    public void grabFocus() {}
    public void resetKeyboardActions() {}

    public boolean getInheritsPopupMenu() { return false; }
    public void setInheritsPopupMenu(boolean value) {}

    public java.awt.Insets getInsets() { return border != null ? border.getBorderInsets(this) : super.getInsets(); }

    private JPopupMenu componentPopupMenu;
    public void setComponentPopupMenu(JPopupMenu menu) { this.componentPopupMenu = menu; }
    public JPopupMenu getComponentPopupMenu() { return componentPopupMenu; }

    @Override
    public void paint(java.awt.Graphics g) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        if (opaque) {
            java.awt.Color saved = g.getColor();
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (saved != null) g.setColor(saved);
        }
        paintComponent(g);
        paintBorder(g);
        paintChildren(g);
    }

    protected void paintComponent(java.awt.Graphics g) {
        // Subclass hook — JLabel/JButton/etc draw text, icons, etc here.
    }

    protected void paintBorder(java.awt.Graphics g) {
        if (border != null) {
            border.paintBorder(this, g, 0, 0, getWidth(), getHeight());
        }
    }

    /** Host-side bridges so the Compose paint walker can invoke our protected paint hooks. */
    public final void hostPaintComponent(java.awt.Graphics g) { paintComponent(g); }
    public final void hostPaintBorder(java.awt.Graphics g) { paintBorder(g); }
}
