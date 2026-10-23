package net.runelite.mp.account

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Properties

/**
 * The account the *next* process start should seed the launcher env with.
 *
 * The game boots straight to its own login screen with no `JX_*` values; the account manager
 * overlay on that screen is where an account gets chosen. Choosing one cannot take effect in
 * the running process -- the injected client reads the `JX_*` set once, in `client.init` -- so
 * the selection is written here, the process restarts, and [consume] hands it to the launcher
 * exactly once. It is one-shot on purpose: a stale session id is the usual reason a launch that
 * worked yesterday fails today, and the picker renews the session at selection time, so a cold
 * start should not silently reuse an old one.
 */
internal object PendingLaunch
{
    private const val TAG = "PendingLaunch"
    private const val FILE = "pending-launch.properties"
    private const val KEY_FILE = "credentialsFile"
    private const val DROP_FILE = "credentials.properties"

    private fun file(context: Context) = File(context.filesDir, FILE)

    private const val PREFS = "launch"
    private const val KEY_LAST_TYPE = "lastType"
    private const val TYPE_JAGEX = "jagex"
    private const val TYPE_IMPORT = "import"

    /** Persist a picker selection for the next start. */
    fun set(context: Context, selection: AccountSelection)
    {
        // The two stores each remember their own last pick (setLastCharacterId / setActive); this
        // is the one bit that says which of the two was picked more recently.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_TYPE, if (selection.credentialsFile != null) TYPE_IMPORT else TYPE_JAGEX)
            .commit() // synchronous: the process exits right after this

        val props = Properties()
        selection.credentialsFile?.let { props.setProperty(KEY_FILE, it.absolutePath) }
        selection.credentials?.forEach { (k, v) -> if (v.isNotEmpty()) props.setProperty(k, v) }
        file(context).outputStream().use { props.store(it, "consumed on next start") }
    }

    /**
     * What this start should launch with: the one-shot selection an account switch just wrote,
     * or, on an ordinary cold start, the account picked last time. A Jagex account goes through
     * [JagexAccountStore.prepare] first, exactly as the picker does, because the session id it
     * launched with yesterday is stale by now. Null when nothing was ever picked, or when the
     * renewal fails -- the client then boots to its plain login screen and the account manager
     * is there to pick again.
     *
     * Runs blocking network work; call it off the UI thread.
     */
    fun resolveForBoot(context: Context): AccountSelection?
    {
        importDropped(context)
        consume(context)?.let { return it }

        val lastType = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_TYPE, null)
        val jagex = lastJagex(context)
        val imported = lastImported(context)

        return when (lastType)
        {
            TYPE_IMPORT -> imported ?: jagex
            TYPE_JAGEX -> jagex ?: imported
            else -> jagex ?: imported
        }
    }

    /**
     * Picks up a credentials.properties pushed to the app's external files dir, for devices
     * with no usable file picker (watches):
     *
     *     adb push credentials.properties /sdcard/Android/data/net.runelite.mp/files/
     *
     * It replaces the saved account it belongs to (or is added as a new one), becomes the
     * active account, and is deleted so the tokens don't sit in shared storage.
     */
    private fun importDropped(context: Context)
    {
        val drop = File(context.getExternalFilesDir(null) ?: return, DROP_FILE)
        if (!drop.isFile) return
        try
        {
            val id = AccountStore.importOrReplace(context, null, drop.readBytes())
            if (id == null)
            {
                Log.w(TAG, "dropped $DROP_FILE wasn't a Jagex credentials file; discarding")
                return
            }
            AccountStore.setActive(context, id)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_TYPE, TYPE_IMPORT)
                .commit()
            Log.i(TAG, "imported dropped $DROP_FILE")
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "failed to import dropped $DROP_FILE", t)
        }
        finally
        {
            drop.delete()
        }
    }

    private fun lastJagex(context: Context): AccountSelection?
    {
        val characterId = JagexAccountStore.lastCharacterId(context) ?: return null
        val account = JagexAccountStore.list(context)
            .firstOrNull { acc -> acc.characters.any { it.accountId == characterId } }
            ?: return null
        return try
        {
            val ready = JagexAccountStore.prepare(context, account)
            val character = ready.characters.firstOrNull { it.accountId == characterId } ?: return null
            AccountSelection(credentials = JagexAccountStore.credentials(ready, character))
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "could not renew the last Jagex account; booting without it", t)
            null
        }
    }

    private fun lastImported(context: Context): AccountSelection?
    {
        val id = AccountStore.activeAccountId(context) ?: return null
        val account = AccountStore.list(context).firstOrNull { it.id == id } ?: return null
        return if (account.file.isFile) AccountSelection(credentialsFile = account.file) else null
    }

    /** The selection written by [set], removed as it is read. Null when there is none. */
    fun consume(context: Context): AccountSelection?
    {
        val f = file(context)
        if (!f.isFile) return null
        val props = try
        {
            f.inputStream().use { Properties().apply { load(it) } }
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "unreadable pending launch; ignoring", t)
            null
        }
        f.delete()
        props ?: return null

        props.getProperty(KEY_FILE)?.let { path ->
            val src = File(path)
            return if (src.isFile) AccountSelection(credentialsFile = src) else null
        }

        val credentials = props.stringPropertyNames()
            .filter { it.startsWith("JX_") }
            .associateWith { props.getProperty(it) }
        return if (credentials.isEmpty()) null else AccountSelection(credentials = credentials)
    }
}
