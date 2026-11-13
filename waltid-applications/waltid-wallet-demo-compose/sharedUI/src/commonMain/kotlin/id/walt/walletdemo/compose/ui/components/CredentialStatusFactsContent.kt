package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoStatusReference
import id.walt.walletdemo.compose.ui.WalletInstantText
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.credential_status_checks
import id.walt.walletdemo.compose.ui.resources.credential_status_custom_value
import id.walt.walletdemo.compose.ui.resources.credential_status_invalid
import id.walt.walletdemo.compose.ui.resources.credential_status_invalid_reference
import id.walt.walletdemo.compose.ui.resources.credential_status_last_result
import id.walt.walletdemo.compose.ui.resources.credential_status_latest_attempt
import id.walt.walletdemo.compose.ui.resources.credential_status_no_issue
import id.walt.walletdemo.compose.ui.resources.credential_status_not_checked
import id.walt.walletdemo.compose.ui.resources.credential_status_purpose_token
import id.walt.walletdemo.compose.ui.resources.credential_status_reference
import id.walt.walletdemo.compose.ui.resources.credential_status_reserved_value
import id.walt.walletdemo.compose.ui.resources.credential_status_retry_from
import id.walt.walletdemo.compose.ui.resources.credential_status_revoked
import id.walt.walletdemo.compose.ui.resources.credential_status_stale
import id.walt.walletdemo.compose.ui.resources.credential_status_suspended
import id.walt.walletdemo.compose.ui.resources.credential_status_unavailable
import id.walt.walletdemo.compose.ui.resources.credential_status_unsupported
import id.walt.walletdemo.compose.ui.resources.credential_status_update_available
import id.walt.walletdemo.compose.ui.resources.credential_status_valid_from
import org.jetbrains.compose.resources.stringResource

/** The same facts body serves stored details and expanded sharing/payment rows. */
@Composable
internal fun CredentialStatusFactsContent(
    credentialId: String,
    main: List<CredentialStatusFact> = emptyList(),
    extra: List<CredentialStatusFactGroup> = emptyList(),
) {
    if (main.isNotEmpty()) FactRows(credentialId, main)
    extra.forEach { group ->
        SettingsDivider()
        FactRows(credentialId, group.facts)
    }
}

@Composable
private fun FactRows(credentialId: String, facts: List<CredentialStatusFact>) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        facts.forEach { fact -> when (fact) {
            is CredentialStatusFact.Scope -> Text("${stringResource(Res.string.credential_status_checks)}: ${fact.purposes.map { statusPurposeTitle(it) }.joinToString(", ")}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is CredentialStatusFact.Note -> Text(stringResource(fact.text), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            is CredentialStatusFact.Time -> CredentialDataRow(stringResource(when (fact.kind) {
                CredentialStatusFact.TimeKind.LAST_CHECKED -> Res.string.credential_status_last_result
                CredentialStatusFact.TimeKind.LATEST_ATTEMPT -> Res.string.credential_status_latest_attempt
                CredentialStatusFact.TimeKind.VALID_FROM -> Res.string.credential_status_valid_from
                CredentialStatusFact.TimeKind.RETRY_FROM -> Res.string.credential_status_retry_from
            })) {
                WalletInstantText(fact.at, Modifier.testTag("credential-status-${fact.id}-$credentialId"),
                    style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
            is CredentialStatusFact.Outcome -> {
                val reference = fact.reference
                val label = reference.purpose?.let { statusPurposeTitle(it) } ?: stringResource(
                    if (reference.protocolName == "TOKEN_STATUS_LIST") Res.string.credential_status_purpose_token else Res.string.credential_status_reference)
                CredentialDataRow(label) {
                    val result = referenceResultTitle(reference)
                    Text(if (reference.freshness == "STALE") "$result · ${stringResource(Res.string.credential_status_stale)}" else result,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        } }
    }
}

@Composable
private fun referenceResultTitle(reference: WalletDemoStatusReference): String = stringResource(when (reference.result) {
    "CLEAR" -> Res.string.credential_status_no_issue
    "REVOKED" -> Res.string.credential_status_revoked
    "INVALID" -> Res.string.credential_status_invalid
    "SUSPENDED" -> Res.string.credential_status_suspended
    "REFRESH" -> Res.string.credential_status_update_available
    "APPLICATION_SPECIFIC" -> Res.string.credential_status_custom_value
    "RESERVED" -> Res.string.credential_status_reserved_value
    else -> when {
        reference.result != null -> Res.string.credential_status_unavailable
        reference.problem == "MALFORMED" -> Res.string.credential_status_invalid_reference
        reference.problem != null -> Res.string.credential_status_unavailable
        reference.method != null -> Res.string.credential_status_unsupported
        reference.failure != null -> Res.string.credential_status_unavailable
        else -> Res.string.credential_status_not_checked
    }
})
