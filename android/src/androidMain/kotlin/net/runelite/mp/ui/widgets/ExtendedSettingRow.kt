package net.runelite.mp.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.runelite.client.config.*
import net.runelite.mp.ui.RlPalette
import net.runelite.mp.ui.bridge.ConfigForm
import org.json.JSONArray

private fun <T> read(raw: String?, type: Class<T>, fallback: T): T =
    runCatching { type.cast(ConfigUiCodec.decode(raw ?: "", type)) ?: fallback }.getOrDefault(fallback)

@Composable
internal fun ExtendedSettingRow(field: ConfigForm.Field, stored: String?, save: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = true }.padding(12.dp)) {
        Text(field.name, color = RlPalette.TextPrimary)
        field.description?.let { Text(it, color = RlPalette.TextSecondary) }
        Text("Edit", color = RlPalette.Accent)
    }
    if (!open) return
    var number by remember { mutableStateOf(stored.orEmpty()) }
    val dimensions = stored.orEmpty().split('x')
    var width by remember { mutableStateOf(dimensions.getOrNull(0).orEmpty()) }
    var height by remember { mutableStateOf(dimensions.getOrNull(1).orEmpty()) }
    var selected by remember { mutableStateOf(runCatching {
        val array = JSONArray(stored ?: "[]")
        (0 until array.length()).map { array.getString(it) }.toSet()
    }.getOrDefault(emptySet())) }
    var font by remember { mutableStateOf(read(stored, FontType::class.java, FontType.REGULAR)) }
    var fontSize by remember { mutableStateOf(font.size.toString()) }
    var notification by remember { mutableStateOf(read(stored, Notification::class.java, Notification.OFF)) }
    var timeout by remember { mutableStateOf(notification.timeout.toString()) }
    val decimal = number.toDoubleOrNull()
    val w = width.toIntOrNull(); val h = height.toIntOrNull(); val size = fontSize.toIntOrNull()
    val valid = when (field.kind) {
        ConfigForm.Kind.DOUBLE -> decimal != null && decimal.isFinite() && decimal >= (field.min ?: 0) && (field.max == null || decimal <= field.max)
        ConfigForm.Kind.DIMENSION -> w != null && h != null && w >= 0 && h >= 0
        ConfigForm.Kind.FONT -> size != null && size in 1..512 && font.family.isNotBlank()
        ConfigForm.Kind.NOTIFICATION -> !notification.isOverride || (timeout.toIntOrNull()?.let { it >= 0 } == true)
        else -> true
    }
    AlertDialog(onDismissRequest = { open = false }, title = { Text(field.name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when (field.kind) {
                ConfigForm.Kind.STRING -> OutlinedTextField(number, { number = it }, singleLine = true,
                    visualTransformation = if (field.secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (field.secret) androidx.compose.ui.text.input.KeyboardType.Password else androidx.compose.ui.text.input.KeyboardType.Text))
                ConfigForm.Kind.DOUBLE -> OutlinedTextField(number, { number = it }, singleLine = true,
                    label = { Text(field.units ?: "Value") }, isError = !valid)
                ConfigForm.Kind.DIMENSION -> {
                    OutlinedTextField(width, { width = it }, label = { Text("Width") }, singleLine = true, isError = w == null || w < 0)
                    OutlinedTextField(height, { height = it }, label = { Text("Height") }, singleLine = true, isError = h == null || h < 0)
                }
                ConfigForm.Kind.MULTI -> field.options.forEachIndexed { index, option ->
                    Check(field.optionLabels.getOrElse(index) { option }, option in selected) {
                        selected = if (it) selected + option else selected - option
                    }
                }
                ConfigForm.Kind.FONT -> {
                    Choice("Font family", (field.options + font.family).distinct(), font.family) { font = font.withFamily(it) }
                    OutlinedTextField(fontSize, { fontSize = it }, label = { Text("Size (1–512)") }, singleLine = true, isError = !valid)
                    Check("Bold", font.isBold) { font = font.withBold(it) }
                    Check("Italic", font.isItalic) { font = font.withItalic(it) }
                }
                ConfigForm.Kind.NOTIFICATION -> {
                    Check("Enabled", notification.isEnabled) { notification = notification.withEnabled(it) }
                    Check("Use custom settings", notification.isOverride) { enabled ->
                        notification = if (enabled && !notification.isInitialized)
                            read(field.notificationDefaults, Notification::class.java, Notification.ON)
                                .withEnabled(notification.isEnabled).withOverride(true)
                        else notification.withOverride(enabled)
                        timeout = notification.timeout.toString()
                    }
                    if (notification.isOverride) {
                        Check("System notification", notification.isTray) { notification = notification.withTray(it) }
                        EnumChoice("Request focus", RequestFocusType.values(), notification.requestFocus) { notification = notification.withRequestFocus(it) }
                        EnumChoice("Sound", NotificationSound.values(), notification.sound) { notification = notification.withSound(it) }
                        if (notification.sound == NotificationSound.CUSTOM) OutlinedTextField(notification.soundName.orEmpty(), {
                            notification = notification.withSoundName(it.ifBlank { null })
                        }, label = { Text("Custom sound filename (optional)") }, singleLine = true)
                        Text("Volume: ${notification.volume}%")
                        Slider(notification.volume.toFloat(), { notification = notification.withVolume(it.toInt()) }, valueRange = 0f..100f)
                        OutlinedTextField(timeout, { timeout = it }, label = { Text("Timeout (ms, 0 = system default)") }, singleLine = true,
                            isError = timeout.toIntOrNull()?.let { it < 0 } ?: true)
                        Check("Chat message", notification.isGameMessage) { notification = notification.withGameMessage(it) }
                        EnumChoice("Flash", FlashNotification.values(), notification.flash) { notification = notification.withFlash(it) }
                        ColorSettingRow("Flash color", null, (notification.flashColor?.rgb ?: -65536).toString(), true) {
                            notification = notification.withFlashColor(java.awt.Color(it.toInt(), true))
                        }
                        Check("Send while focused", notification.isSendWhenFocused) { notification = notification.withSendWhenFocused(it) }
                    } else Text("Uses your global RuneLite notification settings.")
                }
                else -> Unit
            }
            if (!valid) Text("Enter a valid value within the allowed range.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = {
        val result = when (field.kind) {
            ConfigForm.Kind.STRING -> number
            ConfigForm.Kind.DOUBLE -> decimal.toString()
            ConfigForm.Kind.DIMENSION -> "${w}x${h}"
            ConfigForm.Kind.MULTI -> JSONArray(selected.toList()).toString()
            ConfigForm.Kind.FONT -> ConfigUiCodec.encode(font.withSize(size!!))
            ConfigForm.Kind.NOTIFICATION -> ConfigUiCodec.encode(if (notification.isOverride) notification.withTimeout(timeout.toInt()) else notification)
            else -> stored.orEmpty()
        }
        save(result); open = false
    }) { Text("Apply") } }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
}

@Composable
private fun Check(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { change(!checked) }) {
        Checkbox(checked, change)
        Text(label, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Choice(label: String, options: List<String>, selected: String, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("$label: $selected") }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { change(option); expanded = false }) }
        }
    }
}

@Composable
private fun <T : Enum<T>> EnumChoice(label: String, options: Array<T>, selected: T?, change: (T) -> Unit) {
    Choice(label, options.map { it.toString() }, selected?.toString() ?: "Select") { name ->
        options.firstOrNull { it.toString() == name }?.let(change)
    }
}
