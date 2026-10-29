package javax.swing.plaf.basic;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
public class BasicToggleButtonUI extends BasicButtonUI {
    public static ComponentUI createUI(JComponent c){return new BasicToggleButtonUI();}
    protected String getPropertyPrefix(){return "ToggleButton.";}
}
