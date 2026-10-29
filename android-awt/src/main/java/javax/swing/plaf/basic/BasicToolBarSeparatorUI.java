package javax.swing.plaf.basic;
import java.awt.Dimension;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
public class BasicToolBarSeparatorUI extends BasicSeparatorUI {
    public static ComponentUI createUI(JComponent component){return new BasicToolBarSeparatorUI();}
    public Dimension getPreferredSize(JComponent component){Dimension configured=UIManager.getDimension("ToolBar.separatorSize");return configured==null?super.getPreferredSize(component):new Dimension(configured);}
}
