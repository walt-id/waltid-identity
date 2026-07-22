package id.walt.crypto2.jose

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

class CompactJweTest {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    @Test
    fun `ECDH-ES AES-GCM round trips with agreement info`() = runTest {
        val recipient = recipientKey()
        val publicKey = recipient.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk

        JweContentEncryption.entries.forEach { contentEncryption ->
            val compact = CompactJwe.encrypt(
                plaintext = "encrypted payload".encodeToByteArray(),
                recipientPublicKey = publicKey,
                contentEncryption = contentEncryption,
                protectedHeader = JsonObject(mapOf("kid" to JsonPrimitive("recipient-key"))),
                agreementPartyUInfo = "alice".encodeToByteArray(),
                agreementPartyVInfo = "bob".encodeToByteArray(),
            )
            val decrypted = CompactJwe.decrypt(compact, recipient, setOf(contentEncryption))

            assertContentEquals("encrypted payload".encodeToByteArray(), decrypted.plaintext)
            assertEquals("recipient-key", (decrypted.protectedHeader["kid"] as JsonPrimitive).content)
        }
    }

    @Test
    fun `tampering and disallowed content encryption are rejected`() = runTest {
        val recipient = recipientKey()
        val compact = CompactJwe.encrypt(
            plaintext = "payload".encodeToByteArray(),
            recipientPublicKey = recipient.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk,
            contentEncryption = JweContentEncryption.A128GCM,
        )
        val parts = compact.split('.').toMutableList()
        parts[4] = (if (parts[4].first() == 'A') "B" else "A") + parts[4].drop(1)

        assertFails { CompactJwe.decrypt(parts.joinToString("."), recipient, setOf(JweContentEncryption.A128GCM)) }
        assertFails { CompactJwe.decrypt(compact, recipient, setOf(JweContentEncryption.A256GCM)) }
    }

    @Test
    fun `party info and recipient JWK restrictions are enforced`() = runTest {
        val recipient = recipientKey()
        val publicKey = recipient.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk

        assertFails {
            CompactJwe.encrypt(
                plaintext = byteArrayOf(),
                recipientPublicKey = publicKey,
                contentEncryption = JweContentEncryption.A128GCM,
                agreementPartyUInfo = "same".encodeToByteArray(),
                agreementPartyVInfo = "same".encodeToByteArray(),
            )
        }

        val restricted = Jwk.withMetadata(
            publicKey,
            JwkMetadata(use = JwkUse.SIGNATURE, operations = setOf(JwkOperation.VERIFY)),
        )
        assertFails {
            CompactJwe.encrypt(byteArrayOf(), restricted, JweContentEncryption.A128GCM)
        }

        val parsed = Jwk.parse(publicKey).toMutableMap().apply {
            this["x"] = JsonPrimitive("AQ")
        }
        val malformed = EncodedKey.Jwk(
            BinaryData(JsonObject(parsed).toString().encodeToByteArray()),
            privateMaterial = false,
        )
        assertFails {
            CompactJwe.encrypt(byteArrayOf(), malformed, JweContentEncryption.A128GCM)
        }
    }

    @Test
    fun `JWE uses agreement predicate rather than advertised finite set`() = runTest {
        val recipient = recipientKey()
        val predicateOnly = object : Key {
            override val id = recipient.id
            override val spec = recipient.spec
            override val usages = recipient.usages
            override val capabilities = recipient.capabilities.copy(
                keyAgreementAlgorithms = emptySet(),
                supportsKeyAgreementAlgorithm = { it == KeyAgreementAlgorithm.Ecdh },
            )
        }
        val compact = CompactJwe.encrypt(
            plaintext = "predicate".encodeToByteArray(),
            recipientPublicKey = recipient.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk,
            contentEncryption = JweContentEncryption.A128GCM,
        )

        assertEquals(
            "predicate",
            CompactJwe.decrypt(compact, predicateOnly, setOf(JweContentEncryption.A128GCM)).plaintext.decodeToString(),
        )
    }

    private suspend fun recipientKey(): SoftwareKey = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId("recipient-key"),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.KEY_AGREEMENT),
        ),
    )
}
