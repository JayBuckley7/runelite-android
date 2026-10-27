package javax.swing;

public class JRadioButton extends JToggleButton {
    private static final long serialVersionUID = 1L;

    public JRadioButton() { this(null, null, false); }
    public JRadioButton(Icon icon) { this(null, icon, false); }
    public JRadioButton(Icon icon, boolean selected) { this(null, icon, selected); }
    public JRadioButton(String text) { this(text, null, false); }
    public JRadioButton(String text, boolean selected) { this(text, null, selected); }
    public JRadioButton(Action a) { super(a); }
    public JRadioButton(String text, Icon icon) { this(text, icon, false); }
    public JRadioButton(String text, Icon icon, boolean selected) { super(text, icon, selected); }
    private static final int BOX_SIZE = 14;

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        // Don't call AbstractButton.paintComponent because we draw our own box + text.
        if (getWidth() <= 0 || getHeight() <= 0) return;
        java.awt.Color saved = g.getColor();
        if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        // Checkbox indicator on the left, centered vertically.
        int boxY = (getHeight() - BOX_SIZE) / 2;
        int boxX = 2;
        g.setColor(new java.awt.Color(0x60636A));
        g.fillOval(boxX, boxY, BOX_SIZE, BOX_SIZE);
        g.setColor(new java.awt.Color(0x9095A0));
        g.drawOval(boxX, boxY, BOX_SIZE - 1, BOX_SIZE - 1);
        if (isSelected()) {
            g.setColor(new java.awt.Color(0xE0E0E0));
            g.fillOval(boxX + 4, boxY + 4, BOX_SIZE - 8, BOX_SIZE - 8);
        }
        // Label text after the box.
        if (text != null && !text.isEmpty()) {
            g.setColor(getForeground());
            g.setFont(getFont());
            java.awt.FontMetrics fm = g.getFontMetrics(getFont());
            int textX = boxX + BOX_SIZE + 6;
            int baseline = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(text, textX, baseline);
        }
        if (saved != null) g.setColor(saved);
    }

    @Override
    public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        int tw = text == null ? 0 : fm.stringWidth(text);
        int w = 2 + BOX_SIZE + 6 + tw + 2;
        int h = Math.max(fm.getHeight(), BOX_SIZE) + 4;
        return new java.awt.Dimension(w, h);
    }
}
