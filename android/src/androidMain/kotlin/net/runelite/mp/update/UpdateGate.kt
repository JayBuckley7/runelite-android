package net.runelite.mp.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface UpdateState
{
    data object None : UpdateState
    data class Available(val release: UpdateChecker.Release) : UpdateState
    data class Downloading(val release: UpdateChecker.Release, val progress: Float) : UpdateState
    /** Handed to the installer; kept so the user can reopen it if they backed out. */
    data class Ready(val release: UpdateChecker.Release, val apk: java.io.File) : UpdateState
    data class Failed(val release: UpdateChecker.Release, val message: String) : UpdateState
}

/**
 * Runs the update check ahead of the client boot so the prompt is up before anything
 * loads. The boot never waits on the user, only (briefly) on the check itself.
 */
internal object UpdateGate
{
    private const val CHECK_TIMEOUT_MS = 4_000L

    var state: UpdateState by mutableStateOf(UpdateState.None)
    var bootStarted: Boolean by mutableStateOf(false)
        private set

    suspend fun checkThenBoot(scope: CoroutineScope, boot: () -> Unit)
    {
        if (bootStarted) return
        // Blocking IO can't be cancelled, so time out the await rather than the request.
        val check = scope.async(Dispatchers.IO) { UpdateChecker.newerRelease() }
        val release = withTimeoutOrNull(CHECK_TIMEOUT_MS) { check.await() }
        if (release != null) state = UpdateState.Available(release)
        bootStarted = true
        boot()
    }

    /** Boot reached login: an update the user didn't act on goes away; one they started stays. */
    fun onBootComplete()
    {
        if (state is UpdateState.Available || state is UpdateState.Failed) state = UpdateState.None
    }
}
