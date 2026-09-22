package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.KeyUseAuthorizationFailure
import id.walt.crypto2.keys.KeyUseAuthorizationException
import id.walt.crypto2.keys.KeyUseAuthorizationSupport
import id.walt.crypto2.keys.KeyUseAuthorizationReuseEnforcement
import id.walt.crypto2.keys.KeyUseAuthorizationReuseTimeoutValidation
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.PrivateKeyExporter
import kotlinx.coroutines.CancellationException
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.signum.SignumInteractionContextUnavailableException
import id.walt.crypto2.signum.SignumKeyUnavailableException
import id.walt.crypto2.signum.SignumKeyInvalidatedException
import id.walt.crypto2.signum.SignumKeyNotFoundException
import id.walt.crypto2.signum.SignumKeyPolicyMismatchException
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.crypto2.keys.HardwarePreference
import id.walt.crypto2.signum.SignumAuthenticationPolicy
import id.walt.crypto2.signum.SignumStoredKeyMetadataException
import id.walt.crypto2.signum.SignumAuthorizationException
import id.walt.crypto2.signum.SignumUserCancelledException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class SignumWalletKeyMappingTest {
    @Test
    fun `maps known Signum failures at the built-in provider boundary`() {
        val failures = listOf(
            SignumAuthorizationException() to
                KeyUseAuthorizationFailure.AuthorizationNotCompleted,
            SignumInteractionContextUnavailableException() to
                KeyUseAuthorizationFailure.InteractionContextUnavailable,
            SignumUserCancelledException(IllegalStateException("cancelled")) to
                KeyUseAuthorizationFailure.AuthorizationNotCompleted,
            SignumKeyNotFoundException("key") to
                KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
            SignumKeyUnavailableException("key") to
                KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
            SignumKeyInvalidatedException("key") to
                KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
            SignumStoredKeyMetadataException("malformed") to
                KeyUseAuthorizationFailure.InvalidStoredKeyMetadata,
        )

        failures.forEach { (cause, expectedFailure) ->
            val mapped = assertIs<KeyUseAuthorizationException>(
                cause.toKeyUseAuthorizationException(protectedKeyId = "key")
            )
            assertEquals(expectedFailure, mapped.failure)
            assertEquals(cause, mapped.cause)
        }

        val creationMismatch = SignumKeyPolicyMismatchException("key", "unsupported")
        assertEquals(
            KeyUseAuthorizationFailure.UnsupportedCombination,
            assertIs<KeyUseAuthorizationException>(
                creationMismatch.toKeyUseAuthorizationException(
                    protectedKeyId = "key",
                    policyMismatchFailure = KeyUseAuthorizationFailure.UnsupportedCombination,
                )
            ).failure,
        )

        val existingKeyMismatch = SignumKeyPolicyMismatchException("key", "weakened policy")
        assertEquals(
            KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
            assertIs<KeyUseAuthorizationException>(
                existingKeyMismatch.toKeyUseAuthorizationException(protectedKeyId = "key")
            ).failure,
        )
    }

    @Test
    fun `maps existing protected-key policy mismatch when signing`() = runTest {
        val signFailure = SignumKeyPolicyMismatchException("key", "weakened policy")
        var signCalls = 0
        val delegate = object : ManagedKey {
            override val storedKey = StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = KeyId("key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN),
                provider = ProviderId("test"),
                providerSchemaVersion = 1,
                providerData = BinaryData("key".encodeToByteArray()),
            )
            override val capabilities = KeyCapabilities(
                signer = Signer { _, _ ->
                    signCalls++
                    throw signFailure
                },
            )
        }

        val protected = delegate.withWalletAuthorizationMapping()

        val failure = assertFailsWith<KeyUseAuthorizationException> {
            requireNotNull(protected.capabilities.signer).sign(
                byteArrayOf(1),
                SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256),
            )
        }

        assertEquals(1, signCalls)
        assertEquals(KeyUseAuthorizationFailure.ProtectedKeyUnavailable, failure.failure)
        assertEquals(signFailure, failure.cause)
    }

    @Test
    fun `leaves unexpected failures unchanged`() {
        assertNull(IllegalStateException("unexpected").toKeyUseAuthorizationException())
    }

    @Test
    fun `restored biometric policy requires the complete protected wallet key shape`() {
        val policy = SignumKeyPolicy(
            hardware = HardwarePreference.REQUIRED,
            authentication = SignumAuthenticationPolicy.UserPresence(
                biometric = true,
                allowNewBiometrics = false,
                deviceCredential = false,
                timeoutSeconds = 0,
            ),
        )
        val valid = storedManagedKey(KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))

        assertEquals(KeyUseAuthorizationPolicy.BiometricCurrentSet, policy.toWalletPolicy(valid))

        listOf(
            storedManagedKey(KeySpec.Ec(EcCurve.P384), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)),
            storedManagedKey(KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN)),
        ).forEach { malformed ->
            val failure = assertFailsWith<KeyUseAuthorizationException> {
                policy.toWalletPolicy(malformed)
            }
            assertEquals(KeyUseAuthorizationFailure.InvalidStoredKeyMetadata, failure.failure)
        }
    }

    @Test
    fun `maps the complete timed biometric reuse policy`() {
        val timed = KeyUseAuthorizationPolicy.BiometricTimedReuse(timeoutSeconds = 10)
        val expected = SignumKeyPolicy(
            hardware = HardwarePreference.REQUIRED,
            authentication = SignumAuthenticationPolicy.UserPresence(
                biometric = true,
                allowNewBiometrics = true,
                deviceCredential = false,
                timeoutSeconds = 10,
                prompt = "Please authorize cryptographic signature",
            ),
        )
        val valid = storedManagedKey(KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))

        assertEquals(expected, timed.toSignumPolicy())
        assertEquals(timed, expected.toWalletPolicy(valid))
        assertEquals(KeyUseAuthorizationPolicy.BiometricAny,
            expected.copy(authentication = (expected.authentication as SignumAuthenticationPolicy.UserPresence).copy(timeoutSeconds = 0)).toWalletPolicy(valid))
        assertEquals(KeyUseAuthorizationPolicy.BiometricOrDeviceCredential(10),
            expected.copy(authentication = (expected.authentication as SignumAuthenticationPolicy.UserPresence).copy(deviceCredential = true)).toWalletPolicy(valid))

        listOf(
            expected.copy(authentication = (expected.authentication as SignumAuthenticationPolicy.UserPresence).copy(timeoutSeconds = 31)),
            expected.copy(authentication = (expected.authentication as SignumAuthenticationPolicy.UserPresence).copy(allowNewBiometrics = false)),
        ).forEach { malformed ->
            val failure = assertFailsWith<KeyUseAuthorizationException> {
                malformed.toWalletPolicy(valid)
            }
            assertEquals(KeyUseAuthorizationFailure.InvalidStoredKeyMetadata, failure.failure)
        }
    }

    @Test
    fun `rejects timed biometric reuse outside the supported interval`() {
        listOf(0, 31).forEach { timeoutSeconds ->
            assertFailsWith<IllegalArgumentException> {
                KeyUseAuthorizationPolicy.BiometricTimedReuse(timeoutSeconds)
            }
        }
    }

    @Test
    fun `support metadata must match the effective policy shape without coupling its axes`() {
        val timed = KeyUseAuthorizationPolicy.BiometricTimedReuse(timeoutSeconds = 10)

        assertFailsWith<IllegalArgumentException> {
            KeyUseAuthorizationSupport.Supported(effectivePolicy = timed)
        }
        assertFailsWith<IllegalArgumentException> {
            KeyUseAuthorizationSupport.Supported(
                effectivePolicy = KeyUseAuthorizationPolicy.None,
                reuseEnforcement = KeyUseAuthorizationReuseEnforcement.PlatformKeyStore,
                timeoutValidation = KeyUseAuthorizationReuseTimeoutValidation.IndependentReadback,
            )
        }

        val support = KeyUseAuthorizationSupport.Supported(
            effectivePolicy = timed,
            reuseEnforcement = KeyUseAuthorizationReuseEnforcement.ProviderProcess,
            timeoutValidation = KeyUseAuthorizationReuseTimeoutValidation.IndependentReadback,
        )
        assertEquals(timed, support.effectivePolicy)
    }

    @Test
    fun `private export maps authorization failures and preserves coroutine cancellation`() = runTest {
        for (cause in listOf(SignumAuthorizationException(), CancellationException("caller cancelled"))) {
            val delegate = object : ManagedKey {
                override val storedKey = storedManagedKey(KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
                override val capabilities = KeyCapabilities(privateKeyExporter = object : PrivateKeyExporter {
                    override suspend fun exportPrivateKey(): EncodedKey = throw cause
                    override suspend fun exportPrivateKey(format: KeyEncodingFormat): EncodedKey = throw cause
                })
            }
            val exporter = requireNotNull(delegate.withWalletAuthorizationMapping().capabilities.privateKeyExporter)
            for (explicitFormat in listOf(false, true)) {
                val actual = assertFailsWith<Exception> {
                    if (explicitFormat) exporter.exportPrivateKey(KeyEncodingFormat.JWK) else exporter.exportPrivateKey()
                }
                if (cause is CancellationException) assertEquals(cause, actual)
                else {
                    assertEquals(KeyUseAuthorizationFailure.AuthorizationNotCompleted, assertIs<KeyUseAuthorizationException>(actual).failure)
                    assertEquals(cause, actual.cause)
                }
            }
        }
    }

    @Test
    fun `authorization failure uses current availability without misclassifying cancellation`() = runTest {
        for (nativeFailure in listOf(SignumUserCancelledException(IllegalStateException("authorization failed")), SignumAuthorizationException())) {
            var availability: KeyUseAuthorizationFailure? = null
            val delegate = object : ManagedKey {
                override val storedKey = storedManagedKey(KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN))
                override val capabilities = KeyCapabilities(
                    signer = Signer { _, _ -> throw nativeFailure },
                    privateKeyExporter = object : PrivateKeyExporter {
                        override suspend fun exportPrivateKey(): EncodedKey = throw nativeFailure
                        override suspend fun exportPrivateKey(format: KeyEncodingFormat): EncodedKey = throw nativeFailure
                    },
                )
            }
            val mapped = delegate.withWalletAuthorizationMapping { availability }.capabilities
            val operations: List<suspend () -> Unit> = listOf(
                { requireNotNull(mapped.signer).sign(byteArrayOf(1), SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)) },
                { requireNotNull(mapped.privateKeyExporter).exportPrivateKey() },
                { requireNotNull(mapped.privateKeyExporter).exportPrivateKey(KeyEncodingFormat.JWK) },
            )
            for (current in listOf(KeyUseAuthorizationFailure.BiometricNotEnrolled,
                KeyUseAuthorizationFailure.DeviceCredentialNotSet, null)) {
                availability = current
                for (operation in operations) {
                    val failure = assertFailsWith<KeyUseAuthorizationException> { operation() }
                    assertEquals(current ?: KeyUseAuthorizationFailure.AuthorizationNotCompleted, failure.failure)
                    assertEquals(nativeFailure, failure.cause)
                }
            }
        }
    }

    @Test
    fun `successful native reuse invalidation and coroutine cancellation bypass availability checks`() = runTest {
        val cancelled = CancellationException("caller cancelled")
        var nativeFailure: Throwable? = null
        var availabilityCalls = 0
        val delegate = object : ManagedKey {
            override val storedKey = storedManagedKey(KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN))
            override val capabilities = KeyCapabilities(signer = Signer { _, _ ->
                nativeFailure?.let { throw it }
                byteArrayOf(42)
            })
        }
        val signer = requireNotNull(delegate.withWalletAuthorizationMapping {
            availabilityCalls++
            KeyUseAuthorizationFailure.BiometricNotEnrolled
        }.capabilities.signer)
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
        assertEquals(42, signer.sign(byteArrayOf(1), algorithm).single().toInt())
        nativeFailure = SignumKeyInvalidatedException("key")
        assertEquals(KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
            assertFailsWith<KeyUseAuthorizationException> { signer.sign(byteArrayOf(1), algorithm) }.failure)
        nativeFailure = cancelled
        assertEquals(cancelled, assertFailsWith<CancellationException> { signer.sign(byteArrayOf(1), algorithm) })
        assertEquals(0, availabilityCalls)
    }

    private fun storedManagedKey(spec: KeySpec, usages: Set<KeyUsage>) = StoredKey.Managed(
        version = StoredKey.CURRENT_VERSION,
        id = KeyId("protected"),
        spec = spec,
        usages = usages,
        provider = ProviderId("test"),
        providerSchemaVersion = 1,
        providerData = BinaryData("protected".encodeToByteArray()),
    )
}
