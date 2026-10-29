package javax.swing.plaf.basic;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
public class BasicCheckBoxUI extends BasicRadioButtonUI {
    public static ComponentUI createUI(JComponent c){return new BasicCheckBoxUI();}
    protected String getPropertyPrefix(){return "CheckBox.";}
}
