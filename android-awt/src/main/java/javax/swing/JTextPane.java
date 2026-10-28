package javax.swing;
import javax.swing.text.*;
import java.awt.*;
public class JTextPane extends JEditorPane {
    public JTextPane() { setDocument(new DefaultStyledDocument()); }
    public JTextPane(Document doc) { this(); if(doc!=null)setDocument(doc); }
    public JTextPane(StyledDocument doc) { this((Document)doc); }
    public StyledDocument getStyledDocument() { return (StyledDocument)getDocument(); }
    public void setStyledDocument(StyledDocument doc) { setDocument(doc); }
    private int layoutStyled(Graphics g) {
        String text=getText(); Insets in=getInsets();int width=Math.max(1,getWidth()-in.left-in.right);int x=0,y=in.top;
        Font base=getFont();FontMetrics metrics=getFontMetrics(base);int line=metrics.getHeight();
        DefaultStyledDocument doc=(DefaultStyledDocument)getDocument();
        for(int i=0;i<text.length();i++){
            AttributeSet a=doc.attributesAt(i);Font font=base.deriveFont((StyleConstants.isBold(a)?Font.BOLD:0)|(StyleConstants.isItalic(a)?Font.ITALIC:0));
            FontMetrics fm=getFontMetrics(font);char ch=text.charAt(i);int cw=fm.charWidth(ch);
            if(ch=='\n'){y+=line;x=0;continue;}
            if(x>0 && x+cw>width){y+=line;x=0;}
            if(g!=null){g.setFont(font);Object color=a.getAttribute(StyleConstants.Foreground);g.setColor(color instanceof Color?(Color)color:getForeground());g.drawString(String.valueOf(ch),in.left+x,y+fm.getAscent());}
            x+=cw;
        }
        return y+line+in.bottom;
    }
    @Override public Dimension getPreferredSize(){
        if(isPreferredSizeSet() || !(getDocument() instanceof DefaultStyledDocument))return super.getPreferredSize();
        Dimension d=super.getPreferredSize();return new Dimension(d.width,layoutStyled(null));
    }
    @Override protected void paintComponent(Graphics g){
        if(!(getDocument() instanceof DefaultStyledDocument)){super.paintComponent(g);return;}
        if(isOpaque()){g.setColor(getBackground());g.fillRect(0,0,getWidth(),getHeight());}
        layoutStyled(g);
    }
}
