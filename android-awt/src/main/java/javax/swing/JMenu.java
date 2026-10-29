package javax.swing;

public class JMenu extends JMenuItem {
    private static final long serialVersionUID = 1L;
    private final JPopupMenu popup = new JPopupMenu();
    public JMenu() {
        popup.setInvoker(this);
        popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener(){
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent event){setSelected(true);if(getParent() instanceof JMenuBar)((JMenuBar)getParent()).setSelected(JMenu.this);}
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent event){clear();}
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent event){clear();}
            private void clear(){setSelected(false);if(getParent() instanceof JMenuBar){JMenuBar bar=(JMenuBar)getParent();if(bar.getSelectionModel().getSelectedIndex()==bar.getComponentZOrder(JMenu.this))bar.setSelected(null);}}
        });
    }
    public JMenu(String text) { this(); setText(text); }
    public JMenu(String text, boolean tearOff) { this(text); }
    public JMenu(Action action) { this(); setAction(action); }
    public JPopupMenu getPopupMenu() { return popup; }
    public JMenuItem add(JMenuItem item) { return popup.add(item); }
    public JMenuItem add(String text) { return popup.add(text); }
    public JMenuItem add(Action action) { return popup.add(action); }
    public java.awt.Component add(java.awt.Component component) { return popup.add(component); }
    public void addSeparator() { popup.addSeparator(); }
    public JMenuItem insert(JMenuItem item, int index) { popup.insert(item,index); return item; }
    public JMenuItem insert(Action action, int index) { return insert(new JMenuItem(action),index); }
    public void insert(String text, int index) { insert(new JMenuItem(text),index); }
    public void insertSeparator(int index) { popup.insert(new JPopupMenu.Separator(),index); }
    public JMenuItem getItem(int index) { java.awt.Component child=popup.getComponent(index);return child instanceof JMenuItem?(JMenuItem)child:null; }
    public int getItemCount() { return popup.getComponentCount(); }
    public int getMenuComponentCount() { return popup.getComponentCount(); }
    public java.awt.Component getMenuComponent(int index) { return popup.getComponent(index); }
    public java.awt.Component[] getMenuComponents() { return popup.getComponents(); }
    public void remove(JMenuItem item) { popup.remove(item); }
    public void remove(int index) { popup.remove(index); }
    public void remove(java.awt.Component component) { popup.remove(component); }
    public void removeAll() { popup.removeAll(); }
    public boolean isTearOff() { return false; }
    public boolean isPopupMenuVisible() { return popup.isVisible(); }
    public void setPopupMenuVisible(boolean visible) { if(visible&&isEnabled())popup.show(this,0,getHeight());else popup.setVisible(false); }
    public void doClick(int pressTime) { setPopupMenuVisible(!isPopupMenuVisible()); }
    public MenuElement[] getSubElements() { return new MenuElement[]{popup}; }
    public void menuSelectionChanged(boolean included) { setSelected(included); }
}
