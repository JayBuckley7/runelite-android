package net.runelite.mp.compat.regex;

/** Array growth used by the imported engine without JDK-internal dependencies. */
final class RegexPlatform {
    static int newLength(int oldLength, int minGrowth, int preferredGrowth) {
        long minimum = (long) oldLength + minGrowth;
        if (minimum > Integer.MAX_VALUE) throw new OutOfMemoryError("Regex buffer is too large");
        long preferred = (long) oldLength + Math.max(minGrowth, preferredGrowth);
        return (int) Math.max(minimum, Math.min(preferred, Integer.MAX_VALUE - 8L));
    }
}
