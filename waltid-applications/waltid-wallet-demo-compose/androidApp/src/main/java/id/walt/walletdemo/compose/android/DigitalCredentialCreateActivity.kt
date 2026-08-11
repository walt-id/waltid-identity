package id.walt.walletdemo.compose.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.ExperimentalDigitalCredentialApi
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.mobile.AndroidDigitalCredentialCreateProvider
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletDigitalCredentialCreateResponse
import id.walt.wallet2.mobile.MobileWalletIssuanceRequest
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceGrant
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceSession
import id.walt.walletdemo.compose.logic.createAndroidDemoMobileWallet
import id.walt.walletdemo.compose.logic.toDemoIssuanceSession
import id.walt.walletdemo.compose.ui.WalletDemoOfferLoadingScreen
import id.walt.walletdemo.compose.ui.WalletDemoOfferReviewScreen
import id.walt.walletdemo.compose.ui.WalletDemoOfferWaitingForAuthorizationScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Credential Manager create-provider entry point for OpenID4VCI issuance.
 *
 * Separate from [MainActivity]: Credential Manager owns this task's lifecycle and expects exactly one
 * create result from it.
 */
@OptIn(ExperimentalDigitalCredentialApi::class)
class DigitalCredentialCreateActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val resultIntent = Intent()
    private var wallet: MobileWallet? = null
    private var session: WalletDemoIssuanceSession? = null
    private var requestProtocol: String? = null
    private var uiState by mutableStateOf<CreateUiState>(CreateUiState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            when (val state = uiState) {
                CreateUiState.Loading -> WalletDemoOfferLoadingScreen()
                is CreateUiState.Review -> WalletDemoOfferReviewScreen(
                    preview = state.session.preview,
                    title = "Accept digital credential?",
                    enabled = !state.submitting,
                    onAccept = { txCode ->
                        uiState = state.copy(submitting = true)
                        acceptOffer(state.session, txCode)
                    },
                    onDecline = {
                        scope.launch {
                            runCatching { wallet?.cancelIssuance(state.session.id) }
                            AndroidDigitalCredentialCreateProvider.setCancellation(resultIntent)
                            finishProviderResult()
                        }
                    },
                    onBackAtRoot = {
                        finishWithoutProviderResult()
                    },
                )
                CreateUiState.WaitingForAuthorization -> WalletDemoOfferWaitingForAuthorizationScreen(
                    onCancel = {
                        cancelPendingAuthorization()
                        AndroidDigitalCredentialCreateProvider.setCancellation(resultIntent)
                        finishProviderResult()
                    },
                )
            }
        }
        scope.launch {
            runCatching {
                val allowlist = assets.open("privileged_apps.json").bufferedReader().use { it.readText() }
                val input = AndroidDigitalCredentialCreateProvider.extract(intent, allowlist)
                requestProtocol = input.request.protocol
                val created = createAndroidDemoMobileWallet(
                    context = applicationContext,
                    config = demoWalletConfig(),
                )
                val mobileWallet = created.wallet
                wallet = mobileWallet
                mobileWallet.bootstrap()
                val started = mobileWallet.startIssuance(
                    MobileWalletIssuanceRequest(
                        offerJson = input.request.offerJson,
                        redirectUri = "openid://",
                    )
                ).toDemoIssuanceSession()
                session = started
                uiState = CreateUiState.Review(started)
            }.onFailure {
                reportFailure(it)
            }
        }
    }

    private fun acceptOffer(started: WalletDemoIssuanceSession, txCode: String?) {
        val mobileWallet = wallet ?: return reportFailure(IllegalStateException("Wallet is missing"))
        scope.launch {
            runCatching {
                when (started.grant) {
                    WalletDemoIssuanceGrant.PreAuthorizedCode -> {
                        val outcome = mobileWallet.continuePreAuthorizedIssuance(
                            sessionId = started.id,
                            transactionCode = txCode,
                        )
                        completeOutcome(outcome)
                    }
                    WalletDemoIssuanceGrant.AuthorizationCode -> {
                        val authorization = mobileWallet.beginAuthorizationIssuance(started.id)
                        DigitalCredentialCreateAuthHandoff.begin(started.id) { callbackUri ->
                            scope.launch {
                                runCatching {
                                    val outcome = mobileWallet.continueAuthorizationIssuance(
                                        sessionId = started.id,
                                        callbackUri = callbackUri,
                                    )
                                    completeOutcome(outcome)
                                }.onFailure { reportFailure(it) }
                            }
                        }
                        uiState = CreateUiState.WaitingForAuthorization
                        startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(authorization.url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }.onFailure {
                reportFailure(it)
            }
        }
    }

    private suspend fun completeOutcome(outcome: WalletIssuanceOutcome) {
        when (outcome) {
            is WalletIssuanceOutcome.Stored,
            is WalletIssuanceOutcome.Deferred,
            -> {
                DigitalCredentialCreateAuthHandoff.clear(session?.id)
                sendSuccessAck()
            }
            is WalletIssuanceOutcome.Cancelled -> {
                DigitalCredentialCreateAuthHandoff.clear(session?.id)
                AndroidDigitalCredentialCreateProvider.setCancellation(resultIntent)
                finishProviderResult()
            }
            is WalletIssuanceOutcome.Failed -> {
                reportFailure(IllegalStateException(outcome.error.message))
            }
        }
    }

    private fun sendSuccessAck() {
        val protocol = requestProtocol
            ?: MobileWalletDigitalCredentialCreateResponse.acknowledgment().protocol
        val response = MobileWalletDigitalCredentialCreateResponse.acknowledgment(protocol)
        AndroidDigitalCredentialCreateProvider.setResponse(resultIntent, response)
        finishProviderResult()
    }

    private fun cancelPendingAuthorization() {
        val sessionId = session?.id
        DigitalCredentialCreateAuthHandoff.clear(sessionId)
        if (sessionId != null) {
            scope.launch {
                runCatching { wallet?.cancelIssuance(sessionId) }
            }
        }
    }

    private fun reportFailure(error: Throwable) {
        Log.e(TAG, "Digital credential issuance failed (${error::class.simpleName})", error)
        DigitalCredentialCreateAuthHandoff.clear(session?.id)
        AndroidDigitalCredentialCreateProvider.setFailure(resultIntent)
        finishProviderResult()
    }

    private fun finishProviderResult() {
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun finishWithoutProviderResult() {
        cancelPendingAuthorization()
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        if (isFinishing) {
            DigitalCredentialCreateAuthHandoff.clear(session?.id)
        }
        scope.cancel()
        super.onDestroy()
    }

    private sealed interface CreateUiState {
        data object Loading : CreateUiState
        data class Review(
            val session: WalletDemoIssuanceSession,
            val submitting: Boolean = false,
        ) : CreateUiState
        data object WaitingForAuthorization : CreateUiState
    }

    private companion object {
        private const val TAG = "WaltDigitalCredentials"
    }
}
