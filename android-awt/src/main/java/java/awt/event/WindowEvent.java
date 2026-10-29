package java.awt.event;

import java.awt.Component;
import java.awt.Window;

public class WindowEvent extends ComponentEvent {
    private static final long serialVersionUID = -1567959133147912127L;

    public static final int WINDOW_FIRST = 200;
    public static final int WINDOW_OPENED = 200;
    public static final int WINDOW_CLOSING = 201;
    public static final int WINDOW_CLOSED = 202;
    public static final int WINDOW_ICONIFIED = 203;
    public static final int WINDOW_DEICONIFIED = 204;
    public static final int WINDOW_ACTIVATED = 205;
    public static final int WINDOW_DEACTIVATED = 206;
    public static final int WINDOW_GAINED_FOCUS = 207;
    public static final int WINDOW_LOST_FOCUS = 208;
    public static final int WINDOW_STATE_CHANGED = 209;
    public static final int WINDOW_LAST = 209;

    public WindowEvent(Component source, int id) {
        super(source, id);
    }
    private Window opposite;
    private int oldState, newState;
    public WindowEvent(Window source, int id) { this((Component) source, id); }
    public WindowEvent(Window source, int id, Window opposite) { this(source, id); this.opposite = opposite; }
    public WindowEvent(Window source, int id, int oldState, int newState) { this(source, id, null, oldState, newState); }
    public WindowEvent(Window source, int id, Window opposite, int oldState, int newState) {
        this(source, id, opposite); this.oldState = oldState; this.newState = newState;
    }
    public Window getWindow() { return (Window) getSource(); }
    public Window getOppositeWindow() { return opposite; }
    public int getOldState() { return oldState; }
    public int getNewState() { return newState; }
}
