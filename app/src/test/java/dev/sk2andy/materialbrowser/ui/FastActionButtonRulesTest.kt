package dev.sk2andy.materialbrowser.ui

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastActionButtonRulesTest {
    private val inner = 200f
    private val outer = 360f
    private val innerCount = FastActionButtonRules.InnerActions.size
    private val allEnabled = List(innerCount) { true }
    private val outerCount = FastActionButtonRules.MAX_RECENT_TABS + 1

    private fun select(
        drag: FastActionOffset,
        enabled: List<Boolean> = allEnabled,
        isRtl: Boolean = false,
    ) = FastActionButtonRules.selectedTarget(drag, enabled, outerCount, inner, outer, isRtl)

    private fun toward(degrees: Double, radius: Float) = FastActionOffset(
        (cos(Math.toRadians(degrees)) * radius).toFloat(),
        (-sin(Math.toRadians(degrees)) * radius).toFloat(),
    )

    @Test
    fun `inner ring follows the opera gx order`() {
        assertEquals(
            listOf(FastAction.Reload, FastAction.CloseTab, FastAction.Search, FastAction.NewTab, FastAction.Share),
            FastActionButtonRules.InnerActions,
        )
    }

    @Test
    fun `wire values round trip and unknown values are rejected`() {
        FastAction.entries.forEach { assertEquals(it, FastAction.fromWireValue(it.wireValue)) }
        assertNull(FastAction.fromWireValue("missing"))
        assertNull(FastAction.fromWireValue(null))
    }

    @Test
    fun `availability gates reload, close and share only`() {
        val none = FastActionAvailability(canReload = false, canShare = false, canCloseTab = false)
        assertFalse(FastActionButtonRules.isEnabled(FastAction.Reload, none))
        assertFalse(FastActionButtonRules.isEnabled(FastAction.CloseTab, none))
        assertFalse(FastActionButtonRules.isEnabled(FastAction.Share, none))
        assertTrue(FastActionButtonRules.isEnabled(FastAction.Search, none))
        assertTrue(FastActionButtonRules.isEnabled(FastAction.NewTab, none))
        assertTrue(FastActionButtonRules.isEnabled(FastAction.Reload, none.copy(canReload = true)))
        assertTrue(FastActionButtonRules.isEnabled(FastAction.CloseTab, none.copy(canCloseTab = true)))
        assertTrue(FastActionButtonRules.isEnabled(FastAction.Share, none.copy(canShare = true)))
    }

    @Test
    fun `inner ring spans the half disc with search straight up`() {
        val angles = FastActionButtonRules.innerAngles(innerCount, isRtl = false)
        assertEquals(listOf(180f, 135f, 90f, 45f, 0f), angles)
    }

    @Test
    fun `outer arc spreads four recent tabs then overview evenly between 160 and 20 degrees`() {
        val angles = FastActionButtonRules.outerAngles(outerCount, isRtl = false)
        assertEquals(listOf(160f, 125f, 90f, 55f, 20f), angles)
        assertTrue(angles.zipWithNext().all { (a, b) -> a > b })
        assertEquals(listOf(90f), FastActionButtonRules.outerAngles(1, isRtl = false))
        assertEquals(emptyList<Float>(), FastActionButtonRules.outerAngles(0, isRtl = false))
    }

    @Test
    fun `rtl mirrors both rings`() {
        val ltr = FastActionButtonRules.innerAngles(innerCount, isRtl = false)
        val rtl = FastActionButtonRules.innerAngles(innerCount, isRtl = true)
        ltr.zip(rtl).forEach { (l, r) -> assertEquals(180f - l, r, 0.001f) }
        assertEquals(FastActionTarget.Inner(0), select(toward(0.0, inner), isRtl = true))
    }

    @Test
    fun `offsets sit on the radius and above the button`() {
        val offsets = FastActionButtonRules.offsets(FastActionButtonRules.outerAngles(4, false), outer)
        offsets.forEach {
            assertEquals(outer, hypot(it.x, it.y), 0.01f)
            assertTrue(it.y < 0f)
        }
        assertTrue(offsets.first().x < 0f)
        assertTrue(offsets.last().x > 0f)
        assertEquals(
            List(2) { FastActionOffset(0f, 0f) },
            FastActionButtonRules.offsets(listOf(10f, 20f), Float.NaN),
        )
    }

    @Test
    fun `dragging to each inner item selects it`() {
        FastActionButtonRules.innerAngles(innerCount, false).forEachIndexed { index, degrees ->
            assertEquals(FastActionTarget.Inner(index), select(toward(degrees.toDouble(), inner)))
        }
    }

    @Test
    fun `dragging to each outer item selects it`() {
        FastActionButtonRules.outerAngles(outerCount, false).forEachIndexed { index, degrees ->
            assertEquals(FastActionTarget.Outer(index), select(toward(degrees.toDouble(), outer)))
        }
    }

    @Test
    fun `ring is chosen by distance either side of the midpoint`() {
        val midpoint = (inner + outer) / 2f
        assertEquals(FastActionTarget.Inner(2), select(toward(90.0, midpoint - 1f)))
        assertTrue(select(toward(90.0, midpoint + 1f)) is FastActionTarget.Outer)
    }

    @Test
    fun `short and very long drags select nothing`() {
        assertNull(select(toward(90.0, inner * 0.3f)))
        assertNull(select(toward(90.0, outer * 1.6f)))
    }

    @Test
    fun `reload and share tolerate a slightly downward drag but not straight down`() {
        assertEquals(FastActionTarget.Inner(0), select(toward(195.0, inner)))
        assertEquals(FastActionTarget.Inner(4), select(toward(-15.0, inner)))
        assertNull(select(toward(270.0, inner)))
    }

    @Test
    fun `disabled inner actions are not selectable`() {
        val enabled = allEnabled.toMutableList().apply { this[0] = false }
        assertNull(select(toward(180.0, inner), enabled = enabled))
    }

    @Test
    fun `invalid geometry and non finite drags are ignored`() {
        assertNull(select(FastActionOffset(Float.NaN, -100f)))
        assertNull(FastActionButtonRules.selectedTarget(toward(90.0, 100f), allEnabled, 4, 0f, outer, false))
        assertNull(FastActionButtonRules.selectedTarget(toward(90.0, 100f), allEnabled, 4, inner, inner, false))
    }

    @Test
    fun `recent tabs are the newest few ordered oldest first`() {
        val tabs = listOf(
            FastActionTabRecency("a", 10),
            FastActionTabRecency("b", 40),
            FastActionTabRecency("c", 30),
            FastActionTabRecency("d", 20),
        )
        assertEquals(listOf("a", "d", "c", "b"), FastActionButtonRules.recentTabIds(tabs))
        assertEquals(listOf("c", "b"), FastActionButtonRules.recentTabIds(tabs, max = 2))
        assertEquals(listOf("b"), FastActionButtonRules.recentTabIds(tabs, max = 1))
        assertEquals(emptyList<String>(), FastActionButtonRules.recentTabIds(tabs, max = 0))
        assertEquals(listOf("a"), FastActionButtonRules.recentTabIds(tabs.take(1)))
    }

    @Test
    fun `outer arc lists recent tabs then the overview`() {
        assertEquals(
            listOf(
                FastActionOuterItem.Tab("a"),
                FastActionOuterItem.Tab("b"),
                FastActionOuterItem.Tab("c"),
                FastActionOuterItem.Tab("d"),
                FastActionOuterItem.Overview,
            ),
            FastActionButtonRules.outerItems(listOf("a", "b", "c", "d"), includeMenu = false),
        )
        assertEquals(
            listOf(FastActionOuterItem.Overview),
            FastActionButtonRules.outerItems(emptyList(), includeMenu = false),
        )
    }

    @Test
    fun `replacing the address bar puts the current tab left, oldest in the middle and the menu right`() {
        val items = FastActionButtonRules.outerItems(listOf("a", "b", "c", "d"), includeMenu = true)
        assertEquals(
            listOf(
                FastActionOuterItem.Tab("d"),
                FastActionOuterItem.Tab("c"),
                FastActionOuterItem.Tab("b"),
                FastActionOuterItem.Overview,
                FastActionOuterItem.Menu,
            ),
            items,
        )
        assertEquals(outerCount, items.size)
        assertEquals(3, FastActionButtonRules.maxRecentTabs(includeMenu = true))
        assertEquals(
            listOf(FastActionOuterItem.Tab("a"), FastActionOuterItem.Overview, FastActionOuterItem.Menu),
            FastActionButtonRules.outerItems(listOf("a"), includeMenu = true),
        )
    }

    @Test
    fun `hold to open and the open animation take 70 percent of their original time`() {
        assertEquals(280L, FastActionButtonRules.holdToOpenMillis(400L))
        assertEquals(700L, FastActionButtonRules.holdToOpenMillis(1_000L))
        assertEquals(168, FastActionButtonRules.DISC_GROW_MILLIS)
        val settleRatio = sqrt(700f / FastActionButtonRules.ITEM_SPRING_STIFFNESS)
        assertEquals(0.7f, settleRatio, 0.01f)
    }

    @Test
    fun `only new tab skips the button's own confirm vibration`() {
        val newTab = FastActionButtonRules.InnerActions.indexOf(FastAction.NewTab)
        FastActionButtonRules.InnerActions.indices.forEach { index ->
            assertEquals(index != newTab, FastActionButtonRules.confirmsWithHaptic(FastActionTarget.Inner(index)))
        }
        assertTrue(FastActionButtonRules.confirmsWithHaptic(FastActionTarget.Outer(0)))
    }
}
