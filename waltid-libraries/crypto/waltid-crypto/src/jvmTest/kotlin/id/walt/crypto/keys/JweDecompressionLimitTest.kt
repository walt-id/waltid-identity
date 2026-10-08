package id.walt.crypto.keys

import com.nimbusds.jose.CompressionAlgorithm
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDHEncrypter
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JweUtils
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JweDecompressionLimitTest {
    @Test
    fun `both wallet decryption paths accept compressed payloads at the default limit`() = runTest {
        val recipient = ECKeyGenerator(Curve.P_256).generate()
        val payload = payloadOfSize(1_000_000)
        val compact = encrypt(recipient, payload, compressed = true)
        assertEquals(payload, JweUtils.parseJWE(compact, recipient.toJSONString()).payload)
        val key = JWKKey.importJWK(recipient.toJSONString()).getOrThrow()
        assertContentEquals(payload.toString().encodeToByteArray(), key.decryptJwe(compact))
    }

    @Test
    fun `both wallet decryption paths reject compressed payloads beyond the default limit`() = runTest {
        val recipient = ECKeyGenerator(Curve.P_256).generate()
        val compact = encrypt(recipient, payloadOfSize(1_000_001), compressed = true)
        val jsonFailure = assertFailsWith<JOSEException> { JweUtils.parseJWE(compact, recipient.toJSONString()) }
        val key = JWKKey.importJWK(recipient.toJSONString()).getOrThrow()
        val byteFailure = assertFailsWith<JOSEException> { key.decryptJwe(compact) }
        listOf(jsonFailure, byteFailure).forEach {
            assertTrue(it.message.orEmpty().contains("decompress", ignoreCase = true))
        }
    }

    @Test
    fun `the decompression limit does not restrict uncompressed payloads`() = runTest {
        val recipient = ECKeyGenerator(Curve.P_256).generate()
        val payload = payloadOfSize(1_000_001)
        val compact = encrypt(recipient, payload, compressed = false)
        assertEquals(payload, JweUtils.parseJWE(compact, recipient.toJSONString()).payload)
        val key = JWKKey.importJWK(recipient.toJSONString()).getOrThrow()
        assertContentEquals(payload.toString().encodeToByteArray(), key.decryptJwe(compact))
    }

    private fun payloadOfSize(bytes: Int): JsonObject {
        val payload = JsonObject(mapOf("data" to JsonPrimitive("x".repeat(bytes - 11))))
        check(payload.toString().encodeToByteArray().size == bytes)
        return payload
    }

    private fun encrypt(recipient: ECKey, payload: JsonObject, compressed: Boolean): String {
        val header = JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM).apply {
            if (compressed) compressionAlgorithm(CompressionAlgorithm.DEF)
        }.build()
        return JWEObject(header, Payload(payload.toString())).apply {
            encrypt(ECDHEncrypter(recipient.toPublicJWK()))
        }.serialize()
    }
}
