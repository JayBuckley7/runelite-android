package javax.swing.text;

import javax.swing.JComponent;
import javax.swing.event.CaretListener;

public abstract class JTextComponent extends JComponent {
    private static final long serialVersionUID = -2911697971489974478L;

    public static final String DEFAULT_KEYMAP = "default";
    public static final String FOCUS_ACCELERATOR_KEY = "focusAcceleratorKey";

    protected Document doc;
    protected String text = "";

    public JTextComponent() {
        this.doc = new PlainDocument();
    }

    public String getText() {
        if (doc != null) {
            try { return doc.getText(0, doc.getLength()); } catch (BadLocationException ignored) {}
        }
        return text;
    }
    public void setText(String t) {
        // Text is an input to every text component's preferred size.
        invalidate();
        this.text = t == null ? "" : t;
        if (doc != null) {
            try {
                doc.remove(0, doc.getLength());
                doc.insertString(0, this.text, null);
            } catch (BadLocationException ignored) {}
        }
    }

    public Document getDocument() { return doc; }
    public void setDocument(Document d) { this.doc = d; }

    /** Shared plain-text measurement for multiline Swing controls. */
    protected java.util.List<String> textLines(int width, boolean wrap, boolean words, int tabs) {
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        java.util.List<String> lines = new java.util.ArrayList<>();
        String value = getText().replace("\r\n", "\n").replace('\r', '\n');
        for (String paragraph : value.split("\n", -1)) {
            StringBuilder expanded = new StringBuilder();
            for (int i = 0; i < paragraph.length(); i++) {
                char ch = paragraph.charAt(i);
                if (ch == '\t') { int count = Math.max(1, tabs) - expanded.length() % Math.max(1, tabs); while (count-- > 0) expanded.append(' '); }
                else expanded.append(ch);
            }
            String remaining = expanded.toString();
            if (!wrap || width <= 0 || remaining.isEmpty()) { lines.add(remaining); continue; }
            while (!remaining.isEmpty()) {
                int end = 0, lastSpace = -1;
                while (end < remaining.length()) {
                    int next = end + Character.charCount(remaining.codePointAt(end));
                    if (end > 0 && fm.stringWidth(remaining.substring(0, next)) > width) break;
                    if (Character.isWhitespace(remaining.charAt(end))) lastSpace = next;
                    end = next;
                }
                if (words && end < remaining.length() && lastSpace > 0) end = lastSpace;
                lines.add(remaining.substring(0, end));
                remaining = remaining.substring(end);
            }
        }
        return lines;
    }
    protected int textWidth() {
        java.awt.Insets in = getInsets();
        return Math.max(0, getWidth() - in.left - in.right);
    }
    protected java.awt.Dimension plainPreferredSize(boolean wrap, boolean words, int tabs) {
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        java.awt.Insets in = getInsets();
        java.util.List<String> lines = textLines(textWidth(), wrap, words, tabs);
        int width = 0;
        for (String line : lines) width = Math.max(width, fm.stringWidth(line));
        return new java.awt.Dimension(width + in.left + in.right, lines.size() * fm.getHeight() + in.top + in.bottom);
    }
    protected void paintPlainText(java.awt.Graphics g, boolean wrap, boolean words, int tabs) {
        java.awt.Graphics copy = g.create();
        try {
            java.awt.Insets in = getInsets();
            copy.clipRect(in.left, in.top, textWidth(), Math.max(0, getHeight() - in.top - in.bottom));
            copy.setFont(getFont()); copy.setColor(getForeground());
            java.awt.FontMetrics fm = getFontMetrics(getFont());
            int y = in.top + fm.getAscent();
            for (String line : textLines(textWidth(), wrap, words, tabs)) {
                copy.drawString(line, in.left, y); y += fm.getHeight();
            }
        } finally { copy.dispose(); }
    }

    private boolean editable = true;
    public boolean isEditable() { return editable; }
    public void setEditable(boolean b) { editable = b; }

    public void selectAll() {}
    public String getSelectedText() { return null; }
    public int getSelectionStart() { return 0; }
    public int getSelectionEnd() { return 0; }

    public void setCaretPosition(int position) {}
    public int getCaretPosition() { return 0; }

    public void addCaretListener(CaretListener l) {}
    public void removeCaretListener(CaretListener l) {}

    public Caret getCaret() { return null; }
    public Highlighter getHighlighter() { return null; }
    public void setHighlighter(Highlighter h) {}
    public void setCaret(Caret c) {}

    public void setSelectedTextColor(java.awt.Color c) {}
    public java.awt.Color getSelectedTextColor() { return java.awt.Color.WHITE; }
    public void setSelectionColor(java.awt.Color c) {}
    public java.awt.Color getSelectionColor() { return java.awt.Color.BLUE; }
    public void setDisabledTextColor(java.awt.Color c) {}
    public java.awt.Color getDisabledTextColor() { return java.awt.Color.GRAY; }
    public void setCaretColor(java.awt.Color c) {}
    public java.awt.Color getCaretColor() { return java.awt.Color.BLACK; }

    public void copy() {}
    public void cut() {}
    public void paste() {}
}
