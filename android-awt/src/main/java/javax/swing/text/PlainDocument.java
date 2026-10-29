package javax.swing.text;

/** Line-oriented document using the shared editing and undo implementation. */
public class PlainDocument extends AbstractDocument {
    public static final String tabSizeAttribute = "tabSize";
    public static final String lineLimitAttribute = "lineLimit";
    public PlainDocument() { putProperty(tabSizeAttribute,8); }
}
