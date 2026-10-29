package java.awt;

import java.awt.geom.Rectangle2D;
import java.io.Serializable;

/**
 * Stub FontMetrics — measurements are estimated from font size assuming a fixed-width
 * 0.6em glyph. Wire to Android Paint.measureText for accuracy once a Typeface is
 * available on the rendering path.
 */
public abstract class FontMetrics implements Serializable {
    private static final long serialVersionUID = 1681126225205050147L;

    protected Font font;

    protected FontMetrics(Font font) {
        this.font = font;
    }

    public Font getFont() { return font; }
    public java.awt.font.FontRenderContext getFontRenderContext() { return new java.awt.font.FontRenderContext(null,false,false); }

    public int getLeading()   { return Math.max(1, font.getSize() / 8); }
    public int getAscent()    { return (int) Math.ceil(font.getSize() * 0.8); }
    public int getDescent()   { return (int) Math.ceil(font.getSize() * 0.2); }
    public int getHeight()    { return getLeading() + getAscent() + getDescent(); }
    public int getMaxAscent() { return getAscent(); }
    public int getMaxDescent() { return getDescent(); }
    public int getMaxAdvance() { return charWidth('W'); }

    public int charWidth(int codePoint) {
        return (int) Math.ceil(font.getSize() * 0.6);
    }

    public int charWidth(char ch) {
        return charWidth((int) ch);
    }

    public int stringWidth(String str) {
        if (str == null || str.isEmpty()) return 0;
        return (int) Math.ceil(str.length() * font.getSize() * 0.6);
    }

    public int charsWidth(char[] data, int off, int len) {
        return (int) Math.ceil(len * font.getSize() * 0.6);
    }

    public int bytesWidth(byte[] data, int off, int len) {
        return (int) Math.ceil(len * font.getSize() * 0.6);
    }

    public int[] getWidths() {
        int[] widths = new int[256];
        int w = (int) Math.ceil(font.getSize() * 0.6);
        for (int i = 0; i < widths.length; i++) widths[i] = w;
        return widths;
    }

    public Rectangle2D getStringBounds(String str, Graphics context) {
        return new Rectangle2D.Float(0f, -getAscent(), stringWidth(str), getHeight());
    }
}
