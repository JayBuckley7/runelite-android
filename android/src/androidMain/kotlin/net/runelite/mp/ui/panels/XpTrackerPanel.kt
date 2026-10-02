package net.runelite.mp.ui.panels

import android.graphics.BitmapFactory
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.runelite.api.Experience
import net.runelite.api.GameState
import net.runelite.api.Skill
import net.runelite.client.plugins.xptracker.XpTrackerService
import net.runelite.client.ui.SkillColor
import net.runelite.mp.ui.RlFonts
import net.runelite.mp.ui.RlPalette
import net.runelite.mp.ui.bridge.RuneLiteAccess
import java.util.Locale

/** Compact desktop-style tracker backed by the registered plugin's real session state. */
@Composable
internal fun XpTrackerPanel() {
    var rows by remember { mutableStateOf(emptyList<XpTrackerBridge.SkillRow>()) }
    val expanded = remember { mutableStateMapOf<Skill, Boolean>() }
    LaunchedEffect(Unit) {
        while (true) {
            rows = XpTrackerBridge.list()
            delay(1000)
        }
    }
    PanelScaffold(title = "XP Tracker", scrollable = false) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(RlPalette.DarkerGray).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SkillIcon(null, Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    XpStat("Gained", rows.sumOf { it.xpGainedInSession }.formatXp())
                    XpStat("Per hour", rows.sumOf { it.xpPerHour }.formatXp())
                }
            }
            PanelDivider()
            if (rows.isEmpty()) {
                PanelEmptyState("Log in to view your skills.")
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                    items(rows, key = { it.skill }) { row ->
                        val open = expanded[row.skill] ?: (row.xpGainedInSession > 0)
                        SkillRow(row, open) { expanded[row.skill] = !open }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillIcon(skill: Skill?, modifier: Modifier) {
    val name = skill?.getName() ?: "Overall"
    val bitmap = remember(skill) {
        runCatching {
            val resourceName = skill?.name?.lowercase(Locale.ROOT) ?: "overall"
            XpTrackerBridge::class.java.getResourceAsStream("/skill_icons/$resourceName.png")
                ?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
    }
    if (bitmap != null) Image(bitmap, contentDescription = name, modifier = modifier,
        filterQuality = FilterQuality.None)
    else Box(modifier, contentAlignment = Alignment.Center) {
        Text(name.take(1), color = RlPalette.TextSecondary)
    }
}

@Composable
private fun XpStat(label: String, value: String) {
    Row {
        Text("$label: ", color = RlPalette.TextSecondary, fontFamily = RlFonts.Small, fontSize = 14.sp)
        Text(value, color = RlPalette.TextPrimary, fontFamily = RlFonts.Small, fontSize = 14.sp)
    }
}

@Composable
private fun SkillRow(row: XpTrackerBridge.SkillRow, expanded: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().animateContentSize(tween(140)).clickable(onClick = onClick)
        .semantics { contentDescription = "${row.skillName}, level ${row.level}, ${if (expanded) "collapse" else "expand"} statistics" }
        .padding(vertical = 4.dp)) {
        if (expanded) {
            Row(Modifier.fillMaxWidth().background(RlPalette.DarkerGray).padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SkillIcon(row.skill, Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    XpStat("XP gained", row.xpGainedInSession.formatXp())
                    XpStat("XP/hr", row.xpPerHour.formatXp())
                }
                Column {
                    XpStat("XP left", row.xpRemaining.formatXp())
                    XpStat("Actions", if (row.actions > 0) row.actions.formatXp() else "—")
                }
            }
            if (row.timeTillGoal.isNotBlank() && row.timeTillGoal != "∞") {
                Text("Time left: ${row.timeTillGoal}  ·  Actions/hr: ${row.actionsPerHour.formatXp()}",
                    color = RlPalette.TextSecondary, fontFamily = RlFonts.Small, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!expanded) {
                SkillIcon(row.skill, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
            }
            Box(Modifier.weight(1f).height(18.dp).background(Color(0xFF3D3831))
                .border(1.dp, Color(0xFF171717)).padding(1.dp)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(row.progressToGoal.toFloat())
                    .background(Color(SkillColor.find(row.skill).color.rgb)))
                val style = TextStyle(fontFamily = RlFonts.Regular, fontSize = 13.sp, color = Color.White,
                    shadow = Shadow(Color.Black, Offset(1f, 1f), 0f))
                Text("Lvl. ${row.level}", style = style, modifier = Modifier.align(Alignment.CenterStart).padding(start = 3.dp))
                Text(String.format(Locale.US, "%.2f%%", row.progressToGoal * 100), style = style,
                    modifier = Modifier.align(Alignment.Center))
                Text(if (row.level == Experience.MAX_VIRT_LEVEL) "200M" else "Lvl. ${row.endLevel}",
                    style = style, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 3.dp))
            }
        }
    }
}

internal object XpTrackerBridge
{
    data class SkillRow(
        val skill: Skill,
        val skillName: String,
        val level: Int,
        val endLevel: Int,
        val xpRemaining: Int,
        val actions: Int,
        val actionsPerHour: Int,
        val xpGainedInSession: Int,
        val xpPerHour: Int,
        val timeTillGoal: String,
        val progressToGoal: Double,
    )

    data class TotalRow(val xpGainedInSession: Int, val xpPerHour: Int)

    /**
     * Use the REGISTERED plugin instance (the one PluginManager holds + EventBus has
     * subscribed for XP-update events) rather than `injector.getInstance(...)`. RuneLite
     * gives every plugin its own Guice child injector, so the root injector hands back a
     * brand-new XpTrackerPlugin whose XpState is the no-XP-ever default. The registered
     * instance lives in a child injector but PluginManager.getPlugins() exposes it.
     *
     * Same pattern as the WorldHopper fix upstream.
     */
    private fun plugin(): Any? = RuneLiteAccess.registeredPluginByName(
        "net.runelite.client.plugins.xptracker.XpTrackerPlugin"
    )

    /** Resolve XpTrackerService through the plugin's CHILD injector. XpTrackerPlugin's
     *  configure() binds XpTrackerService → XpTrackerServiceImpl on the plugin module's
     *  binder, so the only injector that knows how to provide a singleton-scoped
     *  implementation is the one PluginManager created for the plugin. Going through
     *  `RuneLite.injector.getInstance(...)` would miss that binding entirely (or hand back
     *  a fresh JIT-bound copy with empty XpState). */
    private fun service(): XpTrackerService?
    {
        val p = plugin() ?: return null
        return try
        {
            val pluginCls = Class.forName("net.runelite.client.plugins.Plugin")
            val injF = pluginCls.getDeclaredField("injector").apply { isAccessible = true }
            val inj = injF.get(p) ?: return null
            val m = inj.javaClass.getMethod("getInstance", Class::class.java)
            m.invoke(inj, XpTrackerService::class.java) as? XpTrackerService
        }
        catch (t: Throwable) { null }
    }

    private val skillSnapshotMethod by lazy {
        try
        {
            val cls = Class.forName("net.runelite.client.plugins.xptracker.XpTrackerPlugin")
            cls.getDeclaredMethod("getSkillSnapshot", Skill::class.java).apply { isAccessible = true }
        }
        catch (t: Throwable) { null }
    }

    private val snapshotGetters = mutableMapOf<String, java.lang.reflect.Method?>()

    private fun snapshotInt(snapshot: Any, getter: String): Int
    {
        val m = snapshotGetters.getOrPut(getter) {
            try { snapshot.javaClass.getMethod(getter).apply { isAccessible = true } }
            catch (t: Throwable) { null }
        } ?: return 0
        return try { (m.invoke(snapshot) as? Int) ?: 0 } catch (t: Throwable) { 0 }
    }

    private fun snapshotDouble(snapshot: Any, getter: String): Double
    {
        val m = snapshotGetters.getOrPut(getter) {
            try { snapshot.javaClass.getMethod(getter).apply { isAccessible = true } }
            catch (t: Throwable) { null }
        } ?: return 0.0
        return try { (m.invoke(snapshot) as? Double) ?: 0.0 } catch (t: Throwable) { 0.0 }
    }

    private fun snapshotString(snapshot: Any, getter: String): String
    {
        val m = snapshotGetters.getOrPut(getter) {
            try { snapshot.javaClass.getMethod(getter).apply { isAccessible = true } }
            catch (t: Throwable) { null }
        } ?: return ""
        return try { (m.invoke(snapshot) as? String) ?: "" } catch (t: Throwable) { "" }
    }

    fun list(): List<SkillRow>
    {
        val plugin = plugin() ?: return emptyList()
        val client = RuneLiteAccess.instance(net.runelite.api.Client::class.java) ?: return emptyList()
        if (client.gameState !in setOf(GameState.LOGGED_IN, GameState.LOADING, GameState.HOPPING, GameState.CONNECTION_LOST))
            return emptyList()
        val getSnap = skillSnapshotMethod ?: return emptyList()
        val service = service()
        return Skill.values().map { skill ->
            val snap = try { getSnap.invoke(plugin, skill) } catch (t: Throwable) { null }
            val gained = snap?.let { snapshotInt(it, "getXpGainedInSession") } ?: 0
            val perHr = snap?.let { snapshotInt(it, "getXpPerHour") } ?: service?.getXpHr(skill) ?: 0
            val ttg = snap?.let { snapshotString(it, "getTimeTillGoal") } ?: ""
            val progress = snap?.let { snapshotDouble(it, "getSkillProgressToGoal") } ?: 0.0
            val xp = client.getSkillExperience(skill).coerceAtLeast(0)
            val level = Experience.getLevelForXp(xp)
            val snapshotStart = snap?.let { snapshotInt(it, "getStartLevel") } ?: 0
            val snapshotEnd = snap?.let { snapshotInt(it, "getEndLevel") } ?: 0
            val startXp = Experience.getXpForLevel(level)
            val goalXp = if (level < Experience.MAX_VIRT_LEVEL) Experience.getXpForLevel(level + 1) else Experience.MAX_SKILL_XP
            val snapshotStartXp = snap?.let { snapshotInt(it, "getStartGoalXp") } ?: 0
            val snapshotEndXp = snap?.let { snapshotInt(it, "getEndGoalXp") } ?: 0
            // Untrained skills have a placeholder snapshot with both goal XP values at zero.
            val hasGoal = snapshotStart > 0 && snapshotEnd > 0 && snapshotEndXp > snapshotStartXp && progress.isFinite()
            val fraction = if (hasGoal) progress / 100.0 else
                (xp - startXp).toDouble() / (goalXp - startXp).coerceAtLeast(1)
            SkillRow(
                skill = skill,
                skillName = skill.getName(),
                level = if (hasGoal) snapshotStart else level,
                endLevel = if (hasGoal) snapshotEnd else (level + 1).coerceAtMost(Experience.MAX_VIRT_LEVEL),
                xpRemaining = if (hasGoal) snap?.let { snapshotInt(it, "getXpRemainingToGoal") } ?: 0 else (goalXp - xp).coerceAtLeast(0),
                actions = snap?.let { snapshotInt(it, "getActionsInSession") } ?: 0,
                actionsPerHour = snap?.let { snapshotInt(it, "getActionsPerHour") } ?: 0,
                xpGainedInSession = gained,
                xpPerHour = perHr,
                timeTillGoal = ttg,
                progressToGoal = if (fraction.isFinite()) fraction.coerceIn(0.0, 1.0) else 0.0,
            )
        }.sortedWith(
            compareByDescending<SkillRow> { it.xpGainedInSession > 0 }
                .thenByDescending { it.xpPerHour }
                .thenBy { it.skill.ordinal }
        )
    }

    fun totalSnapshot(): TotalRow
    {
        val rows = list()
        val gained = rows.sumOf { it.xpGainedInSession }
        val perHr = rows.sumOf { it.xpPerHour }
        return TotalRow(gained, perHr)
    }
}

/** Compact XP values, shared by the existing panels. */
internal fun Int.formatXp(): String = when {
    this < 10_000 -> String.format(Locale.US, "%,d", this)
    this < 1_000_000 -> String.format(Locale.US, "%.1fK", this / 1000.0)
    else -> String.format(Locale.US, "%.2fM", this / 1_000_000.0)
}
