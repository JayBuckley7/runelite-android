package javax.swing.plaf.basic;

import java.awt.*;
import javax.swing.*;

public class BasicArrowButton extends JButton implements SwingConstants {
    protected int direction;
    private final Color shadow, darkShadow, highlight;
    public BasicArrowButton(int direction) {
        this(direction, new Color(0x60636a), Color.GRAY, Color.BLACK, Color.WHITE);
    }
    public BasicArrowButton(int direction, Color background, Color shadow, Color darkShadow, Color highlight) {
        this.direction = direction;
        this.shadow = shadow; this.darkShadow = darkShadow; this.highlight = highlight;
        setBackground(background);
    }
    public int getDirection() { return direction; }
    public void setDirection(int direction) { this.direction = direction; repaint(); }
    @Override public Dimension getPreferredSize() { return new Dimension(16, 16); }
    @Override public Dimension getMinimumSize() { return new Dimension(5, 5); }
    @Override protected void paintComponent(Graphics g) {
        g.setColor(getBackground()); g.fillRect(0, 0, getWidth(), getHeight());
        int size = Math.max(2, Math.min(getWidth(), getHeight()) / 3);
        paintTriangle(g, (getWidth() - size) / 2, (getHeight() - size) / 2, size, direction, isEnabled());
    }
    public void paintTriangle(Graphics g, int x, int y, int size, int direction, boolean enabled) {
        Color old = g.getColor();
        g.setColor(enabled ? darkShadow : shadow);
        int mid = size / 2;
        int[] xs, ys;
        switch (direction) {
            case NORTH: xs = new int[]{x, x+size, x+mid}; ys = new int[]{y+size, y+size, y}; break;
            case WEST: xs = new int[]{x+size, x+size, x}; ys = new int[]{y, y+size, y+mid}; break;
            case EAST: xs = new int[]{x, x, x+size}; ys = new int[]{y, y+size, y+mid}; break;
            default: xs = new int[]{x, x+size, x+mid}; ys = new int[]{y, y, y+size}; break;
        }
        g.fillPolygon(xs, ys, 3);
        g.setColor(old);
    }
}
