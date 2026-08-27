@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.objects.document

import id.walt.mdoc.encoding.decodeTextMap
import id.walt.mdoc.encoding.encodeTextMap
import id.walt.mdoc.encoding.extensionsExcluding
import id.walt.mdoc.encoding.fromCborElement
import id.walt.mdoc.encoding.requireNoExtensionCollisions
import id.walt.mdoc.encoding.toCborElement
import id.walt.mdoc.objects.DeviceSigned
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents a single document returned within a `DeviceResponse`. This is the main container
 * for the data elements of a specific credential, such as a Mobile Driving Licence (mDL).
 *
 * It separates data elements into those signed by the issuer and those signed by the mdoc holder's device.
 *
 * @see ISO/IEC 18013-5, Document CDDL
 *
 * @property docType The document type identifier for the credential being presented (e.g., "org.iso.18013.5.1.mDL").
 * @property issuerSigned A mandatory structure containing data elements signed by the issuing authority and the
 * Mobile Security Object (MSO) for their verification.
 * @property deviceSigned Device-authenticated data. It may be absent in a stored or partially built [Document],
 * but [id.walt.mdoc.objects.deviceretrieval.DeviceResponse] requires it for every returned conventional document.
 * @property errors An optional map that reports errors for any requested data elements that could not be returned.
 * The map structure is `Namespace -> (DataElementIdentifier -> ErrorCode)`.
 * @property extensions Unrecognized Document fields retained for a forward-compatible wire round trip.
 */
@Serializable(with = DocumentSerializer::class)
data class Document(
    @SerialName("docType")
    val docType: String,

    @SerialName("issuerSigned")
    val issuerSigned: IssuerSigned,

    @SerialName("deviceSigned")
    val deviceSigned: DeviceSigned? = null,

    @SerialName("errors")
    val errors: Map<String, Map<String, Long>>? = null,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(docType.isNotBlank()) { "Document docType must not be blank" }
        requireNoExtensionCollisions(extensions, DOCUMENT_FIELDS, "Document")
    }
}

object DocumentSerializer : KSerializer<Document> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Document) {
        val fields = linkedMapOf<String, CborElement>()
        fields["docType"] = CborString(value.docType)
        fields["issuerSigned"] = value.issuerSigned.toCborElement(IssuerSigned.serializer())
        value.deviceSigned?.let { fields["deviceSigned"] = it.toCborElement(DeviceSigned.serializer()) }
        value.errors?.let {
            fields["errors"] = it.toCborElement(
                MapSerializer(String.serializer(), MapSerializer(String.serializer(), Long.serializer()))
            )
        }
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): Document {
        val fields = decoder.decodeTextMap("Document")
        return Document(
            docType = (fields["docType"] as? CborString)?.value
                ?: throw SerializationException("Document docType is required and must be text"),
            issuerSigned = fields["issuerSigned"]?.fromCborElement(IssuerSigned.serializer())
                ?: throw SerializationException("Document issuerSigned is required"),
            deviceSigned = fields["deviceSigned"]?.fromCborElement(DeviceSigned.serializer()),
            errors = fields["errors"]?.fromCborElement(
                MapSerializer(String.serializer(), MapSerializer(String.serializer(), Long.serializer()))
            ),
            extensions = fields.extensionsExcluding(DOCUMENT_FIELDS),
        )
    }
}

private val DOCUMENT_FIELDS = setOf("docType", "issuerSigned", "deviceSigned", "errors")
