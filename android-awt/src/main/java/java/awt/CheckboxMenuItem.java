package java.awt;
import java.awt.event.*;
public class CheckboxMenuItem extends MenuItem implements ItemSelectable {
    private boolean state;
    private final java.util.List<ItemListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    public CheckboxMenuItem() { this(""); }
    public CheckboxMenuItem(String label) { this(label, false); }
    public CheckboxMenuItem(String label, boolean state) { super(label); this.state = state; }
    public boolean getState() { return state; }
    public void setState(boolean state) { this.state = state; }
    public Object[] getSelectedObjects() { return state ? new Object[]{getLabel()} : null; }
    public void addItemListener(ItemListener listener) { if (listener != null) listeners.add(listener); }
    public void removeItemListener(ItemListener listener) { listeners.remove(listener); }
    public ItemListener[] getItemListeners() { return listeners.toArray(new ItemListener[0]); }
    protected void processItemEvent(ItemEvent event) { for (ItemListener listener : listeners) listener.itemStateChanged(event); }
    @Override protected void processEvent(AWTEvent event) { if (event instanceof ItemEvent) processItemEvent((ItemEvent) event); else super.processEvent(event); }
    @Override javax.swing.JMenuItem swingItem() {
        javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(getLabel(), state);
        item.setEnabled(isEnabled());
        item.addItemListener(event -> { state = item.isSelected(); processItemEvent(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, getLabel(), state ? ItemEvent.SELECTED : ItemEvent.DESELECTED)); });
        return item;
    }
}
