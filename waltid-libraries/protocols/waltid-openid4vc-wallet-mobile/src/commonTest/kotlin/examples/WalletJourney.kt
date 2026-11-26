package examples

import id.walt.wallet2.mobile.issuance.IssuanceAuthorizationId
import id.walt.wallet2.mobile.issuance.IssuanceSession
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.wallet2.consent.PreparedPaymentConsent
import id.walt.wallet2.handlers.*
import id.walt.wallet2.mobile.Wallet
import id.walt.wallet2.mobile.WalletConfiguration
import id.walt.wallet2.mobile.WalletEvent
import id.walt.wallet2.mobile.WalletFactory
import id.walt.wallet2.mobile.credentials.WalletCredential
import id.walt.wallet2.mobile.identity.SigningIdentityOperationResult
import id.walt.wallet2.mobile.issuance.IssuanceClientRegistration
import id.walt.wallet2.mobile.issuance.CredentialOfferInput
import id.walt.wallet2.mobile.issuance.IssuanceRequest
import id.walt.wallet2.mobile.presentation.ReviewedPresentationClaims
import id.walt.wallet2.mobile.presentation.PresentationConfiguration
import id.walt.wallet2.mobile.presentation.PresentationPreview
import id.walt.wallet2.mobile.presentation.PresentationPreviewResult
import id.walt.wallet2.mobile.presentation.PresentationResult
import id.walt.wallet2.mobile.presentation.PresentationSelection
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
    fun progress(event: WalletEvent)
    fun setup(result: SigningIdentityOperationResult)
    suspend fun accept(offer: WalletIssuanceOfferPreview): Boolean
    suspend fun code(requirement: WalletIssuanceTransactionCode): String
    suspend fun saveAuthorization(id: IssuanceAuthorizationId)
    suspend fun browser(url: String): String? // A closed browser returns null.
    fun received(outcome: WalletIssuanceOutcome)
    fun stored(credentials: List<WalletCredential>)
    suspend fun choose(preview: PresentationPreview): PresentationSelection
    suspend fun confirm(claims: ReviewedPresentationClaims, statuses: List<WalletCredentialStatus>, payment: PreparedPaymentConsent?): Boolean
    fun deliver(result: PresentationResult)
    fun reviewChanged()
}

private suspend fun walletJourney(factory: WalletFactory, offer: String, request: String,
    registrationFor: (String) -> IssuanceClientRegistration,
    trust: ClientIdTrustConfiguration, ui: Screen) {
    val config = WalletConfiguration(walletId = "member", presentation = PresentationConfiguration(verifierTrust = trust))
    suspend fun useWallet(block: suspend (Wallet) -> Unit) = coroutineScope {
        val wallet = factory.open(config)
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
        val session = wallet.startIssuance(IssuanceRequest(offer = CredentialOfferInput.Uri(offer)))
        val outcome = if (!ui.accept(session.offer)) wallet.cancelIssuance(session.id) else when (session) {
            is IssuanceSession.PreAuthorized -> session.accept(
                transactionCode = session.transactionCode?.let { ui.code(it) },
                clientId = if (session.clientIdentification == IssuanceClientIdentification.CLIENT_ID_REQUIRED)
                    registrationFor(session.authorizationServer).clientId else null,
            )
            is IssuanceSession.AuthorizationCode -> {
                val authorization = session.beginAuthorization(registrationFor(session.authorizationServer))
                ui.saveAuthorization(authorization.id)
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
            is PresentationPreviewResult.Invalid -> wallet.discardPresentationPreview(result.previewHandle)
            is PresentationPreviewResult.Ready -> {
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

// Called by the application's redirect handler after opening the same wallet.
private suspend fun completeSavedAuthorization(wallet: Wallet, savedID: String, callback: String): WalletIssuanceOutcome =
    wallet.completeAuthorization(IssuanceAuthorizationId(savedID), callback)
