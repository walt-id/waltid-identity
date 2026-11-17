@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.credentials.presentations

import id.walt.cose.coseCompliantCbor
import id.walt.credentials.examples.MdocsExamples
import id.walt.credentials.presentations.formats.MsoMdocPresentation
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.parser.MdocParser
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertTrue

class MsoMdocPresentationStructureTest {
    private val document = MdocParser.parseToDocument(MdocsExamples.mdocsExampleBase64Url)

    private fun response(version: String = "1.0", status: UInt = 0u, count: Int = 1): String =
        Base64.UrlSafe.encode(coseCompliantCbor.encodeToByteArray(
            DeviceResponse(version, Array(count) { document }, status = status),
        ))

    @Test
    fun `one document with successful status and supported version parses`() = runTest {
        assertTrue(MsoMdocPresentation.parse(response()).isSuccess)
    }

    @Test
    fun `error response with documents is rejected`() = runTest {
        assertTrue(MsoMdocPresentation.parse(response(status = 1u)).isFailure)
    }

    @Test
    fun `unsupported response version is rejected`() = runTest {
        assertTrue(MsoMdocPresentation.parse(response(version = "9.0")).isFailure)
    }

    @Test
    fun `extra documents are rejected rather than silently ignored`() = runTest {
        assertTrue(MsoMdocPresentation.parse(response(count = 2)).isFailure)
    }

    @Test
    fun `empty document list is rejected`() = runTest {
        assertTrue(MsoMdocPresentation.parse(response(count = 0)).isFailure)
    }

    @Test
    fun `bare credential document is not a presentation response`() = runTest {
        val bare = Base64.UrlSafe.encode(coseCompliantCbor.encodeToByteArray(document))
        assertTrue(MsoMdocPresentation.parse(bare).isFailure)
    }

    @Test
    fun `duplicate response map keys are rejected before typed decoding`() = runTest {
        val encoded = coseCompliantCbor.encodeToByteArray(DeviceResponse("1.0", arrayOf(document), status = 0u))
        val end = if (encoded[0] == 0xbf.toByte()) encoded.lastIndex else encoded.size
        val duplicated = byteArrayOf(0xbf.toByte()) + encoded.copyOfRange(1, end) +
            "6776657273696f6e63312e30".hexToByteArray() + byteArrayOf(0xff.toByte())
        assertTrue(MsoMdocPresentation.parse(Base64.UrlSafe.encode(duplicated)).isFailure)
    }

    @Test
    fun `tagged duplicate response keys cannot shadow model fields`() = runTest {
        val encoded = coseCompliantCbor.encodeToByteArray(DeviceResponse("1.0", arrayOf(document), status = 0u))
        val end = if (encoded[0] == 0xbf.toByte()) encoded.lastIndex else encoded.size
        val duplicated = byteArrayOf(0xbf.toByte()) + "d9d9f76776657273696f6e63392e30".hexToByteArray() +
            encoded.copyOfRange(1, end) + byteArrayOf(0xff.toByte())
        assertTrue(MsoMdocPresentation.parse(Base64.UrlSafe.encode(duplicated)).isFailure)
    }

    @Test
    fun `malformed CBOR fails without exposing the submitted value`() = runTest {
        val input = "PRIVATE-CREDENTIAL-CONTENT!"
        val failure = MsoMdocPresentation.parse(input).exceptionOrNull()
        assertTrue(failure != null)
        assertTrue(input !in failure.message.orEmpty())
    }
}
