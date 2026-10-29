package java.awt;

import java.io.Serializable;

public class AWTKeyStroke implements Serializable {
    private static final long serialVersionUID = -6430539691155161871L;

    private char keyChar = (char) 0xFFFF;
    private int keyCode;
    private int modifiers;
    private boolean onKeyRelease;

    protected AWTKeyStroke() {}

    protected void init(char keyChar, int keyCode, int modifiers, boolean onKeyRelease) {
        this.keyChar = keyChar;
        this.keyCode = keyCode;
        this.modifiers = modifiers;
        this.onKeyRelease = onKeyRelease;
    }

    public char getKeyChar() { return keyChar; }
    public int getKeyCode() { return keyCode; }
    public int getModifiers() { return modifiers; }
    public boolean isOnKeyRelease() { return onKeyRelease; }
    public int getKeyEventType() { return keyChar != java.awt.event.KeyEvent.CHAR_UNDEFINED ? java.awt.event.KeyEvent.KEY_TYPED : onKeyRelease ? java.awt.event.KeyEvent.KEY_RELEASED : java.awt.event.KeyEvent.KEY_PRESSED; }

    @Override public int hashCode() { return (keyChar + keyCode + modifiers) * 31 + (onKeyRelease ? 1 : 0); }
    @Override public boolean equals(Object obj) {
        if (!(obj instanceof AWTKeyStroke)) return false;
        AWTKeyStroke o = (AWTKeyStroke) obj;
        return keyChar == o.keyChar && keyCode == o.keyCode && modifiers == o.modifiers && onKeyRelease == o.onKeyRelease;
    }
}
