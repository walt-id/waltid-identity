@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.swiftinterop

import id.walt.crypto2.keys.KeyUseAuthorizationException
import id.walt.crypto2.keys.KeyUseAuthorizationFailure
import id.walt.wallet2.consent.PaymentConsentException
import id.walt.wallet2.consent.PaymentConsentFailure
import id.walt.wallet2.mobile.presentation.PresentationErrorCode
import id.walt.wallet2.mobile.presentation.toPresentationErrorCode
import id.walt.wallet2.mobile.WalletFailure
import id.waltid.openid4vp.wallet.UnsafePresentationErrorResponseException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** Kotlin source alias for the shared mobile failure classification. */
public typealias WalletBridgeErrorCategory = WalletFailure

/**
 * An OpenID4VP validation error retained locally because remote reporting was unsafe.
 * @property errorCode Original OpenID4VP protocol error code.
 * @property message Original request-validation message.
 * @property responseSafetyFailure Reason the error response was not sent.
 */
@Serializable
public data class WalletBridgePresentationValidationFailure(
    public val errorCode: PresentationErrorCode,
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
            val category = WalletFailure.classify(throwable)
            return WalletBridgeError(
                category = category,
                message = throwable.message ?: throwable::class.simpleName ?: "Unknown wallet error",
                causeClass = throwable::class.simpleName,
                authorizationFailure = authorizationFailure,
                paymentConsentFailure = paymentFailure,
                presentationValidationFailure = presentationFailure?.let {
                    WalletBridgePresentationValidationFailure(
                        errorCode = it.error.code.toPresentationErrorCode(),
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
