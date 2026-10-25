package javax.swing;

public class JButton extends AbstractButton {
    private static final long serialVersionUID = -1602050342864801480L;

    public JButton() { this(null, null); }
    public JButton(Icon icon) { this(null, icon); }
    public JButton(String text) { this(text, null); }
    public JButton(Action a) { this(); setAction(a); }
    public JButton(String text, Icon icon) {
        setText(text); setIcon(icon);
    }

    public boolean isDefaultButton() { return false; }
    public boolean isDefaultCapable() { return true; }
    public void setDefaultCapable(boolean d) {}

    /** Typed overload — plugins (e.g. via SwingUtil.removeButtonDecorations) call this. */
    public void setUI(javax.swing.plaf.ButtonUI ui) { super.setUI(ui); }

    /**
     * Paint a flat button: background fill (if opaque or content area filled), a 1-px
     * border (if borderPainted), then the icon and centered text. Without this the
     * Reset/Back buttons in plugin config panels existed in the layout but rendered as
     * empty rectangles — invisible because the parent's gray bg showed through.
     */
    @Override
    protected void paintComponent(java.awt.Graphics g) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        // SwingUtil.removeButtonDecorations sets FlatLaf.styleClass = "iconButton ...".
        // Those buttons are meant to render as bare icons (pin star, config gear) with
        // no surrounding chrome — skip the bg fill and border so they don't render as
        // visible gray boxes inside dense rows like the plugin list.
        Object styleClass = getClientProperty("FlatLaf.styleClass");
        boolean iconOnly = styleClass != null && styleClass.toString().contains("iconButton");
        boolean pressed = getModel() != null && getModel().isPressed();
        java.awt.Color bg = getBackground();
        if (bg == null) bg = new java.awt.Color(0x3E, 0x3E, 0x3E);
        if (!iconOnly && isContentAreaFilled()) {
            g.setColor(pressed ? bg.darker() : bg);
            g.fillRect(0, 0, w, h);
        }
        if (!iconOnly && isBorderPainted()) {
            g.setColor(bg.darker());
            g.drawRect(0, 0, w - 1, h - 1);
        }
        Icon ic = getIcon();
        java.awt.FontMetrics fm = g.getFontMetrics(getFont());
        int textW = (text == null || text.isEmpty()) ? 0 : fm.stringWidth(text);
        int textH = (text == null || text.isEmpty()) ? 0 : fm.getHeight();
        int iconW = ic != null ? ic.getIconWidth() : 0;
        int iconH = ic != null ? ic.getIconHeight() : 0;
        int gap = (iconW > 0 && textW > 0) ? 4 : 0;
        int contentW = iconW + gap + textW;
        int x = (w - contentW) / 2;
        if (ic != null) {
            int iy = (h - iconH) / 2;
            ic.paintIcon(this, g, x, iy);
            x += iconW + gap;
        }
        if (textW > 0) {
            java.awt.Color fg = getForeground();
            if (fg == null) fg = java.awt.Color.WHITE;
            g.setColor(isEnabled() ? fg : fg.darker());
            g.setFont(getFont());
            int baseline = (h - textH) / 2 + fm.getAscent();
            g.drawString(text, x, baseline);
        }
    }
}
