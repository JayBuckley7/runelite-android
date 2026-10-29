package javax.management;

/**
 * Stub of {@code javax.management.MBeanServer}. Android has no JMX; methods accept calls
 * but record nothing.
 */
public interface MBeanServer {
    ObjectInstance registerMBean(Object obj, ObjectName name) throws Exception;
    void unregisterMBean(ObjectName name) throws Exception;
    boolean isRegistered(ObjectName name);
    Object getAttribute(ObjectName name, String attribute) throws Exception;
    void setAttribute(ObjectName name, Attribute attribute) throws Exception;
    Object invoke(ObjectName name, String op, Object[] params, String[] signature) throws Exception;
    java.util.Set<ObjectName> queryNames(ObjectName name, QueryExp query);
    Integer getMBeanCount();
}
