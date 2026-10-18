package net.runelite.client.plugins.mobile;

import java.util.function.Consumer;

/** Small bridge between RuneLite configuration and the Android activity lifecycle. */
public final class MobileDisplaySettings
{
	private static volatile Consumer<Boolean> listener;
	private static volatile Boolean portrait;
	private MobileDisplaySettings() {}
	public static void attach(Consumer<Boolean> next)
	{
		listener = next;
		Boolean saved = portrait;
		if (saved != null) next.accept(saved);
	}
	public static void detach() { listener = null; }
	public static void setPortrait(boolean value)
	{
		portrait = value;
		Consumer<Boolean> current = listener;
		if (current != null) current.accept(value);
	}
}
