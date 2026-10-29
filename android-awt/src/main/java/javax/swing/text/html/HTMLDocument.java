package javax.swing.text.html;

import javax.swing.text.AbstractDocument;

public class HTMLDocument extends AbstractDocument {
    private static final long serialVersionUID = -1697057302311487090L;
    private final StyleSheet styles;
    private java.net.URL base;
    public HTMLDocument(){this(new StyleSheet());}
    public HTMLDocument(StyleSheet styles){this.styles=styles;}
    public StyleSheet getStyleSheet(){return styles;}
    public java.net.URL getBase(){return base;}
    public void setBase(java.net.URL base){this.base=base;}

}
