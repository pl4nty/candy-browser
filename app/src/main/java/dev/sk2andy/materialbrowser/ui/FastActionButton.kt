package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import dev.sk2andy.materialbrowser.browser.BrowserTab
import kotlin.math.roundToInt

internal object FastActionButtonTestTags {
    const val Button = "fast_action_button"
    const val Scrim = "fast_action_button_scrim"
    const val Overview = "fast_action:tabs"
    const val Menu = "fast_action:menu"
    fun action(action: FastAction): String = "fast_action:${action.wireValue}"
    fun tab(tabId: String): String = "fast_action_tab:$tabId"
}

private val ButtonSize = 56.dp
private val InnerItemSize = 44.dp
private val OuterItemSize = 56.dp
private val InnerRadius = 84.dp
private val OuterRadius = 150.dp
// The disc edge runs through the centres of the outer bubbles, as in Opera GX.
private val DiscRadius = OuterRadius

/**
 * Centred floating button modelled on Opera GX's fast action button. Pressing it grows a half-disc
 * with page actions on an inner ring and recent tabs plus the tab overview on an outer arc;
 * sliding toward an item highlights it and runs it on release.
 *
 * @param recentTabs outer-arc tabs, oldest first, as ordered by [FastActionButtonRules.recentTabIds].
 * @param showMenu adds the main-menu bubble at the start of the outer arc, in place of one tab.
 * @param onTap when set, a quick tap runs it instead of opening the disc, and the disc opens on
 * hold or slide. Used when the button replaces the address bar, so a tap edits the address.
 */
