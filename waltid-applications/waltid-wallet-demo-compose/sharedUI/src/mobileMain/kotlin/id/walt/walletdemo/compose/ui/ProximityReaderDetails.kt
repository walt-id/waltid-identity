package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.wallet2.mobile.ProximityDeviceAuthenticationMethod
import id.walt.wallet2.mobile.ProximityDocumentReview
import id.walt.wallet2.mobile.ProximityReaderAuthentication
import id.walt.wallet2.mobile.ProximityReaderAuthenticationScope
import id.walt.wallet2.mobile.ProximityReaderAuthenticationSummary
import id.walt.wallet2.mobile.ProximityReaderAuthenticationValidity
import id.walt.wallet2.mobile.ProximityReaderCertificatePathState
import id.walt.wallet2.mobile.ProximityReaderRevocationState
import id.walt.wallet2.mobile.ProximityReaderTrustState
import id.walt.wallet2.mobile.ProximityRemediationAction
import id.walt.wallet2.mobile.ProximityReview
import id.walt.wallet2.mobile.ProximityRicalState
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.toCardDisplayData
import id.walt.walletdemo.compose.ui.components.ExpandableMetadataCard
import id.walt.walletdemo.compose.ui.components.MetadataDetailItem
import id.walt.walletdemo.compose.ui.components.MetadataDetailList
import id.walt.walletdemo.compose.ui.components.ReviewMetadataSection
import id.walt.walletdemo.compose.ui.resources.*
import id.walt.walletdemo.compose.ui.resources.proximity_verifier
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProximityReaderMetadataCard(
    review: ProximityReview,
    credentialDetailsById: Map<String, CredentialDetails>,
) {
    val suppliedAuthentications = review.readerAuthentication.filterNot {
        it.validity == ProximityReaderAuthenticationValidity.Absent
    }
    if (suppliedAuthentications.isEmpty()) {
        ReviewMetadataSection(
            title = stringResource(Res.string.proximity_verifier),
            modifier = Modifier.testTag(WalletUiTestTags.ProximityReaderSection),
        ) {
            Text(
                stringResource(Res.string.proximity_reader_identity_not_provided),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(Res.string.proximity_reader_not_authenticated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var expanded by rememberSaveable { mutableStateOf(false) }
    val displayNames = suppliedAuthentications.mapNotNull { authentication ->
        authentication.displayName?.trim()?.takeIf(String::isNotEmpty)
    }.distinct()
    val displayName = when (displayNames.size) {
        0 -> stringResource(Res.string.proximity_reader_identity_unavailable)
        1 -> displayNames.single()
        else -> stringResource(Res.string.proximity_multiple_reader_identities)
    }
    val supportingText = when (review.readerAuthenticationSummary) {
        ProximityReaderAuthenticationSummary.Absent -> ProximityReaderAuthenticationValidity.Absent.displayName()
        ProximityReaderAuthenticationSummary.Malformed -> ProximityReaderAuthenticationValidity.Malformed.displayName()
        ProximityReaderAuthenticationSummary.Invalid -> ProximityReaderAuthenticationValidity.Invalid.displayName()
        ProximityReaderAuthenticationSummary.Revoked -> ProximityReaderTrustState.Revoked.displayName()
        ProximityReaderAuthenticationSummary.Partial -> stringResource(Res.string.proximity_reader_authentication_partial)
        ProximityReaderAuthenticationSummary.ValidButUntrusted -> ProximityReaderTrustState.ValidButUntrusted.displayName()
        ProximityReaderAuthenticationSummary.Trusted -> ProximityReaderTrustState.Trusted.displayName()
    }

    ExpandableMetadataCard(
        title = stringResource(Res.string.proximity_verifier),
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = Modifier.testTag(WalletUiTestTags.ProximityReaderSection),
        toggleTestTag = WalletUiTestTags.ProximityReaderDetailsToggle,
        summary = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        details = {
            Column(
                modifier = Modifier.testTag(WalletUiTestTags.ProximityReaderDetails),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                review.readerAuthentication.forEachIndexed { index, readerAuthentication ->
                    if (index > 0) HorizontalDivider()
                    ReaderAuthenticationContent(
                        readerAuthentication,
                        review.documents,
                        credentialDetailsById,
                    )
                }
            }
        },
    )
}

@Composable
private fun ReaderAuthenticationContent(
    authentication: ProximityReaderAuthentication,
    documents: List<ProximityDocumentReview>,
    credentialDetailsById: Map<String, CredentialDetails>,
) {
    val trusted = authentication.trust == ProximityReaderTrustState.Trusted
    Text(
        authentication.displayName ?: stringResource(Res.string.proximity_reader_identity_unavailable),
        fontWeight = FontWeight.SemiBold,
    )
    MetadataDetailList(
        listOf(
            MetadataDetailItem(
                stringResource(Res.string.proximity_applies_to),
                when (val scope = authentication.scope) {
                    ProximityReaderAuthenticationScope.WholeRequest ->
                        stringResource(Res.string.proximity_whole_request)
                    is ProximityReaderAuthenticationScope.Document -> {
                        val document = documents.singleOrNull {
                            it.requestIndex == scope.index
                        }
                        document?.let {
                            val displayName = it.credentialOptions.firstNotNullOfOrNull { option ->
                                credentialDetailsById[option.credentialId]?.toCardDisplayData()?.title
                            } ?: it.docType
                            stringResource(Res.string.proximity_document_scope, displayName)
                        } ?: stringResource(
                            Res.string.proximity_document_request,
                            scope.index + 1,
                        )
                    }
                },
            ),
            MetadataDetailItem(
                stringResource(Res.string.proximity_signature),
                authentication.validity.displayName(),
            ),
            MetadataDetailItem(
                stringResource(Res.string.proximity_certificate_path),
                authentication.certificatePath.displayName(),
            ),
            MetadataDetailItem(
                stringResource(Res.string.proximity_revocation),
                authentication.revocation.displayName(),
            ),
            MetadataDetailItem(
                stringResource(Res.string.proximity_rical_evidence),
                authentication.rical.displayName(),
            ),
            MetadataDetailItem(
                stringResource(Res.string.proximity_trust),
                authentication.trust.displayName(),
            ),
        )
    )
    authentication.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (!trusted && authentication.validity == ProximityReaderAuthenticationValidity.Valid) {
        Text(
            stringResource(Res.string.proximity_reader_trust_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
internal fun ProximityRemediationAction.label(): String = stringResource(
    when (this) {
        ProximityRemediationAction.RequestBluetoothPermission -> Res.string.proximity_allow_bluetooth
        ProximityRemediationAction.RequestNearbyWifiPermission -> Res.string.proximity_allow_nearby_wifi
        ProximityRemediationAction.RequestLocalNetworkPermission -> Res.string.proximity_allow_local_network
        ProximityRemediationAction.OpenApplicationSettings -> Res.string.proximity_open_app_settings
        ProximityRemediationAction.EnableBluetooth -> Res.string.proximity_enable_bluetooth
        ProximityRemediationAction.EnableWifi -> Res.string.proximity_enable_wifi
        ProximityRemediationAction.EnableNfc -> Res.string.proximity_enable_nfc
        ProximityRemediationAction.UseSupportedDevice -> Res.string.proximity_use_supported_device
        ProximityRemediationAction.Retry -> Res.string.proximity_try_again
    }
)

@Composable
private fun ProximityReaderAuthenticationValidity.displayName(): String = stringResource(
    when (this) {
        ProximityReaderAuthenticationValidity.Absent -> Res.string.proximity_auth_absent
        ProximityReaderAuthenticationValidity.Malformed -> Res.string.proximity_auth_malformed
        ProximityReaderAuthenticationValidity.Invalid -> Res.string.proximity_auth_invalid
        ProximityReaderAuthenticationValidity.Valid -> Res.string.proximity_auth_valid
    }
)

@Composable
private fun ProximityReaderTrustState.displayName(): String = stringResource(
    when (this) {
        ProximityReaderTrustState.NotEvaluated -> Res.string.proximity_trust_not_evaluated
        ProximityReaderTrustState.ValidButUntrusted -> Res.string.proximity_trust_untrusted
        ProximityReaderTrustState.Revoked -> Res.string.proximity_trust_revoked
        ProximityReaderTrustState.Trusted -> Res.string.proximity_trust_trusted
    }
)

@Composable
private fun ProximityReaderCertificatePathState.displayName(): String = stringResource(
    when (this) {
        ProximityReaderCertificatePathState.NotEvaluated -> Res.string.proximity_not_evaluated
        ProximityReaderCertificatePathState.UnknownAuthority -> Res.string.proximity_unknown_authority
        ProximityReaderCertificatePathState.Invalid -> Res.string.proximity_auth_invalid
        ProximityReaderCertificatePathState.Valid -> Res.string.proximity_auth_valid
    }
)

@Composable
private fun ProximityReaderRevocationState.displayName(): String = stringResource(
    when (this) {
        ProximityReaderRevocationState.NotChecked -> Res.string.proximity_not_checked
        ProximityReaderRevocationState.Good -> Res.string.proximity_revocation_good
        ProximityReaderRevocationState.Revoked -> Res.string.proximity_trust_revoked
        ProximityReaderRevocationState.Indeterminate -> Res.string.proximity_indeterminate
    }
)

@Composable
private fun ProximityRicalState.displayName(): String = stringResource(
    when (this) {
        ProximityRicalState.NotEvaluated -> Res.string.proximity_not_evaluated
        ProximityRicalState.Unavailable -> Res.string.proximity_unavailable
        ProximityRicalState.Invalid -> Res.string.proximity_auth_invalid
        ProximityRicalState.NoMatchingAuthority -> Res.string.proximity_no_matching_authority
        ProximityRicalState.Matched -> Res.string.proximity_matched_authority
    }
)

@Composable
internal fun ProximityDeviceAuthenticationMethod.displayName(): String = stringResource(
    when (this) {
        ProximityDeviceAuthenticationMethod.Signature -> Res.string.proximity_auth_signature
        ProximityDeviceAuthenticationMethod.Mac -> Res.string.proximity_auth_mac
    }
)
