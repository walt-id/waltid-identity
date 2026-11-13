package id.walt.walletdemo.compose.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences
import kotlin.time.Instant
import id.walt.walletdemo.compose.logic.WalletDateTime
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatus
import id.walt.walletdemo.compose.logic.WalletDemoStatusReadState
import id.walt.walletdemo.compose.logic.displayStatus
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.credential_status_discovery_failed_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_read_unavailable
import id.walt.walletdemo.compose.ui.resources.credential_status_conflicting_reference_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_reserved_value
import id.walt.walletdemo.compose.ui.resources.credential_status_custom_value
import id.walt.walletdemo.compose.ui.resources.credential_status_reference_outdated
import id.walt.walletdemo.compose.ui.resources.credential_status_adverse_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_ambiguous_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_checked_at
import id.walt.walletdemo.compose.ui.resources.credential_status_checking
import id.walt.walletdemo.compose.ui.resources.credential_status_checks
import id.walt.walletdemo.compose.ui.resources.credential_status_conflict_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_credential_time_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_details
import id.walt.walletdemo.compose.ui.resources.credential_status_invalid
import id.walt.walletdemo.compose.ui.resources.credential_status_invalid_reference
import id.walt.walletdemo.compose.ui.resources.credential_status_last_result
import id.walt.walletdemo.compose.ui.resources.credential_status_latest_attempt
import id.walt.walletdemo.compose.ui.resources.credential_status_limit_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_no_issue
import id.walt.walletdemo.compose.ui.resources.credential_status_not_checked
import id.walt.walletdemo.compose.ui.resources.credential_status_not_checked_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_not_provided_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_not_yet_valid_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_partial_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_purpose_token
import id.walt.walletdemo.compose.ui.resources.credential_status_rate_limited_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_reference
import id.walt.walletdemo.compose.ui.resources.credential_status_reference_auth_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_reference_invalid_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_refresh
import id.walt.walletdemo.compose.ui.resources.credential_status_retry_from
import id.walt.walletdemo.compose.ui.resources.credential_status_revoked
import id.walt.walletdemo.compose.ui.resources.credential_status_server_unsupported_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_stale_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_suspended
import id.walt.walletdemo.compose.ui.resources.credential_status_title
import id.walt.walletdemo.compose.ui.resources.credential_status_transport_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_unavailable
import id.walt.walletdemo.compose.ui.resources.credential_status_unsupported
import id.walt.walletdemo.compose.ui.resources.credential_status_unsupported_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_unverified_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_update_available
import id.walt.walletdemo.compose.ui.resources.credential_status_valid_from
import id.walt.walletdemo.compose.ui.resources.metadata_collapsed
import id.walt.walletdemo.compose.ui.resources.metadata_expanded
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CredentialStatusSection(credentialId: String, compact: Boolean = false) {
    val context = LocalCredentialStatusUi.current
    val status = context.snapshot.displayStatus(credentialId)
    var expanded by rememberSaveable(credentialId) { mutableStateOf(false) }
    val refreshing = credentialId in context.snapshot.refreshing
    val reduceMotion = LocalWalletVisualPreferences.current.reduceMotion
    val expandedRotation = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -90f else 90f
    val rotation by animateFloatAsState(if (expanded) expandedRotation else 0f,
        WalletMotion.disclosure(reduceMotion), label = "credential-status-chevron")
    val disclosureLabel = stringResource(Res.string.credential_status_details)
    val disclosureState = stringResource(if (expanded) Res.string.metadata_expanded else Res.string.metadata_collapsed)
    val baselineOffset = with(LocalDensity.current) { 5.sp.roundToPx() }
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            if (compact) SettingsDivider()
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CredentialStatusIcon(status, Modifier.alignBy { it.measuredHeight / 2 + baselineOffset })
                Text(status.title(), Modifier.weight(1f).alignBy(FirstBaseline), style = MaterialTheme.typography.bodyMedium)
                if (!compact && (status.action in setOf("CHECK", "RETRY") || refreshing) && context.check != null) {
                    val refreshLabel = stringResource(if (refreshing) Res.string.credential_status_checking else Res.string.credential_status_refresh)
                    IconButton(onClick = { context.check.invoke(credentialId) },
                        enabled = !refreshing && !context.checking && context.snapshot.readState != WalletDemoStatusReadState.Loading,
                        modifier = Modifier.alignBy { it.measuredHeight / 2 + baselineOffset }
                            .testTag("credential-status-check-$credentialId").semantics { contentDescription = refreshLabel }) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else WalletIcon(WalletSymbol.Retry, null)
                    }
                }
                IconButton(onClick = { expanded = !expanded },
                    enabled = context.snapshot.readState != WalletDemoStatusReadState.Loading || credentialId in context.snapshot.credentials,
                    modifier = Modifier.alignBy { it.measuredHeight / 2 + baselineOffset }
                        .testTag("credential-status-details-$credentialId").semantics { stateDescription = disclosureState }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, disclosureLabel, Modifier.rotate(rotation))
                }
            }
            if (!compact) status.lastCheckedAt?.let { runCatching { WalletDateTime.format(Instant.parse(it)).date }.getOrNull() }?.let { date ->
                Text(stringResource(Res.string.credential_status_checked_at, date),
                    Modifier.padding(start = 52.dp, end = 16.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WalletExpandableContent(expanded) {
                Column {
                    SettingsDivider()
                    if (context.snapshot.readState == WalletDemoStatusReadState.Unavailable ||
                        (context.snapshot.available && credentialId !in context.snapshot.credentials)) {
                        SettingsNotice(stringResource(Res.string.credential_status_read_unavailable))
                    }
                    context.snapshot.credentials[credentialId]?.let { CredentialStatusFacts(credentialId, it) }
                }
            }
        }
    }
    if (compact) content()
    else WalletSection(stringResource(Res.string.credential_status_title), modifier = Modifier.testTag("credential-status-$credentialId")) { content() }
}

