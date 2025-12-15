package id.walt.iso18013.annexc

import id.walt.iso18013.annexc.cbor.Base64UrlNoPad
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Test harness that loads docs/feature/annex-c/test-vectors/ANNEXC-REAL-001.json
 *
 * How to use:
 * 1) Copy the JSON into test resources OR adjust the path below to your repo layout.
 * 2) Paste a real `recipientPrivateKeyHex` (skR) from your backend session store.
 * 3) Run the test. It will print plaintext sha256 if expected is empty.
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
        val vectorPath = Path("docs/feature/annex-c/test-vectors/ANNEXC-REAL-001.json")
        val v = json.decodeFromString(Vector.serializer(), vectorPath.readText())

        // basic sanity: base64url decodes
        Base64UrlNoPad.decode(v.deviceRequestB64)
        Base64UrlNoPad.decode(v.encryptionInfoB64)
        Base64UrlNoPad.decode(v.encryptedResponseB64)

        assertTrue(v.origin.startsWith("http"), "origin must be a serialized origin string")
    }

    @Test
    fun `ANNEXC-REAL-001 HPKE decrypt is deterministic when skR is provided`() {
        val vectorPath = Path("docs/feature/annex-c/test-vectors/ANNEXC-REAL-001.json")
        val v = json.decodeFromString(Vector.serializer(), vectorPath.readText())

        if (v.recipientPrivateKeyHex.isBlank()) {
            // Not failing: this is expected until you paste skR from backend session store.
            println("SKIPPED: recipientPrivateKeyHex (skR) not provided in vector ${'$'}{v.id}")
            return
        }

        val skR = hexToBytes(v.recipientPrivateKeyHex)

        // TODO: wire the real implementation here
        val verifier: AnnexCResponseVerifier = object : AnnexCResponseVerifier {
            override fun decryptToDeviceResponse(
                encryptedResponseB64: String,
                encryptionInfoB64: String,
                origin: String,
                recipientPrivateKey: ByteArray
            ): ByteArray {
                TODO("Connect to real Annex C implementation (HPKE decrypt + transcript)")
            }
        }

        val plaintext = verifier.decryptToDeviceResponse(
            encryptedResponseB64 = v.encryptedResponseB64,
            encryptionInfoB64 = v.encryptionInfoB64,
            origin = v.origin,
            recipientPrivateKey = skR
        )

        val sha = sha256Hex(plaintext)
        if (v.expected.deviceResponseCborSha256Hex.isBlank()) {
            println("Computed DeviceResponse CBOR sha256 for ${'$'}{v.id}: ${'$'}sha")
            // Intentionally not failing; paste the value into vector.expected.deviceResponseCborSha256Hex
            return
        }

        assertTrue(
            sha.equals(v.expected.deviceResponseCborSha256Hex, ignoreCase = true),
            "DeviceResponse sha256 mismatch: expected=${'$'}{v.expected.deviceResponseCborSha256Hex} actual=${'$'}sha"
        )
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val out = md.digest(bytes)
        return out.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim().removePrefix("0x").replace(" ", "").replace("\n", "")
        require(clean.length % 2 == 0) { "hex length must be even" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
