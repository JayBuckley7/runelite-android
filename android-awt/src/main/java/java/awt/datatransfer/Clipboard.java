package java.awt.datatransfer;

import java.util.ArrayList;
import java.util.List;

public class Clipboard {
    private final String name;
    private Transferable contents;
    private ClipboardOwner owner;
    private final List<FlavorListener> flavorListeners = new ArrayList<>();
    public interface Bridge { String readText(); void writeText(String text); }
    private volatile Bridge bridge;
    private String mirroredText;
    public void setBridge(Bridge bridge) { synchronized (this) { this.bridge = bridge; } refreshFromBridge(); }
    public synchronized void clearBridge(Bridge expected) { if (bridge == expected) bridge = null; }
    public void refreshFromBridge() {
        Bridge adapter = bridge;
        if (adapter == null) return;
        String text;
        try { text = adapter.readText(); } catch (IllegalStateException | SecurityException unavailable) { return; }
        synchronized (this) { if (adapter != bridge || java.util.Objects.equals(text, mirroredText)) return; mirroredText = text; }
        updateContents(text == null ? null : new StringSelection(text), null);
    }

    public Clipboard(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public void setContents(Transferable contents, ClipboardOwner owner) {
        updateContents(contents, owner);
        Bridge adapter = bridge;
        if (adapter != null && contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            try { String text = (String) contents.getTransferData(DataFlavor.stringFlavor); synchronized (this) { mirroredText = text; } adapter.writeText(text); }
            catch (java.io.IOException | UnsupportedFlavorException e) { throw new IllegalStateException("Cannot read clipboard text", e); }
        }
    }
    private void updateContents(Transferable value, ClipboardOwner newOwner) {
        ClipboardOwner oldOwner;
        Transferable oldContents;
        FlavorListener[] listeners;
        synchronized (this) {
            oldOwner = owner; oldContents = contents;
            java.util.Set<DataFlavor> before = flavors(contents), after = flavors(value);
            contents = value; owner = newOwner;
            listeners = before.equals(after) ? new FlavorListener[0] : getFlavorListeners();
        }
        if (oldOwner != null && oldOwner != newOwner) {
            java.awt.EventQueue.invokeLater(() -> oldOwner.lostOwnership(this, oldContents));
        }
        for (FlavorListener listener : listeners) java.awt.EventQueue.invokeLater(() -> listener.flavorsChanged(new FlavorEvent(this)));
    }
    private static java.util.Set<DataFlavor> flavors(Transferable value) { return value == null ? java.util.Collections.emptySet() : new java.util.HashSet<>(java.util.Arrays.asList(value.getTransferDataFlavors())); }

    public Transferable getContents(Object requestor) {
        refreshFromBridge();
        synchronized (this) { return contents; }
    }

    public DataFlavor[] getAvailableDataFlavors() {
        Transferable t = getContents(null);
        return t == null ? new DataFlavor[0] : t.getTransferDataFlavors();
    }

    public boolean isDataFlavorAvailable(DataFlavor flavor) {
        Transferable t = getContents(null);
        return t != null && t.isDataFlavorSupported(flavor);
    }

    public Object getData(DataFlavor flavor) throws UnsupportedFlavorException, java.io.IOException {
        java.util.Objects.requireNonNull(flavor);
        Transferable value = getContents(null);
        if (value == null) throw new UnsupportedFlavorException(flavor);
        return value.getTransferData(flavor);
    }
    public synchronized void addFlavorListener(FlavorListener listener) { if (listener != null) flavorListeners.add(listener); }
    public synchronized void removeFlavorListener(FlavorListener listener) { flavorListeners.remove(listener); }
    public synchronized FlavorListener[] getFlavorListeners() { return flavorListeners.toArray(new FlavorListener[0]); }
}
