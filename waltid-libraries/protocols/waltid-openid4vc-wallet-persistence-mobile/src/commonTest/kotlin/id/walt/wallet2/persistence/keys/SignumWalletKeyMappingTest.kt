package id.walt.wallet2.persistence.keys

import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
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
import id.walt.crypto2.signum.SignumKeyInvalidatedException
import id.walt.crypto2.signum.SignumKeyNotFoundException
import id.walt.crypto2.signum.SignumKeyPolicyMismatchException
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.crypto2.signum.SignumHardwarePolicy
import id.walt.crypto2.signum.SignumAuthenticationPolicy
import id.walt.crypto2.signum.SignumStoredKeyMetadataException
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
            SignumInteractionContextUnavailableException() to
                KeyUseAuthorizationFailure.InteractionContextUnavailable,
            SignumUserCancelledException(IllegalStateException("cancelled")) to
                KeyUseAuthorizationFailure.AuthorizationNotCompleted,
            SignumKeyNotFoundException("key") to
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

        val protected = delegate.withWalletAuthorizationMapping(
            KeyUseAuthorizationPolicy.BiometricCurrentSet,
        )

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
            hardware = SignumHardwarePolicy.REQUIRED,
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
            hardware = SignumHardwarePolicy.REQUIRED,
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

        listOf(
            expected.copy(authentication = (expected.authentication as SignumAuthenticationPolicy.UserPresence).copy(timeoutSeconds = 0)),
            expected.copy(authentication = (expected.authentication as SignumAuthenticationPolicy.UserPresence).copy(timeoutSeconds = 31)),
            expected.copy(authentication = (expected.authentication as SignumAuthenticationPolicy.UserPresence).copy(allowNewBiometrics = false)),
            expected.copy(authentication = (expected.authentication as SignumAuthenticationPolicy.UserPresence).copy(deviceCredential = true)),
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
