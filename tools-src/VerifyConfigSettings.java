package net.runelite.client.config;

import java.awt.Color;
import java.awt.Dimension;
import java.util.*;
import java.util.concurrent.*;
import com.google.inject.Guice;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.runelite.client.RuneLite;
import net.runelite.http.api.RuneLiteAPI;

public class VerifyConfigSettings {
    enum Pick { FIRST, SECOND }
    public interface Defaults extends Config { default double width() { return 2.5; } }
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) throws Throwable {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            var ctor = ConfigManager.class.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            var cm = (ConfigManager) ctor.newInstance(null, executor, null, null, RuneLiteAPI.GSON, null, null, null);
            RuneLite.setInjector(Guice.createInjector(b -> {
                b.bind(ConfigManager.class).toInstance(cm);
                b.bind(Gson.class).toInstance(RuneLiteAPI.GSON);
            }));
            check(ConfigUiCodec.decode("2.5", double.class).equals(2.5));
            check(ConfigUiCodec.encode(new Dimension(765, 503)).equals("765x503"));
            check(ConfigUiCodec.decode("765x503", Dimension.class).equals(new Dimension(765, 503)));
            for (var picks : List.of(EnumSet.noneOf(Pick.class), EnumSet.allOf(Pick.class))) {
                check(ConfigUiCodec.decode(ConfigUiCodec.encode(picks), new TypeToken<Set<Pick>>(){}.getType()).equals(picks));
            }
            var n = new Notification().withEnabled(true).withInitialized(true).withOverride(true)
                .withVolume(42).withTimeout(1500).withSound(NotificationSound.CUSTOM).withSoundName("custom.wav")
                .withFlashColor(new Color(12, 34, 56, 78)).withGameMessage(true);
            var decoded = (Notification) ConfigUiCodec.decode(ConfigUiCodec.encode(n), Notification.class);
            check(decoded.getFlashColor().equals(n.getFlashColor()) && decoded.getVolume() == 42 && decoded.getTimeout() == 1500);
            check(decoded.getSoundName().equals("custom.wav") && decoded.isOverride() && decoded.isGameMessage());
            check(((Notification)ConfigUiCodec.decode("true", Notification.class)).isEnabled());
            var font = new FontType().withFamily("Dialog").withSize(18).withBold(true).withItalic(true);
            var decodedFont = (FontType)ConfigUiCodec.decode(ConfigUiCodec.encode(font), FontType.class);
            check(decodedFont.getSize() == 18 && decodedFont.isBold() && decodedFont.isItalic() && decodedFont.getFamily().equals("Dialog"));
            check(ConfigUiCodec.decode("BOLD", FontType.class) == FontType.BOLD);
            Defaults proxy = (Defaults) java.lang.reflect.Proxy.newProxyInstance(Defaults.class.getClassLoader(), new Class[]{Defaults.class}, (p,m,a) -> 99.0);
            check(ConfigUiCodec.defaultValue(proxy, Defaults.class.getMethod("width")).equals(2.5));
            System.out.println("PASS: client codecs, empty/full sets, notification overrides and alpha, fonts, legacy values, real default vs override");
        } finally { executor.shutdownNow(); }
    }
}
