package net.runelite.mp.account

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The Jagex account OAuth2 flow, as Jagex's own Android client performs it.
 *
 * The client details below are lifted from the official OSRS app's resources
 * (`com.jagex.oldscape.android`), which drives this exact flow through AppAuth and a
 * Chrome Custom Tab. Using them instead of the *desktop launcher's* client is what makes
 * sign-in possible on Android at all, and the difference is not cosmetic:
 *
 *  - The desktop client redirects to `https://secure.runescape.com/m=weblogin/
 *    launcher-redirect`, and its game session needs a second "consent" hop through a
 *    different client that redirects to `http://localhost` — port 80, matched exactly. No
 *    Android app can bind port 80, so that answer can only be caught by an in-process
 *    WebView; and an embedded WebView is refused by Jagex partway through login. The
 *    desktop flow simply has no valid shape on Android.
 *  - This client redirects to a **custom scheme** we can claim with an intent filter, so
 *    the whole interactive half happens in the real browser and comes back as an Intent.
 *    It also carries `gamesso.token.create` itself, so its id_token mints a game session
 *    directly and the consent hop disappears entirely.
 *
 * Everything here is network-blocking; call from [kotlinx.coroutines.Dispatchers.IO]. We
 * use [HttpURLConnection] and [org.json] (both in the Android framework) rather than
 * OkHttp/Gson because this runs before RuneLite's injector boots, and a static dependency
 * on the host's copies creates loading-order headaches — the same reasoning [AccountStore]
 * documents for the hiscores lookup.
 */
internal object JagexAuth
{
    private const val TAG = "JagexAuth"

    private const val ORIGIN = "https://account.jagex.com"
    private const val AUTH_API = "https://auth.jagex.com/game-session/v1"
    private const val PROFILE_API = "https://api.jagex.com/v1"

    /** `com_jagex_mobilesdk_clientId` from the official OSRS app. */
    private const val CLIENT_ID = "com_jagex_auth_mobile_android_osrs"

    /**
     * `com_jagex_mobilesdk_redirectUri`. The manifest claims this scheme so the browser
     * hands the authorization code back as an Intent.
     */
    const val REDIRECT_URI = "com.jagex.mobilesdk.android.osrs:/oauth2redirect"

    /** Scheme half of [REDIRECT_URI], matched against an incoming Intent's data. */
    const val REDIRECT_SCHEME = "com.jagex.mobilesdk.android.osrs"

    /** `com_jagex_mobilesdk_scope`. `gamesso.token.create` is what lets this client's
     *  id_token mint a game session with no consent hop. */
    private const val SCOPE = "offline openid gamesso.token.create payments payments.android.oldschool"

    private val secureRandom = SecureRandom()

    /** Thrown for any expected failure in the flow; the message is user-presentable. */
    open class AuthException(
        message: String,
        cause: Throwable? = null,
        /** The OAuth `error` code from a failed response, when there was one. */
        val reason: String? = null,
    ) : IOException(message, cause)

    /**
     * The token endpoint rejected a refresh with an OAuth error (usually `invalid_grant`): the
     * stored refresh token is dead (expired, revoked, or already redeemed by a refresh whose
     * result was lost). Only a full sign-in brings the account back, so callers prompt for one
     * instead of retrying.
     */
    class TokenRevokedException : AuthException(REAUTH_MESSAGE)

    const val REAUTH_MESSAGE =
        "This account's Jagex sign-in has expired. Re-authorize it before it will work again."

    // ------------------------------------------------------------------
    // PKCE
    // ------------------------------------------------------------------

    data class Pkce(val verifier: String, val challenge: String, val state: String)

    fun generatePkce(): Pkce
    {
        val verifier = randomString(43)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
        return Pkce(
            verifier = verifier,
            challenge = base64Url(digest),
            state = randomString(16),
        )
    }

    // ------------------------------------------------------------------
    // Authorization request
    // ------------------------------------------------------------------

