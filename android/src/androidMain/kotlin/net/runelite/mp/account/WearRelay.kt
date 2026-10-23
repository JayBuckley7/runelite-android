package net.runelite.mp.account

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit

/**
 * Carries a Jagex sign-in redirect from the phone to the watch.
 *
 * A watch has no browser of its own: Wear OS opens the sign-in page on the paired phone,
 * so the `com.jagex.mobilesdk.android.osrs://` redirect lands there too. When the phone
 * copy of this app receives a redirect it didn't start, it forwards it here over the Wear
 * Data Layer, and the watch's [JagexLoginActivity] (which kept the PKCE verifier) finishes
 * the sign-in. Both ends must be this same package and signing key, which the Data Layer
 * enforces.
 */
internal object WearRelay
{
    private const val TAG = "WearRelay"
    const val PATH = "/runelite/jagex-redirect"
    private const val TIMEOUT_S = 5L

    /** Sends [uri] to every connected watch; returns how many took it. Network-blocking. */
    fun forward(context: Context, uri: String): Int
    {
        val nodes = try
        {
            Tasks.await(Wearable.getNodeClient(context).connectedNodes, TIMEOUT_S, TimeUnit.SECONDS)
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "no Wear Data Layer: ${t.javaClass.simpleName}")
            return 0
        }
        val messages = Wearable.getMessageClient(context)
        var sent = 0
        for (node in nodes)
        {
            try
            {
                Tasks.await(messages.sendMessage(node.id, PATH, uri.toByteArray()), TIMEOUT_S, TimeUnit.SECONDS)
                sent++
            }
            catch (t: Throwable)
            {
                Log.w(TAG, "relay to a watch failed: ${t.javaClass.simpleName}")
            }
        }
        return sent
    }
}
