@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.swiftinterop

import id.walt.crypto2.keys.KeyUseAuthorizationException
import id.walt.crypto2.keys.KeyUseAuthorizationFailure
import id.walt.wallet2.consent.PaymentConsentException
import id.walt.wallet2.consent.PaymentConsentFailure
import id.walt.wallet2.handlers.PresentationReviewChangedException
import id.walt.wallet2.handlers.PreviewSessionException
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationErrorCode
import id.walt.wallet2.mobile.presentation.toMobileErrorCode
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import id.walt.wallet2.status.CredentialStatusReviewChangedException
import id.waltid.openid4vp.wallet.UnsafePresentationErrorResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Coarse error category for Swift bridge failures.
 */
@Serializable
public enum class WalletBridgeErrorCategory {
    /** Input supplied by the caller is invalid. */
    invalidInput,

    /** Request validation failed and its error response could not be sent safely. */
    presentationValidation,

    /** Wallet storage access failed. */
    storage,

    /** The operation was cancelled. */
    cancelled,

    /** Key-use authorization was unavailable or was not completed. */
    authorization,

    /** A payment could not be reviewed or authorized. */
    paymentConsent,

    /** Unexpected wallet failure that does not fit a narrower category. */
    internalFailure,

    /** The local semantic status warning changed after host review. */
    credentialStatusReviewChanged,

    /** A previously reviewed request, credential or holder has changed. */
    presentationReviewChanged,
}

/**
 * An OpenID4VP validation error retained locally because remote reporting was unsafe.
 * @property errorCode Original OpenID4VP protocol error code.
 * @property message Original request-validation message.
 * @property responseSafetyFailure Reason the error response was not sent.
 */
@Serializable
public data class WalletBridgePresentationValidationFailure(
    public val errorCode: MobileWalletPresentationErrorCode,
    public val message: String,
    public val responseSafetyFailure: String,
)

/**
 * Serializable error returned to Swift callers when a bridge operation fails.
 *
 * @property category Coarse failure category.
 * @property message Human-readable failure message.
 * @property causeClass Kotlin exception class name when available.
 * @property authorizationFailure Stable key-use authorization failure when authorization is the category.
 * @property paymentConsentFailure Stable payment failure when paymentConsent is the category.
 * @property presentationValidationFailure Protocol error and separate response-safety reason.
 */
@Serializable
public class WalletBridgeError internal constructor(
    public val category: WalletBridgeErrorCategory,
    public val message: String,
    public val causeClass: String? = null,
    public val authorizationFailure: KeyUseAuthorizationFailure? = null,
    public val paymentConsentFailure: PaymentConsentFailure? = null,
    public val presentationValidationFailure: WalletBridgePresentationValidationFailure? = null,
) {
    init {
        require((category == WalletBridgeErrorCategory.presentationValidation) == (presentationValidationFailure != null)) {
            "Presentation validation bridge errors must include exactly one validation failure"
        }
        require((category == WalletBridgeErrorCategory.paymentConsent) == (paymentConsentFailure != null)) {
            "Payment consent bridge errors must include exactly one payment failure"
        }
        require((category == WalletBridgeErrorCategory.authorization) == (authorizationFailure != null)) {
            "Authorization bridge errors must include exactly one authorization failure"
        }
    }
    internal companion object {
        fun fromThrowable(throwable: Throwable): WalletBridgeError {
            val authorizationFailure = (throwable as? KeyUseAuthorizationException)?.failure
            val paymentFailure = (throwable as? PaymentConsentException)?.reason
            val presentationFailure = throwable as? UnsafePresentationErrorResponseException
            val category = when {
                presentationFailure != null -> WalletBridgeErrorCategory.presentationValidation
                throwable is PresentationReviewChangedException -> WalletBridgeErrorCategory.presentationReviewChanged
                throwable is CredentialStatusReviewChangedException -> WalletBridgeErrorCategory.credentialStatusReviewChanged
                paymentFailure != null -> WalletBridgeErrorCategory.paymentConsent
                authorizationFailure != null -> WalletBridgeErrorCategory.authorization
                throwable is CancellationException -> WalletBridgeErrorCategory.cancelled
                throwable is IllegalArgumentException -> WalletBridgeErrorCategory.invalidInput
                throwable is PreviewSessionException -> WalletBridgeErrorCategory.invalidInput
                throwable is WalletPersistenceException -> WalletBridgeErrorCategory.storage
                else -> WalletBridgeErrorCategory.internalFailure
            }

            return WalletBridgeError(
                category = category,
                message = throwable.message ?: throwable::class.simpleName ?: "Unknown wallet error",
                causeClass = throwable::class.simpleName,
                authorizationFailure = authorizationFailure,
                paymentConsentFailure = paymentFailure,
                presentationValidationFailure = presentationFailure?.let {
                    WalletBridgePresentationValidationFailure(
                        errorCode = it.error.code.toMobileErrorCode(),
                        message = it.error.message,
                        responseSafetyFailure = it.responseSafetyFailure.message ?: "Unsafe response channel",
                    )
                },
            )
        }
    }
}

/**
 * Result wrapper used by Swift bridge operations.
 */
public sealed interface WalletBridgeResult<out T> {
    /**
     * Successful bridge operation result.
     *
     * @property value Value returned by the wallet operation.
     */
    public data class Success<T>(public val value: T) : WalletBridgeResult<T>

    /**
     * Failed bridge operation result.
     *
     * @property error Structured bridge error returned to Swift callers.
     */
    public data class Failure(public val error: WalletBridgeError) : WalletBridgeResult<Nothing>
}
