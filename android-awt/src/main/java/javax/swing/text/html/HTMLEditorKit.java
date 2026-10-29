package javax.swing.text.html;

import javax.swing.Action;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;

public class HTMLEditorKit extends EditorKit {
    private static final long serialVersionUID = -1097137732655729898L;

    public static final String DEFAULT_CSS = "default.css";
    public static final String BOLD_ACTION = "html-bold-action";

    public abstract static class HTMLFactory {}
    public abstract static class HTMLTextAction {}

    private StyleSheet styles;

    public HTMLEditorKit() {}

    @Override public String getContentType() { return "text/html"; }
    @Override public Action[] getActions() { return new Action[0]; }
    @Override public Caret createCaret() { return new javax.swing.text.DefaultCaret(); }
    @Override public Document createDefaultDocument() { return new HTMLDocument(getStyleSheet()); }
    @Override public Object clone() { return new HTMLEditorKit(); }

    public StyleSheet getStyleSheet() {
        if (styles == null) styles = new StyleSheet();
        return styles;
    }
    public void setStyleSheet(StyleSheet s) { this.styles = s; }

    public void insertHTML(javax.swing.text.html.HTMLDocument doc, int offset, String html, int popDepth, int pushDepth, javax.swing.text.html.HTML.Tag insertTag) throws BadLocationException, java.io.IOException { doc.insertString(offset,html,null); }
}
