package id.walt.crypto

import at.asitplus.signum.supreme.CFCryptoOperationFailed
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.UnlockFailed
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.crypto.keys.KeyType
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Security.errSecItemNotFound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class MobilePlatformKeySupportTest {

    private val protectedOptions = IosKey.Options(
        keyType = KeyType.secp256r1,
        inSecureElement = true,
        keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
    )

    @Test
    fun signumUnlockFailureMapsToStableAuthorizationFailureWithoutParsingMessage() {
        val result = SignatureResult.Failure(
            UnlockFailed("arbitrary text with error-like value 13 that must not be parsed")
        )

        val failure = assertFailsWith<KeyUseAuthorizationException> {
            result.signatureBytesOrThrow(protectedKeyUse = true)
        }

        assertEquals(KeyUseAuthorizationFailure.AuthorizationFailed, failure.failure)
    }

    @Test
    fun structuredSignumErrorsRemainStructured() {
        val expected = KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.ProtectedKeyInvalidated,
            message = "invalidated",
        )
        val result = SignatureResult.Error(expected)

        val actual = assertFailsWith<KeyUseAuthorizationException> {
            result.signatureBytesOrThrow(protectedKeyUse = true)
        }

        assertSame(expected, actual)
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun inaccessibleCurrentSetKeyMapsToStableInvalidatedFailure() {
        val failure = assertIs<KeyUseAuthorizationException>(
            protectedOptions.mapPlatformFailure(
                CFCryptoOperationFailed("retrieve private key", errSecItemNotFound)
            )
        )

        assertEquals(KeyUseAuthorizationFailure.ProtectedKeyInvalidated, failure.failure)
    }

    @Test
    fun absentProtectedKeyRemainsStableMissingFailure() {
        val failure = assertIs<KeyUseAuthorizationException>(
            protectedOptions.mapPlatformFailure(
                NoSuchElementException("No key for alias exists")
            )
        )

        assertEquals(KeyUseAuthorizationFailure.ProtectedKeyMissing, failure.failure)
    }

    @Test
    fun unprotectedSignumFailureKeepsLegacyCheckFailure() {
        val result = SignatureResult.Failure(UnlockFailed("legacy failure"))

        val failure = assertFailsWith<IllegalStateException> {
            result.signatureBytesOrThrow(protectedKeyUse = false)
        }

        assertEquals(true, failure.message?.startsWith("Signing failed:"))
    }
}
