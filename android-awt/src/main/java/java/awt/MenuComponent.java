package java.awt;
public abstract class MenuComponent implements java.io.Serializable {
    MenuContainer parent;
    private Font font;
    private String name;
    public MenuContainer getParent() { return parent; }
    public Font getFont() { return font != null ? font : parent != null ? parent.getFont() : null; }
    public void setFont(Font font) { this.font = font; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public void removeNotify() {}
    public final void dispatchEvent(AWTEvent event) { processEvent(event); }
    protected void processEvent(AWTEvent event) {}
}
