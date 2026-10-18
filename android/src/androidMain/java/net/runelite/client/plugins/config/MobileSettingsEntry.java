package net.runelite.client.plugins.config;

import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.mobile.MobileConfig;
import net.runelite.client.util.Text;

/**
 * Lists Mobile settings in the plugin list the same way ConfigPlugin lists "RuneLite" and
 * "Chat Color": a config-only row with no on/off toggle. Lives in this package because
 * {@link PluginListPanel#addFakePlugin} and the descriptor are package-private.
 */
public final class MobileSettingsEntry
{
	private static final String NAME = "Mobile";
	/** Set once the row has been starred, so a user who un-stars it isn't overridden. */
	private static final String PIN_SEEDED_KEY = "pinSeeded";

	/** PluginListPanel is an injector singleton; remember which one already has the row
	 *  so a plugin restart (eg. profile switch) doesn't add it twice. */
	private static Object registeredOn;

	private MobileSettingsEntry()
	{
	}

	public static void register(Injector injector, ConfigManager configManager, MobileConfig config)
	{
		SwingUtilities.invokeLater(() ->
		{
			PluginListPanel panel = injector.getInstance(PluginListPanel.class);
			if (registeredOn == panel)
			{
				return;
			}
			registeredOn = panel;

			seedPin(configManager);
			panel.addFakePlugin(new PluginConfigurationDescriptor(
				NAME, "Mobile client settings",
				new String[]{"sidebar", "scale", "size", "panel", "interface"},
				config, configManager.getConfigDescriptor(config)));
			panel.rebuildPluginList();
		});
	}

	private static void seedPin(ConfigManager configManager)
	{
		if (configManager.getConfiguration(MobileConfig.GROUP, PIN_SEEDED_KEY) != null)
		{
			return;
		}
		String current = configManager.getConfiguration("runelite", "pinnedPlugins");
		List<String> pinned = current == null ? new ArrayList<>() : new ArrayList<>(Text.fromCSV(current));
		if (!pinned.contains(NAME))
		{
			pinned.add(NAME);
			configManager.setConfiguration("runelite", "pinnedPlugins", String.join(",", pinned));
		}
		configManager.setConfiguration(MobileConfig.GROUP, PIN_SEEDED_KEY, "true");
	}
}
