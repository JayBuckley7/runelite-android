package net.runelite.mp.plugins;

import java.util.Objects;
import net.runelite.mp.compat.regex.Pattern;

/** String regex entry points for external bytecode, using the same engine as Pattern/Matcher. */
public final class DesktopRegexStrings {
    private DesktopRegexStrings() {}
    public static String[] split(String text, String regex) { return split(text, regex, 0); }
    public static String[] split(String text, String regex, int limit) {
        Objects.requireNonNull(text);
        return Pattern.compile(regex).split(text, limit);
    }
    public static boolean matches(String text, String regex) {
        Objects.requireNonNull(text);
        return Pattern.matches(regex, text);
    }
    public static String[] splitWithDelimiters(String text, String regex, int limit) {
        Objects.requireNonNull(text);
        return Pattern.compile(regex).splitWithDelimiters(text, limit);
    }
    public static String replaceAll(String text, String regex, String replacement) {
        Objects.requireNonNull(text);
        return Pattern.compile(regex).matcher(text).replaceAll(replacement);
    }
    public static String replaceFirst(String text, String regex, String replacement) {
        Objects.requireNonNull(text);
        return Pattern.compile(regex).matcher(text).replaceFirst(replacement);
    }
}
