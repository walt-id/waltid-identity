@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package id.walt.crypto2.signum

import platform.CoreFoundation.*
import platform.CryptoTokenKit.*
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSError
import platform.Foundation.NSOSStatusErrorDomain
import platform.LocalAuthentication.*
import platform.Security.*

/** Retains native diagnostics after the CFError has been released. */
internal class IosKeychainException(
    val domain: String?,
    val code: Long,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException("$message ($domain, $code)", cause)

internal fun iosKeychainFailure(alias: String, error: IosKeychainException): Throwable = when (error.domain) {
    NSOSStatusErrorDomain -> when (error.code.toInt()) {
        errSecItemNotFound -> SignumKeyNotFoundException(alias, error)
        errSecDecode -> SignumKeyUnavailableException(alias, error)
        errSecUserCanceled -> SignumUserCancelledException(error)
        errSecAuthFailed -> SignumAuthorizationException(cause = error)
        errSecInteractionNotAllowed -> SignumInteractionContextUnavailableException(
            "Keychain interaction is not allowed in the current device or application state", error,
        )
        else -> error
    }
    TKErrorDomain -> when (error.code) {
        // CorruptedData also occurs while biometrics are absent, even for a key that works
        // after re-enrollment. It is not proof of permanent invalidation or user cancellation.
        TKErrorCodeCorruptedData, TKErrorCodeObjectNotFound, TKErrorCodeTokenNotFound ->
            SignumKeyUnavailableException(alias, error)
        TKErrorCodeCanceledByUser -> SignumUserCancelledException(error)
        TKErrorCodeAuthenticationFailed -> SignumAuthorizationException(cause = error)
        TKErrorCodeAuthenticationNeeded -> SignumInteractionContextUnavailableException(
            "Token authorization requires user interaction", error,
        )
        else -> error
    }
    LAErrorDomain -> when (error.code) {
        LAErrorUserCancel, LAErrorAppCancel, LAErrorSystemCancel -> SignumUserCancelledException(error)
        LAErrorAuthenticationFailed, LAErrorBiometryLockout, LAErrorBiometryNotAvailable,
        LAErrorBiometryNotEnrolled, LAErrorPasscodeNotSet, LAErrorUserFallback ->
            SignumAuthorizationException(cause = error)
        LAErrorNotInteractive -> SignumInteractionContextUnavailableException(
            "Keychain authorization requires user interaction", error,
        )
        else -> error
    }
    else -> error
}

internal fun checkKeychainStatus(status: Int, alias: String, missingAllowed: Boolean = false) {
    if (status == errSecSuccess || (missingAllowed && status == errSecItemNotFound)) return
    throw iosKeychainFailure(alias, IosKeychainException(NSOSStatusErrorDomain, status.toLong(), "Keychain operation failed"))
}

/** Consumes a retained CFError returned by a Security operation. */
internal fun takeKeychainFailure(alias: String, error: CFErrorRef?): Throwable {
    if (error == null) return IllegalStateException("Keychain operation failed without an error")
    val native = CFBridgingRelease(error) as NSError
    return iosKeychainFailure(alias, native.toKeychainException())
}

internal fun NSError.toKeychainException(cause: Throwable? = null): IosKeychainException =
    IosKeychainException(domain, code, localizedDescription, cause)
