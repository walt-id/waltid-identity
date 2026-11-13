package id.walt.walletdemo.compose.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import id.walt.walletdemo.compose.logic.WalletDemoPaymentReview
import id.walt.walletdemo.compose.logic.consent
import id.walt.walletdemo.compose.ui.exportTestTagsForPlatformAutomation
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.credential_status_share_anyway
import id.walt.walletdemo.compose.ui.resources.credential_status_share_confirmation
import id.walt.walletdemo.compose.ui.resources.credential_status_share_confirmation_many
import id.walt.walletdemo.compose.ui.resources.credential_status_share_confirmation_one
import id.walt.walletdemo.compose.ui.resources.credential_status_review_changed
import id.walt.walletdemo.compose.ui.resources.credential_status_warning_title
import id.walt.walletdemo.compose.ui.resources.payment_return_to_review
import id.walt.walletdemo.compose.ui.resources.payment_unsigned_confirmation
import id.walt.walletdemo.compose.ui.resources.payment_unsigned_title
import org.jetbrains.compose.resources.stringResource

private data class SubmissionBinding(
    val review: Any,
    val credentials: List<Triple<String, Int?, String?>>,
    val paymentRevision: String?,
)

private class SubmissionAttempt(
    val binding: SubmissionBinding,
    val revisions: Map<String, String>,
    val submit: (Map<String, String>) -> Unit,
)

internal class ReviewedSubmission(
    val enabled: Boolean,
    val changed: Boolean,
    val refreshAllowed: Boolean,
    val canRefresh: () -> Boolean,
    val submit: () -> Unit,
)

/** One final gate for status and unsigned-payment warnings; all protocol guards remain downstream. */
@Composable
internal fun rememberReviewedSubmission(
    reviewKey: Any,
    credentials: List<ReviewCredentialStatus>,
    payment: WalletDemoPaymentReview,
    enabled: Boolean,
    revisions: Map<String, String>?,
    affirmativeLabel: String,
    ordinarySharing: Boolean,
    onSubmit: (Map<String, String>) -> Unit,
): ReviewedSubmission {
    val binding = SubmissionBinding(reviewKey, credentials.map {
        Triple(it.credentialId, it.status?.storeIndex, it.status?.advisoryRevision)
    }.sortedBy { it.first }, payment.consent?.revision)
    val currentBinding by rememberUpdatedState(binding)
    val canSubmit by rememberUpdatedState(enabled && revisions != null)
    var pending by remember { mutableStateOf<SubmissionAttempt?>(null) }
    var submitted by remember(binding, enabled) { mutableStateOf(false) }
    var changed by remember(reviewKey) { mutableStateOf(false) }
    var disposed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { disposed = true; pending = null } }
    LaunchedEffect(binding, enabled, revisions != null) {
        if (pending != null && (pending?.binding != binding || !enabled || revisions == null)) {
            pending = null
            changed = true
        }
    }
    val adverse = credentials.filter { it.status?.adverse == true }.distinctBy { it.status?.storeIndex to it.credentialId }
    val unsigned = payment.consent?.requiresUnsignedRequestWarning == true
    val affected = adverse.singleOrNull()
    val name = affected?.let { credential ->
        credential.issuer?.takeIf { it.isNotBlank() }?.let { "${credential.title} ($it)" } ?: credential.title
    }
    val warning = when (adverse.size) {
        0 -> null
        1 -> stringResource(Res.string.credential_status_share_confirmation_one,
            requireNotNull(name), requireNotNull(affected.status).title().lowercase())
        else -> stringResource(Res.string.credential_status_share_confirmation_many, adverse.size)
    }
    val attempt = pending?.takeIf { it.binding == binding && enabled && revisions != null }
    if (attempt != null) {
        AlertDialog(
            modifier = Modifier.exportTestTagsForPlatformAutomation(),
            onDismissRequest = { pending = null },
            title = { Text(stringResource(when {
                unsigned -> Res.string.payment_unsigned_title
                ordinarySharing -> Res.string.credential_status_share_confirmation
                else -> Res.string.credential_status_warning_title
            })) },
            text = {
                Text(listOfNotNull(if (unsigned) stringResource(Res.string.payment_unsigned_confirmation) else null, warning).joinToString("\n\n"))
            },
            confirmButton = {
                TextButton(onClick = {
                    val active = pending === attempt
                    pending = null
                    if (active && !disposed && canSubmit && !submitted && currentBinding == attempt.binding) {
                        submitted = true
                        attempt.submit(attempt.revisions)
                    }
                }, modifier = Modifier.testTag(if (unsigned) "payment-unsigned-confirm" else "credential-status-confirm")) {
                    Text(if (ordinarySharing) stringResource(Res.string.credential_status_share_anyway) else affirmativeLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null },
                    modifier = Modifier.testTag(if (unsigned) "payment-unsigned-back" else "credential-status-back")) {
                    Text(stringResource(Res.string.payment_return_to_review))
                }
            },
        )
    }
    val submit = {
        if (!disposed && canSubmit && !submitted && currentBinding == binding && revisions != null) {
            changed = false
            val captured = SubmissionAttempt(binding, revisions.toMap(), onSubmit)
            if (unsigned || adverse.isNotEmpty()) pending = captured else {
                submitted = true
                captured.submit(captured.revisions)
            }
        }
    }
    return ReviewedSubmission(enabled && revisions != null, changed, pending == null && !submitted && !disposed,
        canRefresh = { pending == null && !submitted && !disposed }, submit = submit)
}

/** A review notice belongs to scrollable content; it must not change pinned action geometry. */
@Composable
internal fun ReviewChangedNotice(submission: ReviewedSubmission) {
    if (submission.changed) Text(stringResource(Res.string.credential_status_review_changed),
        Modifier.testTag("credential-status-review-changed").semantics { liveRegion = LiveRegionMode.Polite })
}
