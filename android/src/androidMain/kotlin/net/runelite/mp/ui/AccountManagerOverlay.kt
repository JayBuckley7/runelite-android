package net.runelite.mp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.runelite.api.Client
import net.runelite.api.GameState
import net.runelite.mp.AppRestart
import net.runelite.mp.account.AccountPicker
import net.runelite.mp.account.PendingLaunch
import net.runelite.mp.ui.bridge.RuneLiteAccess

/**
 * The account manager, reachable from the game's own login screen.
 *
 * The app no longer opens on the account picker: the client boots with no `JX_*` seeded and
 * shows its normal login screen, and a small button at the bottom centre -- present only while
 * the client is on that screen -- opens the same [AccountPicker] as before. Choosing an account
 * stores the selection ([PendingLaunch]) and restarts the process, which seeds the env from it
 * on the way up; later cold starts reuse that account ([PendingLaunch.resolveForBoot]).
 *
 * Meant to be placed inside the Box that hosts the game viewport, over it.
 */
@Composable
internal fun BoxScope.AccountManagerOverlay()
{
    val context = LocalContext.current
    var onLoginScreen by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }

    // The client instance only exists once RuneLite's injector is up, and the state is read off
    // the client thread's plain field. Twice a second is plenty for a button.
    LaunchedEffect(Unit) {
        while (true) {
            onLoginScreen = try {
                RuneLiteAccess.instance(Client::class.java)?.gameState == GameState.LOGIN_SCREEN
            } catch (t: Throwable) {
                false
            }
            if (!onLoginScreen) open = false
            delay(500)
        }
    }

    if (onLoginScreen && !open)
    {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC1B1B1B))
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Accounts", color = Color(0xFFF5D67B), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (open)
    {
        // Full-screen, and it eats touches so nothing reaches the game underneath.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF2101010))
                .safeDrawingPadding()
                .pointerInput(Unit) { }
        ) {
            AccountPicker(onSelect = { selection ->
                PendingLaunch.set(context, selection)
                AppRestart.restart(context)
            })

            Text(
                "Close",
                color = Color(0xFFBBBBBB),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { open = false }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
