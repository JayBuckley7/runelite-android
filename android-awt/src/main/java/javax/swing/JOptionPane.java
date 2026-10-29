package javax.swing;

import java.awt.Component;

public class JOptionPane extends JComponent {
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_OPTION = -1;
    public static final int YES_NO_OPTION = 0;
    public static final int YES_NO_CANCEL_OPTION = 1;
    public static final int OK_CANCEL_OPTION = 2;

    public static final int YES_OPTION = 0;
    public static final int NO_OPTION = 1;
    public static final int CANCEL_OPTION = 2;
    public static final int OK_OPTION = 0;
    public static final int CLOSED_OPTION = -1;

    public static final int ERROR_MESSAGE = 0;
    public static final int INFORMATION_MESSAGE = 1;
    public static final int WARNING_MESSAGE = 2;
    public static final int QUESTION_MESSAGE = 3;
    public static final int PLAIN_MESSAGE = -1;

    public interface DialogHandler { int show(Object message, String title, int optionType, Object[] options); }
    public interface InputHandler { Object show(Object message, String title, Object[] choices, Object initialValue); }
    private static volatile DialogHandler dialogHandler;
    private static volatile InputHandler inputHandler;
    public static void setDialogHandler(DialogHandler handler) { dialogHandler = handler; }
    public static void setInputHandler(InputHandler handler) { inputHandler = handler; }
    private static int show(Object message, String title, int optionType, Object[] options) {
        DialogHandler handler = dialogHandler;
        return handler == null ? CLOSED_OPTION : handler.show(message, title, optionType, options);
    }
    public static int showConfirmDialog(Component parent, Object message) { return show(message, "Confirm", YES_NO_CANCEL_OPTION, null); }
    public static int showConfirmDialog(Component parent, Object message, String title, int optionType) { return show(message, title, optionType, null); }
    public static int showConfirmDialog(Component parent, Object message, String title, int optionType, int messageType) { return show(message, title, optionType, null); }
    public static int showConfirmDialog(Component parent, Object message, String title, int optionType, int messageType, Icon icon) { return show(message, title, optionType, null); }
    public static String showInputDialog(Object message) { return showInputDialog(null, message); }
    public static String showInputDialog(Object message, Object initialValue) { return showInputDialog(null, message, initialValue); }
    public static String showInputDialog(Component parent, Object message) { return showInputDialog(parent, message, null); }
    public static String showInputDialog(Component parent, Object message, Object initialSelectionValue) {
        Object value = showInputDialog(parent, message, "Input", QUESTION_MESSAGE, null, null, initialSelectionValue);
        return value == null ? null : value.toString();
    }
    public static String showInputDialog(Component parent, Object message, String title, int messageType) {
        Object value = showInputDialog(parent, message, title, messageType, null, null, null);
        return value == null ? null : value.toString();
    }
    public static Object showInputDialog(Component parent, Object message, String title, int messageType, Icon icon, Object[] selectionValues, Object initialSelectionValue) {
        InputHandler handler = inputHandler;
        return handler == null ? null : handler.show(message, title,
            selectionValues == null ? null : selectionValues.clone(), initialSelectionValue);
    }

    public static void showMessageDialog(Component parent, Object message) { show(message, "RuneLite", DEFAULT_OPTION, null); }
    public static void showMessageDialog(Component parent, Object message, String title, int messageType) { show(message, title, DEFAULT_OPTION, null); }
    public static void showMessageDialog(Component parent, Object message, String title, int messageType, Icon icon) { show(message, title, DEFAULT_OPTION, null); }
    public static int showOptionDialog(Component parent, Object message, String title, int optionType, int messageType, Icon icon, Object[] options, Object initialValue) {
        return show(message, title, optionType, options);
    }
}
