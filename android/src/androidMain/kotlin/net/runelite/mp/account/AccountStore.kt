package net.runelite.mp.account

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.Properties
import androidx.core.content.edit

/**
 * Persistent store for the user's imported Jagex launcher credential files. Each
 * entry lives as two files in `<appFiles>/accounts/`:
 *  - `<id>.properties` — the original credentials.properties (JX_ACCESS_TOKEN /
 *    JX_REFRESH_TOKEN / JX_SESSION_ID / JX_CHARACTER_ID / JX_DISPLAY_NAME), copied
 *    verbatim from whichever desktop launcher generated it.
 *  - `<id>.meta.json` — a tiny ad-hoc JSON written by us with cached metadata
 *    (display name, detected account type, total level, last refresh). Loaded by
 *    [AccountPicker] so it can render cards without re-hitting hiscores every time.
 *
 * The `<id>` is a slug derived from JX_DISPLAY_NAME (or a fallback if absent) so the
 * filenames stay debuggable when poking around with `adb pull`.
 *
 * On first launch we migrate the legacy `assets/credentials.properties` (the original
 * single-account path) into a saved entry called "Default" so the user doesn't lose
 * their previously-working setup the moment the picker ships.
 */
internal object AccountStore
{
    private const val TAG = "AccountStore"
    private const val DIR_NAME = "accounts"
    private const val ACTIVE_PREF = "runelite_mp_accounts"
    private const val ACTIVE_KEY = "active_id"

    enum class AccountType(val label: String)
    {
        UNKNOWN("Unknown"),
        NORMAL("Normal"),
        IRONMAN("Ironman"),
        HARDCORE_IRONMAN("Hardcore Ironman"),
        ULTIMATE_IRONMAN("Ultimate Ironman"),
        ;

        companion object
        {
            fun fromString(s: String?): AccountType = when (s)
            {
                "NORMAL" -> NORMAL
                "IRONMAN" -> IRONMAN
                "HARDCORE_IRONMAN" -> HARDCORE_IRONMAN
                "ULTIMATE_IRONMAN" -> ULTIMATE_IRONMAN
                else -> UNKNOWN
            }
        }
    }

    data class Account(
        val id: String,
        val file: File,
        val displayName: String,
        val accountType: AccountType,
        val totalLevel: Int,
        val totalXp: Long,
        val rank: Int,
        val lastRefreshedEpochMs: Long,
    )

    fun list(context: Context): List<Account>
    {
        migrateLegacyAssetIfNeeded(context)
        val dir = accountsDir(context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.name.endsWith(".properties") }
            ?.sortedBy { it.nameWithoutExtension.lowercase(Locale.ROOT) }
            ?.map { read(context, it) }
            ?: emptyList()
    }

    fun activeAccountId(context: Context): String?
    {
        val sp = context.getSharedPreferences(ACTIVE_PREF, Context.MODE_PRIVATE)
        return sp.getString(ACTIVE_KEY, null)
    }

    fun setActive(context: Context, id: String?)
    {
        val sp = context.getSharedPreferences(ACTIVE_PREF, Context.MODE_PRIVATE)
        // Synchronous: the picker restarts the process right after this, and apply() would
        // still be on its background thread when exit(0) hits.
        sp.edit(commit = true) { if (id == null) remove(ACTIVE_KEY) else putString(ACTIVE_KEY, id) }
    }

    fun read(context: Context, file: File): Account
    {
        val props = Properties().apply { file.inputStream().use { load(it) } }
        val display = props.getProperty("JX_DISPLAY_NAME")?.trim()?.takeIf { it.isNotEmpty() }
            ?: file.nameWithoutExtension
        val meta = MetaFile(metaPathFor(file)).load()
        return Account(
            id = file.nameWithoutExtension,
            file = file,
            displayName = display,
            accountType = AccountType.fromString(meta["accountType"]),
            totalLevel = meta["totalLevel"]?.toIntOrNull() ?: 0,
            totalXp = meta["totalXp"]?.toLongOrNull() ?: 0L,
            rank = meta["rank"]?.toIntOrNull() ?: 0,
            lastRefreshedEpochMs = meta["lastRefreshedEpochMs"]?.toLongOrNull() ?: 0L,
        )
    }

    fun delete(context: Context, id: String)
    {
        val file = File(accountsDir(context), "$id.properties")
        val meta = metaPathFor(file)
        file.delete()
        meta.delete()
        if (activeAccountId(context) == id) setActive(context, null)
    }

