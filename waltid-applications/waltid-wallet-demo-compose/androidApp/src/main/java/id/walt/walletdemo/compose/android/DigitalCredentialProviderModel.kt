package id.walt.walletdemo.compose.android

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import id.walt.walletdemo.compose.logic.createAndroidDemoSharingSettingsStore
import id.walt.walletdemo.compose.logic.defaultCredentialSelection
import id.walt.walletdemo.compose.logic.toSharingReview
import id.walt.walletdemo.compose.logic.WalletDemoPaymentConsent
import id.walt.walletdemo.compose.logic.toDemoPaymentConsent

import android.content.Context
import id.walt.walletdemo.compose.logic.WalletDemoSharingReviewController
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class DigitalCredentialProviderModel(context: Context) : DigitalCredentialActivityModel() {
    private val context = context.applicationContext
    private val resultIntent = Intent()
    private var discardReview: (suspend () -> Unit)? = null
    private var submitReview: (suspend (WalletDemoSharingSelection) -> Unit)? = null
    var reviewController by mutableStateOf<WalletDemoSharingReviewController?>(null)
        private set
    var title by mutableStateOf("Share credentials")
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    var submitting by mutableStateOf(false)
        private set

    override suspend fun prepare(intent: Intent) {
        val allowlist = context.assets.open("privileged_apps.json").bufferedReader().use { it.readText() }
        val input = AndroidDigitalCredentialProvider.extract(intent, allowlist)
        val config = demoWalletConfig()
        val created = createAndroidDemoMobileWallet(context, config, ::interactionActivity)
        val wallet = created.wallet
        created.bootstrap(config.selectedSigningProtection(context))
        val showPreview = createAndroidDemoSharingSettingsStore(context).showDcApiPresentationPreview()
        if (input.request.protocol == MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C) {
            val request = wallet.annexCRequest(input.request)
            val preview = wallet.previewAnnexCPresentation(request)
            currentCoroutineContext().ensureActive()
            install(preview.toSharingReview(), "Share mobile document?", showPreview) { selection ->
                submitAnnexC(wallet, preview, request, selection, input.providerRequest)
            }
        } else {
            val preview = wallet.previewDigitalCredentialPresentation(input.request)
            discardReview = { wallet.discardDigitalCredentialPreview(preview.requestId) }
            currentCoroutineContext().ensureActive()
            install(preview.toSharingReview(), "Share digital credential?", showPreview,
                preparePaymentConsent = { selection ->
                    wallet.prepareDigitalCredentialPaymentConsent(preview.requestId,
                        selection.toCredentialSelections(), selection.toDisclosureSelections())?.toDemoPaymentConsent()
                }) { selection -> submitDigitalCredential(wallet, preview, selection, input.providerRequest) }
        }
    }

    private suspend fun install(
        review: WalletDemoSharingReview,
        title: String,
        showPreview: Boolean,
        preparePaymentConsent: (suspend (WalletDemoSharingSelection) -> WalletDemoPaymentConsent?)? = null,
        submit: suspend (WalletDemoSharingSelection) -> Unit,
    ) {
        if (result != null || released) return
        this.title = title
        submitReview = submit
        if (showPreview) reviewController = WalletDemoSharingReviewController(review, scope, preparePaymentConsent)
        else {
            // Preserve the user's existing preview preference and the SDK's consent guard.
            submitting = true
            submit(WalletDemoSharingSelection(credentials = review.defaultCredentialSelection()))
        }
    }

    fun submit(selection: WalletDemoSharingSelection) {
        val action = submitReview ?: return
        val current = reviewController?.selectionForSubmission() ?: return
        if (submitting || result != null || current != selection) return
        submitting = true
        perform { action(current) }
    }

    fun cancel() {
        if (submitting) return
        if (failure == null) AndroidDigitalCredentialProvider.setCancellation(resultIntent)
        else AndroidDigitalCredentialProvider.setFailure(resultIntent)
        finish(resultIntent)
    }

    fun back() { if (!submitting) finish() }

    private suspend fun submitDigitalCredential(
        wallet: MobileWallet,
        preview: MobileWalletDigitalCredentialPreview,
        selection: WalletDemoSharingSelection,
        providerRequest: ProviderGetCredentialRequest,
    ) {
        val response = wallet.submitDigitalCredentialPresentation(
            requestId = preview.requestId,
            selectedCredentialOptions = selection.toCredentialSelections(),
            selectedDisclosureOptions = selection.toDisclosureSelections(),
            paymentConsentRevision = selection.paymentConsentRevision,
        )
        currentCoroutineContext().ensureActive()
        AndroidDigitalCredentialProvider.setResponse(resultIntent, response, providerRequest)
        finish(resultIntent)
    }

    private suspend fun submitAnnexC(
        wallet: MobileWallet,
        preview: MobileWalletAnnexCPreview,
        request: MobileWalletAnnexCRequest,
        selection: WalletDemoSharingSelection,
        providerRequest: ProviderGetCredentialRequest,
    ) {
        val response = wallet.submitAnnexCPresentation(
            MobileWalletAnnexCSubmission(
                requestId = preview.requestId,
                verifiedOrigin = preview.verifiedOrigin,
                deviceRequestBase64Url = requireNotNull(request.deviceRequestBase64Url),
                encryptionInfoBase64Url = requireNotNull(request.encryptionInfoBase64Url),
                selectedCredentialOptions = selection.toCredentialSelections(),
            )
        )
        currentCoroutineContext().ensureActive()
        AndroidDigitalCredentialProvider.setResponse(resultIntent, response, providerRequest)
        finish(resultIntent)
    }


    override fun showFailure(cause: Exception) {
        Log.e("WaltDigitalCredentials", "Digital credential presentation failed (${cause::class.simpleName})", cause)
        submitting = false
        failure = cause.message ?: "The request could not be completed."
    }

    override suspend fun releaseRequest() {
        reviewController?.close()
        discardReview?.let { discard -> runCatching { discard() } }
    }
}

private fun WalletDemoSharingSelection.toCredentialSelections(): List<MobileWalletPresentationCredentialSelection> =
    credentials.map { MobileWalletPresentationCredentialSelection(it.queryId, it.credentialId) }

private fun WalletDemoSharingSelection.toDisclosureSelections(): List<MobileWalletPresentationDisclosureSelection> =
    disclosures.map { MobileWalletPresentationDisclosureSelection(it.queryId, it.credentialId, it.path) }
