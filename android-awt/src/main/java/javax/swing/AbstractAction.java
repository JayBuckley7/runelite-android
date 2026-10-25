package javax.swing;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractAction implements Action, Cloneable, Serializable {
    private static final long serialVersionUID = -1748743320228L;

    protected boolean enabled = true;
    private Map<String, Object> values = new HashMap<>();
    // Buttons built from this action (AbstractButton.setAction) listen here to follow
    // NAME/enabled/icon edits, as Swing's SwingPropertyChangeSupport does.
    private transient CopyOnWriteArrayList<PropertyChangeListener> listeners;

    public AbstractAction() {}
    public AbstractAction(String name) { putValue(NAME, name); }
    public AbstractAction(String name, Icon icon) { this(name); putValue(SMALL_ICON, icon); }

    @Override public Object getValue(String key) {
        if ("enabled".equals(key)) return enabled;
        return values.get(key);
    }

    @Override public void putValue(String key, Object newValue) {
        Object oldValue;
        if ("enabled".equals(key)) {
            if (!(newValue instanceof Boolean)) newValue = false;
            oldValue = enabled;
            enabled = (Boolean) newValue;
        } else {
            oldValue = newValue == null ? values.remove(key) : values.put(key, newValue);
        }
        firePropertyChange(key, oldValue, newValue);
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean b) {
        boolean old = enabled;
        if (old != b) {
            enabled = b;
            firePropertyChange("enabled", old, b);
        }
    }

    public Object[] getKeys() { return values.isEmpty() ? null : values.keySet().toArray(); }

    @Override public synchronized void addPropertyChangeListener(PropertyChangeListener l) {
        if (l == null) return;
        if (listeners == null) listeners = new CopyOnWriteArrayList<>();
        listeners.add(l);
    }
    @Override public synchronized void removePropertyChangeListener(PropertyChangeListener l) {
        if (listeners != null) listeners.remove(l);
    }
    public synchronized PropertyChangeListener[] getPropertyChangeListeners() {
        return listeners == null ? new PropertyChangeListener[0] : listeners.toArray(new PropertyChangeListener[0]);
    }

    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        CopyOnWriteArrayList<PropertyChangeListener> ls = listeners;
        if (ls == null || ls.isEmpty() || (oldValue != null && newValue != null && oldValue.equals(newValue))) return;
        PropertyChangeEvent e = new PropertyChangeEvent(this, propertyName, oldValue, newValue);
        for (PropertyChangeListener l : ls) l.propertyChange(e);
    }

    @Override public abstract void actionPerformed(ActionEvent e);

    @Override public Object clone() throws CloneNotSupportedException {
        AbstractAction copy = (AbstractAction) super.clone();
        // Swing clones the key table but not the listeners.
        copy.values = new HashMap<>(values);
        copy.listeners = null;
        return copy;
    }
}
