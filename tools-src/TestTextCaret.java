import javax.swing.*;
import javax.swing.text.*;

public class TestTextCaret {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Runnable verify = () -> {
            try {
                JTextArea notes = new JTextArea("previous notes");
                check(notes.getCaret() instanceof DefaultCaret, "text controls provide a DefaultCaret");
                DefaultCaret caret = (DefaultCaret) notes.getCaret();
                caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
                notes.setText("new");
                check(notes.getCaretPosition() == 0, "notes replacement must not move caret to end");
                caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
                notes.setCaretPosition(1);
                notes.getDocument().insertString(0, "x", null);
                check(notes.getCaretPosition() == 2, "insertion shifts caret");
                int[] events = {0}; notes.addCaretListener(e -> events[0]++);
                notes.select(1, 3);
                check("ne".equals(notes.getSelectedText()), "selected text");
                notes.getDocument().remove(0, 1);
                check(notes.getSelectionStart() == 0 && notes.getSelectionEnd() == 2 && "ne".equals(notes.getSelectedText()), "removal shifts selection");
                check(events[0] > 0, "caret changes reach listeners");
                Document old = notes.getDocument();
                PlainDocument replacement = new PlainDocument(); replacement.insertString(0, "abc", null);
                notes.setDocument(replacement); notes.setCaretPosition(1);
                old.insertString(0, "orphaned", null);
                check(notes.getCaretPosition() == 1, "old document detached");
                replacement.insertString(0, "z", null);
                check(notes.getCaretPosition() == 2, "new document attached");
                notes.selectAll(); check("zabc".equals(notes.getSelectedText()), "select all");
                notes.setCaretPosition(0); check(notes.getSelectedText() == null, "collapsed selection");
            } catch (Exception e) { throw new RuntimeException(e); }
        };
        if ("Dalvik".equals(System.getProperty("java.vm.name"))) {
            Thread.currentThread().setName("AWT-InvokeProbe");
            verify.run();
        } else SwingUtilities.invokeAndWait(verify);
        System.out.println("PASS: notes caret policies, selection, document edits, listeners and document replacement");
        System.exit(0); // The Android shim owns a persistent EDT worker.
    }
}
