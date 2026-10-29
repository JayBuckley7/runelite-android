package java.awt;

/** Stable desktop-compatible system palette for Android's peerless AWT environment. */
public final class SystemColor extends Color {
    private static final long serialVersionUID = 4503142729533789064L;
    public static final int DESKTOP = 0;
    public static final int ACTIVE_CAPTION = 1;
    public static final int ACTIVE_CAPTION_TEXT = 2;
    public static final int ACTIVE_CAPTION_BORDER = 3;
    public static final int INACTIVE_CAPTION = 4;
    public static final int INACTIVE_CAPTION_TEXT = 5;
    public static final int INACTIVE_CAPTION_BORDER = 6;
    public static final int WINDOW = 7;
    public static final int WINDOW_BORDER = 8;
    public static final int WINDOW_TEXT = 9;
    public static final int MENU = 10;
    public static final int MENU_TEXT = 11;
    public static final int TEXT = 12;
    public static final int TEXT_TEXT = 13;
    public static final int TEXT_HIGHLIGHT = 14;
    public static final int TEXT_HIGHLIGHT_TEXT = 15;
    public static final int TEXT_INACTIVE_TEXT = 16;
    public static final int CONTROL = 17;
    public static final int CONTROL_TEXT = 18;
    public static final int CONTROL_HIGHLIGHT = 19;
    public static final int CONTROL_LT_HIGHLIGHT = 20;
    public static final int CONTROL_SHADOW = 21;
    public static final int CONTROL_DK_SHADOW = 22;
    public static final int SCROLLBAR = 23;
    public static final int INFO = 24;
    public static final int INFO_TEXT = 25;
    public static final int NUM_COLORS = 26;
    public static final SystemColor desktop = new SystemColor(DESKTOP, 0x005c5c);
    public static final SystemColor activeCaption = new SystemColor(ACTIVE_CAPTION, 0x000080);
    public static final SystemColor activeCaptionText = new SystemColor(ACTIVE_CAPTION_TEXT, 0xffffff);
    public static final SystemColor activeCaptionBorder = new SystemColor(ACTIVE_CAPTION_BORDER, 0xc0c0c0);
    public static final SystemColor inactiveCaption = new SystemColor(INACTIVE_CAPTION, 0x808080);
    public static final SystemColor inactiveCaptionText = new SystemColor(INACTIVE_CAPTION_TEXT, 0xc0c0c0);
    public static final SystemColor inactiveCaptionBorder = new SystemColor(INACTIVE_CAPTION_BORDER, 0xc0c0c0);
    public static final SystemColor window = new SystemColor(WINDOW, 0xffffff);
    public static final SystemColor windowBorder = new SystemColor(WINDOW_BORDER, 0x000000);
    public static final SystemColor windowText = new SystemColor(WINDOW_TEXT, 0x000000);
    public static final SystemColor menu = new SystemColor(MENU, 0xc0c0c0);
    public static final SystemColor menuText = new SystemColor(MENU_TEXT, 0x000000);
    public static final SystemColor text = new SystemColor(TEXT, 0xffffff);
    public static final SystemColor textText = new SystemColor(TEXT_TEXT, 0x000000);
    public static final SystemColor textHighlight = new SystemColor(TEXT_HIGHLIGHT, 0x000080);
    public static final SystemColor textHighlightText = new SystemColor(TEXT_HIGHLIGHT_TEXT, 0xffffff);
    public static final SystemColor textInactiveText = new SystemColor(TEXT_INACTIVE_TEXT, 0x808080);
    public static final SystemColor control = new SystemColor(CONTROL, 0xc0c0c0);
    public static final SystemColor controlText = new SystemColor(CONTROL_TEXT, 0x000000);
    public static final SystemColor controlHighlight = new SystemColor(CONTROL_HIGHLIGHT, 0xc0c0c0);
    public static final SystemColor controlLtHighlight = new SystemColor(CONTROL_LT_HIGHLIGHT, 0xffffff);
    public static final SystemColor controlShadow = new SystemColor(CONTROL_SHADOW, 0x808080);
    public static final SystemColor controlDkShadow = new SystemColor(CONTROL_DK_SHADOW, 0x000000);
    public static final SystemColor scrollbar = new SystemColor(SCROLLBAR, 0xe0e0e0);
    public static final SystemColor info = new SystemColor(INFO, 0xffffe1);
    public static final SystemColor infoText = new SystemColor(INFO_TEXT, 0x000000);
    private final int index;
    private SystemColor(int index, int rgb) { super(rgb); this.index = index; }
    @Override public String toString() { return getClass().getName() + "[i=" + index + "]"; }
}
