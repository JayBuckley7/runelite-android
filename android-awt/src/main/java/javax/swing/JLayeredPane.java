package javax.swing;

import java.awt.Component;

public class JLayeredPane extends JComponent {
    private static final long serialVersionUID = 1L;

    public static final Integer DEFAULT_LAYER = 0;
    public static final Integer PALETTE_LAYER = 100;
    public static final Integer MODAL_LAYER = 200;
    public static final Integer POPUP_LAYER = 300;
    public static final Integer DRAG_LAYER = 400;
    public static final Integer FRAME_CONTENT_LAYER = -30000;

    public static final String LAYER_PROPERTY = "layeredContainerLayer";

    private final java.util.Map<Component,Integer> layers=new java.util.WeakHashMap<>();
    public Component add(Component c){add(c,Integer.valueOf(c instanceof JComponent?getLayer((JComponent)c):0));return c;}
    public void add(Component c,Object layer){add(c,layer,-1);}
    public void add(Component c,Object layer,int position){int value=layer instanceof Integer?(Integer)layer:0;layers.put(c,value);if(c instanceof JComponent)putLayer((JComponent)c,value);super.add(c);setPosition(c,position);}
    public void setLayer(Component c,int layer){setLayer(c,layer,-1);}
    public void setLayer(Component c,int layer,int position){layers.put(c,layer);if(c instanceof JComponent)putLayer((JComponent)c,layer);if(c.getParent()==this)setPosition(c,position);repaint();}
    public int getLayer(Component c){return c instanceof JComponent?getLayer((JComponent)c):layers.getOrDefault(c,0);}
    public int getComponentCountInLayer(int layer){return getComponentsInLayer(layer).length;}
    public Component[] getComponentsInLayer(int layer){java.util.List<Component> result=new java.util.ArrayList<>();for(Component c:getComponents())if(getLayer(c)==layer)result.add(c);return result.toArray(new Component[0]);}
    public int getPosition(Component c){if(c.getParent()!=this)return -1;int position=0;for(Component child:getComponents()){if(child==c)return position;if(getLayer(child)==getLayer(c))position++;}return -1;}
    public void setPosition(Component c,int position){if(c.getParent()!=this)return;int layer=getLayer(c),index=0,same=0;for(Component child:getComponents()){if(child==c)continue;int other=getLayer(child);if(other>layer||(other==layer&&(position<0||same<position))){index++;if(other==layer)same++;}else break;}setComponentZOrder(c,index);repaint();}
    public void moveToFront(Component c){setPosition(c,0);}
    public void moveToBack(Component c){setPosition(c,-1);}
    public static int getLayer(JComponent c){Object layer=c.getClientProperty(LAYER_PROPERTY);return layer instanceof Integer?(Integer)layer:0;}
    public static JLayeredPane getLayeredPaneAbove(Component c){for(Component current=c;current!=null;current=current.getParent())if(current instanceof JLayeredPane)return (JLayeredPane)current;return null;}
    public static void putLayer(JComponent c,int layer){c.putClientProperty(LAYER_PROPERTY,layer);}
    public int highestLayer(){int highest=Integer.MIN_VALUE;for(Component c:getComponents())highest=Math.max(highest,getLayer(c));return highest==Integer.MIN_VALUE?0:highest;}
    public int lowestLayer(){int lowest=Integer.MAX_VALUE;for(Component c:getComponents())lowest=Math.min(lowest,getLayer(c));return lowest==Integer.MAX_VALUE?0:lowest;}
}
