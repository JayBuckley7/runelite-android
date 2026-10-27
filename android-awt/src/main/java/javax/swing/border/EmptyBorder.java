package javax.swing.border;

import java.awt.Component;
import java.awt.Insets;

public class EmptyBorder extends AbstractBorder {
    private static final long serialVersionUID = -7615601466794030291L;
    protected int top, left, bottom, right;

    public EmptyBorder(int top, int left, int bottom, int right) {
        this.top = top; this.left = left; this.bottom = bottom; this.right = right;
    }

    public EmptyBorder(Insets insets) {
        this(insets.top, insets.left, insets.bottom, insets.right);
    }

    public Insets getBorderInsets() { return new Insets(top, left, bottom, right); }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(top, left, bottom, right);
    }
}
