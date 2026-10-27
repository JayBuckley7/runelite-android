package javax.swing.plaf;

import javax.swing.JComboBox;

public abstract class ComboBoxUI extends ComponentUI {
    public abstract void setPopupVisible(JComboBox<?> c, boolean visible);
    public abstract boolean isPopupVisible(JComboBox<?> c);
    public abstract boolean isFocusTraversable(JComboBox<?> c);
}
