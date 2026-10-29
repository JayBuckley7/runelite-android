package javax.swing.plaf.basic;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.LabelUI;

public class BasicLabelUI extends LabelUI {
    public BasicLabelUI() {}
    @Override public void paint(java.awt.Graphics g,JComponent c) { c.hostPaintDefaultComponent(g); }
    public static ComponentUI createUI(JComponent c) { return new BasicLabelUI(); }
}
