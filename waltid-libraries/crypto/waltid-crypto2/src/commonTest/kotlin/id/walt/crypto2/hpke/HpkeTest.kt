package id.walt.crypto2.hpke

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.HpkeAeadId
import id.walt.crypto2.keys.HpkeCiphertext
import id.walt.crypto2.keys.HpkeKdfId
import id.walt.crypto2.keys.HpkeKemId
import id.walt.crypto2.keys.HpkeSuite
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.providers.CryptoOperation
import id.walt.crypto2.providers.CryptoRequirement
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails

class HpkeTest {
    private val provider = CryptographySoftwareKeyProvider()
    private val runtime = CryptoRuntime(listOf(provider))

    @Test
    fun `RFC 9180 P-256 base mode vector decrypts after key restart`() = runTest {
        if (!supportsRecipientImport()) return@runTest
        val recipient = runtime.restore(
            StoredKeyCodec.decodeFromString(
                StoredKeyCodec.encodeToString(recipientStoredKey())
            )
        )
        val ciphertext = vectorCiphertext()

        assertContentEquals(
            hex("4265617574792069732074727574682c20747275746820626561757479"),
            Hpke.openBase(
                recipientKey = recipient,
                ciphertext = ciphertext,
                info = hex("4f6465206f6e2061204772656369616e2055726e"),
                aad = hex("436f756e742d30"),
            ),
        )
    }

    @Test
    fun `wrong context and tampering are rejected`() = runTest {
        if (!supportsRecipientImport()) return@runTest
        val recipient = runtime.restore(recipientStoredKey())
        val ciphertext = vectorCiphertext()
        val info = hex("4f6465206f6e2061204772656369616e2055726e")
        val aad = hex("436f756e742d30")

        assertFails { Hpke.openBase(recipient, ciphertext, info + 0, aad) }
        assertFails { Hpke.openBase(recipient, ciphertext, info, aad + 0) }
        assertFails {
            Hpke.openBase(
                recipient,
                ciphertext.copy(
                    ciphertext = BinaryData(
                        ciphertext.ciphertext.toByteArray().copyOf().also {
                            it[it.lastIndex] = (it.last() + 1).toByte()
                        }
                    )
                ),
                info,
                aad,
            )
        }
        assertFails {
            Hpke.openBase(
                recipient,
                ciphertext.copy(
                    suite = HpkeSuite(
                        HpkeKemId.DHKEM_P256_HKDF_SHA256,
                        HpkeKdfId.HKDF_SHA256,
                        HpkeAeadId.AES_256_GCM,
                    )
                ),
                info,
                aad,
            )
        }
    }

    @Test
    fun `portable seal and open round trip`() = runTest {
        val recipient = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("generated-recipient"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
        val publicJwk = recipient.capabilities.publicKeyExporter?.exportPublicKey() as EncodedKey.Jwk
        val plaintext = "annex-c-device-response".encodeToByteArray()
        val info = "session-transcript".encodeToByteArray()
        val aad = "associated-data".encodeToByteArray()
        val sealed = Hpke.sealBase(publicJwk, plaintext, info, aad)

        assertContentEquals(plaintext, Hpke.openBase(recipient, sealed, info, aad))

        assertFails {
            Hpke.sealBase(withJwkMember(publicJwk, "use", JsonPrimitive("sig")), plaintext)
        }
        assertFails {
            Hpke.sealBase(
                withJwkMember(publicJwk, "key_ops", JsonArray(listOf(JsonPrimitive("verify")))),
                plaintext,
            )
        }
        assertFails {
            Hpke.sealBase(withJwkMember(publicJwk, "alg", JsonPrimitive("ECDH-ES")), plaintext)
        }
        assertFails {
            Hpke.sealBase(withJwkMember(publicJwk, "d", JsonPrimitive("AA")), plaintext)
        }
    }

    private fun recipientStoredKey() = StoredKey.Software(
        version = StoredKey.CURRENT_VERSION,
        id = KeyId("rfc-9180-recipient"),
        spec = KeySpec.Ec(EcCurve.P256),
        usages = setOf(KeyUsage.KEY_AGREEMENT),
        material = EncodedKey.Jwk(
            data = BinaryData(
                """{"kty":"EC","crv":"P-256","x":"_owZzgkFGR68KYqSRXklMfJvDOziRgY56Lw5y39waoI","y":"anebTPlpuKDlOcf2L7PTCtaqj4DjDx0Siq_WiiznLqA","d":"885_2uV-GjENh_HrvebzKL4Kmc28rfTWWJzyneS4_9I"}"""
                    .encodeToByteArray()
            ),
            privateMaterial = true,
        ),
    )

    private fun supportsRecipientImport(): Boolean = provider.supports(
        CryptoRequirement(
            operation = CryptoOperation.IMPORT_KEY,
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.KEY_AGREEMENT),
            keyEncoding = KeyEncodingFormat.JWK,
        ),
    )

    private fun vectorCiphertext() = HpkeCiphertext(
        suite = Hpke.P256_HKDF_SHA256_AES_128_GCM,
        encapsulatedKey = BinaryData(
            hex("04a92719c6195d5085104f469a8b9814d5838ff72b60501e2c4466e5e67b325ac98536d7b61a1af4b78e5b7f951c0900be863c403ce65c9bfcb9382657222d18c4")
        ),
        ciphertext = BinaryData(
            hex("5ad590bb8baa577f8619db35a36311226a896e7342a6d836d8b7bcd2f20b6c7f9076ac232e3ab2523f39513434")
        ),
    )

    private fun hex(value: String): ByteArray = value.hexToByteArray()

    private fun withJwkMember(
        jwk: EncodedKey.Jwk,
        name: String,
        value: JsonElement,
    ): EncodedKey.Jwk {
        val parsed = Json.parseToJsonElement(jwk.data.toByteArray().decodeToString()) as JsonObject
        return EncodedKey.Jwk(
            BinaryData(Json.encodeToString(JsonObject(parsed + (name to value))).encodeToByteArray()),
            privateMaterial = false,
        )
    }
}
