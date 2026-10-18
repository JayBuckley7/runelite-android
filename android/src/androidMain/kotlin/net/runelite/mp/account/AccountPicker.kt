package net.runelite.mp.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the picker hands back to the host so it can seed the launcher env. Exactly one of
 * [credentials] / [credentialsFile] is set: signed-in Jagex accounts produce the `JX_*`
 * map in memory, imported files are read at boot by
 * [net.runelite.mp.RuneLiteLauncher.seedLauncherCredentials].
 */
internal class AccountSelection(
    val credentials: Map<String, String>? = null,
    val credentialsFile: java.io.File? = null,
)

/**
 * The pre-boot Compose screen that picks what to launch with. Sits in front of
 * [net.runelite.mp.RuneLiteLauncher]; tapping a character calls [onSelect] and the host
 * begins the normal RL boot flow with that account's `JX_*` values seeded into the env.
 *
 * Two sources feed it:
 *  - **Jagex accounts** signed in through [JagexLoginScreen]. One card per Jagex account,
 *    expanding to the characters on it — the same account → character shape the desktop
 *    Jagex launcher uses, and the reason multiple accounts can coexist here at all.
 *  - **Imported credentials**, the older path: a `credentials.properties` copied off a
 *    desktop launcher via the SAF picker. Kept as the escape hatch for anyone who'd
 *    rather not sign in on the phone, and so existing setups keep working.
 */
@Composable
internal fun AccountPicker(onSelect: (AccountSelection) -> Unit)
{
    val context = LocalContext.current
    // Sign-in runs in its own Activity rather than inline here — see [JagexLoginActivity]
    // for why (an intent filter for the `jagex:` return scheme, which only an Activity can
    // own).
    //
    // Reloading is driven by our own RESUMED, not by an activity result. JagexLoginActivity
    // is launchMode=singleTask so the browser's `jagex:` return reaches the instance that
    // is already running the flow, and Android answers startActivityForResult for a
    // new-task activity with an immediate RESULT_CANCELED — the callback would fire before
    // sign-in even began and never again. Coming back to the foreground is the signal that
    // actually means "something may have changed".
    var reloadKey by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reloadKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PickerContent(
        reloadKey = reloadKey,
        onAddAccount = {
            context.startActivity(Intent(context, JagexLoginActivity::class.java))
        },
        // A full sign-in: the new tokens overwrite this account's file, clearing needsReauth.
        onReauthorize = {
            context.startActivity(Intent(context, JagexLoginActivity::class.java))
        },
        onSelect = onSelect,
    )
}