@Composable
internal fun BoxScope.FastActionButton(
    availability: FastActionAvailability,
    recentTabs: List<BrowserTab>,
    selectedTabId: String,
    favicons: Map<String, Bitmap>,
    tabCount: Int,
    isLoading: Boolean,
    bottomPadding: Dp,
    onAction: (FastAction) -> Unit,
    onSelectTab: (String) -> Unit,
    onTabs: () -> Unit,
    modifier: Modifier = Modifier,
    showMenu: Boolean = false,
    onMenu: () -> Unit = {},
    onTap: (() -> Unit)? = null,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val innerRadiusPx = with(density) { InnerRadius.toPx() }
    val outerRadiusPx = with(density) { OuterRadius.toPx() }
    val actions = FastActionButtonRules.InnerActions
    val innerEnabled = actions.map { FastActionButtonRules.isEnabled(it, availability) }
    val tabsById = remember(recentTabs) { recentTabs.associateBy(BrowserTab::id) }
    val outerItems = remember(recentTabs, showMenu) {
        FastActionButtonRules.outerItems(recentTabs.map(BrowserTab::id), includeMenu = showMenu)
    }
    val outerCount = outerItems.size
    val innerOffsets = remember(isRtl, innerRadiusPx) {
        FastActionButtonRules.offsets(FastActionButtonRules.innerAngles(actions.size, isRtl), innerRadiusPx)
    }
    val outerOffsets = remember(outerCount, isRtl, outerRadiusPx) {
        FastActionButtonRules.offsets(FastActionButtonRules.outerAngles(outerCount, isRtl), outerRadiusPx)
    }
    var expanded by remember { mutableStateOf(false) }
    var highlighted by remember { mutableStateOf<FastActionTarget?>(null) }
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = FastActionButtonRules.ITEM_SPRING_STIFFNESS),
        label = "fastActionItems",
    )
    // The disc grows out of the button while it is held, ahead of the items.
    val discProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = FastActionButtonRules.DISC_GROW_MILLIS, easing = FastOutSlowInEasing),
        label = "fastActionDisc",
    )
    val currentInnerEnabled by rememberUpdatedState(innerEnabled)
    val currentOuterCount by rememberUpdatedState(outerCount)
    val currentOuterItems by rememberUpdatedState(outerItems)
    val currentOnAction by rememberUpdatedState(onAction)
    val currentOnSelectTab by rememberUpdatedState(onSelectTab)
    val currentOnTabs by rememberUpdatedState(onTabs)
    val currentOnMenu by rememberUpdatedState(onMenu)
    val currentOnTap by rememberUpdatedState(onTap)

    fun close() {
        expanded = false
        highlighted = null
    }

    fun run(target: FastActionTarget) {
        close()
        if (FastActionButtonRules.confirmsWithHaptic(target)) view.performConfirmHaptic()
        when (target) {
            is FastActionTarget.Inner -> currentOnAction(actions[target.index])
            is FastActionTarget.Outer -> when (val item = currentOuterItems.getOrNull(target.index)) {
                is FastActionOuterItem.Tab -> currentOnSelectTab(item.id)
                FastActionOuterItem.Overview -> currentOnTabs()
                FastActionOuterItem.Menu -> currentOnMenu()
                null -> Unit
            }
        }
    }

    // Every layer is anchored on the button centre at the bottom centre of the chrome.
    fun Modifier.anchored(itemSize: Dp, offset: FastActionOffset): Modifier = this
        .align(Alignment.BottomCenter)
        .padding(bottom = bottomPadding + (ButtonSize - itemSize) / 2)
        .absoluteOffset {
            IntOffset((offset.x * progress).roundToInt(), (offset.y * progress).roundToInt())
        }

    if (expanded || progress > 0.01f || discProgress > 0.01f) {
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
                    onClick = ::close,
                ),
        )
        val discRadiusPx = with(density) { DiscRadius.toPx() }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding + ButtonSize / 2)
                .absoluteOffset { IntOffset(0, discRadiusPx.roundToInt()) }
                .zIndex(29.5f)
                .requiredSize(DiscRadius * 2)
                .graphicsLayer {
                    val grow = discProgress.coerceIn(0f, 1f)
                    val scale = ButtonSize.toPx() / DiscRadius.toPx() / 2f * (1f - grow) + grow
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.5f + 0.5f * grow
                }
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f), CircleShape),
        )
        actions.forEachIndexed { index, action ->
            val isHighlighted = highlighted == FastActionTarget.Inner(index)
            val label = stringResource(action.labelRes())
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .anchored(InnerItemSize, innerOffsets[index])
                    .zIndex(30f)
                    .size(InnerItemSize)
                    .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
                    .background(
                        if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape,
                    )
                    .testTag(FastActionButtonTestTags.action(action))
                    .semantics { contentDescription = label }
                    .clickable(enabled = innerEnabled[index] && expanded) { run(FastActionTarget.Inner(index)) },
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides if (isHighlighted) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                ) {
                    FastActionGlyph(action = action, enabled = innerEnabled[index], isLoading = isLoading)
                }
            }
        }
        outerItems.forEachIndexed { index, item ->
            val tab = (item as? FastActionOuterItem.Tab)?.let { tabsById[it.id] }
            val isHighlighted = highlighted == FastActionTarget.Outer(index)
            val label = item.label(tabsById)
            Surface(
                shape = CircleShape,
                color = if (item is FastActionOuterItem.Tab) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = MaterialTheme.colorScheme.primary,
                border = if (tab != null && tab.id == selectedTabId) {
                    BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
                shadowElevation = 6.dp,
                modifier = Modifier
                    .anchored(OuterItemSize, outerOffsets[index])
                    .zIndex(30f)
                    .size(OuterItemSize)
                    .scale(if (isHighlighted) 1.18f else 0.6f + 0.4f * progress)
                    .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
                    .testTag(
                        when (item) {
                            is FastActionOuterItem.Tab -> FastActionButtonTestTags.tab(item.id)
                            FastActionOuterItem.Overview -> FastActionButtonTestTags.Overview
                            FastActionOuterItem.Menu -> FastActionButtonTestTags.Menu
                        },
                    )
                    .semantics { contentDescription = label }
                    .clickable(enabled = expanded) { run(FastActionTarget.Outer(index)) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        tab != null -> TabFavicon(tab = tab, favicon = favicons[tab.id], size = 28.dp)
                        item == FastActionOuterItem.Menu -> Icon(Icons.Default.MoreVert, null)
                        else -> AddressBarTabCounterGlyph(tabCount = tabCount)
                    }
                }
            }
        }
        highlighted?.let { target ->
            val text = when (target) {
                is FastActionTarget.Inner -> stringResource(actions[target.index].labelRes())
                is FastActionTarget.Outer -> outerItems.getOrNull(target.index)?.label(tabsById).orEmpty()
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding + ButtonSize / 2 + OuterRadius + OuterItemSize / 2 + 16.dp)
                    .padding(horizontal = 24.dp)
                    .zIndex(31f),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
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
        shadowElevation = if (expanded) 0.dp else 6.dp,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottomPadding)
            .zIndex(32f)
            .size(ButtonSize)
            .testTag(FastActionButtonTestTags.Button)
            .semantics { contentDescription = buttonDescription }
            .pointerInput(isRtl, innerRadiusPx, outerRadiusPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val openedByPress = !expanded
                    var dragging = false
                    var total = Offset.Zero
                    val tap = currentOnTap
                    if (openedByPress && tap != null) {
                        // A quick tap runs onTap; holding or sliding opens the disc instead.
                        val outcome = withTimeoutOrNull(
                            FastActionButtonRules.holdToOpenMillis(viewConfiguration.longPressTimeoutMillis),
                        ) {
                            var result = PressOutcome.Cancelled
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    result = PressOutcome.Tap
                                    break
                                }
                                total += change.positionChange()
                                if (total.getDistance() > viewConfiguration.touchSlop) {
                                    change.consume()
                                    result = PressOutcome.Slide
                                    break
                                }
                            }
                            result
                        } ?: PressOutcome.Hold
                        when (outcome) {
                            PressOutcome.Tap -> {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                tap()
                                return@awaitEachGesture
                            }
                            PressOutcome.Cancelled -> return@awaitEachGesture
                            PressOutcome.Slide -> dragging = true
                            PressOutcome.Hold -> Unit
                        }
                    }
                    // Without onTap, pressing opens the disc straight away so it grows while held.
                    if (openedByPress) {
                        expanded = true
                        highlighted = null
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            if (dragging) {
                                highlighted?.let(::run) ?: close()
                            } else if (!openedByPress) {
                                // A tap on an open disc closes it; a tap that opened it keeps it open.
                                close()
                            }
                            break
                        }
                        total += change.positionChange()
                        if (!dragging && total.getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                        }
                        if (dragging) {
                            change.consume()
                            val target = FastActionButtonRules.selectedTarget(
                                drag = FastActionOffset(total.x, total.y),
                                innerEnabled = currentInnerEnabled,
                                outerCount = currentOuterCount,
                                innerRadiusPx = innerRadiusPx,
                                outerRadiusPx = outerRadiusPx,
                                isRtl = isRtl,
                            )
                            if (target != highlighted) {
                                highlighted = target
                                if (target != null) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        }
                    }
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            // GX-style ring glyph.
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(2.dp, LocalContentColor.current, CircleShape),
            )
        }
    }
}

