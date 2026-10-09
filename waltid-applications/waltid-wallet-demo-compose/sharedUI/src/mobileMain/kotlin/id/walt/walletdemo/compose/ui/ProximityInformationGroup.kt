package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.wallet2.mobile.*
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProximityInformationGroup(
    document: ProximityDocumentReview,
    credential: ProximityCredentialOption,
    selection: WalletDemoProximityDocumentSelection,
    details: CredentialDetails?,
    onToggleElement: (Int, ProximityElementReference) -> Unit,
    enabled: Boolean,
) {
    val summary = details?.toCardDisplayData()
    ReviewInformationGroup(summary?.title ?: credential.label ?: stringResource(Res.string.proximity_generic_credential),
        summary?.issuer ?: credential.issuer) {
        if (!document.requiredElements.all { required -> credential.requestedElements.any {
            it.namespace == required.namespace && it.elementIdentifier == required.elementIdentifier
        } }) Text(stringResource(Res.string.proximity_required_data_unavailable), color = MaterialTheme.colorScheme.error)
        credential.requestedElements.forEachIndexed { index, element ->
            if (index > 0) MetadataRowDivider()
            val reference = ProximityElementReference(element.namespace, element.elementIdentifier)
            val included = reference in selection.disclosedElements
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reference !in document.requiredElements) Checkbox(included, enabled = enabled,
                    onCheckedChange = { onToggleElement(document.requestIndex, reference) },
                    modifier = Modifier.testTag(WalletUiTestTags.proximityElement(document.requestIndex, element.namespace, element.elementIdentifier)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val claims = details?.mdocClaims(element.namespace, element.elementIdentifier).orEmpty()
                    if (claims.isNotEmpty()) claims.forEach { ClaimValueRow(it) }
                    else {
                        Text(humanizedElementIdentifier(element.elementIdentifier), style = MaterialTheme.typography.labelMedium)
                        Text(stringResource(Res.string.proximity_value_preview_unavailable),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (!included) Text("Not shared", style = MaterialTheme.typography.bodySmall)
                    if (element.intentToRetain) Text(stringResource(Res.string.proximity_reader_retention),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (reference in document.requiredElements) Text(stringResource(Res.string.proximity_required_identity_check),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        MetadataDisclosure(stringResource(Res.string.proximity_technical_details), initiallyExpanded = false) {
            MetadataDetailList(listOf(
                MetadataDetailItem(stringResource(Res.string.proximity_document_type), document.docType),
                MetadataDetailItem(stringResource(Res.string.proximity_device_authentication), credential.deviceAuthentication.displayName()),
            ) + credential.requestedElements.map {
                MetadataDetailItem(stringResource(Res.string.proximity_requested_element), "${it.namespace} / ${it.elementIdentifier}")
            })
        }
    }
}
