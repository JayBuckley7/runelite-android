package net.runelite.mp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.runelite.mp.ui.bridge.ConfigForm
import net.runelite.mp.ui.bridge.PluginBridge
import net.runelite.mp.ui.bridge.PluginRow
import net.runelite.mp.wear.WatchLink
import net.runelite.mp.ui.widgets.IntSliderRow
import net.runelite.mp.ui.widgets.SectionHeader
import net.runelite.mp.ui.widgets.SegmentedRow
import net.runelite.mp.ui.widgets.TextRow
import net.runelite.mp.ui.widgets.ToggleRow

/**
 * Renders a single plugin's config interface as a Compose form. Each `@ConfigItem`-annotated
 * method on the interface produces one row; type drives the widget choice (boolean → toggle,
 * int with [Range] → slider, enum → dropdown, String → text field, etc.).
 *
 * Form state lives in a `mutableStateMapOf<key, value>` cache so the slider thumb feels
 * responsive (the underlying onSet write happens on every change but Compose
 * recomposition reads from the cache first). The bridge persists via ConfigManager and
 * RuneLite's existing change-event plumbing notifies the plugin.
 */
@Composable
internal fun PluginConfigPanel(
    pluginName: String,
    form: ConfigForm?,
    loading: Boolean,
    emptyMessage: String,
    onSet: (group: String, key: String, value: String?) -> Unit,
    onBack: () -> Unit,
)
{
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RlPalette.DarkGray),
    ) {
        // Header: back chip, plugin name, room on the right for actions later.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(RlPalette.DarkerGray)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(RlPalette.MediumGray)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(10.dp))
            Text(pluginName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        if (form == null)
        {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (loading) "Loading…" else emptyMessage,
                    color = RlPalette.TextSecondary,
                    fontSize = 13.sp,
                )
            }
            return@Column
        }

        // Materialise the values once into a state map keyed by item key. Edits write
        // through onSet AND update the map in the same gesture, so a slider drag doesn't
        // fight a delayed read on every frame.
        val cache = remember(form) { mutableStateMapOf<String, String?>().apply { putAll(form.values) } }
        val collapsed = remember(form) { mutableStateMapOf<String, Boolean>() }
        val set: (String, String?) -> Unit = { key, value ->
            cache[key] = value
            onSet(form.group, key, value)
        }

        // Rows own their padding so section backgrounds span the panel width.
        LazyColumn(modifier = Modifier.fillMaxSize())
        {
            // Section-less items first (RuneLite renders them above any sections too).
            val orphans = form.fields.filter { it.section.isEmpty() }
            if (orphans.isNotEmpty()) items(orphans, key = { it.key }) { ConfigRow(it, cache[it.key], set) }
            // Then one section header per section, with its items beneath.
            for (section in form.sections)
            {
                val secItems = form.fields.filter { it.section == section.key }
                if (secItems.isEmpty()) continue
                val isClosed = collapsed[section.key] ?: section.closedByDefault
                item(key = "sec:${section.key}") {
                    SectionHeader(section.name, !isClosed) { collapsed[section.key] = !isClosed }
                }
                if (!isClosed) {
                    items(secItems, key = { "${section.key}/${it.key}" }) {
                        ConfigRow(it, cache[it.key], set)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ConfigRow(field: ConfigForm.Field, current: String?, set: (String, String?) -> Unit) {
    var pending by remember { mutableStateOf<Pair<String, String?>?>(null) }
    Column {
        ConfigControl(field, current) { key, value ->
            if (field.warning.isNotBlank()) pending = field.warning to value else set(key, value)
        }
        if (field.hasDefault) androidx.compose.material3.TextButton(onClick = {
            pending = ("Reset ${field.name} to its default?" + if (field.warning.isBlank()) "" else "\n${field.warning}") to field.defaultValue
        }) { Text("Reset to default") }
    }
    pending?.let { change ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { pending = null },
            title = { Text(field.name) }, text = { Text(change.first) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { set(field.key, change.second); pending = null }) { Text("Confirm") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pending = null }) { Text("Cancel") } })
    }
}

/** Choose the right widget for a single [ConfigForm.Field] and bind it to [set]. */
@Composable
private fun ConfigControl(
    field: ConfigForm.Field,
    current: String?,
    set: (key: String, value: String?) -> Unit,
)
{
    val label = field.name
    val desc = field.description
    when (field.kind)
    {
        ConfigForm.Kind.BOOL ->
        {
            ToggleRow(label, desc, current == "true") { set(field.key, it.toString()) }
        }
        ConfigForm.Kind.INT ->
        {
            val v = current?.toIntOrNull() ?: 0
            if (field.min != null && field.max != null)
            {
                IntSliderRow(
                    label = label,
                    description = desc,
                    value = v.coerceIn(field.min, field.max),
                    min = field.min,
                    max = field.max,
                    unit = field.units,
                ) { set(field.key, it.toString()) }
            }
            else
            {
                // No range — render as text field. Int parsing happens at write time.
                TextRow(label, desc, v.toString()) { txt ->
                    val parsed = txt.toIntOrNull()
                    if (parsed != null) set(field.key, parsed.toString())
                }
            }
        }
        ConfigForm.Kind.STRING ->
        {
            if (field.warning.isNotBlank()) net.runelite.mp.ui.widgets.ExtendedSettingRow(field, current) { set(field.key, it) }
            else TextRow(label, desc, current ?: "", secret = field.secret) { set(field.key, it) }
        }
        ConfigForm.Kind.ENUM ->
        {
            val selected = field.options.indexOf(current).coerceAtLeast(0)
            SegmentedRow(label, desc, field.optionLabels, selected) { idx ->
                val pick = field.options.getOrNull(idx) ?: return@SegmentedRow
                set(field.key, pick)
            }
        }
        ConfigForm.Kind.COLOR ->
        {
            net.runelite.mp.ui.widgets.ColorSettingRow(label, desc, current, field.alpha) {
                set(field.key, it)
            }
        }
        ConfigForm.Kind.DOUBLE, ConfigForm.Kind.DIMENSION, ConfigForm.Kind.MULTI,
        ConfigForm.Kind.NOTIFICATION, ConfigForm.Kind.FONT ->
            net.runelite.mp.ui.widgets.ExtendedSettingRow(field, current) { set(field.key, it) }
        ConfigForm.Kind.OTHER ->
        {
            // Fallback: show as disabled text row so the user at least sees the key.
            // Instant and other unsupported types land here for now.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Column {
                    Text(label, color = RlPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Type ${field.typeName} not yet editable in the Compose UI.",
                        color = RlPalette.TextDisabled,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

/**
 * Loads `row`'s form from wherever the row lives — this device, or the watch being
 * configured — and writes edits back to the same place. Watch writes are debounced per
 * key so a slider drag sends its resting value rather than every step.
 */
@Composable
internal fun PluginConfigPanelFor(row: PluginRow, onBack: () -> Unit)
{
    val remote = row.remoteKey != null
    var form by remember(row.rowKey) { mutableStateOf<ConfigForm?>(null) }
    var loading by remember(row.rowKey) { mutableStateOf(true) }
    var failed by remember(row.rowKey) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pending = remember(row.rowKey) { HashMap<String, Job>() }

    LaunchedEffect(row.rowKey)
    {
        form = withContext(Dispatchers.IO) {
            try
            {
                if (remote) WatchLink.configForm(row) else PluginBridge.configForm(row)
            }
            catch (t: Throwable)
            {
                android.util.Log.w("ConfigPanel", "config form load failed", t)
                failed = true
                null
            }
        }
        loading = false
    }

    PluginConfigPanel(
        pluginName = row.displayName,
        form = form,
        loading = loading,
        emptyMessage = if (failed) "Couldn't reach the watch." else "This plugin has no configurable settings.",
        onSet = { group, key, value ->
            if (!remote) PluginBridge.setConfig(group, key, value)
            else
            {
                pending.remove(key)?.cancel()
                pending[key] = scope.launch(Dispatchers.IO) {
                    delay(150)
                    try { WatchLink.setConfig(group, key, value) }
                    catch (t: Throwable) { android.util.Log.w("ConfigPanel", "watch config write failed", t) }
                }
            }
        },
        onBack = onBack,
    )
}
