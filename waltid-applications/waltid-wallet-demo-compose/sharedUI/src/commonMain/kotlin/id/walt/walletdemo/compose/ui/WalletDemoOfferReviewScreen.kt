package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoOfferPreview
import id.walt.walletdemo.compose.ui.components.OfferReviewSection

/**
 * UI states for the Credential Manager CREATE_CREDENTIAL fulfillment sheet.
 *
 * Drawn as a [ModalBottomSheet] over a translucent provider Activity so issuance feels in-tray
 * rather than opening the full wallet app.
 */
sealed interface WalletDemoOfferCreateUiState {
    data object Loading : WalletDemoOfferCreateUiState

    data class Review(
        val preview: WalletDemoOfferPreview,
        val title: String = "Accept digital credential?",
        val submitting: Boolean = false,
    ) : WalletDemoOfferCreateUiState

    /**
     * Authorization-code grant: issuer/Keycloak sign-in runs in an embedded WebView.
     *
     * @property authorizationUrl Authorization endpoint URL returned after the user accepts the offer.
     * @property redirectUri Registered callback prefix (typically `openid://`).
     * @property completing True after the redirect was captured and issuance is finishing.
     */
    data class Authorizing(
        val authorizationUrl: String,
        val redirectUri: String,
        val completing: Boolean = false,
    ) : WalletDemoOfferCreateUiState
}

/**
 * Bottom-sheet host for Digital Credentials create (OpenID4VCI) fulfillment.
 *
 * Covers offer review (including transaction-code entry), decline/dismiss, and in-sheet
 * authorization-code sign-in via [IssuerAuthorizationWebView].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDemoOfferCreateSheet(
    state: WalletDemoOfferCreateUiState,
    onAccept: (txCode: String?) -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
    onCancelAuthorization: () -> Unit,
    onAuthorizationRedirect: (callbackUri: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismissEnabled = when (state) {
        is WalletDemoOfferCreateUiState.Review -> !state.submitting
        WalletDemoOfferCreateUiState.Loading -> true
        is WalletDemoOfferCreateUiState.Authorizing -> !state.completing
    }

    SystemBackHandler(enabled = dismissEnabled) {
        when (state) {
            is WalletDemoOfferCreateUiState.Authorizing -> onCancelAuthorization()
            is WalletDemoOfferCreateUiState.Review -> if (!state.submitting) onDismiss()
            WalletDemoOfferCreateUiState.Loading -> onDismiss()
        }
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .exportTestTagsForPlatformAutomation(),
        ) {
            ModalBottomSheet(
                onDismissRequest = {
                    if (!dismissEnabled) return@ModalBottomSheet
                    when (state) {
                        is WalletDemoOfferCreateUiState.Authorizing -> onCancelAuthorization()
                        else -> onDismiss()
                    }
                },
                sheetState = sheetState,
            ) {
                when (state) {
                    WalletDemoOfferCreateUiState.Loading -> OfferCreateLoadingContent()
                    is WalletDemoOfferCreateUiState.Review -> OfferCreateReviewContent(
                        preview = state.preview,
                        title = state.title,
                        enabled = !state.submitting,
                        onAccept = onAccept,
                        onDecline = onDecline,
                    )
                    is WalletDemoOfferCreateUiState.Authorizing -> OfferCreateAuthorizingContent(
                        authorizationUrl = state.authorizationUrl,
                        redirectUri = state.redirectUri,
                        completing = state.completing,
                        onCancel = onCancelAuthorization,
                        onRedirect = onAuthorizationRedirect,
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferCreateLoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            "Preparing credential offer…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun OfferCreateReviewContent(
    preview: WalletDemoOfferPreview,
    title: String,
    enabled: Boolean,
    onAccept: (txCode: String?) -> Unit,
    onDecline: () -> Unit,
) {
    var txCode by remember(preview) { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val txRequirement = preview.transactionCode
    val acceptEnabled = enabled && (txRequirement == null || txRequirement.accepts(txCode))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
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

@Composable
private fun OfferCreateAuthorizingContent(
    authorizationUrl: String,
    redirectUri: String,
    completing: Boolean,
    onCancel: () -> Unit,
    onRedirect: (callbackUri: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp)
            .testTag(WalletUiTestTags.OfferAuthorizationSection),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (completing) "Finishing issuance…" else "Verify your identity",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (completing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        } else {
            IssuerAuthorizationWebView(
                authorizationUrl = authorizationUrl,
                redirectUri = redirectUri,
                onRedirect = onRedirect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
            )
            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Cancel")
            }
        }
    }
}
