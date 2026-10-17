package net.runelite.client.plugins.mobile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("mobileinput")
public interface MobileInputConfig extends Config
{
	@ConfigItem(
		keyName = "keepMenuOpen",
		name = "Keep menu open after long press",
		description = "Lift your finger to read the menu, then tap an option. Disable to slide onto an option and release to select.",
		position = 0
	)
	default boolean keepMenuOpen()
	{
		return false;
	}
}
