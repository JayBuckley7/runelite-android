package java.awt;
public class MenuShortcut implements java.io.Serializable {
    private final int key;
    private final boolean shift;
    public MenuShortcut(int key) { this(key, false); }
    public MenuShortcut(int key, boolean shift) { this.key = key; this.shift = shift; }
    public int getKey() { return key; }
    public boolean usesShiftModifier() { return shift; }
    public boolean equals(MenuShortcut other) { return other != null && other.key == key && other.shift == shift; }
    @Override public boolean equals(Object other) { return other instanceof MenuShortcut && equals((MenuShortcut) other); }
    @Override public int hashCode() { return key ^ (shift ? -1 : 0); }
}
