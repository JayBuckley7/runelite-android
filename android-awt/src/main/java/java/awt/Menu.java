package java.awt;
public class Menu extends MenuItem implements MenuContainer {
    private final java.util.List<MenuItem> items = new java.util.ArrayList<>();
    private final boolean tearOff;
    public Menu() { this(""); }
    public Menu(String label) { this(label, false); }
    public Menu(String label, boolean tearOff) { super(label); this.tearOff = tearOff; }
    public boolean isTearOff() { return tearOff; }
    public MenuItem add(MenuItem item) { insert(item, items.size()); return item; }
    public void add(String label) { add(new MenuItem(label)); }
    public void addSeparator() { add("-"); }
    public void insert(MenuItem item, int index) {
        if (index < 0) throw new IllegalArgumentException("negative index");
        for (MenuContainer ancestor = this; ancestor instanceof Menu; ancestor = ((Menu) ancestor).getParent())
            if (ancestor == item) throw new IllegalArgumentException("menu cycle");
        if (item.parent != null) item.parent.remove(item);
        items.add(Math.min(index, items.size()), item); item.parent = this;
    }
    public void insert(String label, int index) { insert(new MenuItem(label), index); }
    public void insertSeparator(int index) { insert("-", index); }
    public int getItemCount() { return items.size(); }
    public int countItems() { return getItemCount(); }
    public MenuItem getItem(int index) { return items.get(index); }
    public void remove(int index) { MenuItem item = items.remove(index); item.parent = null; }
    @Override public void remove(MenuComponent item) { if (items.remove(item)) item.parent = null; }
    public void removeAll() { for (MenuItem item : items) item.parent = null; items.clear(); }
    void fill(javax.swing.JPopupMenu popup) {
        for (MenuItem item : new java.util.ArrayList<>(items)) {
            if ("-".equals(item.getLabel())) popup.addSeparator(); else popup.add(item.swingItem());
        }
    }
    @Override javax.swing.JMenuItem swingItem() {
        javax.swing.JMenu menu = new javax.swing.JMenu(getLabel()); configure(menu); fill(menu.getPopupMenu()); return menu;
    }
}
