package net.runelite.client.plugins.mobile;

import com.google.inject.Injector;
import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.config.MobileSettingsEntry;

/**
 * Home for mobile-only client settings. Hidden so it has no on/off toggle; instead it adds
 * a pinned, config-only "Mobile" row to the plugin list (see {@link MobileSettingsEntry}).
 * The sidebar host reads {@link MobileConfig#SIDEBAR_SCALE} straight from the
 * ConfigManager each repaint, so the setting applies live.
 */
@PluginDescriptor(
	name = "Mobile",
	description = "Mobile client settings",
	hidden = true
)
public class MobilePlugin extends Plugin
{
	@Inject private Injector injector;
	@Inject private ConfigManager configManager;
	@Inject private MobileConfig config;

	@Override
	protected void startUp()
	{
		MobileSettingsEntry.register(injector, configManager, config);
	}

	@Provides
	MobileConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MobileConfig.class);
	}
}
