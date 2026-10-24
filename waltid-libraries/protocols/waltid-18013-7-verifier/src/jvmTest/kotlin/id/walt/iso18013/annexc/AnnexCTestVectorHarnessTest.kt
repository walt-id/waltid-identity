package id.walt.iso18013.annexc

import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.mdoc.objects.sha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnexCTestVectorHarnessTest {

    @Serializable
    data class Vector(
        val id: String,
        val origin: String,
        val deviceRequestB64: String,
        val encryptionInfoB64: String,
        val encryptedResponseB64: String,
        val recipientPrivateKeyHex: String,
        val expected: Expected,
    )

    @Serializable
    data class Expected(
        val sessionTranscriptCborSha256Hex: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `ANNEXC-REAL-001 capture matches its transcript hash`() {
        val v = loadVector("annex-c/ANNEXC-REAL-001.json")

        v.deviceRequestB64.base64UrlDecode()
        v.encryptionInfoB64.base64UrlDecode()
        v.encryptedResponseB64.base64UrlDecode()

        assertTrue(v.origin.startsWith("http"), "origin must be a serialized origin string")
        val skR = hexToBytes(v.recipientPrivateKeyHex)
        assertEquals(32, skR.size, "skR must be 32 bytes (P-256 private scalar)")

        val hpkeInfo = AnnexCTranscriptBuilder.computeHpkeInfo(v.encryptionInfoB64, v.origin)
        val transcriptHashHex = hpkeInfo.sha256().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(v.expected.sessionTranscriptCborSha256Hex, transcriptHashHex)
    }

    private fun loadVector(resourcePath: String): Vector {
        val text = requireNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "Missing test resource: $resourcePath"
        }.bufferedReader().use { it.readText() }
        return json.decodeFromString(Vector.serializer(), text)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim().removePrefix("0x").replace(" ", "").replace("\n", "")
        require(clean.length % 2 == 0) { "hex length must be even" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