    /** Imports raw `credentials.properties` bytes (provided by SAF). Validates that it
     *  contains at least one JX_* key; otherwise refuses so the user doesn't end up with
     *  a useless file on disk. Returns the new account id or null if the import failed. */
    fun import(context: Context, displayHint: String?, bytes: ByteArray): String?
    {
        val props = try
        {
            Properties().apply { bytes.inputStream().use { load(it) } }
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "import: failed to parse properties", t)
            return null
        }
        val jxKeys = props.stringPropertyNames().count { it.startsWith("JX_") }
        if (jxKeys == 0)
        {
            Log.w(TAG, "import: file has no JX_* keys; refusing")
            return null
        }
        val display = props.getProperty("JX_DISPLAY_NAME")?.trim().orEmpty().ifEmpty {
            displayHint?.removeSuffix(".properties").orEmpty()
        }
        val id = uniqueIdFrom(context, display.ifEmpty { "Account" })
        val out = File(accountsDir(context).also { it.mkdirs() }, "$id.properties")
        out.outputStream().use { stream ->
            props.store(stream, "Imported by runelite-mp on ${java.util.Date()}")
        }
        return id
    }

    /** Like [import], but a file for an account that's already saved (same JX_CHARACTER_ID,
     *  else same JX_DISPLAY_NAME) overwrites it in place: a re-exported file with a fresh
     *  session key updates the account rather than adding a duplicate. Returns the id. */
    fun importOrReplace(context: Context, displayHint: String?, bytes: ByteArray): String?
    {
        val props = try
        {
            Properties().apply { bytes.inputStream().use { load(it) } }
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "importOrReplace: failed to parse properties", t)
            return null
        }
        if (props.stringPropertyNames().none { it.startsWith("JX_") })
        {
            Log.w(TAG, "importOrReplace: file has no JX_* keys; refusing")
            return null
        }
        fun key(p: Properties, name: String) = p.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
        val character = key(props, "JX_CHARACTER_ID")
        val display = key(props, "JX_DISPLAY_NAME")
        val existing = list(context).firstOrNull { acc ->
            val saved = try { Properties().apply { acc.file.inputStream().use { load(it) } } } catch (_: Throwable) { return@firstOrNull false }
            if (character != null && key(saved, "JX_CHARACTER_ID") != null) key(saved, "JX_CHARACTER_ID") == character
            else display != null && key(saved, "JX_DISPLAY_NAME") == display
        } ?: return import(context, displayHint, bytes)
        existing.file.outputStream().use { stream ->
            props.store(stream, "Replaced by runelite-mp on ${java.util.Date()}")
        }
        return existing.id
    }

    /** Looks up the given account name against the OSRS hiscores endpoints in order
     *  (Normal → Ironman → HCIM → UIM). The first one to return 200 wins; we cache the
     *  account type + Overall row in the sidecar JSON. Network-blocking; call from a
     *  background dispatcher. */
    fun refreshFromHiscores(context: Context, id: String): Account?
    {
        val file = File(accountsDir(context), "$id.properties")
        if (!file.exists()) return null
        val props = Properties().apply { file.inputStream().use { load(it) } }
        val name = props.getProperty("JX_DISPLAY_NAME")?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null

        val endpoints = listOf(
            "https://services.runescape.com/m=hiscore_oldschool_ultimate/index_lite.json" to AccountType.ULTIMATE_IRONMAN,
            "https://services.runescape.com/m=hiscore_oldschool_hardcore_ironman/index_lite.json" to AccountType.HARDCORE_IRONMAN,
            "https://services.runescape.com/m=hiscore_oldschool_ironman/index_lite.json" to AccountType.IRONMAN,
            "https://services.runescape.com/m=hiscore_oldschool/index_lite.json" to AccountType.NORMAL,
        )

        for ((url, type) in endpoints)
        {
            val res = fetchOverall(url, name) ?: continue
            val meta = MetaFile(metaPathFor(file))
            meta.put("accountType", type.name)
            meta.put("totalLevel", res.level.toString())
            meta.put("totalXp", res.xp.toString())
            meta.put("rank", res.rank.toString())
            meta.put("lastRefreshedEpochMs", System.currentTimeMillis().toString())
            meta.save()
            return read(context, file)
        }
        // No endpoint matched — leave the existing meta as-is so an "Unknown" type
        // doesn't overwrite a previously-detected type.
        val meta = MetaFile(metaPathFor(file))
        meta.put("lastRefreshedEpochMs", System.currentTimeMillis().toString())
        meta.save()
        return read(context, file)
    }

    private data class OverallRow(val rank: Int, val level: Int, val xp: Long)

    /** Fetches the `index_lite.json` payload, extracts the row whose name is "Overall",
     *  and returns its rank/level/xp. Returns null on any HTTP failure or if the row is
     *  missing (player not on that endpoint's hiscores). Uses HttpURLConnection so we
     *  don't depend on OkHttp being initialised pre-launch. */
    private fun fetchOverall(url: String, name: String): OverallRow?
    {
        val fullUrl = "$url?player=" + java.net.URLEncoder.encode(name, "UTF-8")
        return try
        {
            val conn = java.net.URL(fullUrl).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "runelite-mp/1.0")
            val code = conn.responseCode
            if (code != 200) { conn.disconnect(); return null }
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            conn.disconnect()
            parseOverall(body)
        }
        catch (t: Throwable)
        {
            Log.d(TAG, "fetchOverall($url): ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    /** Hand-rolled lookup for the "Overall" object inside the hiscore JSON. The full
     *  response is small (~12 KB) but parsing it via Gson would require hauling in a
     *  binding type — the few values we need are easier to pluck with substring scans. */
    private fun parseOverall(body: String): OverallRow?
    {
        val nameIdx = body.indexOf("\"name\":\"Overall\"")
        if (nameIdx < 0) return null
        val objEnd = body.indexOf('}', nameIdx)
        if (objEnd < 0) return null
        val slice = body.substring(nameIdx, objEnd)
        val rank = extractLongField(slice, "rank")?.toInt() ?: -1
        val level = extractLongField(slice, "level")?.toInt() ?: -1
        val xp = extractLongField(slice, "xp") ?: -1L
        if (rank < 0 && level < 0) return null
        return OverallRow(rank = rank, level = level, xp = xp)
    }

    private fun extractLongField(json: String, key: String): Long?
    {
        val pat = "\"$key\":"
        val idx = json.indexOf(pat)
        if (idx < 0) return null
        var i = idx + pat.length
        // Skip whitespace and an optional minus.
        while (i < json.length && json[i].isWhitespace()) i++
        val start = i
        if (i < json.length && json[i] == '-') i++
        while (i < json.length && json[i].isDigit()) i++
        return if (i > start) json.substring(start, i).toLongOrNull() else null
    }

    // ------------------------------------------------------------------
    // Migration: copy assets/credentials.properties → accounts/Default on
    // first boot if no accounts have been imported yet.
    // ------------------------------------------------------------------

    private fun migrateLegacyAssetIfNeeded(context: Context)
    {
        val dir = accountsDir(context)
        val migratedFlag = File(dir, ".legacy-migrated")
        if (migratedFlag.exists()) return
        dir.mkdirs()
        try
        {
            context.assets.open("credentials.properties").use { stream ->
                val bytes = stream.readBytes()
                val id = import(context, "Default", bytes) ?: return
                if (activeAccountId(context) == null) setActive(context, id)
                // Don't log $id — it's a slug derived from the user's account display
                // name. Logcat is reachable to developer-mode adb users, and the count of
                // imports we already track (= boolean: migration happened or not) is
                // enough context to debug without leaking the identity.
                Log.i(TAG, "migrated assets/credentials.properties → default account")
            }
        }
        catch (e: java.io.FileNotFoundException)
        {
            // No asset; nothing to migrate. Still write the flag so we don't keep
            // probing.
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "legacy migration failed", t)
        }
        finally
        {
            try { migratedFlag.writeText(System.currentTimeMillis().toString()) } catch (_: Throwable) {}
        }
    }

    private fun accountsDir(context: Context): File = File(context.filesDir, DIR_NAME)

    private fun metaPathFor(propsFile: File): File =
        File(propsFile.parentFile, "${propsFile.nameWithoutExtension}.meta.json")

    private fun uniqueIdFrom(context: Context, raw: String): String
    {
        val base = raw
            .lowercase(Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')
            .ifEmpty { "account" }
        val dir = accountsDir(context)
        var candidate = base
        var i = 2
        while (File(dir, "$candidate.properties").exists())
        {
            candidate = "${base}_${i++}"
        }
        return candidate
    }

    /** Microscopic flat-file JSON store — just a Map<String, String> serialized as a
     *  single-line `{"k":"v",...}`. We don't reach for Gson because [AccountStore] runs
     *  before the RL injector boots and pulling in a static dependency on the host's
     *  gson copy creates loading-order headaches. */
    private class MetaFile(private val file: File)
    {
        private val map = LinkedHashMap<String, String>()
        private var loaded = false

        fun load(): MetaFile
        {
            if (loaded) return this
            loaded = true
            if (!file.exists()) return this
            try
            {
                val body = file.readText().trim()
                if (!body.startsWith('{') || !body.endsWith('}')) return this
                val inner = body.substring(1, body.length - 1)
                var i = 0
                while (i < inner.length)
                {
                    if (inner[i].isWhitespace() || inner[i] == ',') { i++; continue }
                    if (inner[i] != '"') break
                    val keyEnd = inner.indexOf('"', i + 1)
                    if (keyEnd < 0) break
                    val key = inner.substring(i + 1, keyEnd)
                    val colon = inner.indexOf(':', keyEnd + 1)
                    if (colon < 0) break
                    var j = colon + 1
                    while (j < inner.length && inner[j].isWhitespace()) j++
                    if (j >= inner.length || inner[j] != '"') break
                    val valEnd = inner.indexOf('"', j + 1)
                    if (valEnd < 0) break
                    val value = inner.substring(j + 1, valEnd)
                    map[key] = value
                    i = valEnd + 1
                }
            }
            catch (t: Throwable) { /* ignore — start fresh */ }
            return this
        }

        operator fun get(key: String): String?
        {
            load()
            return map[key]
        }

        fun put(key: String, value: String)
        {
            load()
            map[key] = value
        }

        fun save()
        {
            file.parentFile?.mkdirs()
            val sb = StringBuilder("{")
            var first = true
            for ((k, v) in map)
            {
                if (!first) sb.append(',')
                first = false
                sb.append('"').append(escape(k)).append("\":\"").append(escape(v)).append('"')
            }
            sb.append('}')
            file.writeText(sb.toString())
        }

        private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
