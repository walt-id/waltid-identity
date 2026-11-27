package id.walt.wallet2.mobile

import id.walt.crypto.exceptions.CryptoArgumentException
import id.walt.crypto.exceptions.CryptoStateException
import id.walt.crypto2.keys.KeyUseAuthorizationException
import id.walt.wallet2.consent.PaymentConsentException
import id.walt.wallet2.handlers.IssuanceOutcomeUncertainException
import id.walt.wallet2.handlers.IssuanceSessionStorageException
import id.walt.wallet2.handlers.PresentationReviewChangedException
import id.walt.wallet2.handlers.PreviewSessionException
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import id.walt.wallet2.status.CredentialStatusReviewChangedException
import id.walt.webdatafetching.DataFetchingException
import id.walt.webdatafetching.ssrf.BlockedAddressException
import id.waltid.openid4vp.wallet.PresentationDeliveryUncertainException
import id.waltid.openid4vp.wallet.UnsafePresentationErrorResponseException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.Serializable

/** Stable categories for thrown operations. Domain outcomes carry issuance progress and preview decisions. */
@Serializable
public enum class WalletFailure(
    /** Display-safe fallback, independent of protocol bodies and provider exception messages. */
    public val userMessage: String,
) {
    invalidInput("The wallet request is invalid or has expired."),
    presentationValidation("The request is invalid and the wallet cannot safely report it to the verifier."),
    storage("Wallet storage is unavailable."),
    cancelled("The wallet operation was cancelled."),
    authorization("The wallet key could not be authorized."),
    paymentConsent("Payment authorization could not be completed."),
    internalFailure("The wallet operation could not be completed."),
    credentialStatusReviewChanged("Credential status changed. Review it again before sharing."),
    presentationReviewChanged("The presentation changed. Review it again before sharing."),
    timeout("The wallet service did not respond in time."),
    network("The wallet service could not be reached."),
    remoteResponse("The remote service returned an error."),
    crypto("The requested cryptographic operation failed."),
    issuanceOutcomeUncertain("Issuance is already in progress or its outcome is unknown. Check its status."),
    deliveryUncertain("Delivery could not be confirmed. Check the verifier transaction before sharing again.");

    /** Classifies thrown errors. Returned issuance receipts carry their own recovery state. */
    public companion object {
        /** Classifies a thrown failure without parsing its message. Rethrow parent cancellation in suspend callers. */
        public fun classify(error: Throwable): WalletFailure = when (error) {
            is CancellationException -> cancelled
            is PresentationDeliveryUncertainException -> deliveryUncertain
            is IssuanceOutcomeUncertainException -> issuanceOutcomeUncertain
            is UnsafePresentationErrorResponseException -> presentationValidation
            is PresentationReviewChangedException -> presentationReviewChanged
            is CredentialStatusReviewChangedException -> credentialStatusReviewChanged
            is PaymentConsentException -> paymentConsent
            is KeyUseAuthorizationException -> authorization
            is WalletPersistenceException, is WalletStorageInUseException, is IssuanceSessionStorageException -> storage
            is CryptoArgumentException, is CryptoStateException -> crypto
            is HttpRequestTimeoutException, is SocketTimeoutException, is ConnectTimeoutException -> timeout
            is ResponseException -> remoteResponse
            is IOException -> network
            is DataFetchingException -> when (val cause = error.cause) {
                is IOException, is HttpRequestTimeoutException, is ResponseException -> classify(cause)
                is BlockedAddressException, is IllegalArgumentException -> invalidInput
                else -> internalFailure
            }
            is IllegalArgumentException, is PreviewSessionException, is BlockedAddressException -> invalidInput
            else -> internalFailure
        }
    }
}
