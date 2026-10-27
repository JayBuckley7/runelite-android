package javax.swing.plaf.basic;

import java.awt.Component;
import java.awt.event.ActionListener;
import javax.swing.ComboBoxEditor;
import javax.swing.JTextField;

public class BasicComboBoxEditor implements ComboBoxEditor {
    protected JTextField editor;
    private Object oldValue;

    public BasicComboBoxEditor() { editor = createEditorComponent(); }
    protected JTextField createEditorComponent() {
        JTextField field = new JTextField("", 9);
        field.setBorder(null);
        return field;
    }
    @Override public Component getEditorComponent() { return editor; }
    @Override public void setItem(Object item) {
        oldValue = item;
        String text = item == null ? "" : item.toString();
        if (!text.equals(editor.getText())) editor.setText(text);
    }
    @Override public Object getItem() {
        String text = editor.getText();
        if (oldValue != null && !(oldValue instanceof String)) {
            if (text.equals(oldValue.toString())) return oldValue;
            try {
                return oldValue.getClass().getMethod("valueOf", String.class).invoke(null, text);
            } catch (ReflectiveOperationException | SecurityException ignored) {
                // Types without a usable string factory keep the edited string.
            }
        }
        return text;
    }
    @Override public void selectAll() { editor.selectAll(); editor.requestFocus(); }
    @Override public void addActionListener(ActionListener l) { editor.addActionListener(l); }
    @Override public void removeActionListener(ActionListener l) { editor.removeActionListener(l); }

    public static class UIResource extends BasicComboBoxEditor implements javax.swing.plaf.UIResource {}
}
