package javax.swing.text;
import java.util.*;
public class SimpleAttributeSet implements MutableAttributeSet, Cloneable, java.io.Serializable {
    public static final AttributeSet EMPTY = new SimpleAttributeSet();
    private final Map<Object,Object> values = new LinkedHashMap<>();
    private AttributeSet parent;
    public SimpleAttributeSet() {}
    public SimpleAttributeSet(AttributeSet a) { addAttributes(a); }
    public int getAttributeCount() { return values.size(); }
    public boolean isDefined(Object key) { return values.containsKey(key); }
    public Object getAttribute(Object key) { return values.containsKey(key) ? values.get(key) : parent == null ? null : parent.getAttribute(key); }
    public Enumeration<?> getAttributeNames() { return Collections.enumeration(values.keySet()); }
    public boolean containsAttribute(Object key,Object value) { return Objects.equals(getAttribute(key),value); }
    public boolean containsAttributes(AttributeSet a) { Enumeration<?> e=a.getAttributeNames();while(e.hasMoreElements()){Object k=e.nextElement();if(!containsAttribute(k,a.getAttribute(k)))return false;}return true; }
    public boolean isEqual(AttributeSet a) { return a.getAttributeCount()==getAttributeCount() && containsAttributes(a); }
    public AttributeSet copyAttributes() { SimpleAttributeSet a=new SimpleAttributeSet(this);a.parent=parent;return a; }
    public Object clone() { return copyAttributes(); }
    public void addAttribute(Object k,Object v) { values.put(k,v); }
    public void addAttributes(AttributeSet a) { if(a==null)return;Enumeration<?> e=a.getAttributeNames();while(e.hasMoreElements()){Object k=e.nextElement();values.put(k,a.getAttribute(k));} }
    public void removeAttribute(Object k) { values.remove(k); }
    public void removeAttributes(Enumeration<?> e) { while(e.hasMoreElements())values.remove(e.nextElement()); }
    public void removeAttributes(AttributeSet a) { List<Object> keys=new ArrayList<>();Enumeration<?> e=a.getAttributeNames();while(e.hasMoreElements()){Object k=e.nextElement();if(containsAttribute(k,a.getAttribute(k)))keys.add(k);}for(Object k:keys)values.remove(k); }
    public AttributeSet getResolveParent() { return parent; }
    public void setResolveParent(AttributeSet a) { parent=a; }
}
