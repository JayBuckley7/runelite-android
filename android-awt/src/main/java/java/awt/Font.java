package java.awt;

import java.awt.font.TextAttribute;
import java.io.Serializable;

public class Font implements Serializable {
    private static final long serialVersionUID = -4206021311591459213L;

    public static final int PLAIN = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;

    public static final String DIALOG = "Dialog";
    public static final String DIALOG_INPUT = "DialogInput";
    public static final String SANS_SERIF = "SansSerif";
    public static final String SERIF = "Serif";
    public static final String MONOSPACED = "Monospaced";

    private final String name;
    private final int style;
    private final int size;
    /** TextAttribute.UNDERLINE value, -1 when off. Decoration only: drawn by
     *  BufferedImageGraphics2D.drawString, never affects metrics. */
    private final int underline;
    private final boolean strikethrough;

    public Font(String name, int style, int size) {
        this(name, style, size, -1, false);
    }

    private Font(String name, int style, int size, int underline, boolean strikethrough) {
        this.name = name != null ? name : "Default";
        this.style = style;
        this.size = size;
        this.underline = underline;
        this.strikethrough = strikethrough;
    }

    public Font(java.util.Map<? extends java.text.AttributedCharacterIterator.Attribute, ?> attributes) {
        this(new Font(DIALOG, PLAIN, 12).deriveFont(attributes));
    }

    protected Font(Font font) {
        this(font.name, font.style, font.size, font.underline, font.strikethrough);
    }

    public String getName()   { return name; }
    public String getFamily() { return name; }
    public int getStyle()     { return style; }
    public int getSize()      { return size; }
    public float getSize2D()  { return size; }

    /** Non-JDK accessor: whether TextAttribute.UNDERLINE is on. */
    public boolean isUnderlined() { return underline >= 0; }
    /** Non-JDK accessor: whether TextAttribute.STRIKETHROUGH is on. */
    public boolean isStrikethrough() { return strikethrough; }

    /** First unsupported UTF-16 index, or -1 when the rendering font supports all text. */
    public int canDisplayUpTo(String text) {
        return net.runelite.awt.impl.BufferedImageFontMetrics.canDisplayUpTo(this, text);
    }

    public boolean isPlain()  { return getStyle() == PLAIN; }
    public boolean isBold()   { return (getStyle() & BOLD) != 0; }
    public boolean isItalic() { return (getStyle() & ITALIC) != 0; }

    public static final int TRUETYPE_FONT = 0;
    public static final int TYPE1_FONT = 1;

    /**
     * Reads a TrueType blob, registers it in {@code AwtFontRegistry} keyed by the
     * font's actual family name (parsed from the TTF 'name' table), and returns a
     * Font whose {@code getFamily()} matches. Subsequent {@code new Font(family,
     * style, size)} calls re-use the registered Typeface.
     *
     * Used by RuneLite's FontManager static initializer to load runescape.ttf,
     * runescape_bold.ttf, runescape_small.ttf and any plugin-side custom fonts.
     */
    public static Font getFont(String property) { return getFont(property,null); }
    public static Font getFont(String property,Font fallback) {
        String value=System.getProperty(property);
        return value==null?fallback:decode(value);
    }
    public static Font decode(String value) {
        if(value==null)return new Font(DIALOG,PLAIN,12);
        String[] parts=value.split("[- ]");int size=12,style=PLAIN,end=parts.length;
        try { size=Integer.parseInt(parts[end-1]);end--; } catch(NumberFormatException ignored) {}
        if(end>1){String s=parts[end-1];if(s.equalsIgnoreCase("BOLD")){style=BOLD;end--;}else if(s.equalsIgnoreCase("ITALIC")){style=ITALIC;end--;}else if(s.equalsIgnoreCase("BOLDITALIC")){style=BOLD|ITALIC;end--;}else if(s.equalsIgnoreCase("PLAIN"))end--;}
        String name=String.join(" ",java.util.Arrays.copyOf(parts,Math.max(1,end)));
        return new Font(name,style,size>0?size:12);
    }
    public static Font createFont(int fontFormat, java.io.InputStream fontStream) throws java.io.IOException, FontFormatException {
        String family = net.runelite.awt.impl.AwtFontRegistry.register(fontStream);
        if (family == null) family = Font.DIALOG;
        return new Font(family, Font.PLAIN, 12);
    }

