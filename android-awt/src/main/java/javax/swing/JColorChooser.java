package javax.swing;
import java.awt.*;
public class JColorChooser extends JComponent {
    public interface DialogHandler { Color show(Component parent,String title,Color initial); }
    private static volatile DialogHandler handler;
    public static void setDialogHandler(DialogHandler h){handler=h;}
    public static Color showDialog(Component parent,String title,Color initial){DialogHandler h=handler;return h==null?null:h.show(parent,title,initial==null?Color.WHITE:initial);}
}
