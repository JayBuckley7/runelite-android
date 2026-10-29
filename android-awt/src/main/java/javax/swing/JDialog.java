package javax.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;

public class JDialog extends Dialog implements RootPaneContainer {
    private static final long serialVersionUID = 1L;

    public static final int DO_NOTHING_ON_CLOSE = 0;
    public static final int HIDE_ON_CLOSE = 1;
    public static final int DISPOSE_ON_CLOSE = 2;

    protected JRootPane rootPane = new JRootPane();
    protected int defaultCloseOperation = HIDE_ON_CLOSE;

    public JDialog() { this((Frame) null); }
    public JDialog(Frame owner) { this(owner, "", false); }
    public JDialog(Frame owner, boolean modal) { this(owner, "", modal); }
    public JDialog(Frame owner, String title) { this(owner, title, false); }
    public JDialog(Frame owner, String title, boolean modal) { this((Window) owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS); }
    public JDialog(Frame owner, String title, boolean modal, java.awt.GraphicsConfiguration gc) { this((Window) owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS, gc); }
    public JDialog(Dialog owner) { this(owner, "", false); }
    public JDialog(Dialog owner, boolean modal) { this(owner, "", modal); }
    public JDialog(Dialog owner, String title) { this(owner, title, false); }
    public JDialog(Dialog owner, String title, boolean modal) { this((Window) owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS); }
    public JDialog(Dialog owner, String title, boolean modal, java.awt.GraphicsConfiguration gc) { this((Window) owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS, gc); }
    public JDialog(Window owner) { this(owner, "", ModalityType.MODELESS); }
    public JDialog(Window owner, String title) { this(owner, title, ModalityType.MODELESS); }
    public JDialog(Window owner, ModalityType modality) { this(owner, "", modality); }
    public JDialog(Window owner, String title, ModalityType modality) {
        super(owner, title, modality); initDialog();
    }
    public JDialog(Window owner, String title, ModalityType modality, java.awt.GraphicsConfiguration gc) {
        super(owner, title, modality, gc); initDialog();
    }

    private void initDialog() {
        super.setLayout(new java.awt.BorderLayout());
        super.add(rootPane, -1);
    }
    @Override public Component add(Component c) { return c == rootPane ? super.add(c) : getContentPane().add(c); }
    @Override public Component add(Component c, int index) { return c == rootPane ? super.add(c, index) : getContentPane().add(c, index); }
    @Override public void add(Component c, Object constraints) { getContentPane().add(c, constraints); }
    @Override public Component add(String name, Component c) { return getContentPane().add(name, c); }
    @Override public void add(Component c, Object constraints, int index) { getContentPane().add(c, constraints, index); }
    @Override public void remove(Component c) { if (c == rootPane) super.remove(c); else getContentPane().remove(c); }
    @Override public void setLayout(java.awt.LayoutManager layout) {
        if (rootPane == null) super.setLayout(layout); else getContentPane().setLayout(layout);
    }
    @Override public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        if (rootPane != null) rootPane.setBounds(0, 0, width, height);
    }
    @Override protected void processWindowEvent(java.awt.event.WindowEvent event) {
        super.processWindowEvent(event);
        if (event.getID() == java.awt.event.WindowEvent.WINDOW_CLOSING) {
            if (defaultCloseOperation == HIDE_ON_CLOSE) setVisible(false);
            else if (defaultCloseOperation == DISPOSE_ON_CLOSE) dispose();
        }
    }

    public int getDefaultCloseOperation() { return defaultCloseOperation; }
    public void setDefaultCloseOperation(int op) {
        if (op < DO_NOTHING_ON_CLOSE || op > DISPOSE_ON_CLOSE) throw new IllegalArgumentException("Invalid close operation");
        int previous = defaultCloseOperation; defaultCloseOperation = op;
        firePropertyChange("defaultCloseOperation", previous, op);
    }
    @Override public JRootPane getRootPane() { return rootPane; }
    @Override public Container getContentPane() { return rootPane.getContentPane(); }
    @Override public void setContentPane(Container c) { rootPane.setContentPane(c); }
    @Override public JLayeredPane getLayeredPane() { return rootPane.getLayeredPane(); }
    @Override public void setLayeredPane(JLayeredPane lp) { rootPane.setLayeredPane(lp); }
    @Override public Component getGlassPane() { return rootPane.getGlassPane(); }
    @Override public void setGlassPane(Component g) { rootPane.setGlassPane(g); }
    public JMenuBar getJMenuBar() { return rootPane.getJMenuBar(); }
    public void setJMenuBar(JMenuBar mb) { rootPane.setJMenuBar(mb); }
}
