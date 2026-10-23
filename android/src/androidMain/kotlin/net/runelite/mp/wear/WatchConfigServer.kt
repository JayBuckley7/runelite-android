package net.runelite.mp.wear

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import net.runelite.mp.ui.bridge.ExternalPluginBridge
import net.runelite.mp.ui.bridge.FavoritesBridge
import net.runelite.mp.ui.bridge.PluginBridge
import net.runelite.mp.update.UpdateChecker
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Watch side of phone-driven configuration: answers [WatchLink]'s requests over the Wear
 * Data Layer with the same bridges the watch's own sidebar uses, so a change made from
 * the phone behaves exactly like one made on the watch. Started once the client has
 * booted; its presence is what tells the phone RuneLite is open here.
 */
internal object WatchConfigServer
{
    private const val TAG = "WatchConfigServer"

    // One thread: requests apply in the order the phone sent them, and none of the bridge
    // calls run on the main thread (setEnabled marshals onto the EDT itself).
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "watch-config") }
    private var started = false

    private val service = MessageClient.RpcService { _, _, data ->
        val result = TaskCompletionSource<ByteArray>()
        executor.execute {
            val reply = try
            {
                handle(JSONObject(String(data)))
            }
            catch (t: Throwable)
            {
                Log.w(TAG, "request failed", t)
                JSONObject().put("error", t.message ?: t.javaClass.simpleName)
            }
            result.setResult(reply.toString().toByteArray())
        }
        result.task
    }

    fun start(context: Context)
    {
        if (started) return
        started = true
        Wearable.getMessageClient(context.applicationContext).addRpcService(service, WearProtocol.PATH)
        Log.i(TAG, "listening for phone configuration")
    }

    private fun handle(req: JSONObject): JSONObject
    {
        val ok = JSONObject()
        when (req.getString("op"))
        {
            WearProtocol.HELLO -> ok.put("tag", UpdateChecker.currentTag).put("device", Build.MODEL)
            WearProtocol.PLUGINS -> ok.put("rows", JSONArray().apply {
                for (row in PluginBridge.list()) put(JSONObject()
                    .put("key", row.rowKey)
                    .put("name", row.displayName)
                    .put("enabled", row.enabled)
                    .put("toggleable", row.toggleable)
                    .put("hasConfig", row.hasConfig))
            })
            WearProtocol.SET_ENABLED ->
            {
                val plugin = row(req.getString("key"))?.plugin ?: error("no such plugin")
                PluginBridge.setEnabled(plugin, req.getBoolean("enabled"))
            }
            WearProtocol.FORM ->
            {
                val form = row(req.getString("key"))?.let { PluginBridge.configForm(it) }
                if (form != null) ok.put("form", form.toJson())
            }
            WearProtocol.SET_CONFIG -> PluginBridge.setConfig(
                req.getString("group"), req.getString("key"), if (req.isNull("value")) null else req.getString("value"),
            )
            WearProtocol.FAVORITES -> ok.put("ids", JSONArray(FavoritesBridge.list().toList()))
            WearProtocol.SET_FAVORITE -> FavoritesBridge.setFavorite(req.getString("id"), req.getBoolean("favorite"))
            WearProtocol.EXTERNALS -> ok.put("names", JSONArray(ExternalPluginBridge.installed().toList()))
            WearProtocol.INSTALL -> ExternalPluginBridge.install(req.getString("name"))
            WearProtocol.REMOVE -> ExternalPluginBridge.remove(req.getString("name"))
            else -> error("unknown op")
        }
        return ok
    }

    private fun row(key: String) = PluginBridge.list().firstOrNull { it.rowKey == key }
}

/** Shared between [WatchConfigServer] and [WatchLink]. */
internal object WearProtocol
{
    const val PATH = "/runelite/config"

    const val HELLO = "hello"
    const val PLUGINS = "plugins"
    const val SET_ENABLED = "setEnabled"
    const val FORM = "form"
    const val SET_CONFIG = "setConfig"
    const val FAVORITES = "favorites"
    const val SET_FAVORITE = "setFavorite"
    const val EXTERNALS = "externals"
    const val INSTALL = "install"
    const val REMOVE = "remove"
}
