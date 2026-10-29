package net.runelite.mp.ui.bridge

import net.runelite.client.config.ConfigItemDescriptor
import org.json.JSONArray
import org.json.JSONObject

/**
 * A plugin's config form as plain data: what [net.runelite.mp.ui.PluginConfigPanel]
 * renders. Built from a live [ConfigBridge] for this device, or received as JSON from a
 * watch being configured from the phone (see [net.runelite.mp.wear.WatchLink]) — the
 * watch may run hub plugins the phone doesn't have, so the phone can't rebuild the form
 * from its own classes.
 *
 * Values are the strings ConfigManager stores: "true"/"false", decimal ints, the enum
 * constant's name, or the text itself. A missing value means the item is unset and
 * [values] already holds the interface default.
 */
internal data class ConfigForm(
    val group: String,
    val sections: List<Section>,
    val fields: List<Field>,
    val values: Map<String, String?>,
)
{
    enum class Kind { BOOL, INT, STRING, ENUM, COLOR, DOUBLE, DIMENSION, MULTI, NOTIFICATION, FONT, KEYBIND, MODIFIERLESS_KEYBIND, OTHER }

    data class Section(val key: String, val name: String, val closedByDefault: Boolean)

    data class Field(
        val key: String,
        val name: String,
        val description: String?,
        val section: String,
        val kind: Kind,
        val min: Int? = null,
        val max: Int? = null,
        val units: String? = null,
        /** ENUM only: constant names (stored values) and their display labels. */
        val options: List<String> = emptyList(),
        val optionLabels: List<String> = emptyList(),
        /** OTHER only: the type, so the panel can say what it can't edit. */
        val typeName: String = "",
        val alpha: Boolean = false,
        val secret: Boolean = false,
        val warning: String = "",
        val hasDefault: Boolean = false,
        val defaultValue: String? = null,
        val notificationDefaults: String? = null,
    )

    fun toJson(): JSONObject = JSONObject()
        .put("group", group)
        .put("sections", JSONArray().apply {
            for (s in sections) put(JSONObject().put("key", s.key).put("name", s.name).put("closed", s.closedByDefault))
        })
        .put("fields", JSONArray().apply {
            for (f in fields) put(JSONObject()
                .put("key", f.key)
                .put("name", f.name)
                .put("desc", f.description)
                .put("section", f.section)
                .put("kind", f.kind.name)
                .put("min", f.min)
                .put("max", f.max)
                .put("units", f.units)
                .put("options", JSONArray(f.options))
                .put("labels", JSONArray(f.optionLabels))
                .put("type", f.typeName)
                .put("alpha", f.alpha)
                .put("secret", f.secret).put("warning", f.warning)
                .put("hasDefault", f.hasDefault).put("defaultValue", f.defaultValue)
                .put("notificationDefaults", f.notificationDefaults)
                .put("value", values[f.key]))
        })

    companion object
    {
        fun fromJson(json: JSONObject): ConfigForm
        {
            val sections = json.getJSONArray("sections").objects().map {
                Section(it.getString("key"), it.getString("name"), it.optBoolean("closed"))
            }
            val values = HashMap<String, String?>()
            val fields = json.getJSONArray("fields").objects().map {
                val key = it.getString("key")
                values[key] = it.optStringOrNull("value")
                Field(
                    key = key,
                    name = it.getString("name"),
                    description = it.optStringOrNull("desc"),
                    section = it.optString("section"),
                    kind = runCatching { Kind.valueOf(it.getString("kind")) }.getOrDefault(Kind.OTHER),
                    min = if (it.has("min")) it.getInt("min") else null,
                    max = if (it.has("max")) it.getInt("max") else null,
                    units = it.optStringOrNull("units"),
                    options = it.optJSONArray("options")?.strings().orEmpty(),
                    optionLabels = it.optJSONArray("labels")?.strings().orEmpty(),
                    typeName = it.optString("type"),
                    alpha = it.optBoolean("alpha", false),
                    secret = it.optBoolean("secret", false), warning = it.optString("warning", ""),
                    hasDefault = it.optBoolean("hasDefault", false), defaultValue = it.optStringOrNull("defaultValue"),
                    notificationDefaults = it.optStringOrNull("notificationDefaults"),
                )
            }
            return ConfigForm(json.getString("group"), sections, fields, values)
        }

        /** This device's form for [bridge], with current values resolved. */
        fun fromBridge(bridge: ConfigBridge): ConfigForm
        {
            val items = bridge.descriptor.items.filter { !it.item.hidden && it.name().isNotBlank() }
            val values = HashMap<String, String?>()
            val fields = items.map { item ->
                values[item.key()] = encode(bridge.getValue(item))
                val default = runCatching { encode(bridge.defaultValue(item)) }
                field(item).copy(hasDefault = default.isSuccess, defaultValue = default.getOrNull(),
                    notificationDefaults = if (item.type == net.runelite.client.config.Notification::class.java)
                        encode(net.runelite.client.config.ConfigUiCodec.notificationDefaults()) else null)
            }
            val sections = bridge.descriptor.sections.map {
                Section(it.key(), it.name(), it.section.closedByDefault)
            }
            return ConfigForm(bridge.groupKey, sections, fields, values)
        }

        private fun field(item: ConfigItemDescriptor): Field
        {
            val clazz = item.type as? Class<*>
            val desc = item.item.description.takeIf { it.isNotBlank() }
            val base = Field(item.key(), item.name(), desc, item.item.section, Kind.OTHER, secret = item.item.secret, warning = item.item.warning, typeName = clazz?.simpleName ?: item.type.toString())
            val parameterized = item.type as? java.lang.reflect.ParameterizedType
            val element = parameterized?.actualTypeArguments?.firstOrNull() as? Class<*>
            return when
            {
                parameterized?.rawType == Set::class.java && element?.isEnum == true -> {
                    val constants = element.enumConstants.orEmpty().map { it as Enum<*> }
                    base.copy(kind = Kind.MULTI, options = constants.map { it.name }, optionLabels = constants.map { it.toString() })
                }
                clazz == Double::class.javaPrimitiveType || clazz == java.lang.Double::class.java ->
                    base.copy(kind = Kind.DOUBLE, min = item.range?.min ?: 0, max = item.range?.max, units = item.units?.value)
                clazz == java.awt.Dimension::class.java -> base.copy(kind = Kind.DIMENSION)
                clazz == net.runelite.client.config.Keybind::class.java -> base.copy(kind = Kind.KEYBIND)
                clazz == net.runelite.client.config.ModifierlessKeybind::class.java -> base.copy(kind = Kind.MODIFIERLESS_KEYBIND)
                clazz == net.runelite.client.config.Notification::class.java -> base.copy(kind = Kind.NOTIFICATION)
                clazz == net.runelite.client.config.FontType::class.java -> base.copy(kind = Kind.FONT,
                    options = (listOf(net.runelite.client.config.FontType.REGULAR.family, net.runelite.client.config.FontType.BOLD.family,
                        net.runelite.client.config.FontType.SMALL.family) + java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames).distinct())
                clazz == Boolean::class.javaPrimitiveType || clazz == java.lang.Boolean::class.java -> base.copy(kind = Kind.BOOL)
                clazz == Int::class.javaPrimitiveType || clazz == java.lang.Integer::class.java ->
                    base.copy(kind = Kind.INT, min = item.range?.min, max = item.range?.max, units = item.units?.value)
                clazz == String::class.java -> base.copy(kind = Kind.STRING)
                clazz == java.awt.Color::class.java -> base.copy(kind = Kind.COLOR, alpha = item.alpha != null)
                clazz != null && clazz.isEnum ->
                {
                    val constants = clazz.enumConstants.orEmpty().map { it as Enum<*> }
                    base.copy(
                        kind = Kind.ENUM,
                        options = constants.map { it.name },
                        optionLabels = constants.map { it.toString().replace('_', ' ') },
                    )
                }
                else -> base
            }
        }

        /** Same string form [ConfigBridge.setValue] writes. */
        fun encode(value: Any?): String? = net.runelite.client.config.ConfigUiCodec.encode(value)

        private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
        private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
        private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
    }
}
