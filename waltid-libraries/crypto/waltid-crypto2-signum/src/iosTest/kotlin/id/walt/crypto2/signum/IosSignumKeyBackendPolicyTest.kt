package id.walt.crypto2.signum

import at.asitplus.signum.supreme.CFCryptoOperationFailed
import platform.Security.errSecItemNotFound
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class IosSignumKeyBackendPolicyTest {
    private val backend = IosSignumKeyBackend()

    @Test
    fun `required hardware rejects a software keychain key`() {
        assertFailsWith<SignumKeyPolicyMismatchException> {
            backend.validateIosNativePolicy(
                alias = "hardware-required",
                policy = SignumKeyPolicy(hardware = SignumHardwarePolicy.REQUIRED),
                needsAuthenticationForEveryUse = false,
                isSecureEnclave = false,
            )
        }
    }

    @Test
    fun `required hardware accepts a Secure Enclave key`() {
        backend.validateIosNativePolicy(
            alias = "hardware-required",
            policy = SignumKeyPolicy(hardware = SignumHardwarePolicy.REQUIRED),
            needsAuthenticationForEveryUse = false,
            isSecureEnclave = true,
        )
    }

    @Test
    fun `biometric current set requires every-use authentication and Secure Enclave`() {
        val policy = SignumKeyPolicy(
            authentication = SignumAuthenticationPolicy.UserPresence(
                biometric = true,
                allowNewBiometrics = false,
                deviceCredential = false,
                timeoutSeconds = 0,
            ),
        )

        assertFailsWith<SignumKeyPolicyMismatchException> {
            backend.validateIosNativePolicy(
                alias = "biometric",
                policy = policy,
                needsAuthenticationForEveryUse = false,
                isSecureEnclave = true,
            )
        }
        assertFailsWith<SignumKeyPolicyMismatchException> {
            backend.validateIosNativePolicy(
                alias = "biometric",
                policy = policy,
                needsAuthenticationForEveryUse = true,
                isSecureEnclave = false,
            )
        }
        backend.validateIosNativePolicy(
            alias = "biometric",
            policy = policy,
            needsAuthenticationForEveryUse = true,
            isSecureEnclave = true,
        )
    }

    @Test
    fun `keychain item not found maps to a typed Signum missing-key failure`() {
        val mapped = CFCryptoOperationFailed(
            thing = "load Signum key",
            osStatus = errSecItemNotFound,
        ).mapSignumFailure("missing")

        assertIs<SignumKeyNotFoundException>(mapped)
    }
}
