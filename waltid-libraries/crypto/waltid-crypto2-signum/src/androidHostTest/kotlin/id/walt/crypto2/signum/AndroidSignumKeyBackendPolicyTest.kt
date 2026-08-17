package id.walt.crypto2.signum

import android.security.keystore.KeyProperties
import at.asitplus.signum.indispensable.CryptoPublicKey
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toSpkiDer
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidSignumKeyBackendPolicyTest {
    @Test
    fun `required hardware rejects software backing`() {
        assertFailsWith<SignumKeyPolicyMismatchException> {
            validateAndroidNativePolicy(
                alias = "hardware-required",
                policy = SignumKeyPolicy(
                    hardware = SignumHardwarePolicy.REQUIRED,
                    authentication = SignumAuthenticationPolicy.UserPresence(
                        biometric = true,
                        allowNewBiometrics = false,
                        deviceCredential = false,
                        timeoutSeconds = 0,
                    ),
                ),
                isInsideSecureHardware = false,
                securityLevel = KeyProperties.SECURITY_LEVEL_SOFTWARE,
                isUserAuthenticationRequired = true,
                userAuthenticationValidityDurationSeconds = -1,
                isInvalidatedByBiometricEnrollment = true,
                userAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG,
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
    fun `required hardware accepts an unknown secure hardware level`() {
        validateAndroidNativePolicy(
            alias = "hardware-required",
            policy = SignumKeyPolicy(hardware = SignumHardwarePolicy.REQUIRED),
            isInsideSecureHardware = true,
            securityLevel = KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE,
            isUserAuthenticationRequired = false,
            userAuthenticationValidityDurationSeconds = -1,
            isInvalidatedByBiometricEnrollment = false,
            userAuthenticationType = null,
        )
    }

    @Test
    fun `required hardware rejects an unknown non-secure level`() {
        assertFailsWith<SignumKeyPolicyMismatchException> {
            validateAndroidNativePolicy(
                alias = "hardware-required",
                policy = SignumKeyPolicy(hardware = SignumHardwarePolicy.REQUIRED),
                isInsideSecureHardware = true,
                securityLevel = KeyProperties.SECURITY_LEVEL_UNKNOWN,
                isUserAuthenticationRequired = false,
                userAuthenticationValidityDurationSeconds = -1,
                isInvalidatedByBiometricEnrollment = false,
                userAuthenticationType = null,
            )
        }
    }

    @Test
    fun `biometric current set rejects inexact native policy`() {
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
                userAuthenticationValidityDurationSeconds = -1,
                isInvalidatedByBiometricEnrollment = true,
                userAuthenticationType = KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        }
        assertFailsWith<SignumKeyPolicyMismatchException> {
            validateAndroidNativePolicy(
                alias = "biometric",
                policy = policy,
                isInsideSecureHardware = true,
                securityLevel = KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
                isUserAuthenticationRequired = false,
                userAuthenticationValidityDurationSeconds = -1,
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
                userAuthenticationValidityDurationSeconds = -1,
                isInvalidatedByBiometricEnrollment = false,
                userAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG,
            )
        }
    }

    @Test
    fun `biometric current set accepts the exact native policy`() {
        validateAndroidNativePolicy(
            alias = "biometric",
            policy = SignumKeyPolicy(
                hardware = SignumHardwarePolicy.REQUIRED,
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
            userAuthenticationValidityDurationSeconds = -1,
            isInvalidatedByBiometricEnrollment = true,
            userAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG,
        )
    }

    @Test
    fun `verification uses the cached public key without requesting interaction`() = runTest {
        val provider = CryptographySoftwareKeyProvider()
        val spec = KeySpec.Ec(EcCurve.P256)
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
        val softwareKey = provider.generate(
            GenerateSoftwareKeyRequest(
                id = KeyId("verification-source"),
                spec = spec,
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )
        val signer = requireNotNull(softwareKey.capabilities.signer)
        val publicKey = requireNotNull(softwareKey.capabilities.publicKeyExporter)
            .exportPublicKey()
            .toSpkiDer(spec)
        val nativePublicKey = CryptoPublicKey.decodeFromDer(publicKey.data.toByteArray())
        val data = "verify without interaction".encodeToByteArray()
        val signature = signer.sign(data, algorithm)
        val handle = SignumPlatformKeyHandle(
            alias = "verification-source",
            spec = spec,
            protectionLevel = SignumProtectionLevel.UNKNOWN,
            attestation = null,
            authentication = SignumAuthenticationPolicy.UserPresence(),
            signerFor = { error("verification must not request a signer") },
            nativePublicKey = nativePublicKey,
            keyAgreementEnabled = false,
        )

        assertTrue(handle.verify(data, signature, algorithm))
    }
}
