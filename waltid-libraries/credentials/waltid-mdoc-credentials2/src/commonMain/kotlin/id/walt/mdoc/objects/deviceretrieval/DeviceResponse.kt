@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.objects.deviceretrieval

import id.walt.mdoc.encoding.decodeTextMap
import id.walt.mdoc.encoding.encodeTextMap
import id.walt.mdoc.encoding.extensionsExcluding
import id.walt.mdoc.encoding.fromCborElement
import id.walt.mdoc.encoding.requireNoExtensionCollisions
import id.walt.mdoc.encoding.toCborElement
import id.walt.mdoc.objects.MdocVersion
import id.walt.mdoc.objects.document.Document
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents the top-level response from an mdoc to an mdoc reader.
 *
 * @see ISO/IEC 18013-5:2021, 8.3.2.1.2.3
 *
 * @property version The version of the DeviceResponse structure.
 * @property documents An optional list of returned documents. This is absent if the status is not OK.
 * @property documentErrors An optional map of document types to error codes for documents that were not returned.
 * @property status The overall status code of the response (0 indicates success).
 */
@Serializable(with = DeviceResponseSerializer::class)
data class DeviceResponse(
    @SerialName("version")
    val version: String,

    @SerialName("documents")
    val documents: List<Document>? = null,

    @SerialName("zkDocuments")
    val zkDocuments: List<ZkDocument>? = null,

    @SerialName("encryptedDocuments")
    val encryptedDocuments: List<EncryptedDocuments>? = null,

    @SerialName("documentErrors")
    val documentErrors: List<Map<String, Long>>? = null,

    @SerialName("status")
    val status: UInt,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(MdocVersion.parse(version).major == 1u) { "Unsupported DeviceResponse major version" }
        require(documents == null || documents.isNotEmpty())
        require(documents.orEmpty().all { it.deviceSigned != null }) {
            "Every response Document must contain deviceSigned"
        }
        require(zkDocuments == null || zkDocuments.isNotEmpty())
        require(encryptedDocuments == null || encryptedDocuments.isNotEmpty())
        require(documentErrors == null || documentErrors.isNotEmpty())
        require(documentErrors.orEmpty().all { it.size == 1 }) { "Each DocumentError must contain exactly one entry" }
        require(status in setOf(0u, 10u, 11u, 12u)) { "Unsupported DeviceResponse status" }
        require(status == 0u || listOf(documents, zkDocuments, encryptedDocuments).all { it == null }) {
            "A non-OK DeviceResponse cannot contain documents"
        }
        requireNoExtensionCollisions(extensions, DEVICE_RESPONSE_FIELDS, "DeviceResponse")
    }
}

object DeviceResponseSerializer : KSerializer<DeviceResponse> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: DeviceResponse) {
        val fields = linkedMapOf<String, CborElement>()
        fields["version"] = CborString(value.version)
        value.documents?.let { fields["documents"] = it.toCborElement(ListSerializer(Document.serializer())) }
        value.zkDocuments?.let { fields["zkDocuments"] = it.toCborElement(ListSerializer(ZkDocument.serializer())) }
        value.encryptedDocuments?.let {
            fields["encryptedDocuments"] = it.toCborElement(ListSerializer(EncryptedDocuments.serializer()))
        }
        value.documentErrors?.let {
            fields["documentErrors"] = it.toCborElement(
                ListSerializer(MapSerializer(String.serializer(), Long.serializer()))
            )
        }
        fields["status"] = value.status.toCborElement(UInt.serializer())
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): DeviceResponse {
        val fields = decoder.decodeTextMap("DeviceResponse")
        return DeviceResponse(
            version = (fields["version"] as? CborString)?.value
                ?: throw SerializationException("DeviceResponse version is required and must be text"),
            documents = fields["documents"]?.fromCborElement(ListSerializer(Document.serializer())),
            zkDocuments = fields["zkDocuments"]?.fromCborElement(ListSerializer(ZkDocument.serializer())),
            encryptedDocuments = fields["encryptedDocuments"]?.fromCborElement(
                ListSerializer(EncryptedDocuments.serializer())
            ),
            documentErrors = fields["documentErrors"]?.fromCborElement(
                ListSerializer(MapSerializer(String.serializer(), Long.serializer()))
            ),
            status = fields["status"]?.fromCborElement(UInt.serializer())
                ?: throw SerializationException("DeviceResponse status is required"),
            extensions = fields.extensionsExcluding(DEVICE_RESPONSE_FIELDS),
        )
    }
}

private val DEVICE_RESPONSE_FIELDS = setOf(
    "version", "documents", "zkDocuments", "encryptedDocuments", "documentErrors", "status",
)
