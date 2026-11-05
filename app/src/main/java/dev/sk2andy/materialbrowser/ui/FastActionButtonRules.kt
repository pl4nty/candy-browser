package dev.sk2andy.materialbrowser.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Page actions on the inner ring of the fast action button. */
internal enum class FastAction(val wireValue: String) {
    Reload("reload"),
    CloseTab("close_tab"),
    Search("search"),
    NewTab("new_tab"),
    Share("share"),
    ;

    companion object {
        fun fromWireValue(value: String?): FastAction? =
            entries.firstOrNull { action -> action.wireValue == value }
    }
}

internal data class FastActionOffset(
    val x: Float,
    val y: Float,
)

internal data class FastActionAvailability(
    val canReload: Boolean,
    val canShare: Boolean,
    val canCloseTab: Boolean,
)

/** Recency input for the outer arc: a tab id and when it was last selected. */
internal data class FastActionTabRecency(
    val id: String,
    val lastAccessedAt: Long,
)

/** What a press-and-slide gesture currently points at. */
internal sealed interface FastActionTarget {
    /** An entry of [FastActionButtonRules.InnerActions]. */
    data class Inner(val index: Int) : FastActionTarget

    /** An entry of the outer arc, as listed by [FastActionButtonRules.outerItems]. */
    data class Outer(val index: Int) : FastActionTarget
}

/** A bubble on the outer arc. */
internal sealed interface FastActionOuterItem {
    data class Tab(val id: String) : FastActionOuterItem

    /** Opens the tab overview. */
    data object Overview : FastActionOuterItem

    /** Opens the address pill's main menu; shown when the button replaces the address bar. */
    data object Menu : FastActionOuterItem
}

/**
 * Pure geometry and selection rules for the centred fast action button.
 *
 * The button sits at the bottom centre. Pressing it opens a half-disc with page actions on an
 * inner ring and an outer arc of recent tabs ending in the tab overview.
 *
 * Angles use the mathematical convention (0 degrees points right, 90 degrees points up), while
 * offsets use screen coordinates (y grows downward), matching Compose layout space.
 */
internal object FastActionButtonRules {
    /**
     * How long a press must be held before the disc opens when a tap has its own action, as
     * 70% of the system long-press timeout (400 ms by default), so the disc starts sooner.
     */
    fun holdToOpenMillis(longPressTimeoutMillis: Long): Long = longPressTimeoutMillis * 7 / 10

    /** Duration of the disc growing out of the button; 70% of the original 240 ms. */
    const val DISC_GROW_MILLIS = 168

    /**
     * Stiffness of the spring that moves the items out. Settle time scales with
     * 1 / sqrt(stiffness), so 700 / 0.7^2 makes it 70% of the original 700 spring.
     */
    const val ITEM_SPRING_STIFFNESS = 1430f

    /**
     * Whether running [target] should give the button's own confirm vibration. New tab already
     * confirms itself when the tab is created, so a second vibration would double up.
     */
    fun confirmsWithHaptic(target: FastActionTarget): Boolean =
        !(target is FastActionTarget.Inner && InnerActions.getOrNull(target.index) == FastAction.NewTab)

    /** Inner ring, from the left end of the half-disc to the right end in left-to-right layouts. */
    val InnerActions: List<FastAction> = listOf(
        FastAction.Reload,
        FastAction.CloseTab,
        FastAction.Search,
        FastAction.NewTab,
        FastAction.Share,
    )

    const val INNER_START_DEGREES = 180f
    const val INNER_END_DEGREES = 0f
    const val OUTER_START_DEGREES = 160f
    const val OUTER_END_DEGREES = 20f

    /** Recent tabs shown on the outer arc, before the tab-overview bubble. */
    const val MAX_RECENT_TABS = 4

    /** Recent tabs shown when the menu bubble takes one outer slot. */
    const val MAX_RECENT_TABS_WITH_MENU = 3

    /** Fraction of the inner radius a drag must travel before anything is highlighted. */
    const val ACTIVATION_RADIUS_FRACTION = 0.45f

    /** Drags beyond this multiple of the outer radius cancel the selection. */
    const val CANCEL_RADIUS_FRACTION = 1.45f

    /** Angular tolerance as a fraction of the spacing between neighbouring items. */
    const val ANGLE_TOLERANCE_FRACTION = 0.75f

    fun isEnabled(action: FastAction, availability: FastActionAvailability): Boolean =
        when (action) {
            FastAction.Reload -> availability.canReload
            FastAction.CloseTab -> availability.canCloseTab
            FastAction.Share -> availability.canShare
            FastAction.Search,
            FastAction.NewTab,
            -> true
        }

