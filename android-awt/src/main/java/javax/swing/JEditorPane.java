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

    private boolean html() { return "text/html".equalsIgnoreCase(contentType); }
    @Override public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        if (!html()) return plainPreferredSize(true, true, 8);
        java.awt.Insets in = getInsets();
        HtmlTextRenderer.Layout layout = HtmlTextRenderer.measure(getText(), getFontMetrics(getFont()), textWidth());
        return new java.awt.Dimension(layout.width + in.left + in.right, layout.height + in.top + in.bottom);
    }
    @Override protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);
        if (!html()) { paintPlainText(g, true, true, 8); return; }
        java.awt.Graphics copy = g.create();
        try {
            java.awt.Insets in = getInsets();
            copy.clipRect(in.left, in.top, textWidth(), Math.max(0, getHeight() - in.top - in.bottom));
            copy.setFont(getFont());
            java.awt.FontMetrics fm = getFontMetrics(getFont());
            HtmlTextRenderer.paint(copy, HtmlTextRenderer.measure(getText(), fm, textWidth()), fm, in.left, in.top, getForeground());
        } finally { copy.dispose(); }
    }

    // Swing hands back a kit for any registered type and callers cast without checking:
    // InfoPlugin's JRichTextPane does ((HTMLEditorKit) getEditorKitForContentType("text/html"))
    // .getStyleSheet() in its constructor, so a null here kept the Info panel from starting.
    private final java.util.Map<String, EditorKit> kits = new java.util.HashMap<>();
    public EditorKit getEditorKitForContentType(String type) {
        EditorKit k = kits.get(type);
        if (k == null && (k = createEditorKitForContentType(type)) != null) kits.put(type, k);
        return k;
    }
    public void setEditorKitForContentType(String type, EditorKit k) { if (type != null) kits.put(type, k); }
    public static EditorKit createEditorKitForContentType(String type) {
        return "text/html".equals(type) ? new javax.swing.text.html.HTMLEditorKit() : null;
    }
}
