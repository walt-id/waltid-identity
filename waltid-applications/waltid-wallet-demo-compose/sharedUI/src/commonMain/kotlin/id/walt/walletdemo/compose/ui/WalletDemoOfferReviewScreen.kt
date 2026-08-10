package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoOfferPreview
import id.walt.walletdemo.compose.ui.components.OfferReviewSection

/**
 * Standalone offer-review screen for a platform-invoked Digital Credentials create flow.
 *
 * The host owns the Credential Manager result; this screen owns tx-code entry and the user's
 * accept/decline choice.
 */
@Composable
fun WalletDemoOfferReviewScreen(
    preview: WalletDemoOfferPreview,
    title: String,
    onAccept: (txCode: String?) -> Unit,
    onDecline: () -> Unit,
    enabled: Boolean = true,
    onBackAtRoot: (() -> Unit)? = null,
) {
    var txCode by remember(preview) { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val txRequirement = preview.transactionCode
    val acceptEnabled = enabled && (txRequirement == null || txRequirement.accepts(txCode))

    SystemBackHandler(enabled = !enabled || onBackAtRoot != null) {
        when {
            !enabled -> Unit
            else -> onBackAtRoot?.invoke()
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .exportTestTagsForPlatformAutomation(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                OfferReviewSection(
                    preview = preview,
                    acceptEnabled = acceptEnabled,
                    reviewEnabled = enabled,
                    txCode = txCode,
                    onTxCodeChange = { value ->
                        txCode = txRequirement?.normalizeInput(value) ?: value
                    },
                    onAccept = {
                        onAccept(txCode.trim().ifBlank { null })
                    },
                    onDecline = onDecline,
                )
            }
        }
    }
}

/** Busy state while a platform create request is being resolved. */
@Composable
fun WalletDemoOfferLoadingScreen() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/** Waiting state while an authorization-code create flow is in the browser. */
@Composable
fun WalletDemoOfferWaitingForAuthorizationScreen(onCancel: () -> Unit) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Waiting for issuer sign-in",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Complete sign-in in the browser. This screen finishes when the issuer redirects back to the wallet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                CircularProgressIndicator()
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}