    /**
     * `prompt=login` forces a fresh credential entry rather than silently reusing the
     * browser's existing Jagex session. That is deliberate: without it, adding a *second*
     * Jagex account would just hand back the one already signed in. Re-authorizing an
     * expired account goes through the same full sign-in.
     */
    fun buildLoginUrl(pkce: Pkce): String = "$ORIGIN/oauth2/auth?" + query(
        "response_type" to "code",
        "client_id" to CLIENT_ID,
        "redirect_uri" to REDIRECT_URI,
        "scope" to SCOPE,
        "code_challenge" to pkce.challenge,
        "code_challenge_method" to "S256",
        "prompt" to "login",
        "state" to pkce.state,
    )

    fun isRedirect(url: String): Boolean = url.startsWith("$REDIRECT_SCHEME:")

    /**
     * Pulls the authorization `code` out of the redirect, rejecting a response whose
     * `state` isn't the one we generated. A redirect carrying no `state` at all is accepted
     * with a warning rather than failed — the parameter is echoed at the provider's
     * discretion, and hard-failing would break sign-in outright if Jagex ever stopped
     * sending it. A *mismatched* state is fatal.
     */
    fun authCodeFromRedirect(url: String, expectedState: String): String
    {
        val params = parseQuery(url.substringAfter('?', "").substringBefore('#'))
        when (val returned = params["state"])
        {
            null -> Log.w(TAG, "redirect carried no 'state'; accepting unverified")
            expectedState -> {}
            else -> throw AuthException("Sign-in response failed its state check. Try again.")
        }
        return params["code"] ?: throw AuthException(
            params["error_description"] ?: params["error"] ?: "Sign-in returned no authorization code."
        )
    }

    // ------------------------------------------------------------------
    // Token endpoint
    // ------------------------------------------------------------------

    data class Tokens(
        val accessToken: String,
        val idToken: String,
        val refreshToken: String,
        /** The `sub` claim — a stable per-Jagex-account identifier. */
        val sub: String,
        val expiryEpochMs: Long,
    )

    fun exchangeCode(code: String, verifier: String): Tokens = tokenRequest(
        "grant_type" to "authorization_code",
        "client_id" to CLIENT_ID,
        "code" to code,
        "code_verifier" to verifier,
        "redirect_uri" to REDIRECT_URI,
    )

    fun refreshTokens(refreshToken: String): Tokens = try
    {
        tokenRequest(
            "grant_type" to "refresh_token",
            "client_id" to CLIENT_ID,
            "refresh_token" to refreshToken,
        )
    }
    catch (e: AuthException)
    {
        // Any OAuth error means Jagex refused this refresh token; only a fresh sign-in fixes
        // it. Failures without one (network, 5xx with no body) stay retryable.
        throw if (!e.reason.isNullOrEmpty()) TokenRevokedException() else e
    }

