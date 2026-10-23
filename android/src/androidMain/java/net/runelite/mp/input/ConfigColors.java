package net.runelite.mp.input;

import java.util.Locale;

/** UI hex is separate from RuneLite's signed decimal ARGB storage. */
public final class ConfigColors
{
	public static String hex(int argb, boolean alpha)
	{
		return alpha ? String.format(Locale.ROOT, "%08X", argb)
			: String.format(Locale.ROOT, "%06X", argb & 0xffffff);
	}

	public static Integer parseHex(String text, boolean alpha)
	{
		String hex = text.trim().replaceFirst("^#", "");
		if (!hex.matches(alpha ? "[0-9a-fA-F]{8}" : "[0-9a-fA-F]{6}")) return null;
		int value = (int) Long.parseLong(hex, 16);
		return alpha ? value : value | 0xff000000;
	}
}
