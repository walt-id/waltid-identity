package id.walt.crypto2.keys

import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class StoredKeySerializationTest {
    @Test
    fun `software key round trips without materializing a provider key`() {
        val stored = StoredKey.Software(
            version = StoredKey.CURRENT_VERSION,
            id = KeyId("issuer-key"),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            material = EncodedKey.Jwk(
                data = BinaryData("{\"kty\":\"EC\"}".encodeToByteArray()),
                privateMaterial = true,
            ),
            metadata = mapOf("tenant" to "example"),
        )

        val encoded = StoredKeyCodec.encodeToString(stored)
        val decoded = StoredKeyCodec.decodeFromString(encoded)

        assertEquals(stored, decoded)
        assertEquals(
            """{"kind":"software","version":1,"id":"issuer-key","spec":{"type":"ec","curve":"P-256"},"usages":["SIGN","VERIFY"],"material":{"format":"jwk","data":"eyJrdHkiOiJFQyJ9","privateMaterial":true},"metadata":{"tenant":"example"}}""",
            encoded,
        )
        assertFalse("EC" in encoded, "Key bytes must be base64url encoded")
    }

    @Test
    fun `managed key preserves provider-owned reference data`() {
        val stored = StoredKey.Managed(
            version = StoredKey.CURRENT_VERSION,
            id = KeyId("hsm-key"),
            spec = KeySpec.Rsa(3072),
            usages = setOf(KeyUsage.SIGN, KeyUsage.DECRYPT),
            provider = ProviderId("pkcs11"),
            providerSchemaVersion = 2,
            providerData = BinaryData(byteArrayOf(1, 2, 3)),
        )

        val encoded = StoredKeyCodec.encodeToString(stored)

        assertEquals(
            """{"kind":"managed","version":1,"id":"hsm-key","spec":{"type":"rsa","bits":3072},"usages":["SIGN","DECRYPT"],"provider":"pkcs11","providerSchemaVersion":2,"providerData":"AQID"}""",
            encoded,
        )
        assertEquals(stored, StoredKeyCodec.decodeFromByteArray(encoded.encodeToByteArray()))
    }

    @Test
    fun `managed public JWK rejects malformed and disguised private material`() {
        val coordinate = ByteArray(32) { 1 }.encodeBase64Url()
        val privateJwks = listOf(
            """{"kty":"EC","crv":"P-256","x":"$coordinate","y":"$coordinate","d":"$coordinate"}""",
            """{"kty":"RSA","p":"AQ"}""",
            """{"kty":"oct","k":"AQ"}""",
            "{",
        )

        privateJwks.forEach { jwk ->
            assertFailsWith<IllegalArgumentException> {
                managedKey(EncodedKey.Jwk(BinaryData(jwk.encodeToByteArray()), privateMaterial = false))
            }
        }

        val malicious = EncodedKey.Jwk(
            BinaryData(privateJwks.first().encodeToByteArray()),
            privateMaterial = false,
        )
        val encoded = StoredKeyCodec.encodeToString(managedKey())
            .dropLast(1) + ",\"publicKey\":${Json.encodeToString<EncodedKey>(malicious)}}"
        assertFailsWith<IllegalArgumentException> { StoredKeyCodec.decodeFromString(encoded) }
    }

    @Test
    fun `key agreement capability round trips`() {
        val stored = StoredKey.Software(
            version = StoredKey.CURRENT_VERSION,
            id = KeyId("agreement-key"),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.KEY_AGREEMENT),
            material = EncodedKey.Jwk(BinaryData("{\"kty\":\"EC\"}".encodeToByteArray()), true),
        )

        assertEquals(stored, StoredKeyCodec.decodeFromString(StoredKeyCodec.encodeToString(stored)))
    }

    @Test
    fun `unknown key kind is rejected`() {
        assertFailsWith<SerializationException> {
            StoredKeyCodec.decodeFromString("""{"kind":"unknown","version":1}""")
        }
    }

    @Test
    fun `missing and unsupported versions are rejected before key decoding`() {
        assertFailsWith<SerializationException> {
            StoredKeyCodec.decodeFromString("""{"kind":"software"}""")
        }
        assertFailsWith<SerializationException> {
            StoredKeyCodec.decodeFromString("""{"kind":"software","version":2}""")
        }
    }

    private fun managedKey(publicKey: EncodedKey? = null) = StoredKey.Managed(
        version = StoredKey.CURRENT_VERSION,
        id = KeyId("managed-jwk"),
        spec = KeySpec.Ec(EcCurve.P256),
        usages = setOf(KeyUsage.VERIFY),
        provider = ProviderId("managed-provider"),
        providerSchemaVersion = 1,
        providerData = BinaryData(byteArrayOf(1)),
        publicKey = publicKey,
    )
}
