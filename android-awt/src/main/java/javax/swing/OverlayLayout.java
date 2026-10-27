package javax.swing;
import java.awt.*;
public class OverlayLayout implements LayoutManager2, java.io.Serializable {
    private final Container target;
    public OverlayLayout(Container target) { this.target = target; }
    private void check(Container c) { if (c != target) throw new Error("OverlayLayout cannot be shared"); }
    public void addLayoutComponent(String name, Component c) {}
    public void addLayoutComponent(Component c, Object constraints) {}
    public void removeLayoutComponent(Component c) {}
    public void invalidateLayout(Container c) { check(c); }
    public float getLayoutAlignmentX(Container c) { check(c); return .5f; }
    public float getLayoutAlignmentY(Container c) { check(c); return .5f; }
    private Dimension size(Container c, int kind) {
        check(c); int w=0,h=0; Insets in=c.getInsets();
        for(Component child:c.getComponents()) { Dimension d=kind==0?child.getPreferredSize():kind==1?child.getMinimumSize():child.getMaximumSize(); w=Math.max(w,d.width);h=Math.max(h,d.height); }
        return new Dimension(w+in.left+in.right,h+in.top+in.bottom);
    }
    public Dimension preferredLayoutSize(Container c) { return size(c,0); }
    public Dimension minimumLayoutSize(Container c) { return size(c,1); }
    public Dimension maximumLayoutSize(Container c) { return size(c,2); }
    public void layoutContainer(Container c) {
        check(c); Insets in=c.getInsets(); int w=Math.max(0,c.getWidth()-in.left-in.right),h=Math.max(0,c.getHeight()-in.top-in.bottom);
        for(Component child:c.getComponents()) { Dimension max=child.getMaximumSize();int cw=Math.min(w,max.width),ch=Math.min(h,max.height);child.setBounds(in.left+Math.round((w-cw)*child.getAlignmentX()),in.top+Math.round((h-ch)*child.getAlignmentY()),cw,ch); }
    }
}
