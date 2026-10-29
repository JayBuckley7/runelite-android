import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import javax.swing.*;

/** Runs against the built Android host APK via dalvikvm; names come from its R8 mapping. */
public class TestKeyDispatch {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { verify(args); } catch (Exception e) { throw new RuntimeException(e); }
        });
        System.exit(0);
    }
    private static void verify(String[] args) throws Exception {
        Class<?> dispatchClass = Class.forName(args[0]);
        Method method = dispatchClass.getDeclaredMethod(args[1], KeyEvent.class); method.setAccessible(true);
        Object dispatch = null;
        if (!Modifier.isStatic(method.getModifiers())) {
            Field singleton = dispatchClass.getDeclaredField("INSTANCE"); singleton.setAccessible(true);
            dispatch = singleton.get(null);
        }
        JFrame window = new JFrame(); window.setVisible(true);
        Canvas canvas = new Canvas(); window.add(canvas);
        JPanel plugin = new JPanel(); window.add(plugin);
        JTextField visibleSearch = new JTextField(), hiddenSearch = new JTextField();
        plugin.add(visibleSearch); plugin.add(hiddenSearch); hiddenSearch.setVisible(false);
        int[] received = new int[5];
        visibleSearch.addKeyListener(new KeyAdapter() { public void keyPressed(KeyEvent e) { received[1]++; } });
        hiddenSearch.addKeyListener(new KeyAdapter() { public void keyPressed(KeyEvent e) { received[2]++; } });
        canvas.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) { check(e.getComponent() == canvas, "source"); received[0]++; }
            public void keyTyped(KeyEvent e) { received[3]++; }
            public void keyReleased(KeyEvent e) { received[4]++; }
        });
        for (int id : new int[]{KeyEvent.KEY_PRESSED, KeyEvent.KEY_TYPED, KeyEvent.KEY_RELEASED}) {
            method.invoke(dispatch, new KeyEvent(canvas, id, 0, 0, id == KeyEvent.KEY_TYPED ? 0 : KeyEvent.VK_ENTER, '\n'));
        }
        check(received[0] == 1 && received[3] == 1 && received[4] == 1, "game receives each event exactly once");
        check(received[1] == 0 && received[2] == 0, "plugin fields must not receive game Enter");
        KeyEventDispatcher consume = e -> { e.consume(); return true; };
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(consume);
        method.invoke(dispatch, new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, '\n'));
        manager.removeKeyEventDispatcher(consume);
        check(received[0] == 1, "consumed global hotkey must not reach game");
        canvas.setVisible(false);
        method.invoke(dispatch, new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, '\n'));
        check(received[0] == 1, "hidden target must not receive keys");
        System.out.println("PASS: game Enter once, no visible/hidden plugin broadcast, global consumption, hidden targets");
    }
}
