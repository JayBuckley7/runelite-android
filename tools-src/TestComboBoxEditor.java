import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxEditor;

public class TestComboBoxEditor {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        BasicComboBoxEditor editor = new BasicComboBoxEditor();
        editor.setItem(null);
        check("".equals(editor.getItem()), "null item");
        Integer number = Integer.valueOf(1234);
        editor.setItem(number);
        check(editor.getItem() == number, "preserve unchanged object");
        JTextField field = (JTextField) editor.getEditorComponent();
        field.setText("2345");
        check(Integer.valueOf(2345).equals(editor.getItem()), "typed valueOf conversion");
        field.setText("not a number");
        check("not a number".equals(editor.getItem()), "string fallback");
        JComboBox<String> combo = new JComboBox<>(new String[]{"one", "two"});
        combo.setEditable(true);
        combo.setEditor(editor);
        check("one".equals(field.getText()), "initial editor selection");
        combo.setSelectedItem("two");
        check("two".equals(field.getText()), "selection updates editor");
        int[] edited = {0};
        combo.addActionListener(e -> { if ("comboBoxEdited".equals(e.getActionCommand())) edited[0]++; });
        field.setText("custom"); field.postActionEvent();
        check("custom".equals(combo.getSelectedItem()), "editor submit updates model");
        check(edited[0] == 1, "one edited event");
        BasicComboBoxEditor replacement = new BasicComboBoxEditor();
        combo.setEditor(replacement);
        field.setText("stale"); field.postActionEvent();
        check("custom".equals(combo.getSelectedItem()), "old editor detached");
        combo.setModel(new DefaultComboBoxModel<>(new String[]{"new"}));
        check("new".equals(replacement.getItem()), "model updates editor");
        combo.updateUI();
        check(combo.getEditor() == replacement, "custom editor preserved");
        System.out.println("PASS: combo editor behavior");
    }
}
