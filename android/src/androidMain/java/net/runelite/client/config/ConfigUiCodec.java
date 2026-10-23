package net.runelite.client.config;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import net.runelite.client.RuneLite;

/** Share the client's codecs rather than inventing a second settings format. */
public final class ConfigUiCodec
{
    private static ConfigManager manager() { return RuneLite.getInjector().getInstance(ConfigManager.class); }
    public static String encode(Object value) { return value == null ? null : manager().objectToString(value); }
    public static Object decode(String value, Type type) { return manager().stringToObject(value, type); }
    public static Object defaultValue(Object proxy, Method method) throws Throwable
    {
        return ConfigInvocationHandler.callDefaultMethod(proxy, method, null);
    }
    public static Notification notificationDefaults()
    {
        RuneLiteConfig c = manager().getConfig(RuneLiteConfig.class);
        return new Notification(true, true, true, c.enableTrayNotifications(), java.awt.TrayIcon.MessageType.NONE,
            c.notificationRequestFocus(), c.notificationSound(), null, c.notificationVolume(), c.notificationTimeout(),
            c.enableGameMessageNotification(), c.flashNotification(), c.notificationFlashColor(), c.sendNotificationsWhenFocused());
    }
}