    /**
     * Up to [max] most recently used tabs, ordered oldest to newest so the newest sits next to
     * the tab-overview bubble at the end of the arc.
     */
    fun recentTabIds(tabs: List<FastActionTabRecency>, max: Int = MAX_RECENT_TABS): List<String> {
        if (max <= 0) return emptyList()
        return tabs
            .sortedByDescending(FastActionTabRecency::lastAccessedAt)
            .take(max)
            .reversed()
            .map(FastActionTabRecency::id)
    }

    fun maxRecentTabs(includeMenu: Boolean): Int =
        if (includeMenu) MAX_RECENT_TABS_WITH_MENU else MAX_RECENT_TABS

    /**
     * Outer arc from its left end to its right end in left-to-right layouts, from [recentTabIds]
     * ordered oldest first. RTL layouts mirror the arc, as for the angles.
     *
     * - Without the menu: the newest tabs oldest first, then the tab overview, so the current tab
     *   sits next to the overview.
     * - With the menu: the newest tabs newest first, so the current tab takes the left end and the
     *   oldest shown tab the middle, then the tab overview, then the menu at the right end.
     */
    fun outerItems(recentTabIds: List<String>, includeMenu: Boolean): List<FastActionOuterItem> = buildList {
        val tabs = recentTabIds.takeLast(maxRecentTabs(includeMenu))
        (if (includeMenu) tabs.asReversed() else tabs).forEach { add(FastActionOuterItem.Tab(it)) }
        add(FastActionOuterItem.Overview)
        if (includeMenu) add(FastActionOuterItem.Menu)
    }

    /** Evenly spread angles from [start] to [end]; RTL mirrors them across the vertical axis. */
    fun spreadAngles(count: Int, start: Float, end: Float, isRtl: Boolean): List<Float> {
        if (count <= 0) return emptyList()
        return List(count) { index ->
            val degrees = if (count == 1) (start + end) / 2f else start + (end - start) * index / (count - 1)
            if (isRtl) 180f - degrees else degrees
        }
    }

    fun innerAngles(count: Int, isRtl: Boolean): List<Float> =
        spreadAngles(count, INNER_START_DEGREES, INNER_END_DEGREES, isRtl)

    fun outerAngles(count: Int, isRtl: Boolean): List<Float> =
        spreadAngles(count, OUTER_START_DEGREES, OUTER_END_DEGREES, isRtl)

    /** Item centre offsets relative to the button centre, in screen coordinates. */
    fun offsets(angles: List<Float>, radiusPx: Float): List<FastActionOffset> {
        if (radiusPx <= 0f || !radiusPx.isFinite()) return angles.map { FastActionOffset(0f, 0f) }
        return angles.map { degrees ->
            val radians = degrees * PI / 180.0
            FastActionOffset(
                x = (cos(radians) * radiusPx).toFloat(),
                y = (-sin(radians) * radiusPx).toFloat(),
            )
        }
    }

    /**
     * Target under a drag that started on the button, or null when the drag is too short, too
     * far, off the half-disc, or nearest to a disabled inner action. The ring is chosen by
     * distance, split halfway between the two radii; the item is the nearest by angle.
     */
    fun selectedTarget(
        drag: FastActionOffset,
        innerEnabled: List<Boolean>,
        outerCount: Int,
        innerRadiusPx: Float,
        outerRadiusPx: Float,
        isRtl: Boolean,
    ): FastActionTarget? {
        if (!innerRadiusPx.isFinite() || !outerRadiusPx.isFinite()) return null
        if (innerRadiusPx <= 0f || outerRadiusPx <= innerRadiusPx) return null
        if (!drag.x.isFinite() || !drag.y.isFinite()) return null
        val distance = hypot(drag.x, drag.y)
        if (distance < innerRadiusPx * ACTIVATION_RADIUS_FRACTION) return null
        if (distance > outerRadiusPx * CANCEL_RADIUS_FRACTION) return null
        val dragDegrees = normalizedDegrees(
            (atan2(-drag.y.toDouble(), drag.x.toDouble()) * 180.0 / PI).toFloat(),
        )
        val bandSplit = (innerRadiusPx + outerRadiusPx) / 2f
        return if (distance < bandSplit) {
            val index = nearestIndex(dragDegrees, innerAngles(innerEnabled.size, isRtl)) ?: return null
            if (innerEnabled[index]) FastActionTarget.Inner(index) else null
        } else {
            nearestIndex(dragDegrees, outerAngles(outerCount, isRtl))?.let(FastActionTarget::Outer)
        }
    }

    private fun nearestIndex(degrees: Float, angles: List<Float>): Int? {
        if (angles.isEmpty()) return null
        val nearest = angles.indices.minBy { index -> angularDistance(degrees, angles[index]) }
        val spacing = if (angles.size == 1) 90f else abs(angles[1] - angles[0])
        return nearest.takeIf {
            angularDistance(degrees, angles[nearest]) <= spacing * ANGLE_TOLERANCE_FRACTION
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