@Composable
private fun PickerContent(
    reloadKey: Int,
    onAddAccount: () -> Unit,
    onReauthorize: (JagexAccount) -> Unit,
    onSelect: (AccountSelection) -> Unit,
)
{
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var jagexAccounts by remember { mutableStateOf<List<JagexAccount>>(emptyList()) }
    var imported by remember { mutableStateOf<List<AccountStore.Account>>(emptyList()) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }

    fun refreshList()
    {
        jagexAccounts = JagexAccountStore.list(context)
        imported = AccountStore.list(context)
        // With a single Jagex account there's nothing to choose between, so open it.
        if (expanded == null && jagexAccounts.size == 1) expanded = jagexAccounts.first().sub
    }

    // Keyed on reloadKey so returning from the sign-in Activity re-reads the store.
    LaunchedEffect(reloadKey) { refreshList() }

    // SAF document picker. Returns a content:// URI; we read the bytes ourselves so we
    // can validate the file *before* it lands in our app-private dir.
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Importing…"
            val displayHint = displayNameFromUri(context, uri)
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null)
            {
                status = "Couldn't read the selected file."
                return@launch
            }
            val newId = withContext(Dispatchers.IO) {
                AccountStore.import(context, displayHint, bytes)
            }
            if (newId == null)
            {
                status = "That file doesn't look like a Jagex credentials.properties (no JX_* keys)."
                return@launch
            }
            refreshList()
            busyId = newId
            status = "Imported. Detecting account type…"
            withContext(Dispatchers.IO) { AccountStore.refreshFromHiscores(context, newId) }
            refreshList()
            busyId = null
            status = null
        }
    }

    /** Renew the session before handing the account off — a stale JX_SESSION_ID is the
     *  usual reason a launch that worked yesterday lands back on the login screen. */
    fun launchCharacter(account: JagexAccount, character: JagexCharacter)
    {
        // Known-dead refresh token: launching can only fail, so go straight to the fix.
        if (account.needsReauth)
        {
            onReauthorize(account)
            return
        }
        busyId = account.sub
        status = "Preparing ${character.displayName}…"
        scope.launch {
            val prepared = withContext(Dispatchers.IO) {
                runCatching { JagexAccountStore.prepare(context, account) }
            }
            busyId = null
            val ready = prepared.getOrElse { t ->
                status = t.message ?: "Couldn't refresh this account. Sign in again."
                refreshList()
                return@launch
            }
            // prepare() re-lists characters; keep the tapped one if it survived.
            val target = ready.characters.firstOrNull { it.accountId == character.accountId } ?: character
            JagexAccountStore.setLastCharacterId(context, target.accountId)
            status = null
            onSelect(AccountSelection(credentials = JagexAccountStore.credentials(ready, target)))
        }
    }

    fun refreshAccount(account: JagexAccount)
    {
        busyId = account.sub
        status = "Refreshing ${account.displayName}…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { JagexAccountStore.prepare(context, account) }
            }
            busyId = null
            status = result.exceptionOrNull()?.let { it.message ?: "Refresh failed." }
            refreshList()
        }
    }

    fun signOut(account: JagexAccount)
    {
        scope.launch {
            withContext(Dispatchers.IO) {
                JagexAuth.revokeToken(account.accessToken)
                JagexAccountStore.delete(context, account)
            }
            if (expanded == account.sub) expanded = null
            refreshList()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDarker),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "RuneLite",
                color = BrandOrange,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Pick the character to launch with",
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(24.dp))

            if (jagexAccounts.isEmpty() && imported.isEmpty())
            {
                EmptyState()
                Spacer(Modifier.height(16.dp))
            }

            val lastCharacter = JagexAccountStore.lastCharacterId(context)
            for (account in jagexAccounts)
            {
                JagexAccountCard(
                    account = account,
                    expanded = expanded == account.sub,
                    busy = busyId == account.sub,
                    lastCharacterId = lastCharacter,
                    onToggle = { expanded = if (expanded == account.sub) null else account.sub },
                    onLaunch = { launchCharacter(account, it) },
                    onRefresh = { refreshAccount(account) },
                    onReauthorize = { onReauthorize(account) },
                    onSignOut = { signOut(account) },
                )
                Spacer(Modifier.height(10.dp))
            }

            PrimaryButton("+ Add Jagex account", onClick = onAddAccount)

            if (imported.isNotEmpty())
            {
                Spacer(Modifier.height(28.dp))
                SectionLabel("Imported credentials")
                Spacer(Modifier.height(8.dp))
                for (acc in imported)
                {
                    ImportedAccountCard(
                        account = acc,
                        busy = acc.id == busyId,
                        onLaunch = {
                            AccountStore.setActive(context, acc.id)
                            onSelect(AccountSelection(credentialsFile = acc.file))
                        },
                        onRefresh = {
                            busyId = acc.id
                            status = "Refreshing ${acc.displayName} from hiscores…"
                            scope.launch {
                                withContext(Dispatchers.IO) { AccountStore.refreshFromHiscores(context, acc.id) }
                                refreshList()
                                busyId = null
                                status = null
                            }
                        },
                        onDelete = {
                            AccountStore.delete(context, acc.id)
                            refreshList()
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlineButton("Import credentials.properties") {
                importLauncher.launch(arrayOf("text/plain", "*/*"))
            }
            if (status != null)
            {
                Spacer(Modifier.height(12.dp))
                Text(status!!, color = TextSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Accounts and tokens are stored in this app's private storage and are only " +
                    "ever sent back to Jagex.",
                color = TextDisabled,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun EmptyState()
{
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "No accounts yet. Sign in with your Jagex account to pull in its characters, " +
                "or import a credentials.properties from a desktop launcher.",
            color = TextSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SectionLabel(text: String)
{
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = TextDisabled, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(SurfaceBorder))
    }
}

@Composable
private fun JagexAccountCard(
    account: JagexAccount,
    expanded: Boolean,
    busy: Boolean,
    lastCharacterId: String?,
    onToggle: () -> Unit,
    onLaunch: (JagexCharacter) -> Unit,
    onRefresh: () -> Unit,
    onReauthorize: () -> Unit,
    onSignOut: () -> Unit,
)
{
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        account.displayName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when (account.characters.size)
                        {
                            0 -> "No characters found"
                            1 -> "1 character"
                            else -> "${account.characters.size} characters"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    if (busy) "…" else if (expanded) "▾" else "▸",
                    color = TextSecondary,
                    fontSize = 16.sp,
                )
            }

            // Shown collapsed too: this is the one state where nothing on the card works.
            if (account.needsReauth)
            {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF3A1F1F))
                        .padding(10.dp),
                ) {
                    Text(JagexAuth.REAUTH_MESSAGE, color = Color(0xFFF2A0A0), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "You'll be asked to sign in to this Jagex account again.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(BrandOrange)
                            .clickable(enabled = !busy, onClick = onReauthorize)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("Re-authorize", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (expanded)
            {
                Spacer(Modifier.height(10.dp))
                if (account.characters.isEmpty())
                {
                    Text(
                        "No characters on this account yet. Refresh once you've created one.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                }
                for (character in account.characters)
                {
                    CharacterRow(
                        character = character,
                        highlight = character.accountId == lastCharacterId,
                        enabled = !busy,
                        onLaunch = { onLaunch(character) },
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (account.lastRefreshedEpochMs <= 0) "Never refreshed"
                        else "Refreshed ${relativeTime(account.lastRefreshedEpochMs)}",
                        color = TextDisabled,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f),
                    )
                    MiniBtn("Refresh", onRefresh)
                    Spacer(Modifier.size(6.dp))
                    MiniBtn("Sign out", onSignOut)
                }
            }
        }
    }
}

@Composable
private fun CharacterRow(
    character: JagexCharacter,
    highlight: Boolean,
    enabled: Boolean,
    onLaunch: () -> Unit,
)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (highlight) MediumGray else Color(0xFF171717))
            .clickable(enabled = enabled, onClick = onLaunch)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            character.displayName,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (highlight)
        {
            Text("last used", color = TextDisabled, fontSize = 10.sp)
            Spacer(Modifier.size(8.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(BrandOrange)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text("Launch", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ImportedAccountCard(
    account: AccountStore.Account,
    busy: Boolean,
    onLaunch: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
)
{
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        account.displayName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountTypePill(account.accountType)
                        if (account.totalLevel > 0)
                        {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Total ${"%,d".format(account.totalLevel)}",
                                color = TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                        if (account.rank > 0)
                        {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Rank #${"%,d".format(account.rank)}",
                                color = TextSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BrandOrange)
                        .clickable(enabled = !busy, onClick = onLaunch)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        if (busy) "…" else "Launch",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val refreshed = when
            {
                account.lastRefreshedEpochMs <= 0 -> "Hiscores not yet checked"
                else -> "Refreshed ${relativeTime(account.lastRefreshedEpochMs)}"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(refreshed, color = TextDisabled, fontSize = 10.sp, modifier = Modifier.weight(1f))
                MiniBtn("Refresh", onRefresh)
                Spacer(Modifier.size(6.dp))
                MiniBtn("Remove", onDelete)
            }
        }
    }
}

@Composable
private fun AccountTypePill(type: AccountStore.AccountType)
{
    val (bg, fg) = when (type)
    {
        AccountStore.AccountType.NORMAL -> Color(0xFF2F4F2F) to Color(0xFF8FE188)
        AccountStore.AccountType.IRONMAN -> Color(0xFF42424A) to Color(0xFFD0D0D0)
        AccountStore.AccountType.HARDCORE_IRONMAN -> Color(0xFF4A1F1F) to Color(0xFFE57373)
        AccountStore.AccountType.ULTIMATE_IRONMAN -> Color(0xFF3A2C44) to Color(0xFFC79DD9)
        AccountStore.AccountType.UNKNOWN -> Color(0xFF2A2A2A) to TextSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(type.label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniBtn(label: String, onClick: () -> Unit)
{
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MediumGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BrandOrange)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OutlineButton(label: String, onClick: () -> Unit)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}

private fun displayNameFromUri(context: Context, uri: Uri): String?
{
    return try
    {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }
    catch (t: Throwable) { null }
}

/** "5m ago", "3h ago", "yesterday", "Apr 14" — a tiny relative-time formatter so cards
 *  don't show the same epoch number that was generated a tick after import. */
private fun relativeTime(epochMs: Long): String
{
    val delta = (System.currentTimeMillis() - epochMs).coerceAtLeast(0)
    val seconds = delta / 1000
    return when
    {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        seconds < 604800 -> "${seconds / 86400}d ago"
        else ->
        {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
            java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(cal.time)
        }
    }
}