private enum class PressOutcome { Tap, Slide, Hold, Cancelled }

@Composable
private fun FastActionOuterItem.label(tabsById: Map<String, BrowserTab>): String = when (this) {
    is FastActionOuterItem.Tab -> tabsById[id]?.let { displayTabTitle(it) }.orEmpty()
    FastActionOuterItem.Overview -> stringResource(R.string.address_bar_action_tabs)
    FastActionOuterItem.Menu -> stringResource(R.string.cd_more_options)
}

@Composable
private fun FastActionGlyph(action: FastAction, enabled: Boolean, isLoading: Boolean) {
    val tint = LocalContentColor.current.let { if (enabled) it else it.copy(alpha = 0.38f) }
    when (action) {
        FastAction.Reload -> Icon(
            painterResource(if (isLoading) R.drawable.ic_symbol_close else R.drawable.ic_symbol_refresh),
            null,
            tint = tint,
        )
        FastAction.CloseTab -> Icon(Icons.Default.Close, null, tint = tint)
        FastAction.Search -> Icon(Icons.Default.Search, null, tint = tint)
        FastAction.NewTab -> Icon(Icons.Default.Add, null, tint = tint)
        FastAction.Share -> Icon(Icons.Default.Share, null, tint = tint)
    }
}

@StringRes
internal fun FastAction.labelRes(): Int = when (this) {
    FastAction.Reload -> R.string.action_reload
    FastAction.CloseTab -> R.string.cd_close_tab
    FastAction.Search -> R.string.settings_section_search
    FastAction.NewTab -> R.string.cd_new_tab
    FastAction.Share -> R.string.action_share
}
