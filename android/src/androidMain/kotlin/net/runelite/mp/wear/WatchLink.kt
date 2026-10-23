package net.runelite.mp.wear

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.runelite.mp.ui.bridge.ConfigForm
import net.runelite.mp.ui.bridge.PluginRow
import net.runelite.mp.update.UpdateChecker
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Phone side of watch configuration. Watches for a connected watch running this same
 * build with RuneLite open (its [WatchConfigServer] answers), and while [configuring] is
 * on, the sidebar sends its reads and writes there instead of to this device.
 *
 * Builds must match exactly: the requests carry the watch's plugin keys and config
 * forms, and a different version could disagree on both.
 */
internal object WatchLink
{
    private const val TAG = "WatchLink"
    private const val PROBE_INTERVAL_MS = 5_000L
    private const val TIMEOUT_S = 4L

    data class Watch(val nodeId: String, val name: String)

    /** A same-version watch with RuneLite open, or null. */
    var watch: Watch? by mutableStateOf(null)
        private set

    /** The sidebar is pointed at [watch] rather than this device. */
    var configuring: Boolean by mutableStateOf(false)

    private lateinit var appContext: Context

    /** Polls for the watch for as long as the caller's coroutine lives. */
    suspend fun run(context: Context)
    {
        appContext = context.applicationContext
        while (true)
        {
            val found = withContext(Dispatchers.IO) { probe() }
            if (found != watch)
            {
                Log.i(TAG, if (found != null) "watch available" else "no watch")
                watch = found
                if (found == null) configuring = false
            }
            delay(PROBE_INTERVAL_MS)
        }
    }

    private fun probe(): Watch?
    {
        val nodes = try
        {
            Tasks.await(Wearable.getNodeClient(appContext).connectedNodes, TIMEOUT_S, TimeUnit.SECONDS)
        }
        catch (t: Throwable)
        {
            return null
        }
        for (node in nodes)
        {
            val reply = try { request(node.id, JSONObject().put("op", WearProtocol.HELLO)) } catch (t: Throwable) { continue }
            val tag = reply.optString("tag")
            if (tag == UpdateChecker.currentTag) return Watch(node.id, node.displayName)
            Log.i(TAG, "watch runs $tag, not ${UpdateChecker.currentTag}; not offering it")
        }
        return null
    }

    private fun request(nodeId: String, req: JSONObject): JSONObject
    {
        val bytes = Tasks.await(
            Wearable.getMessageClient(appContext).sendRequest(nodeId, WearProtocol.PATH, req.toString().toByteArray()),
            TIMEOUT_S, TimeUnit.SECONDS,
        )
        val reply = JSONObject(String(bytes))
        if (reply.has("error")) error(reply.getString("error"))
        return reply
    }

    /** One request to the current watch. Network-blocking; throws when it's gone. */
    private fun call(op: String, fill: JSONObject.() -> Unit = {}): JSONObject
    {
        val target = watch ?: error("no watch connected")
        return request(target.nodeId, JSONObject().put("op", op).apply(fill))
    }

    // All below: network-blocking, call off the main thread.

    fun plugins(): List<PluginRow>
    {
        val rows = call(WearProtocol.PLUGINS).getJSONArray("rows")
        return (0 until rows.length()).map {
            val r = rows.getJSONObject(it)
            PluginRow(
                plugin = null,
                configProxy = null,
                displayName = r.getString("name"),
                description = "",
                tags = emptyList(),
                enabled = r.getBoolean("enabled"),
                toggleable = r.getBoolean("toggleable"),
                hasConfig = r.getBoolean("hasConfig"),
                remoteKey = r.getString("key"),
            )
        }
    }

    fun setEnabled(row: PluginRow, enabled: Boolean)
    {
        call(WearProtocol.SET_ENABLED) { put("key", row.remoteKey).put("enabled", enabled) }
    }

    fun configForm(row: PluginRow): ConfigForm? =
        call(WearProtocol.FORM) { put("key", row.remoteKey) }.optJSONObject("form")?.let { ConfigForm.fromJson(it) }

    fun setConfig(group: String, key: String, value: String?)
    {
        call(WearProtocol.SET_CONFIG) { put("group", group).put("key", key).put("value", value ?: JSONObject.NULL) }
    }

    fun favorites(): Set<String>
    {
        val ids = call(WearProtocol.FAVORITES).getJSONArray("ids")
        return (0 until ids.length()).map { ids.getString(it) }.toSet()
    }

    fun setFavorite(id: String, favorite: Boolean)
    {
        call(WearProtocol.SET_FAVORITE) { put("id", id).put("favorite", favorite) }
    }

    fun installedExternals(): Set<String>
    {
        val names = call(WearProtocol.EXTERNALS).getJSONArray("names")
        return (0 until names.length()).map { names.getString(it) }.toSet()
    }

    fun install(name: String)
    {
        call(WearProtocol.INSTALL) { put("name", name) }
    }

    fun remove(name: String)
    {
        call(WearProtocol.REMOVE) { put("name", name) }
    }
}
