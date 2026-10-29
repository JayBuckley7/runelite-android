package javax.swing.plaf.basic;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
public class BasicRadioButtonUI extends BasicToggleButtonUI {
    protected Icon icon;
    public static ComponentUI createUI(JComponent c){return new BasicRadioButtonUI();}
    protected String getPropertyPrefix(){return "RadioButton.";}
    protected void installDefaults(AbstractButton button){super.installDefaults(button);icon=UIManager.getIcon(getPropertyPrefix()+"icon");}
    public Icon getDefaultIcon(){return icon;}
    public void paint(java.awt.Graphics g,JComponent c){super.paint(g,c);if(c instanceof AbstractButton&&((AbstractButton)c).getIcon()==null&&icon!=null)icon.paintIcon(c,g,2,Math.max(0,(c.getHeight()-icon.getIconHeight())/2));}
}
