package net.runelite.mp.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import net.runelite.client.config.ConfigUiCodec
import net.runelite.client.config.Keybind
import net.runelite.client.config.ModifierlessKeybind
import net.runelite.mp.ui.RlPalette
import net.runelite.mp.ui.bridge.ConfigForm

@Composable
internal fun KeybindSettingRow(field: ConfigForm.Field, stored: String?, save: (String) -> Unit) {
    val modifierless = field.kind == ConfigForm.Kind.MODIFIERLESS_KEYBIND
    val value = remember(stored, modifierless) {
        runCatching {
            ConfigUiCodec.decode(stored ?: "0:0", if (modifierless) ModifierlessKeybind::class.java else Keybind::class.java) as Keybind
        }.getOrDefault(Keybind.NOT_SET)
    }
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = true }.padding(12.dp)) {
        Text(field.name, color = RlPalette.TextPrimary)
        field.description?.let { Text(it, color = RlPalette.TextSecondary) }
        Text(value.toString(), color = RlPalette.Accent)
    }
    if (!open) return
    var code by remember { mutableIntStateOf(value.keyCode) }
    var modifiers by remember { mutableIntStateOf(value.modifiers) }
    var choosing by remember { mutableStateOf(false) }
    val keys = remember(value.keyCode) {
        (listOf(KeyEvent.VK_UNDEFINED) + (KeyEvent.VK_A..KeyEvent.VK_Z) +
            (KeyEvent.VK_0..KeyEvent.VK_9) + (KeyEvent.VK_F1..KeyEvent.VK_F12) +
            (KeyEvent.VK_F13..KeyEvent.VK_F24) + listOf(
                KeyEvent.VK_SPACE, KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE, KeyEvent.VK_TAB,
                KeyEvent.VK_BACK_SPACE, KeyEvent.VK_DELETE, KeyEvent.VK_INSERT,
                KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
                KeyEvent.VK_HOME, KeyEvent.VK_END, KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN,
                KeyEvent.VK_MINUS, KeyEvent.VK_EQUALS, KeyEvent.VK_OPEN_BRACKET, KeyEvent.VK_CLOSE_BRACKET,
                KeyEvent.VK_BACK_SLASH, KeyEvent.VK_SEMICOLON, KeyEvent.VK_QUOTE, KeyEvent.VK_BACK_QUOTE,
                KeyEvent.VK_COMMA, KeyEvent.VK_PERIOD, KeyEvent.VK_SLASH,
                KeyEvent.VK_NUMPAD0, KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD2, KeyEvent.VK_NUMPAD3,
                KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD5, KeyEvent.VK_NUMPAD6, KeyEvent.VK_NUMPAD7,
                KeyEvent.VK_NUMPAD8, KeyEvent.VK_NUMPAD9, KeyEvent.VK_ADD, KeyEvent.VK_SUBTRACT,
                KeyEvent.VK_MULTIPLY, KeyEvent.VK_DIVIDE, KeyEvent.VK_DECIMAL,
            ) + value.keyCode).distinct()
    }
    fun label(key: Int) = if (key == KeyEvent.VK_UNDEFINED) "None" else KeyEvent.getKeyText(key)
    AlertDialog(onDismissRequest = { open = false }, title = { Text(field.name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box {
                OutlinedButton(onClick = { choosing = true }) { Text("Key: ${label(code)}") }
                DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }, modifier = Modifier.heightIn(max = 280.dp)) {
                    keys.forEach { key ->
                        DropdownMenuItem(text = { Text(label(key)) }, onClick = {
                            code = key
                            if (modifierless && code != KeyEvent.VK_UNDEFINED) modifiers = 0
                            choosing = false
                        })
                    }
                }
            }
            if (!modifierless || code == KeyEvent.VK_UNDEFINED) {
                listOf("Control" to InputEvent.CTRL_DOWN_MASK, "Shift" to InputEvent.SHIFT_DOWN_MASK,
                    "Alt" to InputEvent.ALT_DOWN_MASK, "Meta" to InputEvent.META_DOWN_MASK).forEach { (name, mask) ->
                    val checked = modifiers and mask != 0
                    Row(Modifier.fillMaxWidth().clickable { modifiers = modifiers xor mask }) {
                        Checkbox(checked, { modifiers = if (it) modifiers or mask else modifiers and mask.inv() })
                        Text(name, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            } else Text("This binding ignores modifiers.")
            TextButton(onClick = { code = KeyEvent.VK_UNDEFINED; modifiers = 0 }) { Text("Clear binding") }
        }
    }, confirmButton = {
        TextButton(onClick = {
            val next = if (modifierless) ModifierlessKeybind(code, modifiers) else Keybind(code, modifiers)
            save(ConfigUiCodec.encode(next))
            open = false
        }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
}
