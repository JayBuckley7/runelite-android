package net.runelite.mp.hd

import android.util.Log
import java.awt.Canvas
import javax.swing.SwingUtilities
import net.runelite.client.config.ConfigManager
import net.runelite.client.plugins.PluginManager
import net.runelite.client.plugins.gpugles.GlesHost
import net.runelite.client.plugins.gpugles.GpuGlesPlugin
import rs117.hd.AndroidSupport
import rs117.hd.HdPlugin
import rs117.hd.HdPluginConfig

object AndroidHdHost : AndroidSupport.Host {
    private lateinit var manager: PluginManager
    fun configure(pm: PluginManager, config: ConfigManager) {
        manager = pm
        AndroidSupport.host = this
        // 117 HD logs a lot at INFO/WARN during normal play (startup system info, swap-interval
        // notices, per-zone upload warnings). Keep only errors on Android.
        (org.slf4j.LoggerFactory.getLogger("rs117") as? ch.qos.logback.classic.Logger)?.level =
            ch.qos.logback.classic.Level.ERROR
        val hd = pm.plugins.firstOrNull { it is HdPlugin }
        if (hd == null) {
            Log.e("AndroidHdHost", "117 HD was not registered; keeping the existing renderer")
            return
        }
        // Once-only mobile defaults. Further launches retain the user's settings.
        if (config.getConfiguration("runelite-mp", "hdPrototypeDefaults") == null) {
            val group = HdPluginConfig.CONFIG_GROUP
            mapOf("drawDistance" to "35", "shadowMode" to "DETAILED", "shadowResolution" to "RES_2048",
                "dynamicLights" to "NONE", "tiledLighting" to "false", "sceneResolutionScale" to "75",
                "legacyRenderer2" to "false", "anisotropicFilteringLevel" to "2").forEach { (key, value) -> config.setConfiguration(group, key, value) }
            config.setConfiguration("runelite-mp", "hdPrototypeDefaults", "1")
        }
        if (config.getConfiguration("runelite-mp", "hdPrototypeDefaults") == "1") {
            mapOf("antiAliasingMode" to "MSAA_2", "lowMemoryMode" to "true",
                "modelCacheSizeMiBv2" to "128", "expandedMapLoadingChunks" to "0")
                .forEach { (key, value) -> config.setConfiguration(HdPluginConfig.CONFIG_GROUP, key, value) }
            config.setConfiguration("runelite-mp", "hdPrototypeDefaults", "2")
        }
        // Tiled lighting was off because its shader didn't compile on GLES (117 HD also
        // saves it off after a failed compile). It compiles now; without it every scene
        // fragment loops over every visible point light. 117 HD still turns it off by
        // itself if the compile ever fails.
        if (config.getConfiguration("runelite-mp", "hdPrototypeDefaults") == "2") {
            config.setConfiguration(HdPluginConfig.CONFIG_GROUP, "tiledLighting", "true")
            config.setConfiguration("runelite-mp", "hdPrototypeDefaults", "3")
        }
        migrateConfiguration(config)
    }
    fun migrateConfiguration(config: ConfigManager) {
        // 1.6 changes these persisted booleans to DEFAULT/ON/OFF. Preserve explicit
        // choices, including OFF, instead of letting enum parsing fall back to defaults.
        for (key in listOf(HdPluginConfig.KEY_TILED_LIGHTING, HdPluginConfig.KEY_IMAGE_LOAD_STORE)) {
            val migrated = when (config.getConfiguration(HdPluginConfig.CONFIG_GROUP, key)) {
                "true" -> "ON"
                "false" -> "OFF"
                else -> null
            }
            if (migrated != null) config.setConfiguration(HdPluginConfig.CONFIG_GROUP, key, migrated)
        }
    }
    override fun makeCurrent(): Boolean {
        Canvas.setRenderedByGles(true)
        // Surfaceless while backgrounded, so a frame that mapped buffers can still unmap them.
        return GlesHost.get().makeCurrentAllowSurfaceless()
    }
    override fun width() = GlesHost.get().width
    override fun height() = GlesHost.get().height
    override fun contextGeneration() = GlesHost.get().contextGeneration()
    override fun stopped() { Canvas.setRenderedByGles(false) }
    override fun createTileResolvedFbo(width: Int, height: Int, samples: Int, colorFormat: Int, depthFormat: Int): IntArray? {
        val ids = IntArray(4)
        return if (GlesExt.createTileResolvedFbo(width, height, samples, colorFormat, depthFormat, ids)) ids else null
    }
    override fun failed() {
        Log.e("AndroidHdHost", "117 HD stopped after a renderer error; restoring GPU (GLES)")
        SwingUtilities.invokeLater {
            manager.plugins.firstOrNull { it is GpuGlesPlugin }?.let {
                manager.setPluginEnabled(it, true)
                manager.startPlugin(it)
            }
        }
    }
}