@Composable
private fun CredentialStatusFacts(credentialId: String, status: WalletDemoCredentialStatus) {
    val showReferences = status.references.size > 1 || status.references.any {
        it.failure != null || it.conflict || it.problem != null || it.result == null || it.result !in setOf("CLEAR")
    }
    status.discoveryProblem?.let {
        SettingsNotice(stringResource(if (it == "RESOURCE_LIMIT") Res.string.credential_status_limit_detail
            else Res.string.credential_status_discovery_failed_detail))
    }
    if (!showReferences || status.references.size == 1) status.lastCheckedAt?.let(::statusDate)?.let { date ->
        StatusFact(stringResource(Res.string.credential_status_last_result), date, "credential-status-last-checked-$credentialId")
    }
    if (!showReferences && status.freshness == "STALE") SettingsNotice(stringResource(Res.string.credential_status_stale_detail))
    if (!showReferences && status.purposes.isNotEmpty()) StatusFact(stringResource(Res.string.credential_status_checks),
        status.purposes.map { statusPurposeTitle(it) }.distinct().joinToString(", "))
    if (status.coverage == "PARTIAL") SettingsNotice(stringResource(Res.string.credential_status_partial_detail))
    if (showReferences) status.references.forEach { reference ->
        SettingsDivider()
        val label = reference.purpose?.let { statusPurposeTitle(it) }
            ?: stringResource(if (reference.protocolName == "TOKEN_STATUS_LIST") Res.string.credential_status_purpose_token else Res.string.credential_status_reference)
        val result = stringResource(when (reference.result) {
            "CLEAR" -> Res.string.credential_status_no_issue
            "REVOKED" -> Res.string.credential_status_revoked
            "INVALID" -> Res.string.credential_status_invalid
            "SUSPENDED" -> Res.string.credential_status_suspended
            "REFRESH" -> Res.string.credential_status_update_available
            "APPLICATION_SPECIFIC" -> Res.string.credential_status_custom_value
            "RESERVED" -> Res.string.credential_status_reserved_value
            else -> when {
                reference.problem == "MALFORMED" -> Res.string.credential_status_invalid_reference
                reference.problem != null -> Res.string.credential_status_unavailable
                reference.method != null -> Res.string.credential_status_unsupported
                reference.failure != null -> Res.string.credential_status_unavailable
                else -> Res.string.credential_status_not_checked
            }
        })
        StatusFact(label, result)
        if (reference.freshness == "STALE") SettingsNotice(stringResource(Res.string.credential_status_reference_outdated))
        when (reference.problem) {
            "RESOURCE_LIMIT" -> SettingsNotice(stringResource(Res.string.credential_status_limit_detail))
            "CONFLICTING_REFERENCE" -> SettingsNotice(stringResource(Res.string.credential_status_conflicting_reference_detail))
        }
        if (status.references.size > 1) reference.checkedAt?.let(::statusDate)?.let { date ->
            StatusFact(stringResource(Res.string.credential_status_last_result), date)
        }
        reference.failure?.let { SettingsNotice(statusFailureExplanation(it)) }
        if (status.references.size > 1) reference.attemptedAt?.takeIf { it != reference.checkedAt }?.let(::statusDate)?.let { date ->
            StatusFact(stringResource(Res.string.credential_status_latest_attempt), date)
        }
        if (reference.conflict) SettingsNotice(stringResource(Res.string.credential_status_conflict_detail))
        reference.eligibleAt?.let(::statusDate)?.let { date ->
            StatusFact(stringResource(if (reference.failure == "NOT_YET_VALID") Res.string.credential_status_valid_from else Res.string.credential_status_retry_from), date)
        }
    }
    if (!showReferences || status.references.size == 1) status.lastAttemptAt?.takeIf { it != status.lastCheckedAt }?.let(::statusDate)?.let { date ->
        SettingsDivider()
        StatusFact(stringResource(Res.string.credential_status_latest_attempt), date, "credential-status-latest-attempt-$credentialId")
    }
    if (!showReferences) status.failure?.let { SettingsNotice(statusFailureExplanation(it)) }
    val explanation = when {
        status.adverse -> Res.string.credential_status_adverse_detail
        status.kind == "NOT_PROVIDED" -> Res.string.credential_status_not_provided_detail
        status.kind == "NOT_CHECKED" -> Res.string.credential_status_not_checked_detail
        status.kind == "SERVER_UNSUPPORTED" -> Res.string.credential_status_server_unsupported_detail
        else -> null
    }
    explanation?.let { SettingsNotice(stringResource(it)) }
}

