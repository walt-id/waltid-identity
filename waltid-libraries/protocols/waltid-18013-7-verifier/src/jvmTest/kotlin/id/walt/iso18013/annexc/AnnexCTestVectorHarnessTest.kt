package id.walt.iso18013.annexc

import id.walt.iso18013.annexc.cbor.Base64UrlNoPad
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
        val deviceResponseCborSha256Hex: String = "",
        val docType: String = "",
        val requestedElement: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `ANNEXC-REAL-001 loads and is structurally sane`() {
        val v = loadVector("annex-c/ANNEXC-REAL-001.json")

        Base64UrlNoPad.decode(v.deviceRequestB64)
        Base64UrlNoPad.decode(v.encryptionInfoB64)
        Base64UrlNoPad.decode(v.encryptedResponseB64)

        assertTrue(v.origin.startsWith("http"), "origin must be a serialized origin string")
    }

    @Test
    fun `ANNEXC-REAL-001 has a usable skR (generated if missing)`() {
        val v = loadVector("annex-c/ANNEXC-REAL-001.json")

        val skR = hexToBytes(v.recipientPrivateKeyHex)
        assertEquals(32, skR.size, "skR must be 32 bytes (P-256 private scalar)")
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
