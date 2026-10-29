package javax.swing.plaf.basic;
import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.*;
public class BasicSeparatorUI extends SeparatorUI {
    protected Color shadow,highlight;
    public static ComponentUI createUI(JComponent component){return new BasicSeparatorUI();}
    public void installUI(JComponent component){installDefaults((JSeparator)component);installListeners((JSeparator)component);}
    public void uninstallUI(JComponent component){uninstallDefaults((JSeparator)component);uninstallListeners((JSeparator)component);}
    protected void installDefaults(JSeparator separator){LookAndFeel.installColors(separator,"Separator.background","Separator.foreground");shadow=UIManager.getColor("Separator.shadow");highlight=UIManager.getColor("Separator.highlight");}
    protected void uninstallDefaults(JSeparator separator){shadow=highlight=null;}
    // Subclasses install their listeners; the basic separator has no interactive state.
    protected void installListeners(JSeparator separator){}
    protected void uninstallListeners(JSeparator separator){}
    public void paint(Graphics graphics,JComponent component){
        boolean vertical=((JSeparator)component).getOrientation()==SwingConstants.VERTICAL;
        Color dark=shadow==null?component.getForeground():shadow,light=highlight==null?component.getBackground():highlight;
        graphics.setColor(dark==null?Color.GRAY:dark);
        if(vertical)graphics.drawLine(0,0,0,component.getHeight());else graphics.drawLine(0,0,component.getWidth(),0);
        if(light!=null){graphics.setColor(light);if(vertical)graphics.drawLine(1,0,1,component.getHeight());else graphics.drawLine(0,1,component.getWidth(),1);}
    }
    public Dimension getPreferredSize(JComponent component){return ((JSeparator)component).getOrientation()==SwingConstants.VERTICAL?new Dimension(2,0):new Dimension(0,2);}
}
