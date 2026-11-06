package id.walt.openid4vci.clientauth.attestation

import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerificationMethod
import id.walt.openid4vci.proofs.attestation.KeyAttestationVerificationMethod
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class X509ChainExpectedEntityTypeTest {
    private val json = Json { encodeDefaults = false; explicitNulls = false }

    @Test
    fun `client attestation chain omits an unset entity type and round trips a set one`() {
        val omitted = json.encodeToString(
            ClientAttestationVerificationMethod.serializer(),
            ClientAttestationVerificationMethod.X509Chain(listOf("pem")),
        )
        assertFalse("expectedEntityType" in omitted)
        val decodedOmitted = json.decodeFromString(ClientAttestationVerificationMethod.serializer(), omitted)
        assertIs<ClientAttestationVerificationMethod.X509Chain>(decodedOmitted)
        assertNull(decodedOmitted.expectedEntityType)

        val encoded = json.encodeToString(
            ClientAttestationVerificationMethod.serializer(),
            ClientAttestationVerificationMethod.X509Chain(listOf("pem"), expectedEntityType = "WALLET_PROVIDER"),
        )
        val decoded = json.decodeFromString(ClientAttestationVerificationMethod.serializer(), encoded)
        assertEquals(
            ClientAttestationVerificationMethod.X509Chain(listOf("pem"), expectedEntityType = "WALLET_PROVIDER"),
            decoded,
        )
    }

    @Test
    fun `key attestation chain omits an unset entity type and round trips a set one`() {
        val omitted = json.encodeToString(
            KeyAttestationVerificationMethod.serializer(),
            KeyAttestationVerificationMethod.X509Chain(listOf("pem")),
        )
        assertFalse("expectedEntityType" in omitted)
        val decodedOmitted = json.decodeFromString(KeyAttestationVerificationMethod.serializer(), omitted)
        assertIs<KeyAttestationVerificationMethod.X509Chain>(decodedOmitted)
        assertNull(decodedOmitted.expectedEntityType)

        val encoded = json.encodeToString(
            KeyAttestationVerificationMethod.serializer(),
            KeyAttestationVerificationMethod.X509Chain(listOf("pem"), expectedEntityType = "WALLET_PROVIDER"),
        )
        val decoded = json.decodeFromString(KeyAttestationVerificationMethod.serializer(), encoded)
        assertEquals(
            KeyAttestationVerificationMethod.X509Chain(listOf("pem"), expectedEntityType = "WALLET_PROVIDER"),
            decoded,
        )
    }

    @Test
    fun `rejects a blank expected entity type`() {
        assertFailsWith<IllegalArgumentException> {
            ClientAttestationVerificationMethod.X509Chain(listOf("pem"), expectedEntityType = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            KeyAttestationVerificationMethod.X509Chain(listOf("pem"), expectedEntityType = "")
        }
    }
}
