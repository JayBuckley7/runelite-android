package java.awt;

public class PopupMenu extends Menu {
    public PopupMenu() { this(""); }
    public PopupMenu(String label) { super(label); }
    public void show(Component origin, int x, int y) {
        if (origin == null) throw new NullPointerException("origin");
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu(getLabel()); fill(popup); popup.show(origin, x, y);
    }
}
