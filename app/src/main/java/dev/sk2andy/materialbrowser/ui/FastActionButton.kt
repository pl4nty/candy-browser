package dev.sk2andy.materialbrowser.ui

import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.R
import kotlin.math.roundToInt

internal object FastActionButtonTestTags {
    const val Button = "fast_action_button"
    const val Scrim = "fast_action_button_scrim"
    fun action(action: FastAction): String = "fast_action:${action.wireValue}"
}

private val ButtonSize = 56.dp
private val ItemSize = 48.dp
private val ArcRadius = 124.dp
private val EdgePadding = 16.dp

/**
 * Floating button that opens an arc of browser actions. A tap toggles the arc; pressing and
 * sliding toward an action highlights it and runs it on release, so one gesture is enough.
 */
@Composable
internal fun BoxScope.FastActionButton(
    actions: List<FastAction>,
    availability: FastActionAvailability,
    tabCount: Int,
    isLoading: Boolean,
    bottomPadding: Dp,
    onAction: (FastAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    val view = LocalView.current
    val density = LocalDensity.current
    val edge = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        FastActionButtonEdge.Start
    } else {
        FastActionButtonEdge.End
    }
    val radiusPx = with(density) { ArcRadius.toPx() }
    val enabled = actions.map { FastActionButtonRules.isEnabled(it, availability) }
    val offsets = remember(actions.size, edge, radiusPx) {
        FastActionButtonRules.itemOffsets(actions.size, edge, radiusPx)
    }
    var expanded by remember { mutableStateOf(false) }
    var highlighted by remember { mutableStateOf<Int?>(null) }
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 700f),
        label = "fastActionArc",
    )
    val currentOnAction by rememberUpdatedState(onAction)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentActions by rememberUpdatedState(actions)

    fun run(index: Int) {
        expanded = false
        highlighted = null
        view.performConfirmHaptic()
        currentOnAction(currentActions[index])
    }

    val alignment = Alignment.BottomEnd
    val buttonPadding = Modifier.padding(end = EdgePadding, bottom = bottomPadding)
    // Items share the button's centre before their arc offset is applied.
    val itemInset = (ButtonSize - ItemSize) / 2
    val itemPadding = Modifier.padding(
        end = EdgePadding + itemInset,
        bottom = bottomPadding + itemInset,
    )

    if (expanded || progress > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(29f)
                .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .testTag(FastActionButtonTestTags.Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    expanded = false
                    highlighted = null
                },
        )
        actions.forEachIndexed { index, action ->
            val offset = offsets[index]
            val isHighlighted = highlighted == index
            val label = stringResource(action.labelRes())
            Surface(
                shape = CircleShape,
                color = if (isHighlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (isHighlighted) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(alignment)
                    .then(itemPadding)
                    .absoluteOffset {
                        IntOffset(
                            (offset.x * progress).roundToInt(),
                            (offset.y * progress).roundToInt(),
                        )
                    }
                    .zIndex(30f)
                    .size(ItemSize)
                    .scale(if (isHighlighted) 1.15f else 0.6f + 0.4f * progress)
                    .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
                    .testTag(FastActionButtonTestTags.action(action))
                    .semantics { contentDescription = label }
                    .clickable(enabled = enabled[index] && expanded) { run(index) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    FastActionGlyph(
                        action = action,
                        tabCount = tabCount,
                        isLoading = isLoading,
                        enabled = enabled[index],
                    )
                }
            }
        }
        highlighted?.let { index ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .align(alignment)
                    .padding(end = EdgePadding, bottom = bottomPadding + ButtonSize + ArcRadius + 32.dp)
                    .zIndex(31f),
            ) {
                Text(
                    text = stringResource(actions[index].labelRes()),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }

    val buttonDescription = stringResource(R.string.cd_fast_action_button)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
        modifier = modifier
            .align(alignment)
            .then(buttonPadding)
            .zIndex(32f)
            .size(ButtonSize)
            .testTag(FastActionButtonTestTags.Button)
            .semantics { contentDescription = buttonDescription }
            .pointerInput(edge, radiusPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var dragging = false
                    var total = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            if (dragging) {
                                val index = highlighted
                                if (index != null) {
                                    run(index)
                                } else {
                                    expanded = false
                                }
                            } else {
                                expanded = !expanded
                                highlighted = null
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                            break
                        }
                        total += change.positionChange()
                        if (!dragging && total.getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                            expanded = true
                        }
                        if (dragging) {
                            change.consume()
                            val index = FastActionButtonRules.selectedIndex(
                                drag = FastActionOffset(total.x, total.y),
                                enabled = currentEnabled,
                                edge = edge,
                                radiusPx = radiusPx,
                            )
                            if (index != highlighted) {
                                highlighted = index
                                if (index != null) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        }
                    }
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(
                    if (expanded) R.drawable.ic_symbol_close else R.drawable.ic_symbol_fast_action,
                ),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun FastActionGlyph(
    action: FastAction,
    tabCount: Int,
    isLoading: Boolean,
    enabled: Boolean,
) {
    val tint = LocalContentColor.current.let { if (enabled) it else it.copy(alpha = 0.38f) }
    CompositionLocalProvider(LocalContentColor provides tint) {
        when (action) {
            FastAction.Search -> Icon(Icons.Default.Search, contentDescription = null)
            FastAction.NewTab -> Icon(Icons.Default.Add, contentDescription = null)
            FastAction.CloseTab -> Icon(Icons.Default.Close, contentDescription = null)
            FastAction.Home -> Icon(Icons.Default.Home, contentDescription = null)
            FastAction.Tabs -> AddressBarTabCounterGlyph(tabCount = tabCount)
            FastAction.Back -> Icon(
                painterResource(R.drawable.ic_symbol_arrow_back),
                contentDescription = null,
            )
            FastAction.Forward -> Icon(
                painterResource(R.drawable.ic_symbol_arrow_forward),
                contentDescription = null,
            )
            FastAction.Reload -> Icon(
                painterResource(
                    if (isLoading) R.drawable.ic_symbol_close else R.drawable.ic_symbol_refresh,
                ),
                contentDescription = null,
            )
        }
    }
}

@StringRes
internal fun FastAction.labelRes(): Int = when (this) {
    FastAction.Search -> R.string.settings_section_search
    FastAction.Back -> R.string.action_back
    FastAction.Forward -> R.string.action_forward
    FastAction.Reload -> R.string.action_reload
    FastAction.Tabs -> R.string.address_bar_action_tabs
    FastAction.NewTab -> R.string.cd_new_tab
    FastAction.CloseTab -> R.string.cd_close_tab
    FastAction.Home -> R.string.action_home
}
