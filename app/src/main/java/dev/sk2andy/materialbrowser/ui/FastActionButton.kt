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
 * Centred floating button modelled on Opera GX's fast action button. A tap toggles a half-disc
 * with page actions on an inner ring and recent tabs plus the tab overview on an outer arc;
 * pressing and sliding toward an item highlights it and runs it on release.
 *
 * @param recentTabs outer-arc tabs, oldest first, as ordered by [FastActionButtonRules.recentTabIds].
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
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val innerRadiusPx = with(density) { InnerRadius.toPx() }
    val outerRadiusPx = with(density) { OuterRadius.toPx() }
    val actions = FastActionButtonRules.InnerActions
    val innerEnabled = actions.map { FastActionButtonRules.isEnabled(it, availability) }
    val outerCount = recentTabs.size + 1
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
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 700f),
        label = "fastActionItems",
    )
    // The disc grows out of the button while it is held, ahead of the items.
    val discProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "fastActionDisc",
    )
    val currentInnerEnabled by rememberUpdatedState(innerEnabled)
    val currentOuterCount by rememberUpdatedState(outerCount)
    val currentRecentTabs by rememberUpdatedState(recentTabs)
    val currentOnAction by rememberUpdatedState(onAction)
    val currentOnSelectTab by rememberUpdatedState(onSelectTab)
    val currentOnTabs by rememberUpdatedState(onTabs)

    fun close() {
        expanded = false
        highlighted = null
    }

    fun run(target: FastActionTarget) {
        close()
        view.performConfirmHaptic()
        when (target) {
            is FastActionTarget.Inner -> currentOnAction(actions[target.index])
            is FastActionTarget.Outer -> {
                val tab = currentRecentTabs.getOrNull(target.index)
                if (tab != null) currentOnSelectTab(tab.id) else currentOnTabs()
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
        repeat(outerCount) { index ->
            val tab = recentTabs.getOrNull(index)
            val isHighlighted = highlighted == FastActionTarget.Outer(index)
            val label = if (tab != null) displayTabTitle(tab) else stringResource(R.string.address_bar_action_tabs)
            Surface(
                shape = CircleShape,
                color = if (tab == null) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHighest,
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
                    .testTag(tab?.let { FastActionButtonTestTags.tab(it.id) } ?: FastActionButtonTestTags.Overview)
                    .semantics { contentDescription = label }
                    .clickable(enabled = expanded) { run(FastActionTarget.Outer(index)) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (tab != null) {
                        TabFavicon(tab = tab, favicon = favicons[tab.id], size = 28.dp)
                    } else {
                        AddressBarTabCounterGlyph(tabCount = tabCount)
                    }
                }
            }
        }
        highlighted?.let { target ->
            val text = when (target) {
                is FastActionTarget.Inner -> stringResource(actions[target.index].labelRes())
                is FastActionTarget.Outer -> recentTabs.getOrNull(target.index)?.let { displayTabTitle(it) }
                    ?: stringResource(R.string.address_bar_action_tabs)
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
                    // Pressing opens the disc straight away so it can grow while held.
                    val openedByPress = !expanded
                    if (openedByPress) {
                        expanded = true
                        highlighted = null
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    var dragging = false
                    var total = Offset.Zero
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
