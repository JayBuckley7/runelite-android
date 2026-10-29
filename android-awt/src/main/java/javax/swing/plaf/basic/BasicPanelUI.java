package javax.swing.plaf.basic;
import javax.swing.*;
import javax.swing.plaf.*;
public class BasicPanelUI extends PanelUI {
    public static ComponentUI createUI(JComponent component){return new BasicPanelUI();}
    public void installUI(JComponent component){installDefaults((JPanel)component);}
    public void uninstallUI(JComponent component){uninstallDefaults((JPanel)component);}
    protected void installDefaults(JPanel panel){LookAndFeel.installColorsAndFont(panel,"Panel.background","Panel.foreground","Panel.font");LookAndFeel.installBorder(panel,"Panel.border");LookAndFeel.installProperty(panel,"opaque",Boolean.TRUE);}
    protected void uninstallDefaults(JPanel panel){LookAndFeel.uninstallBorder(panel);}
    public void update(java.awt.Graphics graphics,JComponent component){if(component.isOpaque()&&component.getBackground()!=null){graphics.setColor(component.getBackground());graphics.fillRect(0,0,component.getWidth(),component.getHeight());}paint(graphics,component);}
}
