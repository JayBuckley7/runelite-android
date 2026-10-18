package net.runelite.mp.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Jagex account sign-in: out to the device's browser, back as an Intent.
 *
 * This mirrors what the official OSRS app does — it drives the same client through AppAuth
 * and a Custom Tab — and it only works because of the client [JagexAuth] uses. The desktop
 * launcher's client cannot be driven from Android at all: its game session needs a consent
 * hop that redirects to `http://localhost` on port 80, which no Android app can bind, so
 * the answer is only catchable by an in-process WebView — and Jagex refuses an embedded
 * WebView partway through login. The mobile client redirects to a custom scheme instead,
 * and carries `gamesso.token.create` so there is no consent hop to catch.
 *
 * Everything after the browser hands the code back — token exchange, game session,
 * character list — is plain HTTPS from this process.
 */
internal class JagexLoginActivity : ComponentActivity()
{
    private companion object
    {
        const val TAG = "JagexLogin"
        const val PREFS = "runelite_mp_jagex_login"
        const val KEY_VERIFIER = "pkce_verifier"
        const val KEY_STATE = "pkce_state"
        const val KEY_URL = "pending_url"
    }

    /**
     * PKCE for the attempt currently out at the browser.
     *
     * On disk because handing off to another app is exactly when Android is most likely to
     * kill us: the browser comes forward, we go to the background, and the redirect Intent
     * may well arrive at a fresh process. Losing the verifier there would fail the exchange
     * with nothing to show for it.
     */
    private val pending by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val state = LoginUiState()
    private var work: Job? = null

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContent {
            JagexLoginScreen(
                state = state,
                onOpenBrowser = { reopenPending() },
                onCancel = { cancel() },
            )
        }

        val returned = consumeRedirect(intent)
        // A cold start that isn't a redirect is the user asking to sign in; go straight to
        // the browser rather than making them tap through a screen that can only do that.
        if (!returned && savedInstanceState == null) launchLogin()
    }

    override fun onNewIntent(intent: Intent)
    {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeRedirect(intent)
    }

    override fun onDestroy()
    {
        work?.cancel()
        super.onDestroy()
    }

    private fun cancel()
    {
        work?.cancel()
        pending.edit { clear() }
        setResult(RESULT_CANCELED)
        finish()
    }

    /** Arms a fresh attempt and hands its URL to the browser. */
    private fun launchLogin()
    {
        work?.cancel()
        val pkce = JagexAuth.generatePkce()
        val url = JagexAuth.buildLoginUrl(pkce)
        pending.edit {
            clear()
            putString(KEY_VERIFIER, pkce.verifier)
            putString(KEY_STATE, pkce.state)
            putString(KEY_URL, url)
        }
        Log.i(TAG, "opening browser for sign-in")
        state.error = null
        state.status = "Waiting for sign-in in your browser…"
        openInBrowser(url)
    }

    /**
     * Sends the user back to the sign-in already in flight rather than starting a new one.
     * Restarting would mint fresh PKCE, and the code the browser is about to hand back only
     * matches the request it came from.
     */
    private fun reopenPending()
    {
        val url = pending.getString(KEY_URL, null)
        if (url == null)
        {
            launchLogin()
            return
        }
        Log.i(TAG, "reopening browser for the in-flight sign-in")
        state.error = null
        openInBrowser(url)
    }

    private fun openInBrowser(url: String)
    {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            // Without its own task the browser can end up stacked inside ours, and the
            // redirect then resolves back into the browser rather than into this Activity.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try
        {
            startActivity(intent)
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "no browser available: ${t.javaClass.simpleName}")
            state.error = "No browser app is available to sign in with."
            state.status = null
        }
    }

    /** @return true if [intent] was our OAuth redirect and we've taken it from here. */
    private fun consumeRedirect(intent: Intent?): Boolean
    {
        val data = intent?.data ?: return false
        if (!data.scheme.equals(JagexAuth.REDIRECT_SCHEME, ignoreCase = true)) return false

        val uri = data.toString()
        val verifier = pending.getString(KEY_VERIFIER, null)
        val expectedState = pending.getString(KEY_STATE, null)
        if (verifier == null || expectedState == null)
        {
            Log.w(TAG, "redirect with no attempt in flight; ignoring")
            return false
        }
        // Don't log the URI: its query carries the authorization code.
        Log.i(TAG, "redirect received")

        work?.cancel()
        work = lifecycleScope.launch {
            try
            {
                state.error = null
                state.status = "Signing in…"
                val code = JagexAuth.authCodeFromRedirect(uri, expectedState)
                val account = withContext(Dispatchers.IO) {
                    val tokens = JagexAuth.exchangeCode(code, verifier)
                    state.status = "Loading your characters…"
                    buildAccount(tokens)
                }
                JagexAccountStore.save(this@JagexLoginActivity, account)
                Log.i(TAG, "signed in; ${account.characters.size} character(s)")
                pending.edit { clear() }
                state.status = null
                setResult(RESULT_OK)
                finish()
            }
            catch (t: Throwable)
            {
                Log.w(TAG, "sign-in failed: ${t.javaClass.simpleName}: ${t.message}")
                state.status = null
                state.error = t.message ?: "Sign-in failed. Please try again."
            }
        }
        return true
    }

    /** Network-blocking; call from [Dispatchers.IO]. */
    private fun buildAccount(tokens: JagexAuth.Tokens): JagexAccount
    {
        val sessionId = JagexAuth.createSession(tokens.idToken)
        val characters = JagexAuth.getCharacters(sessionId)
        val displayName = JagexAuth.getAccountDisplayName(tokens.sub, tokens.accessToken)
            ?: characters.firstOrNull()?.displayName
            ?: "Jagex account"
        return JagexAccount(
            sub = tokens.sub,
            displayName = displayName,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            idToken = tokens.idToken,
            sessionId = sessionId,
            accessTokenExpiryEpochMs = tokens.expiryEpochMs,
            characters = characters,
            lastRefreshedEpochMs = System.currentTimeMillis(),
        )
    }
}