@Composable
private fun StatusFact(label: String, value: String, identifier: String? = null) {
    CredentialDataRow(label, Modifier.padding(16.dp)) {
        Text(value, modifier = identifier?.let { Modifier.testTag(it) } ?: Modifier, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun statusFailureExplanation(reason: String): String = stringResource(when (reason) {
    "CREDENTIAL_EXPIRED" -> Res.string.credential_status_credential_time_detail
    "NOT_YET_VALID" -> Res.string.credential_status_not_yet_valid_detail
    "REFERENCE_UNAUTHENTICATED" -> Res.string.credential_status_reference_auth_detail
    "REFERENCE_MALFORMED" -> Res.string.credential_status_reference_invalid_detail
    "RATE_LIMITED" -> Res.string.credential_status_rate_limited_detail
    "RESOURCE_LIMIT" -> Res.string.credential_status_limit_detail
    "AMBIGUOUS_CREDENTIAL_ID" -> Res.string.credential_status_ambiguous_detail
    "CONFLICTING_IRREVERSIBLE_STATUS" -> Res.string.credential_status_conflict_detail
    "UNSUPPORTED_METHOD", "UNSUPPORTED_PROOF" -> Res.string.credential_status_unsupported_detail
    "INVALID_SIGNATURE", "UNAUTHORIZED_SIGNER", "INVALID_LIST", "INVALID_VALIDITY" -> Res.string.credential_status_unverified_detail
    else -> Res.string.credential_status_transport_detail
})

private fun statusDate(value: String): String? = runCatching {
    WalletDateTime.format(Instant.parse(value)).fullText
}.getOrNull()
