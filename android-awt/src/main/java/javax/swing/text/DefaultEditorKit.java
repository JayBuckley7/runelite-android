package javax.swing.text;
public class DefaultEditorKit extends EditorKit {
    public String getContentType(){return "text/plain";}
    public javax.swing.Action[] getActions(){return new javax.swing.Action[0];}
    public Caret createCaret(){return new DefaultCaret();}
    public Document createDefaultDocument(){return new PlainDocument();}
    public Object clone(){return new DefaultEditorKit();}
}
