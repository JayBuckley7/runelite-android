package net.runelite.mp.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.runelite.api.Skill
import net.runelite.client.plugins.xptracker.XpTrackerService
import net.runelite.mp.ui.RlPalette
import net.runelite.mp.ui.bridge.RuneLiteAccess

/**
 * Mobile XP Tracker — shows total + per-skill session gain, current XP, level,
 * xp/hr, and time-to-goal. Subscribes to xp-tracker state via [XpTrackerService]
 * (public) for the rate calc and reflects into [net.runelite.client.plugins.xptracker.XpTrackerPlugin.getSkillSnapshot]
 * (package-private) for `xpGainedInSession`, which the public service interface
 * doesn't expose.
 *
 * Refresh cadence: every 1s while visible. Cheap — no allocations on the hot path,
 * just a reflection invoke per skill. Hidden skills (gain=0 since boot) sink to
 * the bottom so the user sees what they're actually training first.
 */
@Composable
internal fun XpTrackerPanel()
{
    val rows = remember { mutableStateListOf<XpTrackerBridge.SkillRow>() }
    var total by remember { mutableStateOf(XpTrackerBridge.totalSnapshot()) }

    LaunchedEffect(Unit)
    {
        while (true)
        {
            val next = XpTrackerBridge.list()
            // We don't try to short-circuit on equality here — list() reads ~28 small
            // structs and the snapshot's `xpPerHour` value changes constantly anyway.
            rows.clear()
            rows.addAll(next)
            total = XpTrackerBridge.totalSnapshot()
            delay(1000)
        }
    }

    PanelScaffold(
        title = "XP Tracker",
        subtitle = "${total.xpGainedInSession.formatXp()} session · ${total.xpPerHour.formatXp()}/hr",
    ) {
        if (rows.all { it.xpGainedInSession == 0 })
        {
            Text(
                "No XP gained since login. Train a skill to start tracking.",
                color = RlPalette.TextSecondary,
                fontSize = 11.sp,
            )
            return@PanelScaffold
        }
        for (row in rows)
        {
            SkillRow(row)
            PanelDivider()
        }
    }
}

@Composable
private fun SkillRow(row: XpTrackerBridge.SkillRow)
{
    val active = row.xpGainedInSession > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.skillName,
                color = if (active) RlPalette.TextPrimary else RlPalette.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Lv ${row.level}",
                color = RlPalette.TextSecondary,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.size(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "+${row.xpGainedInSession.formatXp()} (${row.xpPerHour.formatXp()}/hr)",
                color = if (active) Color(0xFF8FE188) else RlPalette.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            if (row.timeTillGoal.isNotEmpty() && row.timeTillGoal != "∞")
            {
                Text(row.timeTillGoal, color = RlPalette.TextSecondary, fontSize = 11.sp)
            }
        }
        if (active && row.progressToGoal in 0.0..1.0)
        {
            Spacer(Modifier.size(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF2A2A2A))
                    .border(1.dp, RlPalette.SurfaceBorder, RoundedCornerShape(2.dp))
                    .padding(1.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(row.progressToGoal.toFloat())
                        .background(RlPalette.Accent),
                ) { Text("", fontSize = 4.sp) }  // forces height ≈ 6sp
            }
        }
    }
}

internal object XpTrackerBridge
{
    data class SkillRow(
        val skillName: String,
        val level: Int,
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
        val client = RuneLiteAccess.instance(net.runelite.api.Client::class.java)
        val getSnap = skillSnapshotMethod ?: return emptyList()
        val service = service()
        return Skill.values().filter { it != Skill.OVERALL }.map { skill ->
            val snap = try { getSnap.invoke(plugin, skill) } catch (t: Throwable) { null }
            val gained = snap?.let { snapshotInt(it, "getXpGainedInSession") } ?: 0
            val perHr = snap?.let { snapshotInt(it, "getXpPerHour") } ?: service?.getXpHr(skill) ?: 0
            val ttg = snap?.let { snapshotString(it, "getTimeTillGoal") } ?: ""
            val progress = snap?.let { snapshotDouble(it, "getSkillProgressToGoal") } ?: 0.0
            val level = client?.getRealSkillLevel(skill) ?: 1
            SkillRow(
                skillName = skill.getName(),
                level = level,
                xpGainedInSession = gained,
                xpPerHour = perHr,
                timeTillGoal = ttg,
                progressToGoal = progress / 100.0,
            )
        }.sortedWith(
            compareByDescending<SkillRow> { it.xpGainedInSession > 0 }
                .thenByDescending { it.xpPerHour }
                .thenBy { it.skillName }
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

/** "1,234" or "12.3k" / "1.23m" depending on magnitude — matches the desktop XP panel. */
internal fun Int.formatXp(): String = when
{
    this < 10_000 -> "%,d".format(this)
    this < 10_000_000 -> "%.1fk".format(this / 1000.0)
    else -> "%.2fm".format(this / 1_000_000.0)
}
