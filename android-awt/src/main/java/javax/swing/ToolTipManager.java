package javax.swing;

import java.awt.event.MouseAdapter;

public class ToolTipManager extends MouseAdapter {
    private static final ToolTipManager INSTANCE = new ToolTipManager();
    private boolean enabled = true;
    private boolean lightWeightPopupEnabled = true;
    private boolean heavyWeightPopupEnabled = false;
    private int initialDelay = 750;
    private int dismissDelay = 4000;
    private int reshowDelay = 500;

    public static ToolTipManager sharedInstance() { return INSTANCE; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setLightWeightPopupEnabled(boolean aFlag) { this.lightWeightPopupEnabled = aFlag; }
    public boolean isLightWeightPopupEnabled() { return lightWeightPopupEnabled; }
    public boolean isHeavyWeightPopupEnabled() { return heavyWeightPopupEnabled; }
    public void setInitialDelay(int milliseconds) { this.initialDelay = milliseconds; }
    public int getInitialDelay() { return initialDelay; }
    public void setDismissDelay(int milliseconds) { this.dismissDelay = milliseconds; }
    public int getDismissDelay() { return dismissDelay; }
    public void setReshowDelay(int milliseconds) { this.reshowDelay = milliseconds; }
    public int getReshowDelay() { return reshowDelay; }
    // Weak so registering never pins a discarded panel. Desktop registration installs
    // listeners; here the panel host asks instead, to treat these as tooltip owners.
    private final java.util.Map<JComponent, Boolean> registered =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public void registerComponent(JComponent component) { if (component != null) registered.put(component, Boolean.TRUE); }
    public void unregisterComponent(JComponent component) { if (component != null) registered.remove(component); }
    /** Host hook (not JDK API): whether {@link #registerComponent} holds this component. */
    public boolean isRegistered(JComponent component) { return component != null && registered.containsKey(component); }
    public void mouseDragged(java.awt.event.MouseEvent e) {}
}
