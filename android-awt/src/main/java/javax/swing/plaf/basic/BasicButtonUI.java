package javax.swing.plaf.basic;
import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.*;
/** Shared button delegate; platform painting remains in the control adapter. */
public class BasicButtonUI extends ButtonUI {
    protected int defaultTextIconGap=4;
    protected int defaultTextShiftOffset;
    protected String getPropertyPrefix(){return "Button.";}
    public static ComponentUI createUI(JComponent c){return new BasicButtonUI();}
    public void installUI(JComponent c){if(c instanceof AbstractButton)installDefaults((AbstractButton)c);}
    public void uninstallUI(JComponent c){if(c instanceof AbstractButton)uninstallDefaults((AbstractButton)c);}
    protected void installDefaults(AbstractButton button){String prefix=getPropertyPrefix();LookAndFeel.installColorsAndFont(button,prefix+"background",prefix+"foreground",prefix+"font");LookAndFeel.installBorder(button,prefix+"border");}
    protected void uninstallDefaults(AbstractButton button){LookAndFeel.uninstallBorder(button);}
    public void paint(Graphics g,JComponent c){c.hostPaintDefaultComponent(g);}
    public Dimension getPreferredSize(JComponent c){return c.hostDefaultPreferredSize();}
    public int getDefaultTextIconGap(AbstractButton button){return defaultTextIconGap;}
}
