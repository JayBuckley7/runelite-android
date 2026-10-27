package javax.swing;
import java.awt.*;
public class JWindow extends Window implements RootPaneContainer {
    protected JRootPane rootPane = new JRootPane();
    public JWindow() { this((Window) null); }
    public JWindow(Frame owner) { this((Window) owner); }
    public JWindow(Window owner) { super((Frame) null); setLayout(new BorderLayout()); super.add(rootPane); setVisible(false); }
    public JRootPane getRootPane() { return rootPane; }
    public Container getContentPane() { return rootPane.getContentPane(); }
    public void setContentPane(Container c) { rootPane.setContentPane(c); }
    public JLayeredPane getLayeredPane() { return rootPane.getLayeredPane(); }
    public void setLayeredPane(JLayeredPane p) { rootPane.setLayeredPane(p); }
    public Component getGlassPane() { return rootPane.getGlassPane(); }
    public void setGlassPane(Component c) { rootPane.setGlassPane(c); }
    @Override public Component add(Component c) { return rootPane == null ? super.add(c) : getContentPane().add(c); }
}
