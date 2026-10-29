package javax.swing.colorchooser;

import java.awt.Color;
import java.io.Serializable;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

public class DefaultColorSelectionModel implements ColorSelectionModel, Serializable {
    protected transient ChangeEvent changeEvent;
    protected EventListenerList listenerList = new EventListenerList();
    private Color color;
    public DefaultColorSelectionModel() { this(Color.WHITE); }
    public DefaultColorSelectionModel(Color color) { this.color = color; }
    public Color getSelectedColor() { return color; }
    public void setSelectedColor(Color value) {
        if (value != null && !value.equals(color)) { color = value; fireStateChanged(); }
    }
    public void addChangeListener(ChangeListener listener) { listenerList.add(ChangeListener.class, listener); }
    public void removeChangeListener(ChangeListener listener) { listenerList.remove(ChangeListener.class, listener); }
    public ChangeListener[] getChangeListeners() { return listenerList.getListeners(ChangeListener.class); }
    protected void fireStateChanged() {
        if (changeEvent == null) changeEvent = new ChangeEvent(this);
        for (ChangeListener listener : getChangeListeners()) listener.stateChanged(changeEvent);
    }
}
