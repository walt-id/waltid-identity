package id.walt.wallet2.persistence.keys

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosPlatformKeyProviderTest {

    @Test
    fun supportsPlatformKeyTypes() {
        assertEquals(
            setOf(KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1, KeyType.RSA),
            IosPlatformKeyProvider().supportedPlatformKeyTypes,
        )
    }

    @Test
    fun unprotectedCapabilityPreservesExistingAlgorithmSupport() = runTest {
        val provider = IosPlatformKeyProvider()

        KeyType.entries.forEach { keyType ->
            val capability = provider.capability(keyType, KeyUseAuthorizationPolicy.None)
            val expected = keyType in provider.supportedPlatformKeyTypes ||
                keyType in setOf(KeyType.Ed25519, KeyType.secp256k1)
            assertEquals(expected, capability.supported, "Unexpected None capability for $keyType")
            assertEquals(KeyUseAuthorizationPolicy.None, capability.keyUseAuthorizationPolicy)
        }
    }

    @Test
    fun biometricCurrentSetIsPortableOnlyForP256AndRequiresPhysicalSecureEnclave() = runTest {
        val provider = IosPlatformKeyProvider()
        val portable = provider.capability(KeyType.secp256r1, KeyUseAuthorizationPolicy.BiometricCurrentSet)
        val unsupported = provider.capability(KeyType.Ed25519, KeyUseAuthorizationPolicy.BiometricCurrentSet)

        assertEquals(PlatformKeyPlatform.iOS, portable.platform)
        assertTrue(portable.platformBackingAvailable)
        assertTrue(portable.secureHardwareRequired)
        assertFalse(portable.secureHardwareAvailable == true, "The simulator must not report Secure Enclave availability")
        assertEquals(KeyUseAuthorizationFailure.BiometricUnavailable, portable.failure)

        assertFalse(unsupported.supported)
        assertEquals(KeyUseAuthorizationFailure.UnsupportedCombination, unsupported.failure)
    }

    @Test
    fun unsupportedProtectedAlgorithmFailsBeforeSoftwareFallback() = runTest {
        val provider = IosPlatformKeyProvider()

        val failure = assertFailsWith<KeyUseAuthorizationException> {
            provider.generateKey(
                PlatformKeyGenerationRequest(
                    keyType = KeyType.Ed25519,
                    keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
                )
            )
        }

        assertEquals(KeyUseAuthorizationFailure.UnsupportedCombination, failure.failure)
    }
}
