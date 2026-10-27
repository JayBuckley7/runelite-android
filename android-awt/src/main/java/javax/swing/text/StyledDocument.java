package javax.swing.text;
public interface StyledDocument extends Document {
    Element getCharacterElement(int offset);
    void setCharacterAttributes(int offset,int length,AttributeSet attributes,boolean replace);
}
