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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Credential Manager provider entry point.
 *
 * The Activity is deliberately thin: it extracts the platform request, previews it through the wallet,
 * shows the wallet's shared review UI, and turns the user's answer back into a Credential Manager
 * result. It renders no consent copy of its own, because a second presentation surface would drift
 * from the one the in-app OpenID4VP flow is reviewed and tested against.
 *
 * It stays separate from [MainActivity] rather than routing through it: Credential Manager owns this
 * task's lifecycle and expects exactly one result from it, which is not something the wallet's own
 * navigation should be able to influence.
 */
class DigitalCredentialProviderActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val resultIntent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            runCatching {
                val allowlist = assets.open("privileged_apps.json").bufferedReader().use { it.readText() }
                val input = AndroidDigitalCredentialProvider.extract(intent, allowlist)
                // The same construction MainActivity uses. Credential Manager launches this activity
                // without the wallet UI having run, so configuring a wallet here independently would
                // open a different database and apply a different transaction-data policy.
                val wallet = createAndroidDemoMobileWallet(
                    context = applicationContext,
                    config = demoWalletConfig(),
                ).wallet
                wallet.bootstrap()
                if (input.request.protocol == MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C) {
                    val data = Json.parseToJsonElement(input.request.dataJson).jsonObject
                    val deviceRequest = requireNotNull(data["deviceRequest"]?.jsonPrimitive?.content) {
                        "Annex C deviceRequest is required"
                    }
                    val encryptionInfo = requireNotNull(data["encryptionInfo"]?.jsonPrimitive?.content) {
                        "Annex C encryptionInfo is required"
                    }
                    val preview = wallet.previewAnnexCPresentation(
                        MobileWalletAnnexCRequest(
                            parsedRequest = wallet.parseAnnexCDeviceRequest(deviceRequest),
                            verifiedOrigin = input.request.verifiedOrigin,
                            selectedRegistryEntryIds = input.request.selectedRegistryEntryIds,
                            deviceRequestBase64Url = deviceRequest,
                            encryptionInfoBase64Url = encryptionInfo,
                        )
                    )
                    showReview(
                        review = preview.toSharingReview(),
                        title = "Share mobile document?",
                    ) { selection ->
                        submitAnnexC(wallet, preview, deviceRequest, encryptionInfo, selection, input.providerRequest)
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
     * There is no Reject action: Credential Manager has no channel for a protocol-level refusal, so
     * declining returns the platform's cancellation instead. [enabled] is dropped for the duration of
     * a submission so a second tap cannot start a second response for one request.
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
        deviceRequest: String,
        encryptionInfo: String,
        selection: WalletDemoSharingSelection,
        providerRequest: ProviderGetCredentialRequest,
    ) {
        scope.launch {
            runCatching {
                wallet.submitAnnexCPresentation(
                    MobileWalletAnnexCSubmission(
                        requestId = preview.requestId,
                        verifiedOrigin = preview.verifiedOrigin,
                        deviceRequestBase64Url = deviceRequest,
                        encryptionInfoBase64Url = encryptionInfo,
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
