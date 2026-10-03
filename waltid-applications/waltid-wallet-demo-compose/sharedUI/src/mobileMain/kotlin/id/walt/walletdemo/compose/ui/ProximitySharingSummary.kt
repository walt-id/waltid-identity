package id.walt.walletdemo.compose.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import id.walt.wallet2.mobile.ProximityApprovalTiming
import id.walt.wallet2.mobile.ProximityElementReference
import id.walt.wallet2.mobile.ProximityPreparedSharing
import id.walt.wallet2.mobile.ProximityReview
import id.walt.wallet2.mobile.ProximitySubmission
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.toCardDisplayData
import id.walt.walletdemo.compose.ui.components.CredentialCardArtModel
import id.walt.walletdemo.compose.ui.components.CredentialSummaryRow
import id.walt.walletdemo.compose.ui.components.MetadataDisclosure
import id.walt.walletdemo.compose.ui.components.ReviewMetadataSection
import id.walt.walletdemo.compose.ui.components.toCardArt
import id.walt.walletdemo.compose.ui.resources.*
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProximityPreparedSharingSummary(sharing: ProximityPreparedSharing, credentialDetailsById: Map<String, CredentialDetails>) {
    ReviewMetadataSection(stringResource(Res.string.proximity_prepared_ready)) {
        ProximityPreparedSharingCountdown(sharing)
        Text(stringResource(Res.string.proximity_prepared_one_use))
        ProximityDisclosureSummary(sharing.review, sharing.submission, credentialDetailsById)
    }
}

@Composable
internal fun ProximityPreparedSharingCountdown(sharing: ProximityPreparedSharing) {
    var remaining by remember(sharing) { mutableStateOf(sharing.remainingSeconds) }
    LaunchedEffect(sharing) {
        while (remaining > 0) {
            delay(250)
            remaining = sharing.remainingSeconds
        }
    }
    Text(stringResource(Res.string.proximity_prepared_countdown, remaining),
        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("proximity-prepared-countdown"))
}

@Composable
internal fun ProximitySharingReceiptContent(
    review: ProximityReview,
    submission: ProximitySubmission,
    approvalTiming: ProximityApprovalTiming,
    completedAt: Instant,
    credentialDetailsById: Map<String, CredentialDetails>,
) {
    ReviewMetadataSection(stringResource(Res.string.proximity_shared_data)) {
        Text(completedAt.toLocalDateTime(TimeZone.currentSystemDefault()).let {
            "${it.date} ${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
        }, style = MaterialTheme.typography.bodySmall)
        if (approvalTiming == ProximityApprovalTiming.BeforeConnection) {
            Text(stringResource(Res.string.proximity_used_prepared_approval))
        }
        ProximityDisclosureSummary(review, submission, credentialDetailsById, initiallyExpanded = true)
    }
}

@Composable
internal fun ProximityDisclosureSummary(review: ProximityReview, submission: ProximitySubmission, credentialDetailsById: Map<String, CredentialDetails>, initiallyExpanded: Boolean = false) {
    val names = review.readerAuthentication.mapNotNull { it.displayName }.distinct()
    Text(names.joinToString().ifBlank { stringResource(Res.string.proximity_reader_identity_unavailable) },
        fontWeight = FontWeight.SemiBold)
    MetadataDisclosure(title = stringResource(Res.string.proximity_data_to_share), initiallyExpanded = initiallyExpanded) {
        submission.documents.forEach { selected ->
            val document = review.documents.single { it.requestIndex == selected.requestIndex }
            val credential = document.credentialOptions.single { it.credentialId == selected.credentialId }
            val details = credentialDetailsById[credential.credentialId]
            val display = remember(details) { details?.toCardDisplayData() }
            CredentialSummaryRow(display?.toCardArt() ?: CredentialCardArtModel(
                id = credential.credentialId, name = credential.label ?: stringResource(Res.string.proximity_generic_credential)),
                display?.issuer ?: credential.issuer)
            credential.requestedElements.filter {
                ProximityElementReference(it.namespace, it.elementIdentifier) in selected.disclosedElements
            }.forEach { element ->
                Text(details?.mdocClaims(element.namespace, element.elementIdentifier)?.map { it.label }
                    ?.distinct()?.takeIf { it.isNotEmpty() }?.joinToString()
                    ?: humanizedElementIdentifier(element.elementIdentifier))
                if (element.intentToRetain) Text(stringResource(Res.string.proximity_reader_retention),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
