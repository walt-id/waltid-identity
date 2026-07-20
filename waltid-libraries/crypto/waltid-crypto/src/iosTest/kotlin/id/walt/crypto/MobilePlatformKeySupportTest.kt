package id.walt.crypto

import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.UnlockFailed
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MobilePlatformKeySupportTest {

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

    @Test
    fun unprotectedSignumFailureKeepsLegacyCheckFailure() {
        val result = SignatureResult.Failure(UnlockFailed("legacy failure"))

        val failure = assertFailsWith<IllegalStateException> {
            result.signatureBytesOrThrow(protectedKeyUse = false)
        }

        assertEquals(true, failure.message?.startsWith("Signing failed:"))
    }
}
