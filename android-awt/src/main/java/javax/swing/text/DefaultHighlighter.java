package javax.swing.text;

import java.awt.*;
import java.util.*;

public class DefaultHighlighter implements Highlighter {
    public static final Highlighter.HighlightPainter DefaultPainter=new DefaultHighlightPainter(null);
    private JTextComponent component;
    private final java.util.List<Tag> tags=new ArrayList<>();
    public void install(JTextComponent c){component=c;tags.clear();}public void deinstall(JTextComponent c){tags.clear();component=null;}
    public void paint(Graphics g){if(component==null)return;Rectangle bounds=new Rectangle(0,0,component.getWidth(),component.getHeight());for(Tag tag:new ArrayList<>(tags))tag.painter.paint(g,tag.start.getOffset(),tag.end.getOffset(),bounds,component);}
    private final class Tag implements Highlight {Position start,end;HighlightPainter painter;public int getStartOffset(){return start.getOffset();}public int getEndOffset(){return end.getOffset();}public HighlightPainter getPainter(){return painter;}}
    private void check(int start,int end)throws BadLocationException{if(component==null||start<0||end<start||end>component.getDocument().getLength())throw new BadLocationException("highlight range",start);}
    public Object addHighlight(int start,int end,HighlightPainter painter)throws BadLocationException{check(start,end);Tag tag=new Tag();tag.start=component.getDocument().createPosition(start);tag.end=component.getDocument().createPosition(end);tag.painter=Objects.requireNonNull(painter);tags.add(tag);component.repaint();return tag;}
    public void removeHighlight(Object tag){tags.remove(tag);if(component!=null)component.repaint();}
    public void removeAllHighlights(){tags.clear();if(component!=null)component.repaint();}
    public void changeHighlight(Object value,int start,int end)throws BadLocationException{check(start,end);if(!tags.contains(value))throw new IllegalArgumentException("unknown highlight");Tag tag=(Tag)value;tag.start=component.getDocument().createPosition(start);tag.end=component.getDocument().createPosition(end);component.repaint();}
    public Highlight[] getHighlights(){return tags.toArray(new Highlight[0]);}
    public static class DefaultHighlightPainter implements HighlightPainter {
        private final Color color;public DefaultHighlightPainter(Color color){this.color=color;}public Color getColor(){return color;}
        public void paint(Graphics g,int start,int end,Shape bounds,JTextComponent c){try{g.setColor(color==null?c.getSelectionColor():color);Rectangle a=c.modelToView(start),b=c.modelToView(end);if(a.y==b.y)g.fillRect(a.x,a.y,Math.max(0,b.x-a.x),a.height);else{java.awt.Insets in=c.getInsets();g.fillRect(a.x,a.y,c.getWidth()-in.right-a.x,a.height);for(int y=a.y+a.height;y<b.y;y+=a.height)g.fillRect(in.left,y,c.getWidth()-in.left-in.right,a.height);g.fillRect(in.left,b.y,b.x-in.left,b.height);}}catch(BadLocationException ignored){}}
    }
}
