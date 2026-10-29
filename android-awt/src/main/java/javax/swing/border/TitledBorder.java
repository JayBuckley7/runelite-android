package javax.swing.border;
import java.awt.*;
import javax.swing.*;
/** A border with a measured title and reserved title insets. */
public class TitledBorder extends AbstractBorder {
    public static final int DEFAULT_POSITION=0,ABOVE_TOP=1,TOP=2,BELOW_TOP=3,ABOVE_BOTTOM=4,BOTTOM=5,BELOW_BOTTOM=6;
    public static final int DEFAULT_JUSTIFICATION=0,LEFT=1,CENTER=2,RIGHT=3,LEADING=4,TRAILING=5;
    protected String title;protected Border border;protected int titlePosition,titleJustification;protected Font titleFont;protected Color titleColor;
    public TitledBorder(String title){this(null,title);}
    public TitledBorder(Border border){this(border,"");}
    public TitledBorder(Border border,String title){this(border,title,LEADING,TOP);}
    public TitledBorder(Border border,String title,int justification,int position){this(border,title,justification,position,null,null);}
    public TitledBorder(Border border,String title,int justification,int position,Font font){this(border,title,justification,position,font,null);}
    public TitledBorder(Border border,String title,int justification,int position,Font font,Color color){this.border=border;this.title=title;setTitleJustification(justification);setTitlePosition(position);titleFont=font;titleColor=color;}
    public String getTitle(){return title;}public void setTitle(String value){title=value;}
    public Border getBorder(){return border==null?new EtchedBorder():border;}public void setBorder(Border value){border=value;}
    public int getTitlePosition(){return titlePosition;}public void setTitlePosition(int value){if(value<0||value>6)throw new IllegalArgumentException("position");titlePosition=value;}
    public int getTitleJustification(){return titleJustification;}public void setTitleJustification(int value){if(value<0||value>5)throw new IllegalArgumentException("justification");titleJustification=value;}
    public Font getTitleFont(){return titleFont;}public void setTitleFont(Font value){titleFont=value;}
    public Color getTitleColor(){return titleColor;}public void setTitleColor(Color value){titleColor=value;}
    private Font font(Component c){return titleFont==null?c.getFont():titleFont;}
    private boolean lower(){return titlePosition>=ABOVE_BOTTOM;}
    public Insets getBorderInsets(Component c){Insets in=getBorder().getBorderInsets(c);Insets result=new Insets(in.top+2,in.left+4,in.bottom+2,in.right+4);if(title!=null&&!title.isEmpty()){int height=c.getFontMetrics(font(c)).getHeight();if(lower())result.bottom+=height;else result.top+=height;}return result;}
    public Insets getBorderInsets(Component c,Insets in){Insets value=getBorderInsets(c);in.set(value.top,value.left,value.bottom,value.right);return in;}
    public boolean isBorderOpaque(){return false;}
    public void paintBorder(Component c,Graphics g,int x,int y,int width,int height){
        if(title==null||title.isEmpty()){getBorder().paintBorder(c,g,x,y,width,height);return;}
        FontMetrics metrics=c.getFontMetrics(font(c));int textHeight=metrics.getHeight(),textWidth=metrics.stringWidth(title);int position=titlePosition==DEFAULT_POSITION?TOP:titlePosition;
        int inset=(position==ABOVE_TOP||position==BELOW_BOTTOM)?textHeight:(position==TOP||position==BOTTOM)?textHeight/2:0;
        int by=lower()?y:y+inset,bh=height-inset;getBorder().paintBorder(c,g,x,by,width,bh);
        int tx=x+6;if(titleJustification==CENTER)tx=x+(width-textWidth)/2;else if(titleJustification==RIGHT||titleJustification==TRAILING)tx=x+width-textWidth-6;
        int baseline=lower()?y+height-metrics.getDescent()-2:y+metrics.getAscent()+2;
        Color old=g.getColor();Font oldFont=g.getFont();g.setColor(c.getBackground());g.fillRect(tx-2,baseline-metrics.getAscent(),textWidth+4,textHeight);g.setFont(font(c));g.setColor(titleColor==null?c.getForeground():titleColor);g.drawString(title,tx,baseline);g.setColor(old);g.setFont(oldFont);
    }
    public Dimension getMinimumSize(Component c){Insets in=getBorderInsets(c);return new Dimension(in.left+in.right+(title==null?0:c.getFontMetrics(font(c)).stringWidth(title)),in.top+in.bottom);}
}
