package java.awt;

import java.awt.event.KeyEvent;

public abstract class KeyboardFocusManager {
    public static final int FORWARD_TRAVERSAL_KEYS = 0;
    public static final int BACKWARD_TRAVERSAL_KEYS = 1;
    public static final int UP_CYCLE_TRAVERSAL_KEYS = 2;
    public static final int DOWN_CYCLE_TRAVERSAL_KEYS = 3;

    private static KeyboardFocusManager current;

    protected KeyboardFocusManager() {}

    public static synchronized KeyboardFocusManager getCurrentKeyboardFocusManager() {
        if (current == null) current = new DefaultKFM();
        return current;
    }

    public static synchronized void setCurrentKeyboardFocusManager(KeyboardFocusManager mgr) {
        current = mgr;
    }

    private volatile Component focusOwner;
    public Component getFocusOwner() { return focusOwner; }
    public Component getPermanentFocusOwner() { return focusOwner; }
    public Window getFocusedWindow() { return windowOf(focusOwner); }
    public Window getActiveWindow() { return getFocusedWindow(); }
    private static Window windowOf(Component component) {
        while (component != null && !(component instanceof Window)) component = component.getParent();
        return (Window) component;
    }
    public void clearGlobalFocusOwner() { changeFocus(null); }
    public boolean requestFocus(Component component) {
        if (component == null || !component.isEnabled() || !component.isVisible() || !component.isFocusable()) return false;
        for (Component p = component.getParent(); p != null; p = p.getParent()) if (!p.isVisible()) return false;
        Window window = windowOf(component);
        if (window != null && !window.isFocusableWindow()) return false;
        changeFocus(component); return true;
    }
    public void clearFocusIn(Component subtree) {
        for (Component p = focusOwner; p != null; p = p.getParent()) if (p == subtree) { changeFocus(null); return; }
    }
    private synchronized void changeFocus(Component next) {
        Component old = focusOwner;
        if (old == next) return;
        Window oldWindow = windowOf(old), nextWindow = windowOf(next);
        focusOwner = next;
        if (old != null) old.dispatchEvent(new java.awt.event.FocusEvent(old, java.awt.event.FocusEvent.FOCUS_LOST, false, next));
        if (oldWindow != nextWindow && oldWindow != null) {
            oldWindow.dispatchEvent(new java.awt.event.WindowEvent(oldWindow, java.awt.event.WindowEvent.WINDOW_LOST_FOCUS));
            oldWindow.dispatchEvent(new java.awt.event.WindowEvent(oldWindow, java.awt.event.WindowEvent.WINDOW_DEACTIVATED));
        }
        if (next != null) next.dispatchEvent(new java.awt.event.FocusEvent(next, java.awt.event.FocusEvent.FOCUS_GAINED, false, old));
        if (oldWindow != nextWindow && nextWindow != null) {
            nextWindow.dispatchEvent(new java.awt.event.WindowEvent(nextWindow, java.awt.event.WindowEvent.WINDOW_ACTIVATED));
            nextWindow.dispatchEvent(new java.awt.event.WindowEvent(nextWindow, java.awt.event.WindowEvent.WINDOW_GAINED_FOCUS));
        }
    }

    public abstract boolean dispatchEvent(AWTEvent e);
    public abstract boolean dispatchKeyEvent(KeyEvent e);
    public abstract boolean postProcessKeyEvent(KeyEvent e);
    public abstract void processKeyEvent(Component focusedComponent, KeyEvent e);

    protected final java.util.List<KeyEventDispatcher> keyEventDispatchers = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addKeyEventDispatcher(KeyEventDispatcher dispatcher) {
        if (dispatcher != null && !keyEventDispatchers.contains(dispatcher)) {
            keyEventDispatchers.add(dispatcher);
        }
    }

    public void removeKeyEventDispatcher(KeyEventDispatcher dispatcher) {
        if (dispatcher != null) {
            keyEventDispatchers.remove(dispatcher);
        }
    }

    private static final class DefaultKFM extends KeyboardFocusManager {
        @Override public boolean dispatchEvent(AWTEvent e) { return false; }
        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            for (KeyEventDispatcher dispatcher : keyEventDispatchers) {
                if (dispatcher.dispatchKeyEvent(e)) {
                    return true;
                }
            }
            return false;
        }
        @Override public boolean postProcessKeyEvent(KeyEvent e) { return false; }
        @Override public void processKeyEvent(Component c, KeyEvent e) {}
    }
}
