package java.awt.event;

import java.awt.Component;

public class FocusEvent extends ComponentEvent {
    private static final long serialVersionUID = 523220093017728481L;

    public static final int FOCUS_FIRST = 1004;
    public static final int FOCUS_GAINED = 1004;
    public static final int FOCUS_LOST = 1005;
    public static final int FOCUS_LAST = 1005;

    public enum Cause { UNKNOWN, MOUSE_EVENT, TRAVERSAL, TRAVERSAL_UP, TRAVERSAL_DOWN, TRAVERSAL_FORWARD, TRAVERSAL_BACKWARD, ROLLBACK, UNEXPECTED, ACTIVATION, CLEAR_GLOBAL_FOCUS_OWNER }
    private Cause cause = Cause.UNKNOWN;
    public FocusEvent(Component source, int id, boolean temporary, Component opposite, Cause cause) {
        this(source, id, temporary, opposite);
        this.cause = cause == null ? Cause.UNKNOWN : cause;
    }
    public Cause getCause() { return cause; }
    private final boolean temporary;
    private final Component opposite;

    public FocusEvent(Component source, int id) {
        this(source, id, false, null);
    }

    public FocusEvent(Component source, int id, boolean temporary) {
        this(source, id, temporary, null);
    }

    public FocusEvent(Component source, int id, boolean temporary, Component opposite) {
        super(source, id);
        this.temporary = temporary;
        this.opposite = opposite;
    }

    public boolean isTemporary() {
        return temporary;
    }

    public Component getOppositeComponent() {
        return opposite;
    }
}
