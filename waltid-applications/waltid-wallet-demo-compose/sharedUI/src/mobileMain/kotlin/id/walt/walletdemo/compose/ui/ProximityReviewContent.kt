package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.wallet2.mobile.ProximityCredentialOption
import id.walt.wallet2.mobile.ProximityDocumentReview
import id.walt.wallet2.mobile.ProximityElementReference
import id.walt.wallet2.mobile.ProximityReview
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.WalletDemoProximityDocumentSelection
import id.walt.walletdemo.compose.logic.toCardDisplayData
import id.walt.walletdemo.compose.ui.components.ClaimValueRow
import id.walt.walletdemo.compose.ui.components.CredentialSummaryRow
import id.walt.walletdemo.compose.ui.components.MetadataDetailItem
import id.walt.walletdemo.compose.ui.components.MetadataDetailList
import id.walt.walletdemo.compose.ui.components.MetadataDisclosure
import id.walt.walletdemo.compose.ui.components.ReviewMetadataSection
import id.walt.walletdemo.compose.ui.components.toCardArt
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
        review.documents.forEach { document ->
            DocumentReviewContent(
                document = document,
                selection = selections.singleOrNull { it.requestIndex == document.requestIndex },
                credentialDetailsById = credentialDetailsById,
                onSelectCredential = onSelectCredential,
                onToggleElement = onToggleElement,
            )
        }
        if (allowContinuation) Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = continueAfterResponse,
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
private fun DocumentReviewContent(
    document: ProximityDocumentReview,
    selection: WalletDemoProximityDocumentSelection?,
    credentialDetailsById: Map<String, CredentialDetails>,
    onSelectCredential: (Int, String) -> Unit,
    onToggleElement: (Int, ProximityElementReference) -> Unit,
) {
    ReviewMetadataSection(stringResource(Res.string.proximity_credential_to_share)) {
        if (document.credentialOptions.size > 1) {
            Text(stringResource(Res.string.proximity_choose_credential), style = MaterialTheme.typography.labelLarge)
        }
        document.credentialOptions.forEach { credential ->
            CredentialOption(
                requestIndex = document.requestIndex,
                credential = credential,
                details = credentialDetailsById[credential.credentialId],
                showSelectionControl = document.credentialOptions.size > 1,
                selected = selection?.credentialId == credential.credentialId,
                onSelect = onSelectCredential,
            )
        }
        val selectedCredential = document.credentialOptions.singleOrNull {
            it.credentialId == selection?.credentialId
        }
        selectedCredential?.let { credential ->
            val details = credentialDetailsById[credential.credentialId]
            HorizontalDivider()
            Text(stringResource(Res.string.proximity_data_to_share), style = MaterialTheme.typography.labelLarge)
            if (!document.requiredElements.all { required -> credential.requestedElements.any {
                    it.namespace == required.namespace && it.elementIdentifier == required.elementIdentifier
                } }) {
                Text(stringResource(Res.string.proximity_required_data_unavailable), color = MaterialTheme.colorScheme.error)
            }
            credential.requestedElements.forEach { element ->
                val reference = ProximityElementReference(
                    namespace = element.namespace,
                    elementIdentifier = element.elementIdentifier,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = reference in (selection?.disclosedElements ?: emptySet()),
                        onCheckedChange = { onToggleElement(document.requestIndex, reference) },
                        enabled = reference !in document.requiredElements,
                        modifier = Modifier.testTag(
                            WalletUiTestTags.proximityElement(
                                document.requestIndex,
                                element.namespace,
                                element.elementIdentifier,
                            )
                        ),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val claims = details?.mdocClaims(element.namespace, element.elementIdentifier).orEmpty()
                        if (claims.isNotEmpty()) {
                            claims.forEach { claim -> ClaimValueRow(claim) }
                        } else {
                            Text(
                                humanizedElementIdentifier(element.elementIdentifier),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(Res.string.proximity_value_preview_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (element.intentToRetain) {
                            Text(
                                stringResource(Res.string.proximity_reader_retention),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (reference in document.requiredElements) {
                            Text(stringResource(Res.string.proximity_required_identity_check), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            MetadataDisclosure(
                title = stringResource(Res.string.proximity_technical_details),
                initiallyExpanded = false,
            ) {
                MetadataDetailList(
                    buildList {
                        add(
                            MetadataDetailItem(
                                stringResource(Res.string.proximity_document_type),
                                document.docType,
                            )
                        )
                        add(
                            MetadataDetailItem(
                                stringResource(Res.string.proximity_device_authentication),
                                credential.deviceAuthentication.displayName(),
                            )
                        )
                        credential.requestedElements.forEach { element ->
                            add(
                                MetadataDetailItem(
                                    stringResource(Res.string.proximity_requested_element),
                                    "${element.namespace} / ${element.elementIdentifier}",
                                )
                            )
                        }
                    }
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
    showSelectionControl: Boolean,
    selected: Boolean,
    onSelect: (Int, String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.proximityCredential(requestIndex, credential.credentialId))
            .then(if (showSelectionControl) Modifier.selectable(selected, role = Role.RadioButton,
                onClick = { onSelect(requestIndex, credential.credentialId) }) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSelectionControl) {
            RadioButton(
                selected = selected,
                onClick = null,
            )
        }
        if (details != null) {
            val display = remember(details) { details.toCardDisplayData() }
            CredentialSummaryRow(display.toCardArt(), display.issuer, Modifier.weight(1f))
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(credential.label ?: stringResource(Res.string.proximity_generic_credential))
                credential.issuer?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    stringResource(Res.string.proximity_valid_until, credential.validUntil.toString()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
