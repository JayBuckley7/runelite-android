package javax.swing;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ActionMap implements Serializable {
    private static final long serialVersionUID = -2294474015281814929L;
    private final Map<Object, Action> map = new HashMap<>();
    private ActionMap parent;

    public void put(Object key, Action action) {
        if (action == null) map.remove(key);
        else map.put(key, action);
    }

    public Action get(Object key) {
        Action a = map.get(key);
        return a != null ? a : (parent != null ? parent.get(key) : null);
    }

    public void remove(Object key) { map.remove(key); }
    public void clear() { map.clear(); }
    public int size() { return map.size(); }
    public Object[] keys() { return map.keySet().toArray(); }
    public Object[] allKeys() { java.util.Set<Object> all = new java.util.LinkedHashSet<>(map.keySet()); if (parent != null && parent.allKeys() != null) java.util.Collections.addAll(all, parent.allKeys()); return all.toArray(new Object[0]); }

    public void setParent(ActionMap parent) { this.parent = parent; }
    public ActionMap getParent() { return parent; }
}
