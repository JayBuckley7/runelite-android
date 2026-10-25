package java.awt.event;

import java.awt.Component;
import java.awt.Point;

public class MouseEvent extends InputEvent {
    private static final long serialVersionUID = -991214153494842848L;

    public static final int MOUSE_FIRST = 500;
    public static final int MOUSE_LAST  = 507;
    public static final int MOUSE_CLICKED = 500;
    public static final int MOUSE_PRESSED = 501;
    public static final int MOUSE_RELEASED = 502;
    public static final int MOUSE_MOVED = 503;
    public static final int MOUSE_ENTERED = 504;
    public static final int MOUSE_EXITED = 505;
    public static final int MOUSE_DRAGGED = 506;
    public static final int MOUSE_WHEEL = 507;

    public static final int NOBUTTON = 0;
    public static final int BUTTON1 = 1;
    public static final int BUTTON2 = 2;
    public static final int BUTTON3 = 3;

    private int x;
    private int y;
    private int xAbs;
    private int yAbs;
    private final int clickCount;
    private final boolean popupTrigger;
    private final int button;

    public MouseEvent(Component source, int id, long when, int modifiers,
                       int x, int y, int clickCount, boolean popupTrigger) {
        this(source, id, when, modifiers, x, y, clickCount, popupTrigger, NOBUTTON);
    }

    public MouseEvent(Component source, int id, long when, int modifiers,
                       int x, int y, int clickCount, boolean popupTrigger, int button) {
        this(source, id, when, modifiers, x, y, 0, 0, clickCount, popupTrigger, button);
        // As in the JDK, the screen position is captured now: drag handlers compare it
        // against components they move mid-gesture, so it must not follow the source.
        if (source != null) {
            Point origin = source.getLocationOnScreen();
            xAbs = origin.x + x;
            yAbs = origin.y + y;
        } else {
            xAbs = x;
            yAbs = y;
        }
    }

    public MouseEvent(Component source, int id, long when, int modifiers,
                       int x, int y, int xAbs, int yAbs, int clickCount, boolean popupTrigger, int button) {
        super(source, id, when, modifiers);
        this.x = x;
        this.y = y;
        this.xAbs = xAbs;
        this.yAbs = yAbs;
        this.clickCount = clickCount;
        this.popupTrigger = popupTrigger;
        this.button = button;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public Point getPoint() { return new Point(x, y); }
    public int getXOnScreen() { return xAbs; }
    public int getYOnScreen() { return yAbs; }
    public Point getLocationOnScreen() { return new Point(xAbs, yAbs); }
    public int getClickCount() { return clickCount; }
    public boolean isPopupTrigger() { return popupTrigger; }
    public int getButton() { return button; }

    /** JDK semantics: moves the component-relative point only; the screen point stays. */
    public void translatePoint(int dx, int dy) {
        x += dx;
        y += dy;
    }
}
