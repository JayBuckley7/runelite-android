package net.runelite.client.plugins.mobile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(MobileConfig.GROUP)
public interface MobileConfig extends Config
{
	String GROUP = "mobile";
	String SIDEBAR_SCALE = "sidebarScale";

	@ConfigItem(keyName = "forceVertical", name = "Force vertical",
		description = "Keep the app in portrait orientation. Disable to return to landscape.", position = 2)
	default boolean forceVertical() { return false; }

	@Range(min = 100, max = 200)
	@Units(Units.PERCENT)
	@ConfigItem(keyName = "inventoryScale", name = "Inventory scale",
		description = "Enlarge only the open inventory panel in resizable mode with GPU or 117 HD. 100% restores normal size.", position = 3)
	default int inventoryScale() { return 100; }

	// No Lombok here: SwingPanelHost (Kotlin) reads getScale(), and Kotlin compiles
	// against these sources before Lombok has generated anything.
	enum SidebarScale
	{
		P100("100%", 1f),
		P90("90%", 0.9f),
		P80("80%", 0.8f),
		P70("70%", 0.7f),
		P60("60%", 0.6f);

		private final String label;
		private final float scale;

		SidebarScale(String label, float scale)
		{
			this.label = label;
			this.scale = scale;
		}

		public float getScale()
		{
			return scale;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = SIDEBAR_SCALE,
		name = "Sidebar scale",
		description = "Size of sidebar panels. Lower values draw panels smaller so more fits on screen.",
		position = 1
	)
	default SidebarScale sidebarScale()
	{
		return SidebarScale.P100;
	}
}
