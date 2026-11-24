package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.ui.draw.rotate
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletStatusKind
import id.walt.walletdemo.compose.logic.isStatusBusy
import id.walt.walletdemo.compose.logic.isStatusExpanded
import id.walt.walletdemo.compose.logic.isStatusVisible
import id.walt.walletdemo.compose.logic.statusBanner
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import kotlin.math.abs
import kotlin.math.roundToInt

private val ErrorMinimumHeight = 64.dp
private val SwipeDismissThreshold = 80.dp

internal enum class WalletNoticeTone { Neutral, Error, Warning }

@Composable
internal fun WalletNoticeCard(
    modifier: Modifier = Modifier,
    tone: WalletNoticeTone = WalletNoticeTone.Neutral,
    onDismiss: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val warning = tone == WalletNoticeTone.Warning
    val background = when {
        !warning -> colors.surfaceContainerHigh
        dark -> Color(0xFF342A1B)
        else -> Color(0xFFFDF0D5)
    }
    val foreground = when (tone) {
        WalletNoticeTone.Neutral -> colors.onSurfaceVariant
        WalletNoticeTone.Error -> colors.error
        WalletNoticeTone.Warning -> if (dark) Color(0xFFFFE5AE) else Color(0xFF4B3208)
    }
    val border = if (warning) BorderStroke(1.dp, if (dark) Color(0xFF8E754D) else Color(0xFFC6A35C)) else null
    var dragDistance by remember { mutableStateOf(0f) }
    val dismiss by rememberUpdatedState(onDismiss)
    val swipe = if (onDismiss != null) {
        Modifier.pointerInput(Unit) {
                val threshold = SwipeDismissThreshold.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(dragDistance) >= threshold) dismiss?.invoke()
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f },
                    onHorizontalDrag = { _, amount -> dragDistance += amount },
                )
            }.offset { IntOffset(dragDistance.roundToInt(), 0) }
    } else Modifier
    Surface(
        modifier = modifier.fillMaxWidth().then(swipe).semantics { liveRegion = LiveRegionMode.Polite },
        color = background,
        contentColor = foreground,
        border = border,
        shape = MaterialTheme.shapes.small,
        content = content,
    )
}

@Composable
internal fun WalletNoticeClose(label: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onDismiss, modifier = modifier) {
        WalletIcon(WalletSymbol.Decline, label)
    }
}

@Composable
internal fun StatusCard(
    state: WalletDemoUiState,
    onDismiss: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    if (!state.isStatusVisible) return
    val banner = state.statusBanner() ?: return
    val dismissable = banner.kind == WalletStatusKind.Success || banner.kind == WalletStatusKind.Error
    val expanded = state.isStatusExpanded
    val reduceMotion = LocalWalletVisualPreferences.current.reduceMotion
    val expansion = updateTransition(expanded, label = "error-expansion")
    val rotation by expansion.animateFloat(transitionSpec = { WalletMotion.disclosure(reduceMotion) },
        label = "error-chevron") { if (it) 180f else 0f }
    TrackWalletExpansion(expansion.isRunning || expansion.currentState != expansion.targetState)
    key(banner.key) {
        WalletNoticeCard(
            modifier = Modifier.testTag("wallet.status.feedback"),
            tone = if (banner.kind == WalletStatusKind.Error) WalletNoticeTone.Error else WalletNoticeTone.Neutral,
            onDismiss = onDismiss.takeIf { dismissable },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(WalletMotion.disclosure(reduceMotion), alignment = Alignment.TopStart)
                    .then(
                        if (banner.kind == WalletStatusKind.Error) {
                            Modifier.heightIn(min = ErrorMinimumHeight)
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (banner.kind == WalletStatusKind.Error) {
                            Modifier.clickable(onClick = onToggleExpanded).testTag(WalletUiTestTags.Status)
                        } else {
                            Modifier
                        },
                    )
                    .heightIn(min = 48.dp).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isStatusBusy) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                }
                Text(
                    text = banner.message,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .then(if (banner.kind == WalletStatusKind.Error) Modifier else Modifier.testTag(WalletUiTestTags.Status))
                        .semantics { contentDescription = banner.message },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (banner.kind == WalletStatusKind.Error && !expanded) 2 else Int.MAX_VALUE,
                    overflow = if (banner.kind == WalletStatusKind.Error && !expanded) {
                        TextOverflow.Ellipsis
                    } else {
                        TextOverflow.Clip
                    },
                )
                if (banner.kind == WalletStatusKind.Error) {
                    IconButton(
                        onClick = onToggleExpanded,
                        modifier = Modifier.testTag(WalletUiTestTags.StatusExpand),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            modifier = Modifier.rotate(rotation),
                            contentDescription = if (expanded) "Collapse error" else "Expand error",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (dismissable) {
                    WalletNoticeClose("Dismiss status", onDismiss, Modifier.testTag(WalletUiTestTags.StatusDismiss))
                }
            }
        }
    }
}
