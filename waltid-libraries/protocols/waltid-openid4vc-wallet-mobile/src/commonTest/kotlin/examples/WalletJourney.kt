package examples

import id.walt.wallet2.mobile.issuance.MobileWalletIssuanceSession
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.wallet2.consent.PreparedPaymentConsent
import id.walt.wallet2.handlers.*
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletEvent
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.wallet2.mobile.credentials.MobileWalletCredential
import id.walt.wallet2.mobile.identity.SigningIdentityOperationResult
import id.walt.wallet2.mobile.issuance.IssuanceClientRegistration
import id.walt.wallet2.mobile.issuance.MobileWalletCredentialOffer
import id.walt.wallet2.mobile.issuance.MobileWalletIssuanceRequest
import id.walt.wallet2.mobile.presentation.MobileWalletPreparedPresentationReview
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationConfig
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationPreview
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationPreviewResult
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationResult
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationSelection
import id.walt.wallet2.handlers.PresentationReviewChangedException
import id.walt.wallet2.status.CredentialStatusReviewChangedException
import id.walt.wallet2.status.WalletCredentialStatus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The app implements these UI decisions and delivers each Prepared or Transmitted result.
private interface Screen {
    fun progress(event: MobileWalletEvent)
    fun setup(result: SigningIdentityOperationResult)
    suspend fun accept(offer: WalletIssuanceOfferPreview): Boolean
    suspend fun code(requirement: WalletIssuanceTransactionCode): String
    suspend fun browser(url: String): String? // A closed browser returns null.
    fun received(outcome: WalletIssuanceOutcome)
    fun stored(credentials: List<MobileWalletCredential>)
    suspend fun choose(preview: MobileWalletPresentationPreview): MobileWalletPresentationSelection
    suspend fun confirm(claims: MobileWalletPreparedPresentationReview, statuses: List<WalletCredentialStatus>, payment: PreparedPaymentConsent?): Boolean
    fun deliver(result: MobileWalletPresentationResult)
    fun reviewChanged()
}

private suspend fun walletJourney(factory: MobileWalletFactory, offer: String, request: String,
    registrationFor: (String) -> IssuanceClientRegistration,
    trust: ClientIdTrustConfiguration, ui: Screen) {
    val config = MobileWalletConfig(walletId = "member", presentation = MobileWalletPresentationConfig(verifierTrust = trust))
    suspend fun useWallet(block: suspend (MobileWallet) -> Unit) = coroutineScope {
        val wallet = factory.create(config)
        val observer = launch { wallet.events.collect(ui::progress) }
        try {
            block(wallet)
        } finally {
            withContext(NonCancellable) {
                observer.cancelAndJoin()
                wallet.close()
            }
        }
    }

    useWallet { wallet ->
        val identity = wallet.signingIdentity.initialize()
        if (identity !is SigningIdentityOperationResult.Active) {
            ui.setup(identity)
            return@useWallet
        }
        val session = wallet.startIssuance(MobileWalletIssuanceRequest(offer = MobileWalletCredentialOffer.Uri(offer)))
        val outcome = if (!ui.accept(session.offer)) wallet.cancelIssuance(session.id) else when (session) {
            is MobileWalletIssuanceSession.PreAuthorized -> session.accept(
                transactionCode = session.transactionCode?.let { ui.code(it) },
                clientId = if (session.clientIdentification == IssuanceClientIdentification.CLIENT_ID_REQUIRED)
                    registrationFor(session.authorizationServer).clientId else null,
            )
            is MobileWalletIssuanceSession.AuthorizationCode -> {
                val authorization = session.beginAuthorization(registrationFor(session.authorizationServer))
                val redirect = ui.browser(authorization.request.url)
                if (redirect == null) wallet.cancelIssuance(session.id) else authorization.complete(redirect)
            }
        }
        ui.received(outcome) // Includes stored IDs, retry eligibility and retained continuations on partial failure.
    }

    // A later app session opens the same wallet and can present an already stored credential.
    useWallet { wallet ->
        ui.stored(wallet.credentials())
        when (val result = wallet.previewPresentation(request)) {
            is MobileWalletPresentationPreviewResult.Invalid -> wallet.discardPresentationPreview(result.previewHandle)
            is MobileWalletPresentationPreviewResult.Ready -> {
                val preview = result.preview
                val handle = preview.previewHandle
                try {
                    val selection = ui.choose(preview)
                    val review = wallet.reviewPresentation(handle, selection)
                    val response = if (ui.confirm(review.claims, review.statuses, review.payment)) {
                        wallet.submitPresentation(review)
                    } else wallet.rejectPresentation(handle)
                    ui.deliver(response)
                } catch (failure: Exception) {
                    // A failed submission can already have consumed the handle.
                    withContext(NonCancellable) { runCatching { wallet.discardPresentationPreview(handle) } }
                    if (failure is CredentialStatusReviewChangedException || failure is PresentationReviewChangedException) ui.reviewChanged() else throw failure
                }
            }
        }
    }
}
