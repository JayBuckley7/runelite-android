package java.awt.event;

import java.awt.AWTEvent;

public class ItemEvent extends AWTEvent {
    private static final long serialVersionUID = -608708132447206933L;

    public static final int ITEM_FIRST = 701;
    public static final int ITEM_LAST = 701;
    public static final int ITEM_STATE_CHANGED = 701;
    public static final int SELECTED = 1;
    public static final int DESELECTED = 2;

    private final Object item;
    private final int stateChange;

    public ItemEvent(Object source, int id, Object item, int stateChange) {
        super(source, id);
        this.item = item;
        this.stateChange = stateChange;
    }
    public ItemEvent(java.awt.ItemSelectable source, int id, Object item, int stateChange) {
        this((Object) source, id, item, stateChange);
    }

    public Object getItem() { return item; }
    public int getStateChange() { return stateChange; }
    public java.awt.ItemSelectable getItemSelectable() { return (java.awt.ItemSelectable) source; }
}
