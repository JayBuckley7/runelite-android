package javax.swing;

import java.awt.Component;

public class PopupFactory {
    private static PopupFactory sharedFactory = new PopupFactory();

    public PopupFactory() {}

    public static void setSharedInstance(PopupFactory factory) { sharedFactory = factory; }
    public static PopupFactory getSharedInstance() { return sharedFactory; }

    public Popup getPopup(Component owner, Component contents, int x, int y) { return getPopup(owner,contents,x,y,false); }
    public Popup getPopup(Component owner, Component contents, int x, int y, boolean isHeavyWeightPopup) {
        if(contents==null)throw new IllegalArgumentException("contents");
        return new Popup(){private JWindow window;public void show(){if(window==null){window=new JWindow();window.getContentPane().add(contents);java.awt.Dimension size=contents.getPreferredSize();window.setBounds(x,y,Math.max(1,size.width),Math.max(1,size.height));}window.setVisible(true);}public void hide(){if(window!=null){window.dispose();window=null;}}};
    }
}
