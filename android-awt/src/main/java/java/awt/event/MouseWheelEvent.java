package java.awt.event;

import java.awt.Component;

public class MouseWheelEvent extends MouseEvent {
    private static final long serialVersionUID = 6459879390515399677L;

    public static final int WHEEL_UNIT_SCROLL = 0;
    public static final int WHEEL_BLOCK_SCROLL = 1;

    private final int scrollType;
    private final int scrollAmount;
    private final int wheelRotation;
    private final double preciseWheelRotation;

    public MouseWheelEvent(Component source, int id, long when, int modifiers,
                            int x, int y, int clickCount, boolean popupTrigger,
                            int scrollType, int scrollAmount, int wheelRotation) {
        this(source, id, when, modifiers, x, y, clickCount, popupTrigger,
            scrollType, scrollAmount, wheelRotation, (double) wheelRotation);
    }

    public MouseWheelEvent(Component source, int id, long when, int modifiers,
                            int x, int y, int clickCount, boolean popupTrigger,
                            int scrollType, int scrollAmount, int wheelRotation,
                            double preciseWheelRotation) {
        super(source, id, when, modifiers, x, y, clickCount, popupTrigger);
        this.scrollType = scrollType;
        this.scrollAmount = scrollAmount;
        this.wheelRotation = wheelRotation;
        this.preciseWheelRotation = preciseWheelRotation;
    }

    public MouseWheelEvent(Component source, int id, long when, int modifiers,
                            int x, int y, int xAbs, int yAbs, int clickCount, boolean popupTrigger,
                            int scrollType, int scrollAmount, int wheelRotation) {
        this(source, id, when, modifiers, x, y, xAbs, yAbs, clickCount, popupTrigger,
            scrollType, scrollAmount, wheelRotation, (double) wheelRotation);
    }

    public MouseWheelEvent(Component source, int id, long when, int modifiers,
                            int x, int y, int xAbs, int yAbs, int clickCount, boolean popupTrigger,
                            int scrollType, int scrollAmount, int wheelRotation,
                            double preciseWheelRotation) {
        super(source, id, when, modifiers, x, y, xAbs, yAbs, clickCount, popupTrigger, NOBUTTON);
        this.scrollType = scrollType;
        this.scrollAmount = scrollAmount;
        this.wheelRotation = wheelRotation;
        this.preciseWheelRotation = preciseWheelRotation;
    }

    public int getScrollType()   { return scrollType; }
    public int getScrollAmount() { return scrollAmount; }
    public int getWheelRotation() { return wheelRotation; }
    public double getPreciseWheelRotation() { return preciseWheelRotation; }
    public int getUnitsToScroll() { return scrollAmount * wheelRotation; }
}
