package javax.swing.text;
import java.awt.*;
import java.io.Serializable;
import java.util.*;
import javax.swing.event.*;
public class StyleContext implements Serializable {
    public static final String DEFAULT_STYLE="default";
    private static final StyleContext SHARED=new StyleContext();
    private final Map<String,Style> styles=new LinkedHashMap<>();
    public StyleContext(){addStyle(DEFAULT_STYLE,null);}
    public static StyleContext getDefaultStyleContext(){return SHARED;}
    public Style addStyle(String name,Style parent){Style style=new NamedStyle(name,parent);if(name!=null)styles.put(name,style);return style;}
    public void removeStyle(String name){styles.remove(name);}
    public Style getStyle(String name){return styles.get(name);}
    public Enumeration<?> getStyleNames(){return Collections.enumeration(styles.keySet());}
    public Font getFont(String family,int style,int size){return new Font(family,style,size);}
    public Font getFont(AttributeSet attrs){return getFont(StyleConstants.getFontFamily(attrs),(StyleConstants.isBold(attrs)?Font.BOLD:0)|(StyleConstants.isItalic(attrs)?Font.ITALIC:0),StyleConstants.getFontSize(attrs));}
    public Color getForeground(AttributeSet attrs){return StyleConstants.getForeground(attrs);}
    public Color getBackground(AttributeSet attrs){return StyleConstants.getBackground(attrs);}
    public AttributeSet addAttribute(AttributeSet old,Object name,Object value){SimpleAttributeSet attrs=new SimpleAttributeSet(old);attrs.addAttribute(name,value);return attrs;}
    public AttributeSet addAttributes(AttributeSet old,AttributeSet value){SimpleAttributeSet attrs=new SimpleAttributeSet(old);attrs.addAttributes(value);return attrs;}
    public AttributeSet removeAttribute(AttributeSet old,Object name){SimpleAttributeSet attrs=new SimpleAttributeSet(old);attrs.removeAttribute(name);return attrs;}
    public AttributeSet removeAttributes(AttributeSet old,AttributeSet value){SimpleAttributeSet attrs=new SimpleAttributeSet(old);attrs.removeAttributes(value);return attrs;}
    public AttributeSet removeAttributes(AttributeSet old,Enumeration<?> names){SimpleAttributeSet attrs=new SimpleAttributeSet(old);attrs.removeAttributes(names);return attrs;}
    public AttributeSet getEmptySet(){return SimpleAttributeSet.EMPTY;}
    public class NamedStyle extends SimpleAttributeSet implements Style {
        private final String name;
        private final EventListenerList listeners=new EventListenerList();
        public NamedStyle(String name,Style parent){this.name=name;setResolveParent(parent);}
        public NamedStyle(Style parent){this(null,parent);}
        public NamedStyle(){this(null,null);}
        public String getName(){return name;}
        public void addChangeListener(ChangeListener l){listeners.add(ChangeListener.class,l);}
        public void removeChangeListener(ChangeListener l){listeners.remove(ChangeListener.class,l);}
        protected void fireStateChanged(){if(listeners!=null)for(ChangeListener l:listeners.getListeners(ChangeListener.class))l.stateChanged(new ChangeEvent(this));}
        public void addAttribute(Object key,Object value){super.addAttribute(key,value);fireStateChanged();}
        public void addAttributes(AttributeSet attrs){super.addAttributes(attrs);fireStateChanged();}
        public void removeAttribute(Object key){super.removeAttribute(key);fireStateChanged();}
        public void removeAttributes(AttributeSet attrs){super.removeAttributes(attrs);fireStateChanged();}
        public void removeAttributes(Enumeration<?> attrs){super.removeAttributes(attrs);fireStateChanged();}
        public void setResolveParent(AttributeSet attrs){super.setResolveParent(attrs);fireStateChanged();}
    }
}
