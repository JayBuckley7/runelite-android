package javax.swing.text;
public class StyleConstants {
    public static final Object Bold="bold", Italic="italic", Foreground="foreground", FontSize="size", FontFamily="family";
    public static void setBold(MutableAttributeSet a,boolean b) { a.addAttribute(Bold,b); }
    public static boolean isBold(AttributeSet a) { return Boolean.TRUE.equals(a.getAttribute(Bold)); }
    public static void setItalic(MutableAttributeSet a,boolean b) { a.addAttribute(Italic,b); }
    public static boolean isItalic(AttributeSet a) { return Boolean.TRUE.equals(a.getAttribute(Italic)); }
    public static void setForeground(MutableAttributeSet a,java.awt.Color c) { a.addAttribute(Foreground,c); }
    public static java.awt.Color getForeground(AttributeSet a) { Object c=a.getAttribute(Foreground);return c instanceof java.awt.Color?(java.awt.Color)c:java.awt.Color.BLACK; }
}
