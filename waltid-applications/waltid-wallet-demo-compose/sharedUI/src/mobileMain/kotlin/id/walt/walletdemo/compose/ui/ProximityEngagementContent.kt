package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import id.walt.wallet2.mobile.ProximityEngagement
import id.walt.wallet2.mobile.ProximityEngagementMethod
import id.walt.wallet2.mobile.ProximityState
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.WalletDemoProximityApprovalMode
import id.walt.walletdemo.compose.logic.WalletDemoProximityUiState
import id.walt.walletdemo.compose.ui.components.ProximityApprovalModeChoice
import id.walt.walletdemo.compose.ui.components.QrCodeCanvas
import id.walt.walletdemo.compose.ui.components.encodeProximityQrCode
import id.walt.walletdemo.compose.ui.resources.*
import id.walt.walletdemo.compose.ui.resources.proximity_qr_accessibility
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProximityEngagementContent(
    state: WalletDemoProximityUiState,
    credentialDetailsById: Map<String, CredentialDetails>,
    onShowEngagement: (ProximityEngagementMethod) -> Unit,
    onApprovalModeChange: (WalletDemoProximityApprovalMode) -> Unit,
) {
    val method = state.displayedEngagement
    val choices = state.engagementChoices
    val sharing = state.preparedSharing
    var showApprovedData by remember(sharing) { mutableStateOf(false) }
    if (showApprovedData && sharing != null) {
        AlertDialog(
            onDismissRequest = { showApprovedData = false },
            title = { Text(stringResource(Res.string.proximity_approved_data)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.proximity_prepared_one_use))
                    ProximityDisclosureSummary(sharing.review, sharing.submission, credentialDetailsById, initiallyExpanded = true)
                }
            },
            confirmButton = { TextButton(onClick = { showApprovedData = false }) { Text(stringResource(Res.string.proximity_done)) } },
        )
    }
    val header: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.actionError?.let { ProximityErrorCard(it) }
            Text(stringResource(when {
                sharing != null -> Res.string.proximity_prepared_ready
                method == ProximityEngagementMethod.Qr -> Res.string.proximity_show_qr
                method == ProximityEngagementMethod.Nfc -> Res.string.proximity_reader_hold_title
                else -> Res.string.proximity_share_in_person
            }), style = MaterialTheme.typography.titleLarge)
            if (sharing != null) {
                Text(sharing.review.readerAuthentication.mapNotNull { it.displayName }.distinct().joinToString(),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ProximityPreparedSharingCountdown(sharing)
            }
            Text(stringResource(when {
                method == null -> Res.string.proximity_choose_connection
                sharing != null -> Res.string.proximity_prepared_connection_instructions
                method == ProximityEngagementMethod.Qr -> Res.string.proximity_qr_instructions
                else -> Res.string.proximity_tap_instructions
            }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val footer: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (sharing == null) {
                ProximityApprovalModeChoice(state.approvalMode, onApprovalModeChange, compact = true, enabled = !state.refreshingEngagement)
            } else {
                TextButton(onClick = { showApprovedData = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.proximity_approved_data))
                }
            }
            if (method != null) {
                choices.filter { it != method }.forEach { other ->
                    TextButton(onClick = { onShowEngagement(other) }, enabled = !state.refreshingEngagement, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (other == ProximityEngagementMethod.Qr)
                            Res.string.proximity_show_qr_instead else Res.string.proximity_tap_instead))
                    }
                }
            }
            state.connectedRoute?.let { ProximityConnectionDetails(it) }
        }
    }
    val qrAccessibility = stringResource(Res.string.proximity_qr_accessibility)
    val qr = (state.sessionState as? ProximityState.EngagementReady)?.engagements
        ?.filterIsInstance<ProximityEngagement.Qr>()?.singleOrNull()
    if (method == ProximityEngagementMethod.Qr) {
        val qrCode = remember(qr?.payload) { qr?.let { runCatching { encodeProximityQrCode(it.payload) }.getOrNull() } }
        ProximityQrEngagementLayout(header = header, footer = footer) {
            if (state.refreshingEngagement) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (qrCode != null) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (constraints.maxWidth >= qrCode.width + 8 && constraints.maxHeight >= qrCode.height + 8) {
                        Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                            QrCodeCanvas(qrCode, Modifier.fillMaxSize()
                                .semantics { contentDescription = qrAccessibility }
                                .testTag(WalletUiTestTags.ProximityQr))
                        }
                    } else {
                        Text(stringResource(Res.string.proximity_qr_rotate), textAlign = TextAlign.Center)
                    }
                }
            } else {
                Text(stringResource(Res.string.proximity_qr_render_failed), color = MaterialTheme.colorScheme.error)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            header()
            if (method == null) choices.forEach { choice -> ProximityEngagementChoice(choice, enabled = !state.refreshingEngagement) { onShowEngagement(choice) } }
            footer()
        }
    }
}

/** Measures the real text/controls first; the QR uses the remaining viewport without losing its quiet zone.
 * Portrait keeps a 200 dp minimum; short landscape viewports keep the QR visible beside scrollable controls.
 */
@Composable
private fun ProximityQrEngagementLayout(
    header: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    qr: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Layout(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            content = { Box { header() }; Box { qr() }; Box { footer() } },
        ) { children, constraints ->
            val gap = 12.dp.roundToPx()
            val viewport = viewportHeight.roundToPx()
            val width = constraints.maxWidth
            val landscape = width >= 600.dp.roundToPx() && width > viewport
            val maxQr = minOf(width, 360.dp.roundToPx())
            val minQr = minOf(maxQr, 200.dp.roundToPx())
            val side = if (landscape) minOf(maxQr, (width - gap) / 2, viewport.coerceAtLeast(1)) else 0
            val textWidth = if (landscape) width - side - gap else width
            val textConstraints = Constraints(maxWidth = textWidth)
            val top = children[0].measure(textConstraints)
            val bottom = children[2].measure(textConstraints)
            val qrSide = if (landscape) side else minOf(maxQr, maxOf(minQr, viewport - top.height - bottom.height - gap * 2))
            val code = children[1].measure(Constraints.fixed(qrSide, qrSide))
            val height = maxOf(viewport, if (landscape) maxOf(qrSide, top.height + bottom.height + gap)
                else top.height + qrSide + bottom.height + gap * 2)
            layout(width, height) {
                if (landscape) {
                    code.placeRelative(0, (viewport - qrSide) / 2)
                    top.placeRelative(qrSide + gap, 0)
                    bottom.placeRelative(qrSide + gap, height - bottom.height)
                } else {
                    top.placeRelative(0, 0)
                    code.placeRelative((width - qrSide) / 2, top.height + (height - top.height - bottom.height - qrSide) / 2)
                    bottom.placeRelative(0, height - bottom.height)
                }
            }
        }
    }
}
