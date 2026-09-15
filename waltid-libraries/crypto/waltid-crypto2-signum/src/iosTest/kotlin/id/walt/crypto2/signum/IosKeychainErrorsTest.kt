package id.walt.crypto2.signum

import at.asitplus.signum.supreme.CFCryptoOperationFailed
import kotlinx.coroutines.CancellationException
import platform.Foundation.NSOSStatusErrorDomain
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
    fun anUnrelatedDomainCannotMasqueradeAsAnAuthenticationFailure() {
        val other = IosKeychainException("unrelated.domain", errSecAuthFailed.toLong(), "Unrelated error")
        assertSame(other, iosKeychainFailure("key", other))
    }
}
