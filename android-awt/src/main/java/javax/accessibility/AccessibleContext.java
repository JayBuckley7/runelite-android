package javax.accessibility;

public abstract class AccessibleContext {
    public static final String ACCESSIBLE_NAME_PROPERTY = "AccessibleName";
    public static final String ACCESSIBLE_DESCRIPTION_PROPERTY = "AccessibleDescription";
    protected String accessibleName, accessibleDescription;
    protected Accessible accessibleParent;
    private final java.beans.PropertyChangeSupport changes = new java.beans.PropertyChangeSupport(this);
    public AccessibleContext() {}
    public String getAccessibleName() { return accessibleName; }
    public void setAccessibleName(String s) { String old = accessibleName; accessibleName = s; firePropertyChange(ACCESSIBLE_NAME_PROPERTY, old, s); }
    public String getAccessibleDescription() { return accessibleDescription; }
    public void setAccessibleDescription(String s) { String old = accessibleDescription; accessibleDescription = s; firePropertyChange(ACCESSIBLE_DESCRIPTION_PROPERTY, old, s); }
    public Accessible getAccessibleParent() { return accessibleParent; }
    public void setAccessibleParent(Accessible parent) { accessibleParent = parent; }
    public void addPropertyChangeListener(java.beans.PropertyChangeListener listener) { changes.addPropertyChangeListener(listener); }
    public void removePropertyChangeListener(java.beans.PropertyChangeListener listener) { changes.removePropertyChangeListener(listener); }
    public void firePropertyChange(String property, Object oldValue, Object newValue) { changes.firePropertyChange(property, oldValue, newValue); }
}
