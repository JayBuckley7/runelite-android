package javax.swing;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class InputMap implements Serializable {
    private static final long serialVersionUID = 2454458884813506064L;
    private final Map<KeyStroke, Object> map = new HashMap<>();
    private InputMap parent;

    public void put(KeyStroke keyStroke, Object actionMapKey) {
        if (actionMapKey == null) map.remove(keyStroke);
        else map.put(keyStroke, actionMapKey);
    }

    public Object get(KeyStroke keyStroke) {
        Object o = map.get(keyStroke);
        return o != null ? o : (parent != null ? parent.get(keyStroke) : null);
    }

    public void remove(KeyStroke key) { map.remove(key); }
    public void clear() { map.clear(); }
    public int size() { return map.size(); }
    public KeyStroke[] keys() { return map.keySet().toArray(new KeyStroke[0]); }
    public KeyStroke[] allKeys() { java.util.Set<KeyStroke> all = new java.util.LinkedHashSet<>(map.keySet()); if (parent != null && parent.allKeys() != null) java.util.Collections.addAll(all, parent.allKeys()); return all.toArray(new KeyStroke[0]); }

    public void setParent(InputMap parent) { this.parent = parent; }
    public InputMap getParent() { return parent; }
}
