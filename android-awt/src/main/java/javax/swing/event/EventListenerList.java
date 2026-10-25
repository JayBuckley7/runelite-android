package javax.swing.event;

import java.io.Serializable;
import java.util.EventListener;

/**
 * Swing's copy-on-write (class, listener) pair array. Models (DefaultBoundedRangeModel,
 * plugin-defined ones) store their listeners here and walk getListenerList() back to front
 * to fire, so the stub that dropped every add() silently disconnected them.
 */
public class EventListenerList implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Object[] NULL_ARRAY = new Object[0];
    protected transient volatile Object[] listenerList = NULL_ARRAY;

    public Object[] getListenerList() { return listenerList; }

    public <T extends EventListener> T[] getListeners(Class<T> t) {
        Object[] lList = listenerList;
        int n = getListenerCount(lList, t);
        @SuppressWarnings("unchecked")
        T[] result = (T[]) java.lang.reflect.Array.newInstance(t, n);
        int j = 0;
        for (int i = lList.length - 2; i >= 0; i -= 2) {
            if (lList[i] == t) {
                @SuppressWarnings("unchecked")
                T l = (T) lList[i + 1];
                result[j++] = l;
            }
        }
        return result;
    }

    public int getListenerCount() { return listenerList.length / 2; }
    public int getListenerCount(Class<?> t) { return getListenerCount(listenerList, t); }

    private static int getListenerCount(Object[] list, Class<?> t) {
        int count = 0;
        for (int i = 0; i < list.length; i += 2) if (t == (Class<?>) list[i]) count++;
        return count;
    }

    public synchronized <T extends EventListener> void add(Class<T> t, T l) {
        if (l == null) return;
        if (!t.isInstance(l)) throw new IllegalArgumentException("Listener " + l + " is not of type " + t);
        Object[] old = listenerList;
        Object[] next = new Object[old.length + 2];
        System.arraycopy(old, 0, next, 0, old.length);
        next[old.length] = t;
        next[old.length + 1] = l;
        listenerList = next;
    }

    public synchronized <T extends EventListener> void remove(Class<T> t, T l) {
        if (l == null) return;
        if (!t.isInstance(l)) throw new IllegalArgumentException("Listener " + l + " is not of type " + t);
        Object[] old = listenerList;
        int index = -1;
        for (int i = old.length - 2; i >= 0; i -= 2) {
            if (old[i] == t && old[i + 1].equals(l)) { index = i; break; }
        }
        if (index < 0) return;
        Object[] next = new Object[old.length - 2];
        System.arraycopy(old, 0, next, 0, index);
        if (index < next.length) System.arraycopy(old, index + 2, next, index, next.length - index);
        listenerList = next.length == 0 ? NULL_ARRAY : next;
    }

    @Override public String toString() {
        Object[] lList = listenerList;
        StringBuilder s = new StringBuilder("EventListenerList: ");
        s.append(lList.length / 2).append(" listeners: ");
        for (int i = 0; i <= lList.length - 2; i += 2) {
            s.append(" type ").append(((Class<?>) lList[i]).getName());
            s.append(" listener ").append(lList[i + 1]);
        }
        return s.toString();
    }
}
