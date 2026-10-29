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

    private final javax.swing.event.EventListenerList hyperlinks=new javax.swing.event.EventListenerList();
    public JEditorPane() {
        setEditorKit(getEditorKitForContentType("text/plain"));
        addMouseListener(new java.awt.event.MouseAdapter(){public void mouseClicked(java.awt.event.MouseEvent event){if(!isEditable()&&html()){String link=linkAt(event.getX(),event.getY());if(link!=null){URL target=null;try{target=page==null?new URL(link):new URL(page,link);}catch(java.net.MalformedURLException ignored){}fireHyperlinkUpdate(new javax.swing.event.HyperlinkEvent(JEditorPane.this,javax.swing.event.HyperlinkEvent.EventType.ACTIVATED,target,link));}}}});
    }
    public JEditorPane(URL initialPage) throws IOException { this();setPage(initialPage); }
    public JEditorPane(String url) throws IOException { this(new URL(url)); }
    public JEditorPane(String type, String text) { this();setContentType(type);setText(text); }

    public String getContentType() { return contentType; }
    public void setContentType(String type) { String value=type.split(";",2)[0].trim().toLowerCase(java.util.Locale.ROOT);if(!value.equals(contentType)){contentType=value;setEditorKit(getEditorKitForContentType(value));} }
    public EditorKit getEditorKit() { return kit; }
    public void setEditorKit(EditorKit kit) { EditorKit old=this.kit;if(old!=null)old.deinstall(this);this.kit=kit;if(kit!=null){setDocument(kit.createDefaultDocument());kit.install(this);}firePropertyChange("editorKit",old,kit); }
    public URL getPage() { return page; }
    public void setPage(URL page) throws IOException { URL old=this.page;java.net.URLConnection connection=page.openConnection();String type=connection.getContentType();if(type!=null)setContentType(type);javax.swing.text.Document document=kit.createDefaultDocument();document.putProperty(javax.swing.text.Document.StreamDescriptionProperty,page);if(document instanceof javax.swing.text.html.HTMLDocument)((javax.swing.text.html.HTMLDocument)document).setBase(page);try(java.io.InputStream input=connection.getInputStream()){kit.read(input,document,0);}catch(javax.swing.text.BadLocationException e){throw new IOException(e);}setDocument(document);this.page=page;setCaretPosition(0);firePropertyChange("page",old,page); }
    public void setPage(String url) throws IOException { setPage(new URL(url)); }
    public void addHyperlinkListener(HyperlinkListener listener) { hyperlinks.add(HyperlinkListener.class,listener); }
    public void removeHyperlinkListener(HyperlinkListener listener) { hyperlinks.remove(HyperlinkListener.class,listener); }
    public void fireHyperlinkUpdate(javax.swing.event.HyperlinkEvent e) { for(HyperlinkListener listener:hyperlinks.getListeners(HyperlinkListener.class))listener.hyperlinkUpdate(e); }
    public HyperlinkListener[] getHyperlinkListeners(){return hyperlinks.getListeners(HyperlinkListener.class);}

    private String linkAt(int x,int y){java.awt.Insets in=getInsets();java.awt.FontMetrics metrics=getFontMetrics(getFont());HtmlTextRenderer.Layout layout=HtmlTextRenderer.measure(renderedHtml(),metrics,textWidth());int row=(y-in.top)/Math.max(1,metrics.getHeight());if(y<in.top||row<0||row>=layout.lines.size())return null;int left=in.left;for(HtmlTextRenderer.Run run:layout.lines.get(row).runs){int width=metrics.stringWidth(run.text);if(x>=left&&x<left+width)return run.href;left+=width;}return null;}
    private String renderedHtml(){return kit instanceof javax.swing.text.html.HTMLEditorKit?((javax.swing.text.html.HTMLEditorKit)kit).getStyleSheet().applyRules(getText()):getText();}
    private boolean html() { return "text/html".equalsIgnoreCase(contentType); }
    @Override public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        if (!html()) return plainPreferredSize(true, true, 8);
        java.awt.Insets in = getInsets();
        HtmlTextRenderer.Layout layout = HtmlTextRenderer.measure(renderedHtml(), getFontMetrics(getFont()), textWidth());
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
            HtmlTextRenderer.paint(copy, HtmlTextRenderer.measure(renderedHtml(), fm, textWidth()), fm, in.left, in.top, getForeground());
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
        return "text/html".equals(type) ? new javax.swing.text.html.HTMLEditorKit() : new javax.swing.text.DefaultEditorKit();
    }
}
