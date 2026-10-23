package net.runelite.mp.wear

import android.util.Log
import kotlinx.coroutines.delay
import net.runelite.api.Client
import net.runelite.api.GameState
import net.runelite.client.callback.ClientThread
import net.runelite.mp.ui.bridge.RuneLiteAccess
import java.lang.reflect.Method
import java.util.Properties

/**
 * Keeps a watch's client in resizable mode. OSRS starts in fixed (765x503), which on a
 * 498px round screen is a postage stamp; resizable fills it. RuneLite's API can only read
 * the mode, so this calls the client's own setter — the one clientscript's `setwindowmode`
 * uses — which the build's locateWindowMode task finds in the gamepack and records in
 * `windowmode.properties`. Modern vs classic layout is an account setting and follows
 * whatever the account already uses.
 */
internal object WindowModeForcer
{
    private const val TAG = "WindowModeForcer"
    private const val RESIZABLE = 2
    private const val CHECK_INTERVAL_MS = 2_000L

    private class Setter(val method: Method, val dummy: Any)

    /** Re-applies resizable whenever the client is in fixed, for as long as the caller lives. */
    suspend fun run()
    {
        val client = RuneLiteAccess.instance(Client::class.java) ?: return
        val clientThread = RuneLiteAccess.instance(ClientThread::class.java) ?: return
        val setter = resolve(client) ?: return
        while (true)
        {
            clientThread.invokeLater(Runnable {
                // Only where the client is settled; mid-load it is rebuilding the very
                // interfaces the setter would rebuild.
                val state = client.gameState
                if (!client.isResized && (state == GameState.LOGIN_SCREEN || state == GameState.LOGGED_IN))
                {
                    try
                    {
                        setter.method.invoke(null, RESIZABLE, setter.dummy)
                        Log.i(TAG, "switched to resizable")
                    }
                    catch (t: Throwable)
                    {
                        Log.w(TAG, "window mode switch failed", t)
                    }
                }
            })
            delay(CHECK_INTERVAL_MS)
        }
    }

    private fun resolve(client: Client): Setter?
    {
        return try
        {
            val loader = client.javaClass.classLoader
            val props = Properties()
            (loader.getResourceAsStream("net/runelite/mp/windowmode.properties")
                ?: error("windowmode.properties missing from the build"))
                .use { props.load(it) }
            val desc = props.getProperty("desc")
            // (I<dummy>)V: the dummy's type is the descriptor's second parameter.
            val dummyType = when (desc[2])
            {
                'B' -> Byte::class.javaPrimitiveType!!
                'S' -> Short::class.javaPrimitiveType!!
                else -> Int::class.javaPrimitiveType!!
            }
            val raw = props.getProperty("dummy").toInt()
            val dummy: Any = when (dummyType)
            {
                Byte::class.javaPrimitiveType -> raw.toByte()
                Short::class.javaPrimitiveType -> raw.toShort()
                else -> raw
            }
            val method = Class.forName(props.getProperty("owner"), true, loader)
                .getDeclaredMethod(props.getProperty("name"), Int::class.javaPrimitiveType, dummyType)
                .apply { isAccessible = true }
            Setter(method, dummy)
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "can't resolve the client's window-mode setter; leaving the mode alone", t)
            null
        }
    }
}
