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
import kotlin.test.assertIs
import kotlin.test.assertSame
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
            userAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG,
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
            userAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG,
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
                userAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG,
            )
        }
    }

    @Test
    fun `biometric current set rejects a non per-operation native policy`() {
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
                userAuthenticationValidityDurationSeconds = -2,
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
    fun `biometric current set accepts both per-operation native representations`() {
        listOf(-1, 0).forEach { duration ->
            validateAndroidNativePolicy(
                alias = "biometric-$duration",
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
                userAuthenticationValidityDurationSeconds = duration,
                isInvalidatedByBiometricEnrollment = true,
                userAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG,
            )
        }
    }

    @Test
    fun `timed biometric reuse requires the exact Keystore policy`() {
        val policy = SignumKeyPolicy(
            hardware = SignumHardwarePolicy.REQUIRED,
            authentication = SignumAuthenticationPolicy.UserPresence(
                biometric = true,
                allowNewBiometrics = true,
                deviceCredential = false,
                timeoutSeconds = 10,
            ),
        )

        validateAndroidNativePolicy(
            alias = "timed-biometric",
            policy = policy,
            isInsideSecureHardware = true,
            securityLevel = KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
            isUserAuthenticationRequired = true,
            userAuthenticationValidityDurationSeconds = 10,
            isInvalidatedByBiometricEnrollment = false,
            userAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG,
        )

        listOf(
            Triple(0, false, KeyProperties.AUTH_BIOMETRIC_STRONG),
            Triple(11, false, KeyProperties.AUTH_BIOMETRIC_STRONG),
            Triple(10, true, KeyProperties.AUTH_BIOMETRIC_STRONG),
            Triple(10, false, KeyProperties.AUTH_DEVICE_CREDENTIAL),
        ).forEach { (duration, invalidatedByEnrollment, authenticationType) ->
            assertFailsWith<SignumKeyPolicyMismatchException> {
                validateAndroidNativePolicy(
                    alias = "timed-biometric",
                    policy = policy,
                    isInsideSecureHardware = true,
                    securityLevel = KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
                    isUserAuthenticationRequired = true,
                    userAuthenticationValidityDurationSeconds = duration,
                    isInvalidatedByBiometricEnrollment = invalidatedByEnrollment,
                    userAuthenticationType = authenticationType,
                )
            }
        }
    }

    @Test
    fun `expired timed reuse without interaction context maps to stable boundary failure`() {
        val cause = UnsupportedOperationException("A prompt host is unavailable")

        val mapped = cause.mapTimedReuseInteractionContextFailure(
            alias = "timed-biometric",
            hasInteractionContext = false,
        )

        assertIs<SignumInteractionContextUnavailableException>(mapped)
        assertSame(cause, mapped.cause)
    }

    @Test
    fun `timed reuse preserves provider failure when an interaction context can prompt`() {
        val cause = UnsupportedOperationException("Timed authorization expired")

        assertSame(
            cause,
            cause.mapTimedReuseInteractionContextFailure(
                alias = "timed-biometric",
                hasInteractionContext = true,
            ),
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
