package id.walt.wallet2.persistence.keys

import id.walt.crypto2.signum.SignumInteractionContextUnavailableException
import id.walt.crypto2.signum.SignumKeyInvalidatedException
import id.walt.crypto2.signum.SignumKeyNotFoundException
import id.walt.crypto2.signum.SignumKeyPolicyMismatchException
import id.walt.crypto2.signum.SignumStoredKeyMetadataException
import id.walt.crypto2.signum.SignumUserCancelledException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SignumWalletKeyMappingTest {
    @Test
    fun `maps known Signum failures at the built-in provider boundary`() {
        val failures = listOf(
            SignumKeyPolicyMismatchException("key", "unsupported") to
                KeyUseAuthorizationFailure.UnsupportedCombination,
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
    }

    @Test
    fun `leaves unexpected failures unchanged`() {
        assertNull(IllegalStateException("unexpected").toKeyUseAuthorizationException())
    }
}
