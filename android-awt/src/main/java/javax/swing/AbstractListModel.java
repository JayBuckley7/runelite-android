package javax.swing;

import java.io.Serializable;
import java.util.EventListener;
import javax.swing.event.EventListenerList;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

public abstract class AbstractListModel<E> implements ListModel<E>, Serializable {
    private static final long serialVersionUID = -3285184064379168730L;
    protected EventListenerList listenerList = new EventListenerList();

    @Override public void addListDataListener(ListDataListener l) { listenerList.add(ListDataListener.class, l); }
    @Override public void removeListDataListener(ListDataListener l) { listenerList.remove(ListDataListener.class, l); }
    public ListDataListener[] getListDataListeners() { return listenerList.getListeners(ListDataListener.class); }
    public <T extends EventListener> T[] getListeners(Class<T> listenerType) { return listenerList.getListeners(listenerType); }

    // JComboBox learns about selection and item changes only through these, so they have
    // to really fire (back to front, as Swing does).
    protected void fireContentsChanged(Object source, int index0, int index1) {
        fire(source, ListDataEvent.CONTENTS_CHANGED, index0, index1);
    }
    protected void fireIntervalAdded(Object source, int index0, int index1) {
        fire(source, ListDataEvent.INTERVAL_ADDED, index0, index1);
    }
    protected void fireIntervalRemoved(Object source, int index0, int index1) {
        fire(source, ListDataEvent.INTERVAL_REMOVED, index0, index1);
    }

    private void fire(Object source, int type, int index0, int index1) {
        Object[] listeners = listenerList.getListenerList();
        ListDataEvent e = null;
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] != ListDataListener.class) continue;
            if (e == null) e = new ListDataEvent(source, type, index0, index1);
            ListDataListener l = (ListDataListener) listeners[i + 1];
            if (type == ListDataEvent.CONTENTS_CHANGED) l.contentsChanged(e);
            else if (type == ListDataEvent.INTERVAL_ADDED) l.intervalAdded(e);
            else l.intervalRemoved(e);
        }
    }
}
