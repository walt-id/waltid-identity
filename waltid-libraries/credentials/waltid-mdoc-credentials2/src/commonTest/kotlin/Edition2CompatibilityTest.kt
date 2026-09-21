@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

import id.walt.cose.CoseHeaders
import id.walt.cose.CoseMac0
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.ByteArrayBase64UrlSerializer
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.encoding.TransformingSerializerTemplate
import id.walt.mdoc.objects.DeviceSigned
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.parser.MdocParser
import kotlinx.serialization.cbor.CborString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import id.walt.mdoc.objects.edition2.document.Document as Edition2Document
import id.walt.mdoc.objects.edition2.document.IssuerSigned as Edition2IssuerSigned
import id.walt.mdoc.objects.edition2.elements.IssuerSignedItem as Edition2IssuerSignedItem
import id.walt.mdoc.objects.edition2.elements.IssuerSignedList as Edition2IssuerSignedList

class Edition2CompatibilityTest {
    private val issuerAuth = CoseSign1(byteArrayOf(), CoseHeaders(), null, byteArrayOf())

    @Test
    fun releasedConstructorsCopyAndSerializersRemainUsable() {
        val request = DeviceRequest("org.example", mapOf("org.example" to listOf("name")))
        val copiedRequest = request.copy(docRequests = request.docRequests.map { it.copy(readerAuth = null) })
        assertEquals(request, DeviceRequest.decodeFromBase64Url(copiedRequest.encodeToBase64Url()))

        val authentication = DeviceAuth(deviceMac = CoseMac0(byteArrayOf(), CoseHeaders(), null, byteArrayOf()))
        val document = Document(
            "org.example",
            IssuerSigned.fromIssuerSignedItems(
                mapOf("org.example" to listOf(id.walt.mdoc.objects.elements.IssuerSignedItem(
                    7u, ByteArray(24) { 1 }, "name", CborString("Jane")
                ))), issuerAuth,
            ),
            DeviceSigned(ByteStringWrapper(DeviceNameSpaces(emptyMap())), authentication.copy()),
        )
        val response = DeviceResponse("1.0", arrayOf(document.copy()), status = 0u).copy()
        val decoded = coseCompliantCbor.decodeFromByteArray(
            DeviceResponse.serializer(), coseCompliantCbor.encodeToByteArray(DeviceResponse.serializer(), response)
        )
        assertContentEquals(
            coseCompliantCbor.encodeToByteArray(DeviceResponse.serializer(), response),
            coseCompliantCbor.encodeToByteArray(DeviceResponse.serializer(), decoded),
        )
        assertEquals("org.example", decoded.documents!!.single().docType)
        assertNotNull(decoded.documents!!.single().deviceSigned!!.deviceAuth.deviceMac)
        val serializer: TransformingSerializerTemplate<ByteArray, String> = ByteArrayBase64UrlSerializer
        assertEquals(ByteArrayBase64UrlSerializer, serializer)
    }

    @Test
    fun edition2ParsingRetainsExtensionsAndOriginalIssuerSignedItemBytes() {
        val item = Edition2IssuerSignedItem(
            7u, ByteArray(24) { 1 }, "name", CborString("Jane"), mapOf("itemExtension" to CborString("retained"))
        )
        val itemBytes = coseCompliantCbor.encodeToByteArray(Edition2IssuerSignedItem.serializer(), item)
        val document = Edition2Document(
            "org.example",
            Edition2IssuerSigned.fromIssuerSignedLists(
                mapOf("org.example" to Edition2IssuerSignedList(listOf(ByteStringWrapper(item, itemBytes)))),
                issuerAuth,
                extensions = mapOf("issuerExtension" to CborString("retained")),
            ),
            extensions = mapOf("documentExtension" to CborString("retained")),
        )
        val encoded = coseCompliantCbor.encodeToByteArray(Edition2Document.serializer(), document)
        val parsed = MdocParser.parseToEdition2Document(encoded.toHexString())
        assertEquals(document, parsed)
        assertContentEquals(itemBytes, parsed.issuerSigned.namespaces!!.getValue("org.example").entries.single().serialized)

        // The released parser still produces its released type and preserves the signed item bytes.
        val legacy: Document = MdocParser.parseToDocument(encoded.toHexString())
        assertContentEquals(itemBytes, legacy.issuerSigned.namespaces!!.getValue("org.example").entries.single().serialized)
    }
}
