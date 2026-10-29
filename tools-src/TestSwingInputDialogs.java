import javax.swing.JOptionPane;

public class TestSwingInputDialogs {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(JOptionPane.showInputDialog("No host") == null, "no host cancels");
        JOptionPane.setInputHandler((message, title, choices, initial) -> {
            check(message.equals("Name"), "prompt preserved");
            check(title.equals("Input"), "default title");
            check(choices == null && initial.equals("Existing"), "text initial preserved");
            return "Edited";
        });
        check(JOptionPane.showInputDialog("Name", "Existing").equals("Edited"), "text result");
        Object choice = new Object(); Object[] choices = {choice};
        JOptionPane.setInputHandler((message, title, options, initial) -> {
            check(title.equals("Pick") && initial == choice, "choice prompt");
            check(options != choices && options[0] == choice, "copy array, retain original choice object");
            options[0] = new Object();
            return initial;
        });
        check(JOptionPane.showInputDialog(null, "Choose", "Pick", -1, null, choices, choice) == choice, "typed selection result");
        check(choices[0] == choice, "handler cannot mutate caller choices");
        JOptionPane.setInputHandler((message, title, options, initial) -> null);
        check(JOptionPane.showInputDialog(null, "Name", "Section", -1) == null, "cancel remains null");
        JOptionPane.setInputHandler(null);
        check(JOptionPane.showInputDialog(null, "Closed host") == null, "removed host cancels");
        check(JOptionPane.showConfirmDialog(null, "Confirm") == JOptionPane.CLOSED_OPTION, "confirmation behavior unchanged");
        System.out.println("PASS: input results, initial values, typed choices, cancellation and absent host");
    }
}
