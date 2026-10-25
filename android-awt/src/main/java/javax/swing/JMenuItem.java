package javax.swing;

public class JMenuItem extends AbstractButton implements MenuElement {
    private static final long serialVersionUID = 1L;

    public JMenuItem() {}
    public JMenuItem(String text) { setText(text); }
    public JMenuItem(Icon icon) { setIcon(icon); }
    public JMenuItem(Action a) { this(); setAction(a); }
    public JMenuItem(String text, Icon icon) { setText(text); setIcon(icon); }
    public JMenuItem(String text, int mnemonic) { setText(text); setMnemonic(mnemonic); }

    private KeyStroke accelerator;
    public KeyStroke getAccelerator() { return accelerator; }
    public void setAccelerator(KeyStroke keyStroke) { this.accelerator = keyStroke; }

    // Swing's JMenuItem takes only SMALL_ICON from its Action (never LARGE_ICON_KEY) and
    // also picks up ACCELERATOR_KEY.
    @Override
    void setIconFromAction(Action a) {
        setIcon(a == null ? null : (Icon) a.getValue(Action.SMALL_ICON));
    }

    @Override
    protected void configurePropertiesFromAction(Action a) {
        super.configurePropertiesFromAction(a);
        Object ks = a == null ? null : a.getValue(Action.ACCELERATOR_KEY);
        setAccelerator(ks instanceof KeyStroke ? (KeyStroke) ks : null);
    }

    @Override
    protected void actionPropertyChanged(Action a, String propertyName) {
        if (Action.LARGE_ICON_KEY.equals(propertyName)) return;
        if (Action.ACCELERATOR_KEY.equals(propertyName)) {
            Object ks = a.getValue(Action.ACCELERATOR_KEY);
            setAccelerator(ks instanceof KeyStroke ? (KeyStroke) ks : null);
            return;
        }
        super.actionPropertyChanged(a, propertyName);
    }
    public boolean isArmed() { return getModel().isArmed(); }
    public void setArmed(boolean b) { getModel().setArmed(b); }

    // MenuElement — a leaf item has no sub-elements; JMenu overrides getSubElements().
    @Override public void menuSelectionChanged(boolean isIncluded) {}
    @Override public MenuElement[] getSubElements() { return new MenuElement[0]; }
    @Override public java.awt.Component getComponent() { return this; }
}
