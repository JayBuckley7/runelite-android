package net.runelite.client.plugins.mobile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(MobileInputConfig.GROUP)
public interface MobileInputConfig extends Config
{
    String GROUP = "mobileInput";

    @ConfigItem(keyName = "passwordProtectedInput", name = "Password-protected input",
        description = "Disable keyboard suggestions, swipe typing and voice typing. Always enabled outside the game.")
    default boolean passwordProtectedInput() { return false; }
}
