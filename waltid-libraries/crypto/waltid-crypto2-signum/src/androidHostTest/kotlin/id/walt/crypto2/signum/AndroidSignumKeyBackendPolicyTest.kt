package id.walt.crypto2.signum

import android.security.keystore.KeyProperties
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AndroidSignumKeyBackendPolicyTest {
    @Test
    fun `required hardware rejects software backing`() {
        assertFailsWith<SignumKeyPolicyMismatchException> {
            validateAndroidNativePolicy(
                alias = "hardware-required",
                policy = SignumKeyPolicy(hardware = SignumHardwarePolicy.REQUIRED),
                isInsideSecureHardware = false,
                securityLevel = KeyProperties.SECURITY_LEVEL_SOFTWARE,
                isUserAuthenticationRequired = false,
                userAuthenticationValidityDurationSeconds = -1,
                isInvalidatedByBiometricEnrollment = false,
                userAuthenticationType = null,
            )
        }
    }

    @Test
    fun `required hardware accepts a trusted-environment key`() {
        validateAndroidNativePolicy(
            alias = "hardware-required",
            policy = SignumKeyPolicy(hardware = SignumHardwarePolicy.REQUIRED),
            isInsideSecureHardware = true,
            securityLevel = KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
            isUserAuthenticationRequired = false,
            userAuthenticationValidityDurationSeconds = -1,
            isInvalidatedByBiometricEnrollment = false,
            userAuthenticationType = null,
        )
    }

    @Test
    fun `biometric current set rejects missing auth per use`() {
        val policy = SignumKeyPolicy(
            authentication = SignumAuthenticationPolicy.UserPresence(
                biometric = true,
                allowNewBiometrics = false,
                deviceCredential = false,
                timeoutSeconds = 0,
            ),
        )

        assertFailsWith<SignumKeyPolicyMismatchException> {
            validateAndroidNativePolicy(
                alias = "biometric",
                policy = policy,
                isInsideSecureHardware = true,
                securityLevel = KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
                isUserAuthenticationRequired = true,
                userAuthenticationValidityDurationSeconds = 30,
                isInvalidatedByBiometricEnrollment = true,
                userAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG,
            )
        }
        assertFailsWith<SignumKeyPolicyMismatchException> {
            validateAndroidNativePolicy(
                alias = "biometric",
                policy = policy,
                isInsideSecureHardware = true,
                securityLevel = KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
                isUserAuthenticationRequired = true,
                userAuthenticationValidityDurationSeconds = 0,
                isInvalidatedByBiometricEnrollment = false,
                userAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG,
            )
        }
        assertFailsWith<SignumKeyPolicyMismatchException> {
            validateAndroidNativePolicy(
                alias = "biometric",
                policy = policy,
                isInsideSecureHardware = true,
                securityLevel = KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
                isUserAuthenticationRequired = true,
                userAuthenticationValidityDurationSeconds = 0,
                isInvalidatedByBiometricEnrollment = true,
                userAuthenticationType = KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        }
    }

    @Test
    fun `biometric current set accepts the exact native policy`() {
        validateAndroidNativePolicy(
            alias = "biometric",
            policy = SignumKeyPolicy(
                authentication = SignumAuthenticationPolicy.UserPresence(
                    biometric = true,
                    allowNewBiometrics = false,
                    deviceCredential = false,
                    timeoutSeconds = 0,
                ),
            ),
            isInsideSecureHardware = true,
            securityLevel = KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
            isUserAuthenticationRequired = true,
            userAuthenticationValidityDurationSeconds = 0,
            isInvalidatedByBiometricEnrollment = true,
            userAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG,
        )
    }
}
