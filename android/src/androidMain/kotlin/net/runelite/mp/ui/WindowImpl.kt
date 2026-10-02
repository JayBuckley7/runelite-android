package net.runelite.mp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import net.runelite.mp.MainActivity
import net.runelite.mp.ui.bridge.PluginRow

/**
 * Three-zone chrome. The icon strip is pinned to the FAR right so the chosen panel's
 * content lives immediately to its left (closest to the game viewport) — the same
 * mental model as RuneLite desktop's east sidebar.
 *
 *   ┌─────────────────────────────┬───────────┬──┐
 *   │                             │           │I │
 *   │       game viewport         │  content  │c │
 *   │                             │   panel   │o │
 *   │                             │           │n │
 *   │                             │           │  │
 *   └─────────────────────────────┴───────────┴──┘
 *
 * No collapse — the strip is always visible. The Compose-native "Plugins" wrench is
 * the default selection; clicking any other plugin's nav calls into ClientUI so the
 * native AWT panel actually swaps tabs, and the Compose content area shows a hint
 * until that panel is ported to Compose.
 */
object WindowImpl
{
    private val ICON_WIDTH = 36.dp
    private val CONTENT_WIDTH = 256.dp

    /** First launch shows the game with NO panel open — user explicitly taps a nav
     *  icon to reveal one. Avoids slamming the plugin list on top of every fresh boot. */
    private val selectedKey = mutableStateOf<String?>(null)
    private val configTarget = mutableStateOf<PluginRow?>(null)

    /**
     * Programmatically switch to a Compose panel by [PanelRegistry] key (the same key
     * as the RL nav button's tooltip). Used by [net.runelite.mp.AppAndroidKt]'s startup
     * hook to forward `ClientUI.openPanel` calls — e.g. right-click "Lookup" on a
     * player in-game fires HiscorePlugin → ClientUI.openPanel → here → the Compose
     * HiScore panel takes the foreground. No-op if the key has no Compose replacement.
     */
    fun showPanel(key: String)
    {
        if (key != NAV_KEY_PLUGINS && !net.runelite.mp.ui.panels.PanelRegistry.hasPanel(key)) return
        selectedKey.value = key
        if (key != NAV_KEY_PLUGINS) configTarget.value = null
    }

    /**
     * Hardware/gesture back-button reducer. Always consumes the press once we're booted,
     * so an accidental swipe-back doesn't kill the activity (and the logged-in session
     * with it). Step order matches what the user would intuitively expect: drill OUT of
     * the open config first, then close the panel, then no-op.
     */
    fun handleBack()
    {
        when
        {
            configTarget.value != null -> configTarget.value = null
            selectedKey.value != null -> selectedKey.value = null
            else -> { /* swallow — refuse to exit the activity from in-game */ }
        }
    }

    /** Flipped to true by [net.runelite.mp.AppAndroidKt.ComposeSplash] once the AWT
     *  splash has come and gone. The chrome (icon strip + content panel) stays
     *  hidden until boot completes so users don't see a sidebar pinned beside the
     *  splash during the load. */
    val bootComplete = mutableStateOf(false)

    @Composable
    fun Window(gameContent: @Composable () -> Unit)
    {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Row(Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    gameContent()
                }
                // Chrome (content panel + icon strip) doesn't render at all until
                // the splash dismisses — otherwise the user sees a pinned sidebar
                // floating beside the boot splash during the long initial load.
                if (bootComplete.value)
                {
                    RuneLiteMenuTheme {
                        // Compose-native content column. Renders for our synthetic "Plugins"
                        // key AND for any nav button whose tooltip has a Compose replacement
                        // in [PanelRegistry] (XP Tracker, Notes, GE, etc). RL-registered
                        // buttons without a Compose override keep painting their panels
                        // inside the AWT bitmap on the left — no Compose column eats game
                        // space in that case.
                        val key = selectedKey.value
                        val renderCompose = key == NAV_KEY_PLUGINS ||
                            (key != null && net.runelite.mp.ui.panels.PanelRegistry.hasPanel(key))
                        if (renderCompose)
                        {
                            Box(Modifier.width(CONTENT_WIDTH).fillMaxHeight()) {
                                ContentPanel()
                            }
                        }
                        NavIconStrip(
                            width = ICON_WIDTH,
                            selected = selectedKey.value,
                            onSelect = { key ->
                                selectedKey.value = key
                                if (key != NAV_KEY_PLUGINS) configTarget.value = null
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ContentPanel()
    {
        val key = selectedKey.value
        when
        {
            key == NAV_KEY_PLUGINS ->
            {
                PluginSidebar(
                    width = CONTENT_WIDTH,
                    onConfigure = { configTarget.value = it },
                )
                val target = configTarget.value
                if (target != null)
                {
                    PluginConfigPanelFor(
                        row = target,
                        onBack = {
                            configTarget.value = null
                            MainActivity.hideSystemUI()
                                 },
                    )
                }
            }
            key != null && net.runelite.mp.ui.panels.PanelRegistry.hasPanel(key) ->
            {
                net.runelite.mp.ui.panels.PanelRegistry.render(key)
            }
        }
    }
}
