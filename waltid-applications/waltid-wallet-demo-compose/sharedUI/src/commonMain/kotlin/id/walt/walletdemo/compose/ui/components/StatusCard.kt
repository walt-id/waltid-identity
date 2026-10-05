package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletStatusKind
import id.walt.walletdemo.compose.logic.isError
import id.walt.walletdemo.compose.logic.isStatusBusy
import id.walt.walletdemo.compose.logic.isStatusExpanded
import id.walt.walletdemo.compose.logic.isStatusVisible
import id.walt.walletdemo.compose.logic.statusBanner
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import kotlin.math.abs

private val CollapsedErrorHeight = 64.dp
private const val SwipeDismissThreshold = 80f

internal enum class WalletStatusPlacement { Contextual, Footer }

@Composable
internal fun StatusCard(
    state: WalletDemoUiState,
    onDismiss: () -> Unit,
    onToggleExpanded: () -> Unit,
    placement: WalletStatusPlacement,
) {
    if (!state.isStatusVisible) return
    val banner = state.statusBanner() ?: return
    if ((banner.kind == WalletStatusKind.Error) != (placement == WalletStatusPlacement.Contextual)) return
    val dismissable = banner.kind == WalletStatusKind.Success || banner.kind == WalletStatusKind.Error
    val expanded = state.isStatusExpanded
    val containerColor = when {
        state.isError -> MaterialTheme.colorScheme.errorContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        state.isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    var dragDistance by remember(banner.key) { mutableStateOf(0f) }

    Surface(
        color = containerColor, contentColor = contentColor,
        shape = if (placement == WalletStatusPlacement.Contextual) RoundedCornerShape(12.dp) else RectangleShape,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (placement == WalletStatusPlacement.Contextual) "wallet.status.contextual" else "wallet.status.feedback")
            .semantics { liveRegion = LiveRegionMode.Polite }
            .then(
                if (dismissable) {
                    Modifier.pointerInput(banner.key) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(dragDistance) >= SwipeDismissThreshold) {
                                    onDismiss()
                                }
                                dragDistance = 0f
                            },
                            onDragCancel = { dragDistance = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                dragDistance += dragAmount
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (banner.kind == WalletStatusKind.Error && !expanded) {
                        Modifier.heightIn(min = CollapsedErrorHeight)
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
                .padding(start = if (placement == WalletStatusPlacement.Contextual) 16.dp else 0.dp,
                    end = 4.dp, top = 4.dp, bottom = 4.dp),
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
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse error" else "Expand error",
                    )
                }
            }
            if (dismissable) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(WalletUiTestTags.StatusDismiss),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss status",
                    )
                }
            }
        }
    }
}
