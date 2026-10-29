package javax.swing;

import javax.swing.text.Document;

public class JPasswordField extends JTextField {
    private static final long serialVersionUID = 1L;
    private char echoChar = '*';

    public JPasswordField() {}
    public JPasswordField(String text) { super(text); }
    public JPasswordField(int columns) { super(columns); }
    public JPasswordField(String text, int columns) { super(text, columns); }
    public JPasswordField(Document doc, String text, int columns) { super(doc, text, columns); }

    @Override public void copy() { getToolkit().beep(); }
    @Override public void cut() { getToolkit().beep(); }

    public char getEchoChar() { return echoChar; }
    public void setEchoChar(char c) { echoChar = c; }
    public boolean echoCharIsSet() { return echoChar != 0; }
    public char[] getPassword() { return getText() == null ? new char[0] : getText().toCharArray(); }
}
