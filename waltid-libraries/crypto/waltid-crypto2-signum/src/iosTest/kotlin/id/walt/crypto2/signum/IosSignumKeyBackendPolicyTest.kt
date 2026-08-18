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
            hardware = SignumHardwarePolicy.REQUIRED,
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
    fun `preferred hardware does not imply Secure Enclave for exact UserPresence`() {
        backend.validateIosNativePolicy(
            alias = "preferred-biometric",
            policy = SignumKeyPolicy(
                hardware = SignumHardwarePolicy.PREFERRED,
                authentication = SignumAuthenticationPolicy.UserPresence(
                    biometric = true,
                    allowNewBiometrics = false,
                    deviceCredential = false,
                    timeoutSeconds = 0,
                ),
            ),
            needsAuthenticationForEveryUse = true,
            isSecureEnclave = false,
        )
    }

    @Test
    fun `timed biometric reuse requires reusable authenticated Secure Enclave access`() {
        val policy = SignumKeyPolicy(
            hardware = SignumHardwarePolicy.REQUIRED,
            authentication = SignumAuthenticationPolicy.UserPresence(
                biometric = true,
                allowNewBiometrics = true,
                deviceCredential = false,
                timeoutSeconds = 10,
            ),
        )

        backend.validateIosNativePolicy(
            alias = "timed-biometric",
            policy = policy,
            needsAuthentication = true,
            needsAuthenticationForEveryUse = false,
            isSecureEnclave = true,
        )

        listOf(
            false to false,
            true to true,
        ).forEach { (needsAuthentication, needsAuthenticationForEveryUse) ->
            assertFailsWith<SignumKeyPolicyMismatchException> {
                backend.validateIosNativePolicy(
                    alias = "timed-biometric",
                    policy = policy,
                    needsAuthentication = needsAuthentication,
                    needsAuthenticationForEveryUse = needsAuthenticationForEveryUse,
                    isSecureEnclave = true,
                )
            }
        }
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
