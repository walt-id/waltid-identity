package id.walt.walletdemo.compose.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.credentials.provider.ProviderGetCredentialRequest
import id.walt.wallet2.mobile.AndroidDigitalCredentialProvider
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletAnnexCPreview
import id.walt.wallet2.mobile.MobileWalletAnnexCRequest
import id.walt.wallet2.mobile.MobileWalletAnnexCSubmission
import id.walt.wallet2.mobile.MobileWalletDigitalCredentialPreview
import id.walt.wallet2.mobile.MobileWalletDigitalCredentialProtocols
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialSelection
import id.walt.wallet2.mobile.MobileWalletPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.WalletDemoSharingSelection
import id.walt.walletdemo.compose.logic.createAndroidDemoMobileWallet
import id.walt.walletdemo.compose.logic.toSharingReview
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Credential Manager provider entry point.
 *
 * Separate from [MainActivity]: Credential Manager owns this task's lifecycle and expects exactly one
 * result from it, which the wallet's own navigation must not be able to influence.
 */
class DigitalCredentialProviderActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val resultIntent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            runCatching {
                // Vendored subset of https://www.gstatic.com/gpm-passkeys-privileged-apps/apps.json.
                // Pinned in the APK on purpose: fetching it would put caller-origin trust on the network.
                val allowlist = assets.open("privileged_apps.json").bufferedReader().use { it.readText() }
                val input = AndroidDigitalCredentialProvider.extract(intent, allowlist)
                // The same construction MainActivity uses: Credential Manager launches this activity
                // without the wallet UI having run, and a wallet configured independently here would
                // open a different database.
                val wallet = createAndroidDemoMobileWallet(
                    context = applicationContext,
                    config = demoWalletConfig(),
                ).wallet
                wallet.bootstrap()
                if (input.request.protocol == MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C) {
                    val annexCRequest = wallet.annexCRequest(input.request)
                    val preview = wallet.previewAnnexCPresentation(annexCRequest)
                    showReview(
                        review = preview.toSharingReview(),
                        title = "Share mobile document?",
                    ) { selection ->
                        submitAnnexC(wallet, preview, annexCRequest, selection, input.providerRequest)
                    }
                } else {
                    val preview = wallet.previewDigitalCredentialPresentation(input.request)
                    showReview(
                        review = preview.toSharingReview(),
                        title = "Share digital credential?",
                    ) { selection ->
                        submitDigitalCredential(wallet, preview, selection, input.providerRequest)
                    }
                }
            }.onFailure {
                reportFailure(it)
            }
        }
    }

    /**
     * Shows the shared review UI.
     *
     * Cancel and back resolve to different Credential Manager outcomes: Cancel ends the caller's whole
     * operation, while backing out of this provider's review returns [RESULT_CANCELED] so Credential
     * Manager can put its selector back up and another provider can still answer.
     */
    private fun showReview(
        review: WalletDemoSharingReview,
        title: String,
        onSubmit: (WalletDemoSharingSelection) -> Unit,
    ) {
        setContent {
            var submitting by remember { mutableStateOf(false) }
            WalletDemoSharingReviewScreen(
                review = review,
                title = title,
                enabled = !submitting,
                onSubmit = { selection ->
                    submitting = true
                    onSubmit(selection)
                },
                onCancel = {
                    AndroidDigitalCredentialProvider.setCancellation(resultIntent)
                    finishProviderResult()
                },
                onBackAtRoot = ::finishWithoutProviderResult,
            )
        }
    }

    private fun submitDigitalCredential(
        wallet: MobileWallet,
        preview: MobileWalletDigitalCredentialPreview,
        selection: WalletDemoSharingSelection,
        providerRequest: ProviderGetCredentialRequest,
    ) {
        scope.launch {
            runCatching {
                wallet.submitDigitalCredentialPresentation(
                    requestId = preview.requestId,
                    selectedCredentialOptions = selection.toCredentialSelections(),
                    selectedDisclosureOptions = selection.toDisclosureSelections(),
                )
            }.onSuccess { response ->
                AndroidDigitalCredentialProvider.setResponse(resultIntent, response, providerRequest)
                finishProviderResult()
            }.onFailure {
                reportFailure(it)
            }
        }
    }

    private fun submitAnnexC(
        wallet: MobileWallet,
        preview: MobileWalletAnnexCPreview,
        request: MobileWalletAnnexCRequest,
        selection: WalletDemoSharingSelection,
        providerRequest: ProviderGetCredentialRequest,
    ) {
        scope.launch {
            runCatching {
                wallet.submitAnnexCPresentation(
                    MobileWalletAnnexCSubmission(
                        requestId = preview.requestId,
                        verifiedOrigin = preview.verifiedOrigin,
                        deviceRequestBase64Url = requireNotNull(request.deviceRequestBase64Url),
                        encryptionInfoBase64Url = requireNotNull(request.encryptionInfoBase64Url),
                        selectedCredentialOptions = selection.toCredentialSelections(),
                    )
                )
            }.onSuccess { response ->
                AndroidDigitalCredentialProvider.setResponse(resultIntent, response, providerRequest)
                finishProviderResult()
            }.onFailure {
                reportFailure(it)
            }
        }
    }

    private fun reportFailure(error: Throwable) {
        Log.e(TAG, "Digital credential presentation failed (${error::class.simpleName})", error)
        AndroidDigitalCredentialProvider.setFailure(resultIntent)
        finishProviderResult()
    }

    private fun finishProviderResult() {
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    /**
     * Leaves this provider without answering, which Credential Manager reads as "ask again".
     *
     * [RESULT_CANCELED] must carry no Credential Manager payload: writing a cancellation exception here
     * would end the caller's `getCredential` call, as the Cancel button does.
     */
    private fun finishWithoutProviderResult() {
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        private const val TAG = "WaltDigitalCredentials"
    }
}

private fun WalletDemoSharingSelection.toCredentialSelections(): List<MobileWalletPresentationCredentialSelection> =
    credentials.map { MobileWalletPresentationCredentialSelection(it.queryId, it.credentialId) }

private fun WalletDemoSharingSelection.toDisclosureSelections(): List<MobileWalletPresentationDisclosureSelection> =
    disclosures.map { MobileWalletPresentationDisclosureSelection(it.queryId, it.credentialId, it.path) }
