package dev.sk2andy.materialbrowser.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Actions reachable from the floating fast action button. */
internal enum class FastAction(val wireValue: String) {
    Search("search"),
    Back("back"),
    Forward("forward"),
    Reload("reload"),
    Tabs("tabs"),
    NewTab("new_tab"),
    CloseTab("close_tab"),
    Home("home"),
    ;

    companion object {
        fun fromWireValue(value: String?): FastAction? =
            entries.firstOrNull { action -> action.wireValue == value }
    }
}

/** Physical screen edge the button sits against; the action arc opens away from it. */
internal enum class FastActionButtonEdge {
    Start,
    End,
}

internal data class FastActionOffset(
    val x: Float,
    val y: Float,
)

internal data class FastActionAvailability(
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val canUsePageActions: Boolean,
    val canCloseTab: Boolean,
)

/**
 * Pure geometry and selection rules for the fast action button.
 *
 * Angles use the mathematical convention (0 degrees points right, 90 degrees points up), while
 * offsets use screen coordinates (y grows downward), matching Compose layout space.
 */
internal object FastActionButtonRules {
    val DefaultActions: List<FastAction> = listOf(
        FastAction.Back,
        FastAction.Search,
        FastAction.Tabs,
        FastAction.NewTab,
        FastAction.Reload,
        FastAction.CloseTab,
    )

    /** Arc measured from straight up toward the open side of the screen. */
    const val ARC_START_DEGREES = 80f
    const val ARC_SWEEP_DEGREES = 110f

    /** Fraction of the arc radius a drag must travel before an action is highlighted. */
    const val ACTIVATION_RADIUS_FRACTION = 0.42f

    /** Drags beyond this multiple of the arc radius cancel the selection. */
    const val CANCEL_RADIUS_FRACTION = 2.2f

    fun isEnabled(action: FastAction, availability: FastActionAvailability): Boolean =
        when (action) {
            FastAction.Back -> availability.canGoBack
            FastAction.Forward -> availability.canGoForward
            FastAction.Reload -> availability.canUsePageActions
            FastAction.CloseTab -> availability.canCloseTab
            FastAction.Search,
            FastAction.Tabs,
            FastAction.NewTab,
            FastAction.Home,
            -> true
        }

    /** Angle in degrees for each item, spread evenly over the arc. */
    fun itemAngles(count: Int, edge: FastActionButtonEdge): List<Float> {
        if (count <= 0) return emptyList()
        val step = if (count == 1) 0f else ARC_SWEEP_DEGREES / (count - 1)
        return List(count) { index ->
            val fromUp = if (count == 1) ARC_SWEEP_DEGREES / 2f else step * index
            val angleOnEndEdge = ARC_START_DEGREES + fromUp
            when (edge) {
                // Button on the right: the arc opens up and to the left (80..190 degrees).
                FastActionButtonEdge.End -> angleOnEndEdge
                // Button on the left: mirror across the vertical axis.
                FastActionButtonEdge.Start -> 180f - angleOnEndEdge
            }
        }
    }

    /** Item centre offsets relative to the button centre, in screen coordinates. */
    fun itemOffsets(
        count: Int,
        edge: FastActionButtonEdge,
        radiusPx: Float,
    ): List<FastActionOffset> {
        if (radiusPx <= 0f || !radiusPx.isFinite()) return List(count.coerceAtLeast(0)) {
            FastActionOffset(0f, 0f)
        }
        return itemAngles(count, edge).map { degrees ->
            val radians = degrees * PI / 180.0
            FastActionOffset(
                x = (cos(radians) * radiusPx).toFloat(),
                y = (-sin(radians) * radiusPx).toFloat(),
            )
        }
    }

    /**
     * Index of the action under a drag that started on the button, or null when the drag is too
     * short, too far, or nearest to a disabled action.
     */
    fun selectedIndex(
        drag: FastActionOffset,
        enabled: List<Boolean>,
        edge: FastActionButtonEdge,
        radiusPx: Float,
    ): Int? {
        if (enabled.isEmpty() || radiusPx <= 0f || !radiusPx.isFinite()) return null
        if (!drag.x.isFinite() || !drag.y.isFinite()) return null
        val distance = hypot(drag.x, drag.y)
        if (distance < radiusPx * ACTIVATION_RADIUS_FRACTION) return null
        if (distance > radiusPx * CANCEL_RADIUS_FRACTION) return null
        val dragDegrees = normalizedDegrees(
            (atan2(-drag.y.toDouble(), drag.x.toDouble()) * 180.0 / PI).toFloat(),
        )
        val angles = itemAngles(enabled.size, edge)
        val nearest = angles.indices.minByOrNull { index ->
            angularDistance(dragDegrees, angles[index])
        } ?: return null
        val step = if (angles.size == 1) ARC_SWEEP_DEGREES else ARC_SWEEP_DEGREES / (angles.size - 1)
        // Reject drags more than one item step away from the nearest item.
        if (angularDistance(dragDegrees, angles[nearest]) > step) return null
        return nearest.takeIf { enabled[it] }
    }

    /** Edge the button should sit against for a horizontal centre within the container. */
    fun edgeFor(centerX: Float, containerWidth: Float, isRtl: Boolean): FastActionButtonEdge {
        if (containerWidth <= 0f || !centerX.isFinite()) {
            return if (isRtl) FastActionButtonEdge.Start else FastActionButtonEdge.End
        }
        return if (centerX >= containerWidth / 2f) {
            FastActionButtonEdge.End
        } else {
            FastActionButtonEdge.Start
        }
    }

    private fun normalizedDegrees(degrees: Float): Float {
        val wrapped = degrees % 360f
        return if (wrapped < 0f) wrapped + 360f else wrapped
    }

    private fun angularDistance(a: Float, b: Float): Float {
        val difference = abs(normalizedDegrees(a) - normalizedDegrees(b)) % 360f
        return if (difference > 180f) 360f - difference else difference
    }
}
