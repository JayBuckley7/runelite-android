package javax.swing;

public class JToggleButton extends AbstractButton {
    private static final long serialVersionUID = -3128140058214808885L;

    public static class ToggleButtonModel extends DefaultButtonModel {
        private static final long serialVersionUID = 1L;
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
