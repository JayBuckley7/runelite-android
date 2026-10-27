package javax.swing.text;
import java.util.*;
public class DefaultStyledDocument extends PlainDocument implements StyledDocument {
    private final List<AttributeSet> attributes=new ArrayList<>();
    public AttributeSet attributesAt(int offset) { return offset>=0 && offset<attributes.size()?attributes.get(offset):SimpleAttributeSet.EMPTY; }
    @Override public void insertString(int offset,String text,AttributeSet a) throws BadLocationException {
        if(offset<0 || offset>getLength())throw new BadLocationException("insert",offset);
        if(text==null)return;
        AttributeSet copy=a==null?SimpleAttributeSet.EMPTY:a.copyAttributes();
        for(int i=0;i<text.length();i++)attributes.add(offset+i,copy);
        super.insertString(offset,text,a);
    }
    @Override public void remove(int offset,int length) throws BadLocationException {
        if(offset<0 || length<0 || offset+length>getLength())throw new BadLocationException("remove",offset);
        for(int i=0;i<length;i++)attributes.remove(offset);
        super.remove(offset,length);
    }
    @Override public void replace(int offset,int length,String text,AttributeSet a) throws BadLocationException { remove(offset,length);insertString(offset,text,a); }
    public Element getCharacterElement(int offset) {
        final int at=Math.max(0,Math.min(offset,getLength()));
        return new Element(){
            public Document getDocument(){return DefaultStyledDocument.this;}
            public Element getParentElement(){return null;}
            public String getName(){return "content";}
            public AttributeSet getAttributes(){return attributesAt(at);}
            public int getStartOffset(){return at;}
            public int getEndOffset(){return at+1;}
            public int getElementIndex(int pos){return -1;}
            public int getElementCount(){return 0;}
            public Element getElement(int i){return null;}
            public boolean isLeaf(){return true;}
        };
    }
    public void setCharacterAttributes(int offset,int length,AttributeSet a,boolean replace) {
        if(offset<0 || length<0 || offset+length>getLength())throw new IllegalArgumentException("range");
        for(int i=offset;i<offset+length;i++){SimpleAttributeSet v=new SimpleAttributeSet(replace?SimpleAttributeSet.EMPTY:attributes.get(i));v.addAttributes(a);attributes.set(i,v);}
    }
}