    public static Font createFont(int fontFormat, java.io.File fontFile) throws java.io.IOException, FontFormatException {
        try (java.io.InputStream in = new java.io.FileInputStream(fontFile)) {
            return createFont(fontFormat, in);
        }
    }

    // derive* keep the decoration attributes, as the JDK's do.
    public Font deriveFont(int style) {
        return new Font(name, style, size, underline, strikethrough);
    }

    public Font deriveFont(float size) {
        return new Font(name, style, (int) size, underline, strikethrough);
    }

    public Font deriveFont(int style, float size) {
        return new Font(name, style, (int) size, underline, strikethrough);
    }

    /**
     * The font's attribute map. quest-helper's Fonts.java seeds a HashMap with this, adds
     * UNDERLINE and calls deriveFont(Map) for its hover links, so the decoration keys
     * round-trip.
     */
    public java.util.Map<TextAttribute, ?> getAttributes() {
        java.util.Map<TextAttribute, Object> m = new java.util.HashMap<>();
        m.put(TextAttribute.FAMILY, name);
        m.put(TextAttribute.SIZE, (float) size);
        m.put(TextAttribute.WEIGHT, isBold() ? TextAttribute.WEIGHT_BOLD : TextAttribute.WEIGHT_REGULAR);
        m.put(TextAttribute.POSTURE, isItalic() ? TextAttribute.POSTURE_OBLIQUE : TextAttribute.POSTURE_REGULAR);
        m.put(TextAttribute.UNDERLINE, underline >= 0 ? (Object) underline : null);
        m.put(TextAttribute.STRIKETHROUGH, strikethrough ? TextAttribute.STRIKETHROUGH_ON : null);
        return m;
    }

    /**
     * Map-based deriveFont companion to getAttributes(). Honors family, size, a coarse
     * bold/italic style from WEIGHT/POSTURE, and UNDERLINE / STRIKETHROUGH. A key mapped
     * to null (or UNDERLINE -1) turns that attribute off; an absent key keeps ours.
     */
    public Font deriveFont(java.util.Map<? extends java.text.AttributedCharacterIterator.Attribute, ?> attrs) {
        if (attrs == null || attrs.isEmpty()) return this;
        String newName = name;
        int newStyle = style;
        int newSize = size;
        int newUnderline = underline;
        boolean newStrike = strikethrough;
        Object fam = attrs.get(TextAttribute.FAMILY);
        if (fam instanceof String) newName = (String) fam;
        Object sz = attrs.get(TextAttribute.SIZE);
        if (sz instanceof Number) newSize = ((Number) sz).intValue();
        Object weight = attrs.get(TextAttribute.WEIGHT);
        if (weight instanceof Number) {
            if (((Number) weight).floatValue() >= TextAttribute.WEIGHT_BOLD) newStyle |= BOLD;
            else newStyle &= ~BOLD;
        }
        Object posture = attrs.get(TextAttribute.POSTURE);
        if (posture instanceof Number) {
            if (((Number) posture).floatValue() > 0.0f) newStyle |= ITALIC;
            else newStyle &= ~ITALIC;
        }
        if (attrs.containsKey(TextAttribute.UNDERLINE)) {
            Object u = attrs.get(TextAttribute.UNDERLINE);
            newUnderline = u instanceof Number ? Math.max(-1, ((Number) u).intValue()) : -1;
        }
        if (attrs.containsKey(TextAttribute.STRIKETHROUGH)) {
            newStrike = Boolean.TRUE.equals(attrs.get(TextAttribute.STRIKETHROUGH));
        }
        return new Font(newName, newStyle, newSize, newUnderline, newStrike);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Font) {
            Font f = (Font) obj;
            return size == f.size && style == f.style && name.equals(f.name)
                && underline == f.underline && strikethrough == f.strikethrough;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode() ^ style ^ size ^ ((underline + 1) << 16) ^ (strikethrough ? 0x40000000 : 0);
    }

    @Override
    public String toString() {
        return getClass().getName() + "[family=" + name + ",style=" + style + ",size=" + size + "]";
    }
}
