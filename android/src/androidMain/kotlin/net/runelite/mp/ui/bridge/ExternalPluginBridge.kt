package net.runelite.mp.ui.bridge

import net.runelite.client.externalplugins.ExternalPluginClient
import net.runelite.client.externalplugins.ExternalPluginManager
import net.runelite.client.externalplugins.PluginHubManifest
import net.runelite.mp.plugins.PluginInstallTracker

/**
 * Exposes RuneLite's external plugin hub state to the Compose UI. The mp install talks
 * to the same hub the desktop client does (URL is set in {@link
 * net.runelite.mp.RuneLiteLauncher} via `runelite.pluginhub.url`), so the available list
 * matches whatever's published there.
 *
 * Three surfaces matter:
 *  - {@link #installed()} — which externals the user has opted into. Cheap; reads the
 *    configManager key {@code runelite.externalPlugins}.
 *  - {@link #available()} — the full manifest + install counts. Expensive (HTTP + signature
 *    verify), so the call is one-shot and cached by the caller in a Compose state.
 *  - {@link #downloadIcon} — fetches a single plugin's PNG icon bytes from the hub mirror.
 *    Per-row; cached by the caller.
 *
 * Install / remove just update the configManager list; ExternalPluginManager listens for
 * the resulting {@link net.runelite.client.events.ExternalPluginsChanged} and does the
 * actual jar download / classloader setup. UI doesn't need to wait — the row's
 * "installed" state flips on the next [installed] poll.
 */
internal data class ExternalPluginEntry(
    val internalName: String,
    val displayName: String,
    val author: String,
    val description: String,
    val version: String,
    val warning: String?,
    val unavailableReason: String?,
    val installed: Boolean,
    val iconHash: String?,
    val userCount: Int,
)
{
    val rowKey: String get() = "ext/$internalName"
    val available: Boolean get() = unavailableReason == null
}

internal object ExternalPluginBridge
{
    /** Hub plugins that convert and load but can't work on Android: desktop OpenGL renderers
     *  (the client already renders through GLES) and microphone/process access. The jar scan
     *  in [net.runelite.mp.plugins.PluginDexer] catches other LWJGL/JOGL users at install. */
    private val ANDROID_UNAVAILABLE = mapOf(
        "117hd" to "Not available on Android — use the built-in 117 HD renderer",
        "gpu-experimental" to "Not available on Android (desktop OpenGL renderer)",
        "voicescape-communication" to "Not available on Android (needs desktop audio capture)",
        "proximity-chat" to "Not available on Android (needs desktop audio capture)",
        "quest-voiceover" to "Not available on Android (needs desktop audio capture)",
        "process-monitor" to "Not available on Android (needs desktop process access)",
    )

    fun installed(): Set<String>
    {
        val mgr = RuneLiteAccess.instance(ExternalPluginManager::class.java) ?: return emptySet()
        return try { mgr.installedExternalPlugins.toSet() } catch (t: Throwable) { emptySet() }
    }

    /** Downloads the FULL manifest (display metadata for every published plugin) + install
     *  counts from api.runelite.net. Blocks — caller should run on a background coroutine.
     *  Returns an empty list on any failure so the UI can show "no plugins available"
     *  rather than crash. Install counts that don't resolve (eg. plugin internal name not
     *  known to the upstream API) default to -1, which sorts below all real counts. */
    fun available(): List<ExternalPluginEntry>
    {
        val client = RuneLiteAccess.instance(ExternalPluginClient::class.java) ?: return emptyList()
        val installed = installed()
        return try
        {
            val full: PluginHubManifest.ManifestFull = client.downloadManifestFull()
            val counts: Map<String, Int> = try { client.pluginCounts } catch (t: Throwable) { emptyMap() }
            full.display.orEmpty().map { d ->
                ExternalPluginEntry(
                    internalName = d.internalName,
                    displayName = d.displayName ?: d.internalName,
                    author = d.author ?: "",
                    description = d.description ?: "",
                    version = d.version ?: "",
                    warning = d.warning,
                    unavailableReason = d.unavailableReason ?: ANDROID_UNAVAILABLE[d.internalName],
                    installed = installed.contains(d.internalName),
                    iconHash = d.iconHash,
                    userCount = counts[d.internalName] ?: -1,
                )
            }
        }
        catch (t: Throwable) { emptyList() }
    }

    fun install(internalName: String, displayName: String = internalName)
    {
        val mgr = RuneLiteAccess.instance(ExternalPluginManager::class.java) ?: return
        PluginInstallTracker.queued(internalName, displayName)
        try
        {
            // install() is a no-op for a name already on the list (one that failed earlier),
            // which left its row "waiting" forever; refresh instead so it's actually retried.
            if (internalName in mgr.installedExternalPlugins) mgr.update() else mgr.install(internalName)
        }
        catch (t: Throwable) { /* host logs */ }
    }

    fun remove(internalName: String)
    {
        val mgr = RuneLiteAccess.instance(ExternalPluginManager::class.java) ?: return
        PluginInstallTracker.dismiss(internalName)
        try { mgr.remove(internalName) } catch (t: Throwable) { /* host logs */ }
    }

    /** Re-run the manager's refresh, which retries every installed plugin that isn't loaded
     *  (re-downloading jars that failed their hash). [internalName] just marks which row the
     *  user asked about, so its outcome gets a toast. */
    fun retry(internalName: String, displayName: String)
    {
        val mgr = RuneLiteAccess.instance(ExternalPluginManager::class.java) ?: return
        PluginInstallTracker.queued(internalName, displayName)
        PluginInstallTracker.clearHubErrors()
        try { mgr.update() } catch (t: Throwable) { /* host logs */ }
    }

    /** Fetch icon PNG bytes via the same OkHttpClient RuneLite uses, hitting the hub
     *  mirror path `<base>/icon/<internalName>_<iconHash>.png`. Blocks; returns null on
     *  any failure so the row can fall back to its placeholder. */
    fun downloadIcon(internalName: String, iconHash: String): ByteArray?
    {
        val hubBase = System.getProperty("runelite.pluginhub.url") ?: return null
        val client = RuneLiteAccess.instance(okhttp3.OkHttpClient::class.java) ?: return null
        val url = "$hubBase/icon/${internalName}_${iconHash}.png"
        return try
        {
            val req = okhttp3.Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body()?.bytes()
            }
        }
        catch (t: Throwable) { null }
    }

}
