package net.runelite.mp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.runelite.mp.plugins.InstallPhase
import net.runelite.mp.plugins.InstallStatus
import net.runelite.mp.plugins.PluginInstallTracker
import net.runelite.mp.ui.bridge.ExternalPluginEntry

// --------------------------------------------------------------------------------------
// Plugin Hub install flow: confirm → download → convert for Android → load/start, with
// the outcome on the row, in the activity card above the list, and (for installs the
// user started) as a toast. State comes from PluginInstallTracker.
// --------------------------------------------------------------------------------------

/** One-line status under a hub row while it installs, or why it failed. */
@Composable
internal fun InstallStatusLine(status: InstallStatus, onRetry: () -> Unit, onDetails: () -> Unit)
{
    when (val phase = status.phase)
    {
        is InstallPhase.Downloading -> PhaseProgress(
            if (phase.fraction == null) "Waiting to download…" else "Downloading… ${(phase.fraction * 100).toInt()}%",
            phase.fraction,
        )
        InstallPhase.Converting -> PhaseProgress("Converting for Android…", null)
        InstallPhase.Loading -> PhaseProgress("Starting…", null)
        is InstallPhase.Ready -> if (status.fresh)
        {
            Text("✓ Installed", color = RlPalette.OkGreen, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
        is InstallPhase.Failed -> Column {
            Text(
                "✕ ${phase.message}",
                color = RlPalette.DangerRed,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 2.dp)) {
                SmallAction("Retry", onRetry)
                Spacer(Modifier.width(6.dp))
                SmallAction("Details", onDetails)
            }
        }
    }
}

/**
 * Summary above the hub list: everything installing right now, what finished this session,
 * and anything that failed (including installs from before the hub tab was opened, e.g. a
 * plugin that wouldn't convert at boot). Hidden when there's nothing to say.
 */
@Composable
internal fun PluginActivityCard(onRetry: (InstallStatus) -> Unit, onDetails: (InstallStatus) -> Unit)
{
    val shown = PluginInstallTracker.statuses.values
        .filter { it.fresh || it.phase is InstallPhase.Failed }
        .sortedWith(compareBy<InstallStatus> { rank(it.phase) }.thenByDescending { it.at })
    val hubErrors = PluginInstallTracker.hubErrors
    if (shown.isEmpty() && hubErrors.isEmpty()) return

    val active = shown.count { rank(it.phase) == 0 }
    val failed = shown.count { it.phase is InstallPhase.Failed }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(RlPalette.Surface)
            .border(1.dp, RlPalette.SurfaceBorder, RoundedCornerShape(6.dp))
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when
                {
                    active > 0 -> "Installing $active plugin${if (active == 1) "" else "s"}…"
                    failed > 0 -> "$failed plugin${if (failed == 1) "" else "s"} couldn't be installed"
                    else -> "Plugin Hub"
                },
                color = RlPalette.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            // Finished installs clear themselves after a few seconds; this is for failures.
            if (failed > 0 || hubErrors.isNotEmpty())
            {
                SmallAction("Clear") {
                    shown.filter { it.phase is InstallPhase.Failed }.forEach { PluginInstallTracker.dismiss(it.internalName) }
                    PluginInstallTracker.clearHubErrors()
                }
            }
        }
        for (error in hubErrors)
        {
            Text("✕ $error", color = RlPalette.DangerRed, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
            for (status in shown)
            {
                Column(Modifier.padding(top = 6.dp)) {
                    Text(
                        status.displayName,
                        color = RlPalette.TextPrimary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    InstallStatusLine(status, { onRetry(status) }, { onDetails(status) })
                }
            }
        }
    }
}

/** Confirmation before installing: what happens, and the hub's warning if it has one. */
@Composable
internal fun InstallConfirmDialog(row: ExternalPluginEntry, onConfirm: () -> Unit, onDismiss: () -> Unit)
{
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Install ${row.displayName}?") },
        text = {
            Column {
                Text(
                    "by ${row.author.ifEmpty { "unknown" }}" + if (row.version.isNotEmpty()) " · v${row.version}" else "",
                    color = RlPalette.TextSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Downloads the plugin from the RuneLite Plugin Hub and converts it for Android. " +
                        "Converting happens once per plugin version and can take a few seconds for large plugins.",
                    fontSize = 13.sp,
                )
                row.warning?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = RlPalette.Accent, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Plugin Hub plugins are made by the community, not RuneLite. Install only plugins you trust.",
                    color = RlPalette.TextSecondary,
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Install") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Why an install failed, with the full trace to copy into a bug report. */
@Composable
internal fun InstallFailureDialog(status: InstallStatus, onRetry: () -> Unit, onDismiss: () -> Unit)
{
    val failed = status.phase as? InstallPhase.Failed ?: return onDismiss()
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${status.displayName} — ${stageLabel(failed)}") },
        text = {
            Column {
                Text(failed.message, fontSize = 13.sp)
                if (failed.detail.isNotEmpty())
                {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(RlPalette.DarkerGray)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(6.dp),
                    ) {
                        Text(failed.detail, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = RlPalette.TextSecondary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onRetry() }) { Text("Retry") } },
        dismissButton = {
            Row {
                if (failed.detail.isNotEmpty())
                {
                    TextButton(onClick = { copy(context, "${status.displayName} (${status.internalName}): ${failed.message}\n\n${failed.detail}") }) {
                        Text("Copy")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

private fun stageLabel(failed: InstallPhase.Failed) = when (failed.stage)
{
    net.runelite.client.externalplugins.PluginHubHooks.Stage.MANIFEST -> "hub unavailable"
    net.runelite.client.externalplugins.PluginHubHooks.Stage.DOWNLOAD,
    net.runelite.client.externalplugins.PluginHubHooks.Stage.VERIFY -> "download failed"
    net.runelite.client.externalplugins.PluginHubHooks.Stage.DEX -> "conversion failed"
    net.runelite.client.externalplugins.PluginHubHooks.Stage.LOAD -> "load failed"
    net.runelite.client.externalplugins.PluginHubHooks.Stage.START -> "start failed"
}

/** In-flight first, then failures, then successes. */
private fun rank(phase: InstallPhase) = when (phase)
{
    is InstallPhase.Downloading, InstallPhase.Converting, InstallPhase.Loading -> 0
    is InstallPhase.Failed -> 1
    is InstallPhase.Ready -> 2
}

@Composable
private fun PhaseProgress(label: String, fraction: Float?)
{
    Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(label, color = RlPalette.TextSecondary, fontSize = 10.sp)
        Spacer(Modifier.height(3.dp))
        ProgressBar(fraction)
    }
}

/** Thin bar: determinate when [fraction] is known, otherwise a sliding segment. */
@Composable
private fun ProgressBar(fraction: Float?)
{
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(RlPalette.MediumGray),
    ) {
        if (fraction != null)
        {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).background(RlPalette.Accent))
        }
        else
        {
            val sweep by rememberInfiniteTransition(label = "install-progress").animateFloat(
                initialValue = -0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
                label = "install-progress-sweep",
            )
            Box(
                Modifier
                    .offset(x = maxWidth * sweep)
                    .width(maxWidth * 0.35f)
                    .height(3.dp)
                    .background(RlPalette.Accent),
            )
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit)
{
    Box(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(RlPalette.MediumGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, color = Color.White, fontSize = 10.sp)
    }
}

private fun copy(context: Context, text: String)
{
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Plugin install error", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
