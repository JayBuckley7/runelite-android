package javax.swing.plaf.basic;

import java.awt.Graphics;
import javax.swing.*;
import javax.swing.plaf.*;

/** Delegate hooks for plugin styling; Android's panel host owns the interactive picker. */
public class BasicComboBoxUI extends ComboBoxUI {
    protected JComboBox<?> comboBox;
    protected JButton arrowButton;
    private boolean popupVisible;
    public static ComponentUI createUI(JComponent c) { return new BasicComboBoxUI(); }
    @Override public void installUI(JComponent c) {
        comboBox = (JComboBox<?>) c;
        arrowButton = createArrowButton();
    }
    @Override public void uninstallUI(JComponent c) {
        comboBox = null; arrowButton = null; popupVisible = false;
    }
    protected JButton createArrowButton() { return new BasicArrowButton(SwingConstants.SOUTH); }
    @Override public void setPopupVisible(JComboBox<?> c, boolean visible) { popupVisible = visible; }
    @Override public boolean isPopupVisible(JComboBox<?> c) { return popupVisible; }
    @Override public boolean isFocusTraversable(JComboBox<?> c) { return c.isEnabled(); }
    public void paintArrow(Graphics g, int x, int y, int width, int height) {
        if (arrowButton == null) return;
        arrowButton.setEnabled(comboBox.isEnabled());
        arrowButton.setSize(width, height);
        Graphics child = g.create(x, y, width, height);
        try { arrowButton.paint(child); } finally { child.dispose(); }
    }
}
