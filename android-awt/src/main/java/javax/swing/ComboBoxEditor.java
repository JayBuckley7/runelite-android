package javax.swing;

import java.awt.Component;
import java.awt.event.ActionListener;

public interface ComboBoxEditor {
    Component getEditorComponent();
    void setItem(Object item);
    Object getItem();
    void selectAll();
    void addActionListener(ActionListener listener);
    void removeActionListener(ActionListener listener);
}
