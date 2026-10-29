package javax.management;
public class ObjectInstance implements java.io.Serializable {
    private final ObjectName name;
    private final String className;
    public ObjectInstance(ObjectName name, String className) { this.name = name; this.className = className; }
    public ObjectInstance(String name, String className) throws MalformedObjectNameException { this(new ObjectName(name), className); }
    public ObjectName getObjectName() { return name; }
    public String getClassName() { return className; }
    @Override public boolean equals(Object other) { return other instanceof ObjectInstance && java.util.Objects.equals(name, ((ObjectInstance) other).name) && java.util.Objects.equals(className, ((ObjectInstance) other).className); }
    @Override public int hashCode() { return java.util.Objects.hash(name, className); }
    @Override public String toString() { return className + "[" + name + "]"; }
}
