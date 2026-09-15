package id.walt.crypto2.signum

import at.asitplus.signum.supreme.CFCryptoOperationFailed
import kotlinx.coroutines.CancellationException
import platform.Foundation.NSOSStatusErrorDomain
import platform.CryptoTokenKit.*
import platform.LocalAuthentication.LAErrorDomain
import platform.LocalAuthentication.LAErrorAuthenticationFailed
import platform.LocalAuthentication.LAErrorUserCancel
import platform.Security.*
import kotlin.test.*

class IosKeychainErrorsTest {
    @Test
    fun authenticationFailureIsNotReportedAsUserCancellation() {
        val native = IosKeychainException(NSOSStatusErrorDomain, errSecAuthFailed.toLong(), "Authentication failed")
        val mapped = assertIs<SignumAuthorizationException>(iosKeychainFailure("key", native))
        assertSame(native, mapped.cause)
        assertIs<SignumAuthorizationException>(CFCryptoOperationFailed("sign", errSecAuthFailed).mapSignumFailure("key"))
        assertIs<SignumAuthorizationException>(iosKeychainFailure("key",
            IosKeychainException(LAErrorDomain, LAErrorAuthenticationFailed, "Authentication failed")))
    }

    @Test
    fun cancellationMissingKeyAndInteractionRemainDistinct() {
        val cancelled = IosKeychainException(LAErrorDomain, LAErrorUserCancel, "Cancelled")
        assertSame(cancelled, assertIs<SignumUserCancelledException>(iosKeychainFailure("key", cancelled)).reason)
        assertIs<SignumKeyNotFoundException>(iosKeychainFailure("key",
            IosKeychainException(NSOSStatusErrorDomain, errSecItemNotFound.toLong(), "Missing")))
        assertIs<SignumInteractionContextUnavailableException>(iosKeychainFailure("key",
            IosKeychainException(NSOSStatusErrorDomain, errSecInteractionNotAllowed.toLong(), "Locked")))
        val cancellation = CancellationException("Job cancelled")
        assertSame(cancellation, cancellation.mapSignumFailure("key"))
    }

    @Test
    fun tokenFailuresDoNotAssumePermanentInvalidation() {
        val unavailable = IosKeychainException(TKErrorDomain, TKErrorCodeCorruptedData, "Token data unavailable")
        assertSame(unavailable, assertIs<SignumKeyUnavailableException>(iosKeychainFailure("key", unavailable)).cause)
        assertIs<SignumKeyUnavailableException>(iosKeychainFailure("key",
            IosKeychainException(TKErrorDomain, TKErrorCodeObjectNotFound, "Object unavailable")))
        assertIs<SignumKeyUnavailableException>(iosKeychainFailure("key",
            IosKeychainException(TKErrorDomain, TKErrorCodeTokenNotFound, "Token unavailable")))
        assertIs<SignumUserCancelledException>(iosKeychainFailure("key",
            IosKeychainException(TKErrorDomain, TKErrorCodeCanceledByUser, "Cancelled")))
        assertIs<SignumAuthorizationException>(iosKeychainFailure("key",
            IosKeychainException(TKErrorDomain, TKErrorCodeAuthenticationFailed, "Denied")))
        assertIs<SignumInteractionContextUnavailableException>(iosKeychainFailure("key",
            IosKeychainException(TKErrorDomain, TKErrorCodeAuthenticationNeeded, "Interaction required")))
        assertIs<SignumKeyUnavailableException>(CFCryptoOperationFailed("decode key", errSecDecode).mapSignumFailure("key"))
        val unknown = IosKeychainException(TKErrorDomain, TKErrorCodeBadParameter, "Invalid parameter")
        assertSame(unknown, iosKeychainFailure("key", unknown))
    }

    @Test
    fun anUnrelatedDomainCannotMasqueradeAsAnAuthenticationFailure() {
        val other = IosKeychainException("unrelated.domain", errSecAuthFailed.toLong(), "Unrelated error")
        assertSame(other, iosKeychainFailure("key", other))
    }
}
