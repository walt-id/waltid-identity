package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.wallet2.mobile.ProximityCredentialOption
import id.walt.wallet2.mobile.ProximityElementReference
import id.walt.wallet2.mobile.ProximityReview
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.WalletDemoProximityDocumentSelection
import id.walt.walletdemo.compose.logic.toCardDisplayData
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProximityReviewContent(
    review: ProximityReview,
    selections: List<WalletDemoProximityDocumentSelection>,
    credentialDetailsById: Map<String, CredentialDetails>,
    continueAfterResponse: Boolean,
    onSelectCredential: (Int, String) -> Unit,
    onToggleElement: (Int, ProximityElementReference) -> Unit,
    onContinueAfterResponseChange: (Boolean) -> Unit,
    allowContinuation: Boolean = true,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.ProximityReview),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProximityReaderMetadataCard(review, credentialDetailsById)
        review.useCases.forEach { useCase ->
            ReviewMetadataSection(stringResource(Res.string.proximity_reader_purpose)) {
                Text(
                    stringResource(
                        Res.string.proximity_use_case,
                        useCase.index + 1,
                        if (useCase.mandatory) stringResource(Res.string.proximity_mandatory_suffix) else "",
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
                if (useCase.purposeHints.isEmpty()) {
                    Text(stringResource(Res.string.proximity_no_purpose))
                } else {
                    useCase.purposeHints.forEach { hint ->
                        Text(stringResource(Res.string.proximity_purpose_hint, hint.type, hint.code))
                    }
                }
            }
        }
        review.applicationAuthorizations.forEach { authorization ->
            ReviewMetadataSection(authorization.displayTitle) {
                Text(
                    stringResource(Res.string.proximity_validated_application_request),
                    style = MaterialTheme.typography.labelLarge,
                )
                MetadataDetailList(
                    authorization.details.map { detail -> MetadataDetailItem(detail.label, detail.value) }
                )
            }
        }
        WalletSection(title = "Select credentials to share") {
            review.documents.forEach { document ->
                document.credentialOptions.forEachIndexed { index, credential ->
                    if (index > 0) MetadataRowDivider()
                    CredentialOption(document.requestIndex, credential, credentialDetailsById[credential.credentialId],
                        selections.singleOrNull { it.requestIndex == document.requestIndex }?.credentialId == credential.credentialId,
                        onSelectCredential, enabled)
                }
            }
        }
        WalletSection(title = "Information to share", modifier = Modifier.testTag("review-information-to-share")) {
            review.documents.forEach { document ->
                val selection = selections.singleOrNull { it.requestIndex == document.requestIndex }
                document.credentialOptions.singleOrNull { it.credentialId == selection?.credentialId }?.let { credential ->
                    ProximityInformationGroup(document, credential, selection!!,
                        credentialDetailsById[credential.credentialId], onToggleElement, enabled)
                }
            }
        }
        if (allowContinuation) Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = continueAfterResponse,
                enabled = enabled,
                onCheckedChange = onContinueAfterResponseChange,
                modifier = Modifier.testTag(WalletUiTestTags.ProximityContinueAfterResponse),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.proximity_continue_after_response))
                Text(
                    stringResource(Res.string.proximity_continue_after_response_description),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CredentialOption(
    requestIndex: Int,
    credential: ProximityCredentialOption,
    details: CredentialDetails?,
    selected: Boolean,
    onSelect: (Int, String) -> Unit,
    enabled: Boolean,
) {
    ReviewCredentialChoice(selected, multiple = false, enabled = enabled,
        modifier = Modifier.testTag(WalletUiTestTags.proximityCredential(requestIndex, credential.credentialId)),
        onSelect = { if (!selected) onSelect(requestIndex, credential.credentialId) }) {
        if (details != null) {
            val display = remember(details) { details.toCardDisplayData() }
            CredentialSummaryRow(display.toCardArt(), display.issuer)
        } else {
            Text(credential.label ?: stringResource(Res.string.proximity_generic_credential))
            credential.issuer?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(Res.string.proximity_valid_until, credential.validUntil.toString()),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun CredentialDetails.mdocClaims(namespace: String, elementIdentifier: String): List<ClaimItem> =
    groups.asSequence()
        .flatMap { group -> group.items.asSequence() }
        .filter { claim ->
            claim.pathComponents.getOrNull(0) == namespace &&
                claim.pathComponents.getOrNull(1) == elementIdentifier
        }
        .toList()

internal fun humanizedElementIdentifier(identifier: String): String =
    identifier
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .replaceFirstChar { character -> character.uppercase() }
