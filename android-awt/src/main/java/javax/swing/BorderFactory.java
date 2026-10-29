package javax.swing;

import java.awt.Color;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;

public class BorderFactory {
    private BorderFactory() {}

    public static Border createEmptyBorder() { return new EmptyBorder(0, 0, 0, 0); }
    public static Border createEmptyBorder(int t, int l, int b, int r) { return new EmptyBorder(t, l, b, r); }

    public static Border createLineBorder(Color color) { return new LineBorder(color); }
    public static Border createLineBorder(Color color, int thickness) { return new LineBorder(color, thickness); }
    public static Border createLineBorder(Color color, int thickness, boolean rounded) { return new LineBorder(color, thickness, rounded); }

    public static MatteBorder createMatteBorder(int top, int left, int bottom, int right, Color color) {
        return new MatteBorder(top, left, bottom, right, color);
    }
    public static MatteBorder createMatteBorder(int top, int left, int bottom, int right, Icon tile) {
        return new MatteBorder(top, left, bottom, right, tile);
    }

    public static CompoundBorder createCompoundBorder() { return new CompoundBorder(); }
    public static CompoundBorder createCompoundBorder(Border outside, Border inside) { return new CompoundBorder(outside, inside); }

    public static Border createRaisedBevelBorder() { return new javax.swing.border.BevelBorder(javax.swing.border.BevelBorder.RAISED); }
    public static Border createLoweredBevelBorder() { return new javax.swing.border.BevelBorder(javax.swing.border.BevelBorder.LOWERED); }
    public static Border createEtchedBorder() { return new javax.swing.border.EtchedBorder(); }
    public static Border createEtchedBorder(Color highlight, Color shadow) { return new javax.swing.border.EtchedBorder(highlight,shadow); }
    public static javax.swing.border.TitledBorder createTitledBorder(String title) { return new javax.swing.border.TitledBorder(title); }
    public static javax.swing.border.TitledBorder createTitledBorder(Border border,String title) { return new javax.swing.border.TitledBorder(border,title); }
    public static Border createDashedBorder(java.awt.Paint paint) { return new EmptyBorder(1, 1, 1, 1); }
    public static javax.swing.border.TitledBorder createTitledBorder(Border border){return new javax.swing.border.TitledBorder(border);}
    public static javax.swing.border.TitledBorder createTitledBorder(Border border,String title,int justification,int position){return new javax.swing.border.TitledBorder(border,title,justification,position);}
    public static javax.swing.border.TitledBorder createTitledBorder(Border border,String title,int justification,int position,java.awt.Font font){return new javax.swing.border.TitledBorder(border,title,justification,position,font);}
    public static javax.swing.border.TitledBorder createTitledBorder(Border border,String title,int justification,int position,java.awt.Font font,Color color){return new javax.swing.border.TitledBorder(border,title,justification,position,font,color);}
    public static Border createEtchedBorder(int type){return new javax.swing.border.EtchedBorder(type);}
    public static Border createEtchedBorder(int type,Color highlight,Color shadow){return new javax.swing.border.EtchedBorder(type,highlight,shadow);}
    public static Border createBevelBorder(int type){return new javax.swing.border.BevelBorder(type);}
}
