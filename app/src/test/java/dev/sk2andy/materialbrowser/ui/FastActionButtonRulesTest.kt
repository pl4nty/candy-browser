package dev.sk2andy.materialbrowser.ui

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastActionButtonRulesTest {
    private val radius = 300f
    private val allEnabled = List(FastActionButtonRules.DefaultActions.size) { true }

    @Test
    fun `wire values round trip and unknown values are rejected`() {
        FastAction.entries.forEach { action ->
            assertEquals(action, FastAction.fromWireValue(action.wireValue))
        }
        assertNull(FastAction.fromWireValue("missing"))
        assertNull(FastAction.fromWireValue(null))
        assertEquals(FastAction.entries.size, FastAction.entries.map { it.wireValue }.toSet().size)
    }

    @Test
    fun `availability gates navigation, page and close actions only`() {
        val none = FastActionAvailability(
            canGoBack = false,
            canGoForward = false,
            canUsePageActions = false,
            canCloseTab = false,
        )
        assertFalse(FastActionButtonRules.isEnabled(FastAction.Back, none))
        assertFalse(FastActionButtonRules.isEnabled(FastAction.Forward, none))
        assertFalse(FastActionButtonRules.isEnabled(FastAction.Reload, none))
        assertFalse(FastActionButtonRules.isEnabled(FastAction.CloseTab, none))
        listOf(FastAction.Search, FastAction.Tabs, FastAction.NewTab, FastAction.Home).forEach {
            assertTrue(FastActionButtonRules.isEnabled(it, none))
        }
        assertTrue(FastActionButtonRules.isEnabled(FastAction.Back, none.copy(canGoBack = true)))
        assertTrue(FastActionButtonRules.isEnabled(FastAction.Forward, none.copy(canGoForward = true)))
        assertTrue(FastActionButtonRules.isEnabled(FastAction.Reload, none.copy(canUsePageActions = true)))
        assertTrue(FastActionButtonRules.isEnabled(FastAction.CloseTab, none.copy(canCloseTab = true)))
    }

    @Test
    fun `end edge arc spans from near vertical to past horizontal`() {
        val angles = FastActionButtonRules.itemAngles(6, FastActionButtonEdge.End)
        assertEquals(6, angles.size)
        assertEquals(80f, angles.first(), 0.001f)
        assertEquals(190f, angles.last(), 0.001f)
        assertTrue(angles.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `start edge arc mirrors the end edge`() {
        val end = FastActionButtonRules.itemAngles(5, FastActionButtonEdge.End)
        val start = FastActionButtonRules.itemAngles(5, FastActionButtonEdge.Start)
        end.zip(start).forEach { (e, s) -> assertEquals(180f - e, s, 0.001f) }
    }

    @Test
    fun `single and empty arcs are well defined`() {
        assertEquals(emptyList<Float>(), FastActionButtonRules.itemAngles(0, FastActionButtonEdge.End))
        assertEquals(listOf(135f), FastActionButtonRules.itemAngles(1, FastActionButtonEdge.End))
    }

    @Test
    fun `end edge offsets open up and toward the screen centre`() {
        val offsets = FastActionButtonRules.itemOffsets(6, FastActionButtonEdge.End, radius)
        // Screen y grows downward, so items sit above the button or barely below its centre.
        assertTrue(offsets.all { it.y < radius * 0.2f })
        assertTrue(offsets.first().y < -radius * 0.9f)
        // The last item sits left of the button, slightly below its centre line.
        assertTrue(offsets.last().x < -radius * 0.9f)
        offsets.forEach { assertEquals(radius, kotlin.math.hypot(it.x, it.y), 0.01f) }
    }

    @Test
    fun `invalid radius yields zero offsets and no selection`() {
        val offsets = FastActionButtonRules.itemOffsets(3, FastActionButtonEdge.End, Float.NaN)
        assertEquals(List(3) { FastActionOffset(0f, 0f) }, offsets)
        assertNull(
            FastActionButtonRules.selectedIndex(
                FastActionOffset(-200f, -200f),
                allEnabled,
                FastActionButtonEdge.End,
                0f,
            ),
        )
    }

    @Test
    fun `dragging toward each item selects it on either edge`() {
        FastActionButtonEdge.entries.forEach { edge ->
            val offsets = FastActionButtonRules.itemOffsets(allEnabled.size, edge, radius)
            offsets.forEachIndexed { index, offset ->
                assertEquals(
                    "edge=$edge index=$index",
                    index,
                    FastActionButtonRules.selectedIndex(offset, allEnabled, edge, radius),
                )
            }
        }
    }

    @Test
    fun `short drags and very long drags do not select`() {
        val toward = FastActionButtonRules.itemOffsets(allEnabled.size, FastActionButtonEdge.End, radius)[2]
        val short = FastActionOffset(toward.x * 0.2f, toward.y * 0.2f)
        val far = FastActionOffset(toward.x * 3f, toward.y * 3f)
        assertNull(FastActionButtonRules.selectedIndex(short, allEnabled, FastActionButtonEdge.End, radius))
        assertNull(FastActionButtonRules.selectedIndex(far, allEnabled, FastActionButtonEdge.End, radius))
        val justPast = FastActionOffset(toward.x * 0.5f, toward.y * 0.5f)
        assertEquals(2, FastActionButtonRules.selectedIndex(justPast, allEnabled, FastActionButtonEdge.End, radius))
    }

    @Test
    fun `dragging away from the arc does not select`() {
        // Straight down-right is the opposite side of the arc for an end-edge button.
        val awayDegrees = -45.0 * Math.PI / 180.0
        val away = FastActionOffset(
            (cos(awayDegrees) * radius).toFloat(),
            (-sin(awayDegrees) * radius).toFloat(),
        )
        assertNull(FastActionButtonRules.selectedIndex(away, allEnabled, FastActionButtonEdge.End, radius))
    }

    @Test
    fun `disabled items are not selectable`() {
        val enabled = allEnabled.toMutableList().apply { this[0] = false }
        val first = FastActionButtonRules.itemOffsets(enabled.size, FastActionButtonEdge.End, radius).first()
        assertNull(FastActionButtonRules.selectedIndex(first, enabled, FastActionButtonEdge.End, radius))
    }

    @Test
    fun `non finite drags are ignored`() {
        assertNull(
            FastActionButtonRules.selectedIndex(
                FastActionOffset(Float.NaN, -100f),
                allEnabled,
                FastActionButtonEdge.End,
                radius,
            ),
        )
    }

    @Test
    fun `edge follows the half of the container the button sits in`() {
        assertEquals(FastActionButtonEdge.End, FastActionButtonRules.edgeFor(900f, 1000f, isRtl = false))
        assertEquals(FastActionButtonEdge.Start, FastActionButtonRules.edgeFor(100f, 1000f, isRtl = false))
        assertEquals(FastActionButtonEdge.Start, FastActionButtonRules.edgeFor(Float.NaN, 1000f, isRtl = true))
        assertEquals(FastActionButtonEdge.End, FastActionButtonRules.edgeFor(10f, 0f, isRtl = false))
    }
}
