package javax.swing.text;

import java.awt.*;
import javax.swing.event.*;

public class DefaultCaret implements Caret, DocumentListener, java.io.Serializable {
    public static final int UPDATE_WHEN_ON_EDT=0, NEVER_UPDATE=1, ALWAYS_UPDATE=2;
    private transient JTextComponent component;
    private int dot,mark,blinkRate=500,updatePolicy=UPDATE_WHEN_ON_EDT;
    private boolean visible,selectionVisible=true,blinkOn=true;
    private transient javax.swing.Timer blinkTimer;
    private final java.awt.event.FocusListener focusListener=new java.awt.event.FocusAdapter(){public void focusGained(java.awt.event.FocusEvent e){setVisible(true);}public void focusLost(java.awt.event.FocusEvent e){setVisible(false);}};
    private Point magic;
    private final EventListenerList listeners=new EventListenerList();
    public void install(JTextComponent c){component=c;dot=mark=0;c.getDocument().addDocumentListener(this);c.addFocusListener(focusListener);setVisible(c.isFocusOwner());}
    public void deinstall(JTextComponent c){c.getDocument().removeDocumentListener(this);c.removeFocusListener(focusListener);if(blinkTimer!=null)blinkTimer.stop();component=null;}
    public void paint(Graphics g){if(!visible||!blinkOn||component==null)return;try{Rectangle r=component.modelToView(dot);g.setColor(component.getCaretColor());g.drawLine(r.x,r.y,r.x,r.y+r.height-1);}catch(BadLocationException ignored){}}
    public void addChangeListener(ChangeListener l){listeners.add(ChangeListener.class,l);}
    public void removeChangeListener(ChangeListener l){listeners.remove(ChangeListener.class,l);}
    public ChangeListener[] getChangeListeners(){return listeners.getListeners(ChangeListener.class);}
    protected void fireStateChanged(){ChangeEvent e=new ChangeEvent(this);for(ChangeListener l:getChangeListeners())l.stateChanged(e);if(component!=null)component.repaint();}
    public boolean isVisible(){return visible;}public void setVisible(boolean value){visible=value;blinkOn=true;restartBlink();if(component!=null)component.repaint();}
    private void restartBlink(){if(blinkTimer!=null)blinkTimer.stop();if(visible&&blinkRate>0){blinkTimer=new javax.swing.Timer(blinkRate,e->{blinkOn=!blinkOn;if(component!=null)component.repaint();});blinkTimer.start();}}
    public boolean isSelectionVisible(){return selectionVisible;}public void setSelectionVisible(boolean value){selectionVisible=value;if(component!=null)component.repaint();}
    public void setMagicCaretPosition(Point value){magic=value==null?null:new Point(value);}public Point getMagicCaretPosition(){return magic==null?null:new Point(magic);}
    public void setBlinkRate(int value){if(value<0)throw new IllegalArgumentException("blink rate");blinkRate=value;restartBlink();}public int getBlinkRate(){return blinkRate;}
    public void setUpdatePolicy(int value){if(value<0||value>2)throw new IllegalArgumentException("policy");updatePolicy=value;}public int getUpdatePolicy(){return updatePolicy;}
    public int getDot(){return dot;}public int getMark(){return mark;}
    private int bound(int p){return Math.max(0,Math.min(component==null?0:component.getDocument().getLength(),p));}
    public void setDot(int p){p=bound(p);if(dot!=p||mark!=p){dot=mark=p;fireStateChanged();}}
    public void moveDot(int p){p=bound(p);if(dot!=p){dot=p;fireStateChanged();}}
    public void insertUpdate(DocumentEvent e){if(updatePolicy==NEVER_UPDATE)return;int at=e.getOffset(),n=e.getLength();if(dot>=at)dot+=n;if(mark>=at)mark+=n;dot=bound(dot);mark=bound(mark);fireStateChanged();}
    public void removeUpdate(DocumentEvent e){int at=e.getOffset(),end=at+e.getLength();dot=dot>=end?dot-e.getLength():dot>at?at:dot;mark=mark>=end?mark-e.getLength():mark>at?at:mark;dot=bound(dot);mark=bound(mark);fireStateChanged();}
    public void changedUpdate(DocumentEvent e){if(component!=null)component.repaint();}
}
