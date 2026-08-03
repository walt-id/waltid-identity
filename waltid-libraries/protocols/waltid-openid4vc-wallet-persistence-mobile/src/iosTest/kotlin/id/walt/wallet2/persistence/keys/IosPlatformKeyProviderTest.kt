package id.walt.wallet2.persistence.keys

import at.asitplus.signum.supreme.CFCryptoOperationFailed
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import kotlinx.coroutines.test.runTest
import platform.Security.errSecItemNotFound
import platform.Security.errSecNotAvailable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IosPlatformKeyProviderTest {

    @Test
    fun unprotectedPreflightPreservesExistingAlgorithmSupport() = runTest {
        val provider = IosPlatformKeyProvider()

        KeyType.entries.forEach { keyType ->
            val preflight = provider.preflight(
                PlatformKeyRequest(keyType = keyType, keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None)
            )
            val expected = keyType in PlatformKeyProvider.DEFAULT_SUPPORTED_PLATFORM_KEY_TYPES ||
                keyType in PlatformKeyProvider.DEFAULT_SUPPORTED_SOFTWARE_KEY_TYPES
            assertEquals(expected, preflight.supported, "Unexpected None preflight for $keyType")
        }
    }

    @Test
    fun biometricCurrentSetIsRejectedOnSimulatorWithoutSoftwareFallback() = runTest {
        val provider = IosPlatformKeyProvider()
        val preflight = provider.preflight(
            PlatformKeyRequest(
                keyType = KeyType.secp256r1,
                keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
            )
        )
        val unsupported = provider.preflight(
            PlatformKeyRequest(
                keyType = KeyType.Ed25519,
                keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
            )
        )

        assertFalse(preflight.supported)
        assertEquals(KeyUseAuthorizationFailure.BiometricUnavailable, preflight.failure)
        assertFalse(unsupported.supported)
        assertEquals(KeyUseAuthorizationFailure.UnsupportedCombination, unsupported.failure)
    }

    @Test
    fun unsupportedProtectedAlgorithmFailsBeforeGeneration() = runTest {
        val failure = assertFailsWith<KeyUseAuthorizationException> {
            IosPlatformKeyProvider().generate(
                PlatformKeyRequest(
                    keyType = KeyType.Ed25519,
                    keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
                )
            )
        }

        assertEquals(KeyUseAuthorizationFailure.UnsupportedCombination, failure.failure)
    }

    @Test
    fun secureEnclavePreferenceIsIndependentFromAuthorizationPolicy() {
        val secureProvider = IosPlatformKeyProvider(useSecureElement = true)
        val keychainProvider = IosPlatformKeyProvider(useSecureElement = false)

        assertTrue(secureProvider.usesSecureElementFor(KeyType.secp256r1))
        assertFalse(secureProvider.usesSecureElementFor(KeyType.secp384r1))
        assertFalse(keychainProvider.usesSecureElementFor(KeyType.secp256r1))
    }

    @Test
    fun protectedP256RequiresSecureEnclavePreference() = runTest {
        val preflight = IosPlatformKeyProvider(useSecureElement = false).preflight(
            PlatformKeyRequest(
                keyType = KeyType.secp256r1,
                keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
            )
        )

        assertFalse(preflight.supported)
        assertEquals(KeyUseAuthorizationFailure.UnsupportedCombination, preflight.failure)
    }

    @Test
    fun missingPlatformFailureRecognitionIsNarrow() {
        assertTrue(NoSuchElementException().isMissingPlatformKey())
        assertTrue(CFCryptoOperationFailed("missing", errSecItemNotFound).isMissingPlatformKey())
        assertFalse(CFCryptoOperationFailed("unavailable", errSecNotAvailable).isMissingPlatformKey())
        assertFalse(IllegalStateException().isMissingPlatformKey())
    }
}
