package net.runelite.mp.ui.bridge

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard

/** Keep text transfer on Android's clipboard while retaining other in-process flavors. */
internal class SwingClipboard(private val activity: ComponentActivity) : AutoCloseable {
    private val platform = activity.getSystemService(ClipboardManager::class.java)
    private val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { clipboard.refreshFromBridge() }

    private val bridge = object : Clipboard.Bridge {
            override fun readText(): String? {
                if (!activity.hasWindowFocus()) throw IllegalStateException("Clipboard is unavailable while unfocused")
                val clip = platform.primaryClip ?: return null
                return if (clip.itemCount == 0) null else clip.getItemAt(0).text?.toString()
            }

            override fun writeText(text: String) {
                platform.setPrimaryClip(ClipData.newPlainText("RuneLite", text))
            }
        }

    init {
        clipboard.setBridge(bridge)
        platform.addPrimaryClipChangedListener(listener)
    }

    override fun close() {
        platform.removePrimaryClipChangedListener(listener)
        clipboard.clearBridge(bridge)
    }
}
