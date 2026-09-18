package id.walt.crypto2.signum

import kotlin.test.*

class IosKeyBiometricsTest {
    @Test
    fun unavailableBiometricsBlockBiometricOnlyPoliciesWithoutClaimingInvalidation() {
        val unavailable = IllegalStateException("Biometrics not enrolled")
        for (authentication in listOf(
            SignumAuthenticationPolicy.UserPresence(deviceCredential = false),
            SignumAuthenticationPolicy.UserPresence(deviceCredential = false, allowNewBiometrics = true),
            SignumAuthenticationPolicy.UserPresence(deviceCredential = false, allowNewBiometrics = true, timeoutSeconds = 10),
        )) {
            val policy = SignumKeyPolicy(authentication = authentication)
            val failure = assertFailsWith<SignumKeyUnavailableException> {
                requireIosKeyBiometrics("retained-key", policy) { unavailable }
            }
            assertEquals("retained-key", failure.alias)
            assertSame(unavailable, failure.cause)
            // Re-enrollment allows the original key to be looked up again; no sticky invalidation.
            requireIosKeyBiometrics("retained-key", policy) { null }
        }
    }

    @Test
    fun missingBiometricsDoNotBlockUnprotectedOrCredentialCapableKeys() {
        for (authentication in listOf(
            SignumAuthenticationPolicy.None,
            SignumAuthenticationPolicy.UserPresence(biometric = false),
            SignumAuthenticationPolicy.UserPresence(deviceCredential = true),
        )) {
            requireIosKeyBiometrics("retained-key", SignumKeyPolicy(authentication = authentication)) {
                fail("This policy must not require biometric availability")
            }
        }
    }
}