    /** Best-effort token revocation on sign-out; failures are logged, never thrown. */
    fun revokeToken(accessToken: String)
    {
        try
        {
            post(
                url = "$ORIGIN/oauth2/revoke",
                contentType = "application/x-www-form-urlencoded",
                body = query("token" to accessToken, "client_id" to CLIENT_ID),
            )
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "token revocation failed: ${t.javaClass.simpleName}")
        }
    }

    private fun tokenRequest(vararg params: Pair<String, String>): Tokens
    {
        val body = post(
            url = "$ORIGIN/oauth2/token",
            contentType = "application/x-www-form-urlencoded",
            body = query(*params),
        )
        val json = JSONObject(body)
        val idToken = json.optString("id_token").takeIf { it.isNotEmpty() }
            ?: throw AuthException("Token response carried no id_token.")
        val sub = decodeJwtPayload(idToken).optString("sub").takeIf { it.isNotEmpty() }
            ?: throw AuthException("Token response carried no account identifier.")
        return Tokens(
            accessToken = json.optString("access_token"),
            idToken = idToken,
            refreshToken = json.optString("refresh_token"),
            sub = sub,
            expiryEpochMs = System.currentTimeMillis() + json.optLong("expires_in", 0L) * 1000L,
        )
    }

    // ------------------------------------------------------------------
    // Game session + account APIs
    // ------------------------------------------------------------------

    /** Mints a game session from this client's id_token. Returns `JX_SESSION_ID`. */
    fun createSession(idToken: String): String
    {
        val body = post(
            url = "$AUTH_API/sessions",
            contentType = "application/json",
            body = JSONObject().put("idToken", idToken).toString(),
        )
        return JSONObject(body).optString("sessionId").takeIf { it.isNotEmpty() }
            ?: throw AuthException("Session response carried no sessionId.")
    }

    /** The characters (RuneScape accounts) reachable from a game session. */
    fun getCharacters(sessionId: String): List<JagexCharacter>
    {
        // Not logged: the body carries userHash and other per-character identifiers.
        val body = get("$AUTH_API/accounts", "Bearer $sessionId")
        val arr = JSONArray(body)
        val out = ArrayList<JagexCharacter>(arr.length())
        for (i in 0 until arr.length())
        {
            val o = arr.optJSONObject(i) ?: continue
            val accountId = o.optString("accountId").takeIf { it.isNotEmpty() } ?: continue
            out += JagexCharacter(
                accountId = accountId,
                displayName = o.optString("displayName").takeIf { it.isNotEmpty() } ?: accountId,
                userHash = o.optString("userHash"),
            )
        }
        return out
    }

    /** The Jagex account's own display name, used to label the account card. */
    fun getAccountDisplayName(sub: String, accessToken: String): String?
    {
        return try
        {
            val body = get("$PROFILE_API/users/$sub/displayName", "Bearer $accessToken")
            JSONObject(body).optString("displayName").takeIf { it.isNotEmpty() }
        }
        catch (t: Throwable)
        {
            // Purely cosmetic — the account is still usable without it.
            Log.w(TAG, "display-name fetch failed: ${t.javaClass.simpleName}")
            null
        }
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private fun post(url: String, contentType: String, body: String): String
    {
        val conn = open(url)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", contentType)
        conn.setRequestProperty("Accept", "application/json")
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return finish(conn, url)
    }

    private fun get(url: String, authorization: String): String
    {
        val conn = open(url)
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Authorization", authorization)
        return finish(conn, url)
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
        }

    private fun finish(conn: HttpURLConnection, url: String): String
    {
        try
        {
            val code = conn.responseCode
            if (code !in 200..299)
            {
                val err = conn.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                // The endpoint path is safe to log; the response body can carry tokens and
                // account identifiers, so only its OAuth `error` code is surfaced.
                val reason = runCatching { JSONObject(err).optString("error") }.getOrNull().orEmpty()
                Log.w(TAG, "HTTP $code from ${URL(url).path}${if (reason.isEmpty()) "" else " ($reason)"}")
                throw AuthException(
                    "Jagex returned HTTP $code" + if (reason.isEmpty()) "." else " ($reason).",
                    reason = reason,
                )
            }
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        }
        finally
        {
            conn.disconnect()
        }
    }

    /**
     * Decodes a JWT's claims without verifying its signature. These tokens arrive over TLS
     * from the provider we just spoke to and are read only for `sub`, so there's nothing
     * for a signature check to protect here.
     */
    private fun decodeJwtPayload(token: String): JSONObject
    {
        val parts = token.split('.')
        if (parts.size != 3) throw AuthException("Malformed id_token (${parts.size} sections, expected 3).")
        val bytes = try
        {
            Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }
        catch (t: Throwable)
        {
            throw AuthException("Could not decode id_token payload.", t)
        }
        return try
        {
            JSONObject(bytes.toString(Charsets.UTF_8))
        }
        catch (t: Throwable)
        {
            throw AuthException("Could not parse id_token payload.", t)
        }
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun query(vararg params: Pair<String, String>): String =
        params.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Splits an `a=1&b=2` blob into decoded pairs. */
    private fun parseQuery(raw: String): Map<String, String>
    {
        if (raw.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (pair in raw.split('&'))
        {
            if (pair.isEmpty()) continue
            val idx = pair.indexOf('=')
            val key = if (idx < 0) pair else pair.substring(0, idx)
            val value = if (idx < 0) "" else pair.substring(idx + 1)
            out[dec(key)] = dec(value)
        }
        return out
    }

    private fun dec(s: String): String =
        runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun randomString(len: Int): String
    {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val sb = StringBuilder(len)
        repeat(len) { sb.append(charset[secureRandom.nextInt(charset.length)]) }
        return sb.toString()
    }
}
