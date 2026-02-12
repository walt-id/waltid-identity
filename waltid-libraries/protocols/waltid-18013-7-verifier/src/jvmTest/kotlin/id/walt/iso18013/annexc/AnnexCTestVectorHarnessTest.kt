package id.walt.iso18013.annexc

import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.iso18013.annexc.TestResources.createJwkKeyFromRawHex
import id.walt.mdoc.objects.sha256
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test harness that loads `ANNEXC-REAL-001.json` from JVM test resources.
 *
 * This harness intentionally does not hardcode CBOR parsing; it focuses on deterministic plumbing.
 * Codex should connect it to the real Annex C implementation classes once they exist.
 */
class AnnexCTestVectorHarnessTest {

    @Serializable
    data class Vector(
        val id: String,
        val origin: String,
        val deviceRequestB64: String,
        val encryptionInfoB64: String,
        val encryptedResponseB64: String,
        val recipientPrivateKeyHex: String = "",
        val expected: Expected = Expected()
    )

    @Serializable
    data class Expected(
        val sessionTranscriptCborSha256Hex: String = "",
        val deviceResponseCborSha256Hex: String = "",
        val docType: String = "",
        val requestedElement: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `ANNEXC-REAL-001 loads and is structurally sane`() {
        val v = loadVector("annex-c/ANNEXC-REAL-001.json")

        v.deviceRequestB64.base64UrlDecode()
        v.encryptionInfoB64.base64UrlDecode()
        v.encryptedResponseB64.base64UrlDecode()

        assertTrue(v.origin.startsWith("http"), "origin must be a serialized origin string")
    }

    @Test
    fun `ANNEXC-REAL-001 has a usable skR (generated if missing)`() {
        val v = loadVector("annex-c/ANNEXC-REAL-001.json")

        val skR = hexToBytes(v.recipientPrivateKeyHex)
        assertEquals(32, skR.size, "skR must be 32 bytes (P-256 private scalar)")
    }

    @Test
    fun `ANNEXC-REAL-001 transcript hash matches expected (if provided)`() {
        val v = loadVector("annex-c/ANNEXC-REAL-001.json")
        val hpkeInfo = AnnexCTranscriptBuilder.computeHpkeInfo(v.encryptionInfoB64, v.origin)
        val transcriptHashHex = hpkeInfo.sha256().joinToString("") { "%02x".format(it.toInt() and 0xff) }

        if (v.expected.sessionTranscriptCborSha256Hex.isNotBlank()) {
            assertEquals(v.expected.sessionTranscriptCborSha256Hex, transcriptHashHex)
        } else {
            println("ANNEXC-REAL-001 sessionTranscriptCborSha256Hex: $transcriptHashHex")
            assertTrue(transcriptHashHex.isNotBlank())
        }
    }

    @Test
    fun `ANNEXC-REAL-001 decrypt hash matches expected (if provided)`() = runTest {
        val v = loadVector("annex-c/ANNEXC-REAL-001.json")
        if (v.expected.deviceResponseCborSha256Hex.isBlank()) return@runTest

        val plaintext = AnnexCResponseVerifier.decryptToDeviceResponse(
            encryptedResponseB64 = v.encryptedResponseB64,
            encryptionInfoB64 = v.encryptionInfoB64,
            origin = v.origin,
            recipientPrivateKey = createJwkKeyFromRawHex(v.recipientPrivateKeyHex)
        )
        val hashHex = plaintext.sha256().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(v.expected.deviceResponseCborSha256Hex, hashHex)
    }

    private fun loadVector(resourcePath: String): Vector {
        val text = requireNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "Missing test resource: $resourcePath"
        }.bufferedReader().use { it.readText() }
        val decoded = json.decodeFromString(Vector.serializer(), text)
        return if (decoded.recipientPrivateKeyHex.isBlank()) {
            decoded.copy(recipientPrivateKeyHex = SkrGenerator.generateSkRHexForTestVector(decoded.id))
        } else {
            decoded
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim().removePrefix("0x").replace(" ", "").replace("\n", "")
        require(clean.length % 2 == 0) { "hex length must be even" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
