package java.awt;
import java.awt.event.*;
public class MenuItem extends MenuComponent {
    private String label, command;
    private boolean enabled = true;
    private MenuShortcut shortcut;
    private final java.util.List<ActionListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    public MenuItem() { this(""); }
    public MenuItem(String label) { this(label, null); }
    public MenuItem(String label, MenuShortcut shortcut) { this.label = label; this.shortcut = shortcut; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void enable() { setEnabled(true); }
    public void enable(boolean enabled) { setEnabled(enabled); }
    public void disable() { setEnabled(false); }
    public String getActionCommand() { return command == null ? label : command; }
    public void setActionCommand(String command) { this.command = command; }
    public MenuShortcut getShortcut() { return shortcut; }
    public void setShortcut(MenuShortcut shortcut) { this.shortcut = shortcut; }
    public void deleteShortcut() { shortcut = null; }
    public void addActionListener(ActionListener listener) { if (listener != null) listeners.add(listener); }
    public void removeActionListener(ActionListener listener) { listeners.remove(listener); }
    public ActionListener[] getActionListeners() { return listeners.toArray(new ActionListener[0]); }
    @Override protected void processEvent(AWTEvent event) { if (event instanceof ActionEvent) processActionEvent((ActionEvent) event); }
    protected void processActionEvent(ActionEvent event) { for (ActionListener listener : listeners) listener.actionPerformed(event); }
    javax.swing.JMenuItem swingItem() {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(label);
        configure(item); return item;
    }
    void configure(javax.swing.JMenuItem item) {
        item.setEnabled(enabled);
        if (getFont() != null) item.setFont(getFont());
        item.addActionListener(event -> processActionEvent(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, getActionCommand(), event.getWhen(), event.getModifiers())));
    }
}
