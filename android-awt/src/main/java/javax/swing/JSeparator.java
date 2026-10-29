package javax.swing;

public class JSeparator extends JComponent implements SwingConstants {
    private static final long serialVersionUID = 1L;
    private int orientation;

    public JSeparator() { this(HORIZONTAL); }
    public JSeparator(int orientation) { this.orientation = orientation; }

    public int getOrientation() { return orientation; }
    public void setOrientation(int orientation) { if(orientation!=HORIZONTAL&&orientation!=VERTICAL)throw new IllegalArgumentException("orientation");int old=this.orientation;this.orientation=orientation;firePropertyChange("orientation",old,orientation);revalidate();repaint(); }
    public javax.swing.plaf.SeparatorUI getUI(){return (javax.swing.plaf.SeparatorUI)ui;}
    public void setUI(javax.swing.plaf.SeparatorUI value){super.setUI(value);}
    public String getUIClassID(){return "SeparatorUI";}
    public java.awt.Dimension getPreferredSize(){if(isPreferredSizeSet())return super.getPreferredSize();return ui==null?new javax.swing.plaf.basic.BasicSeparatorUI().getPreferredSize(this):ui.getPreferredSize(this);}
    protected void paintComponent(java.awt.Graphics graphics){if(paintDelegate(graphics))return;new javax.swing.plaf.basic.BasicSeparatorUI().paint(graphics,this);}
}
