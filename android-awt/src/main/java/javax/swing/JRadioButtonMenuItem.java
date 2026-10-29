package javax.swing;
public class JRadioButtonMenuItem extends JMenuItem {
    private static final long serialVersionUID=1L;
    public JRadioButtonMenuItem() { setModel(new JToggleButton.ToggleButtonModel()); }
    public JRadioButtonMenuItem(String text) { this(text,null,false); }
    public JRadioButtonMenuItem(Icon icon) { this(null,icon,false); }
    public JRadioButtonMenuItem(String text,Icon icon) { this(text,icon,false); }
    public JRadioButtonMenuItem(String text,boolean selected) { this(text,null,selected); }
    public JRadioButtonMenuItem(String text,Icon icon,boolean selected) { this();setText(text);setIcon(icon);setSelected(selected); }
    public JRadioButtonMenuItem(Action action) { this();setAction(action); }
    protected boolean shouldUpdateSelectedStateFromAction() { return true; }
    public boolean getState() { return isSelected(); }
    public void setState(boolean value) { setSelected(value); }
}
