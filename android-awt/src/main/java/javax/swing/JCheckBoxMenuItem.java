package javax.swing;
public class JCheckBoxMenuItem extends JMenuItem {
    private static final long serialVersionUID=1L;
    public JCheckBoxMenuItem() { setModel(new JToggleButton.ToggleButtonModel()); }
    public JCheckBoxMenuItem(String text) { this(text,null,false); }
    public JCheckBoxMenuItem(Icon icon) { this(null,icon,false); }
    public JCheckBoxMenuItem(String text,Icon icon) { this(text,icon,false); }
    public JCheckBoxMenuItem(String text,boolean selected) { this(text,null,selected); }
    public JCheckBoxMenuItem(String text,Icon icon,boolean selected) { this();setText(text);setIcon(icon);setSelected(selected); }
    public JCheckBoxMenuItem(Action action) { this();setAction(action); }
    protected boolean shouldUpdateSelectedStateFromAction() { return true; }
    public boolean getState() { return isSelected(); }
    public void setState(boolean value) { setSelected(value); }
}
