package javax.swing;

public class JToggleButton extends AbstractButton {
    private static final long serialVersionUID = -3128140058214808885L;

    public static class ToggleButtonModel extends DefaultButtonModel {
        private static final long serialVersionUID = 1L;
        @Override public void setSelected(boolean selected) {
            ButtonGroup group = getGroup();
            if (group != null) { group.setSelected(this, selected); selected = group.isSelected(this); }
            super.setSelected(selected);
        }
        @Override public void setPressed(boolean pressed) {
            if (isPressed() == pressed || !isEnabled()) return;
            if (!pressed && isArmed()) setSelected(!isSelected());
            super.setPressed(pressed);
        }
    }

    public JToggleButton() { this(null, null, false); }
    public JToggleButton(Icon icon) { this(null, icon, false); }
    public JToggleButton(Icon icon, boolean selected) { this(null, icon, selected); }
    public JToggleButton(String text) { this(text, null, false); }
    public JToggleButton(String text, boolean selected) { this(text, null, selected); }
    public JToggleButton(Action a) { this(); setAction(a); }
    public JToggleButton(String text, Icon icon) { this(text, icon, false); }
    public JToggleButton(String text, Icon icon, boolean selected) {
        setModel(new ToggleButtonModel());
        setText(text); setIcon(icon); setSelected(selected);
    }

    @Override boolean shouldUpdateSelectedStateFromAction() { return true; }
}
