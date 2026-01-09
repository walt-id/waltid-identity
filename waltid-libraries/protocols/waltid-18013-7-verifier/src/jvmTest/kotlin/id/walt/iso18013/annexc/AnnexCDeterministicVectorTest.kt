@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.iso18013.annexc

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.sha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnexCDeterministicVectorTest {

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
        val deviceResponseCborSha256Hex: String,
        val docType: String,
        val requestedElement: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `ANNEXC-DETERMINISTIC-001 transcript hash and HPKE decrypt match`() {
        val v = loadVector("annex-c/ANNEXC-DETERMINISTIC-001.json")

        val hpkeInfo = AnnexCTranscriptBuilder.computeHpkeInfo(v.encryptionInfoB64, v.origin)
        val transcriptHashHex = hpkeInfo.sha256().toHex()
        assertEquals(v.expected.sessionTranscriptCborSha256Hex, transcriptHashHex)

        val plaintext = AnnexCResponseVerifierJvm.decryptToDeviceResponse(
            encryptedResponseB64 = v.encryptedResponseB64,
            encryptionInfoB64 = v.encryptionInfoB64,
            origin = v.origin,
            recipientPrivateKey = hexToBytes(v.recipientPrivateKeyHex),
        )
        assertEquals(v.expected.deviceResponseCborSha256Hex, plaintext.sha256().toHex())

        val deviceResponse = coseCompliantCbor.decodeFromByteArray(DeviceResponse.serializer(), plaintext)
        assertEquals("1.0", deviceResponse.version)
        assertEquals(0u, deviceResponse.status)
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
        }.also { bytes ->
            assertTrue(bytes.isNotEmpty(), "recipientPrivateKeyHex must not be empty for deterministic vector")
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
