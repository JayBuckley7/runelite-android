package javax.swing;

import java.io.IOException;
import java.net.URL;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.EditorKit;
import javax.swing.text.JTextComponent;

public class JEditorPane extends JTextComponent {
    private static final long serialVersionUID = 1L;

    private String contentType = "text/plain";
    private EditorKit kit;
    private final java.util.Map<String, EditorKit> kits = new java.util.HashMap<>();
    private URL page;

    public JEditorPane() {}
    public JEditorPane(URL initialPage) throws IOException { this.page = initialPage; }
    public JEditorPane(String url) throws IOException {}
    public JEditorPane(String type, String text) { this.contentType = type; setText(text); }

    public String getContentType() { return contentType; }
    public void setContentType(String type) { this.contentType = type; }
    public EditorKit getEditorKit() { return kit; }
    public void setEditorKit(EditorKit kit) { this.kit = kit; }
    public URL getPage() { return page; }
    public void setPage(URL page) throws IOException { this.page = page; }
    public void setPage(String url) throws IOException {}
    public void addHyperlinkListener(HyperlinkListener listener) {}
    public void removeHyperlinkListener(HyperlinkListener listener) {}
    public void fireHyperlinkUpdate(javax.swing.event.HyperlinkEvent e) {}

    public EditorKit getEditorKitForContentType(String type) {
        EditorKit result = kits.get(type);
        if (result == null) {
            result = createEditorKitForContentType(type);
            if (result != null) kits.put(type, result);
        }
        return result;
    }
    public void setEditorKitForContentType(String type, EditorKit k) { kits.put(type, k); }
    public static EditorKit createEditorKitForContentType(String type) { return "text/html".equals(type) ? new javax.swing.text.html.HTMLEditorKit() : null; }
}
