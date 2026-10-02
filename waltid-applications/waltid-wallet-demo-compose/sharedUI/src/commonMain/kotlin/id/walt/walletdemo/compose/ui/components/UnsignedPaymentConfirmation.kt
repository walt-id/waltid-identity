package id.walt.walletdemo.compose.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.WalletDemoPaymentReview
import id.walt.walletdemo.compose.logic.consent
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** A warning is not consent. The second action explicitly accepts an unsigned payment request. */
@Composable
internal fun rememberPaymentSubmission(
    review: WalletDemoPaymentReview,
    enabled: Boolean,
    onSubmit: () -> Unit,
): () -> Unit {
    val consent = review.consent
    // A changed selection/consent or disabled action cancels the pending decision. Nothing is
    // persisted as an authorization, including across disposal or process restoration.
    var confirming by remember(consent?.revision, enabled) { mutableStateOf(false) }
    if (confirming && enabled && consent != null) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(Res.string.payment_unsigned_title)) },
            text = { Text(stringResource(Res.string.payment_unsigned_confirmation)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onSubmit() },
                    modifier = Modifier.testTag("payment-unsigned-confirm")) { Text(consent.affirmativeAction) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false },
                    modifier = Modifier.testTag("payment-unsigned-back")) { Text(stringResource(Res.string.payment_return_to_review)) }
            },
        )
    }
    return {
        if (enabled) {
            if (consent?.requiresUnsignedRequestWarning == true) confirming = true else onSubmit()
        }
    }
}
