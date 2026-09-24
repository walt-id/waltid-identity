package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.KeyUseAuthorizationFailure
import id.walt.crypto2.keys.KeyUseAuthorizationUnsupportedReason
import id.walt.crypto2.keys.toAuthorizationFailure
import platform.LocalAuthentication.*
import kotlin.test.*

class IosKeyUseAuthorizationTest {
    @Test fun missingPasscodeIsDistinctFromMissingBiometrics() {
        assertEquals(KeyUseAuthorizationUnsupportedReason.DeviceCredentialNotSet,
            iosAuthorizationUnavailableReason(LAErrorDomain, LAErrorPasscodeNotSet))
        assertEquals(KeyUseAuthorizationFailure.DeviceCredentialNotSet,
            iosAuthorizationUnavailableReason(LAErrorDomain, LAErrorPasscodeNotSet).toAuthorizationFailure())
        assertEquals(KeyUseAuthorizationUnsupportedReason.BiometricNotEnrolled,
            iosAuthorizationUnavailableReason(LAErrorDomain, LAErrorBiometryNotEnrolled))
        assertEquals(KeyUseAuthorizationUnsupportedReason.BiometricUnavailable,
            iosAuthorizationUnavailableReason("other.domain", LAErrorPasscodeNotSet))
        assertNull(iosAuthorizationAvailabilityFailure(KeyUseAuthorizationPolicy.None))
    }
}
