package javax.swing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

/**
 * Swing's semantics: an array/Vector-built model starts with its first item selected, the
 * first addElement into an empty model selects it, removing the selected item moves the
 * selection to a neighbour, and every change fires a ListDataEvent (selection changes as
 * contentsChanged(-1, -1)) — JComboBox derives its ItemEvents/ActionEvents from those.
 */
public class DefaultComboBoxModel<E> extends AbstractListModel<E> implements MutableComboBoxModel<E> {
    private static final long serialVersionUID = 1L;
    private final List<E> objects;
    private Object selectedObject;

    public DefaultComboBoxModel() { objects = new ArrayList<>(); }
    public DefaultComboBoxModel(E[] items) {
        objects = new ArrayList<>(items.length);
        for (E e : items) objects.add(e);
        if (getSize() > 0) selectedObject = getElementAt(0);
    }
    public DefaultComboBoxModel(Vector<E> v) {
        objects = new ArrayList<>(v);
        if (getSize() > 0) selectedObject = getElementAt(0);
    }

    @Override public int getSize() { return objects.size(); }
    @Override public E getElementAt(int index) {
        return index >= 0 && index < objects.size() ? objects.get(index) : null;
    }
    @Override public Object getSelectedItem() { return selectedObject; }
    @Override public void setSelectedItem(Object anObject) {
        if ((selectedObject != null && !selectedObject.equals(anObject)) || (selectedObject == null && anObject != null)) {
            selectedObject = anObject;
            fireContentsChanged(this, -1, -1);
        }
    }
    public int getIndexOf(Object anObject) { return objects.indexOf(anObject); }

    @Override public void addElement(E anObject) {
        objects.add(anObject);
        fireIntervalAdded(this, objects.size() - 1, objects.size() - 1);
        if (objects.size() == 1 && selectedObject == null && anObject != null) setSelectedItem(anObject);
    }
    @Override public void insertElementAt(E anObject, int index) {
        objects.add(index, anObject);
        fireIntervalAdded(this, index, index);
    }
    @Override public void removeElementAt(int index) {
        if (getElementAt(index) == selectedObject) {
            if (index == 0) setSelectedItem(getSize() == 1 ? null : getElementAt(index + 1));
            else setSelectedItem(getElementAt(index - 1));
        }
        objects.remove(index);
        fireIntervalRemoved(this, index, index);
    }
    @Override public void removeElement(Object anObject) {
        int index = objects.indexOf(anObject);
        if (index != -1) removeElementAt(index);
    }
    public void removeAllElements() {
        if (!objects.isEmpty()) {
            int lastIndex = objects.size() - 1;
            objects.clear();
            selectedObject = null;
            fireIntervalRemoved(this, 0, lastIndex);
        } else {
            selectedObject = null;
        }
    }
    public void addAll(Collection<? extends E> c) { addAll(objects.size(), c); }
    public void addAll(int index, Collection<? extends E> c) {
        if (index < 0 || index > getSize()) throw new ArrayIndexOutOfBoundsException("index out of range: " + index);
        if (c.isEmpty()) return;
        objects.addAll(index, c);
        fireIntervalAdded(this, index, index + c.size() - 1);
    }
}
