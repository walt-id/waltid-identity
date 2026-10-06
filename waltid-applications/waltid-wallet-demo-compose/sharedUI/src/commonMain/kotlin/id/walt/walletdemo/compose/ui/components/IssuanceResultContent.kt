package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** Only actual saved credentials have a values/details route. Pending work is explicitly different. */
@Composable
internal fun IssuanceResultContent(
    receipt: WalletDemoIssuanceReceipt?,
    saved: List<WalletDemoCredential>,
    pending: List<WalletDemoDeferredCredential>,
    busy: Boolean,
    onResume: (String) -> Unit,
) {
    receipt?.issuer?.let { IssuanceIssuerSection(it) }
    if (saved.isNotEmpty()) WalletSection(stringResource(Res.string.issuance_saved_count, saved.size)) {
        saved.forEachIndexed { index, credential ->
            if (index > 0) HorizontalDivider()
            SavedCredentialRow(credential)
        }
    }
    if (pending.isNotEmpty()) WalletSection(stringResource(Res.string.issuance_pending_count, pending.size)) {
        pending.forEachIndexed { index, credential ->
            if (index > 0) HorizontalDivider()
            PendingCredentialRow(credential, busy, onResume)
        }
    }
    receipt?.problem?.let { problem ->
        WalletSection(stringResource(Res.string.issuance_original_result)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (problem.failedTargetCount > 0) Text(stringResource(Res.string.issuance_failed_targets, problem.failedTargetCount))
                if (problem.notAttemptedTargetCount > 0) Text(stringResource(Res.string.issuance_unattempted_targets, problem.notAttemptedTargetCount))
                Text(problem.message, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.issuance_consumed_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("issuance-progress"))
}

@Composable
private fun SavedCredentialRow(credential: WalletDemoCredential) {
    var detailsOpen by rememberSaveable(credential.id) { mutableStateOf(false) }
    val navigation = LocalWalletReviewNavigation.current
    val summary = remember(credential) { credential.toCardDisplayData() }
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) {
        if (navigation != null) navigation.openStored(credential.id) else detailsOpen = true
    }
        .testTag("issuance-saved-${credential.id}").padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        CredentialSummaryRow(summary.toCardArt(), summary.issuer, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
    if (detailsOpen) {
        val details = remember(credential) { credential.toCredentialDetails() }
        CredentialInformationSheet(details, onDismiss = { detailsOpen = false })
    }
}

@Composable
private fun PendingCredentialRow(credential: WalletDemoDeferredCredential, busy: Boolean, onResume: (String) -> Unit) {
    val display = remember(credential.displayMetadataJson) { credential.credentialDisplay() }
    val fallback = stringResource(Res.string.issuance_pending_credential)
    val art = display?.toCardArt(credential.id, fallback) ?: CredentialCardArtModel(credential.id, fallback)
    val statusText = stringResource(when (credential.status) {
        WalletDemoContinuationStatus.AwaitingIssuer -> Res.string.issuance_awaiting_issuer
        WalletDemoContinuationStatus.AwaitingLocalSave -> Res.string.issuance_awaiting_local_save
        WalletDemoContinuationStatus.RemoteOutcomeUncertain -> Res.string.issuance_remote_uncertain
        WalletDemoContinuationStatus.StorageOutcomeUncertain -> Res.string.issuance_storage_uncertain
        WalletDemoContinuationStatus.Unresolved -> Res.string.issuance_pending_unknown
    })
    Column(Modifier.padding(16.dp).testTag("issuance-pending-${credential.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CredentialSummaryRow(art, statusText)
        if (credential.status == WalletDemoContinuationStatus.AwaitingIssuer) credential.intervalSeconds?.takeIf { it > 0 }?.let {
            Text(stringResource(Res.string.issuance_poll_interval, it), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (credential.status.canResume) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onResume(credential.id) }, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("issuance-resume-${credential.id}")) {
                Text(stringResource(when (credential.status) {
                    WalletDemoContinuationStatus.AwaitingLocalSave -> Res.string.issuance_finish_saving
                    WalletDemoContinuationStatus.AwaitingIssuer -> Res.string.issuance_check_issuer
                    else -> Res.string.issuance_continue
                }))
            }
        }
    }
}
