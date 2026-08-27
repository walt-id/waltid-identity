@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.objects.deviceretrieval

import id.walt.cose.CoseKey
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.encoding.decodeTextMap
import id.walt.mdoc.encoding.encodeTextMap
import id.walt.mdoc.encoding.extensionsExcluding
import id.walt.mdoc.encoding.fromCborElement
import id.walt.mdoc.encoding.fromTaggedByteString
import id.walt.mdoc.encoding.requireNoExtensionCollisions
import id.walt.mdoc.encoding.toCborElement
import id.walt.mdoc.encoding.toTaggedByteString
import id.walt.mdoc.objects.document.Document
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborObjectAsArray
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = DocRequestInfoSerializer::class)
data class DocRequestInfo(
    val alternativeDataElements: List<AlternativeDataElementsSet>? = null,
    val issuerIdentifiers: List<ByteArray>? = null,
    val uniqueDocSetRequired: Boolean? = null,
    val maximumResponseSize: UInt? = null,
    val zkRequest: ZkRequest? = null,
    val docResponseEncryption: ByteStringWrapper<EncryptionParameters>? = null,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(
            alternativeDataElements != null || issuerIdentifiers != null || uniqueDocSetRequired != null ||
                maximumResponseSize != null || zkRequest != null || docResponseEncryption != null || extensions.isNotEmpty()
        ) { "DocRequestInfo must contain at least one field" }
        require(alternativeDataElements == null || alternativeDataElements.isNotEmpty())
        require(issuerIdentifiers == null || issuerIdentifiers.isNotEmpty())
        require(issuerIdentifiers.orEmpty().all { it.isNotEmpty() }) { "Issuer identifiers must not be empty" }
        require(maximumResponseSize == null || maximumResponseSize > 0u) { "Maximum response size must be positive" }
        require(extensions.keys.none { it in STANDARD_DOC_REQUEST_INFO_KEYS }) {
            "DocRequestInfo extension collides with a standard field"
        }
    }

    override fun equals(other: Any?): Boolean = other is DocRequestInfo &&
        alternativeDataElements == other.alternativeDataElements &&
        issuerIdentifiers.contentEqualsNested(other.issuerIdentifiers) &&
        uniqueDocSetRequired == other.uniqueDocSetRequired && maximumResponseSize == other.maximumResponseSize &&
        zkRequest == other.zkRequest && docResponseEncryption == other.docResponseEncryption && extensions == other.extensions

    override fun hashCode(): Int = listOf(
        alternativeDataElements,
        issuerIdentifiers?.fold(1) { value, bytes -> 31 * value + bytes.contentHashCode() },
        uniqueDocSetRequired,
        maximumResponseSize,
        zkRequest,
        docResponseEncryption,
        extensions,
    ).hashCode()
}

object DocRequestInfoSerializer : KSerializer<DocRequestInfo> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: DocRequestInfo) {
        val fields = linkedMapOf<CborElement, CborElement>()
        fun put(name: String, element: CborElement?) { if (element != null) fields[CborString(name)] = element }
        put("alternativeDataElements", value.alternativeDataElements?.toRequestElement(
            kotlinx.serialization.builtins.ListSerializer(AlternativeDataElementsSet.serializer())
        ))
        put("issuerIdentifiers", value.issuerIdentifiers?.toRequestElement(
            kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.ByteArraySerializer())
        ))
        put("uniqueDocSetRequired", value.uniqueDocSetRequired?.toRequestElement(Boolean.serializer()))
        put("maximumResponseSize", value.maximumResponseSize?.toRequestElement(UInt.serializer()))
        put("zkRequest", value.zkRequest?.toRequestElement(ZkRequest.serializer()))
        put(
            "docResponseEncryption",
            value.docResponseEncryption?.toTaggedByteString(EncryptionParameters.serializer()),
        )
        value.extensions.forEach { (key, extension) -> fields[CborString(key)] = extension }
        encoder.encodeSerializableValue(CborElement.serializer(), CborMap(fields))
    }

    override fun deserialize(decoder: Decoder): DocRequestInfo {
        val map = decoder.decodeSerializableValue(CborElement.serializer()) as? CborMap
            ?: throw SerializationException("DocRequestInfo must be a CBOR map")
        val fields = map.entries.associate { (key, value) ->
            ((key as? CborString)?.value ?: throw SerializationException("DocRequestInfo keys must be text")) to value
        }
        fun <T> get(name: String, serializer: KSerializer<T>): T? = fields[name]?.fromRequestElement(serializer)
        return DocRequestInfo(
            alternativeDataElements = get(
                "alternativeDataElements", kotlinx.serialization.builtins.ListSerializer(AlternativeDataElementsSet.serializer())
            ),
            issuerIdentifiers = get(
                "issuerIdentifiers", kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.ByteArraySerializer())
            ),
            uniqueDocSetRequired = get("uniqueDocSetRequired", Boolean.serializer()),
            maximumResponseSize = get("maximumResponseSize", UInt.serializer()),
            zkRequest = get("zkRequest", ZkRequest.serializer()),
            docResponseEncryption = fields["docResponseEncryption"]?.fromTaggedByteString(
                EncryptionParameters.serializer(),
                "DocRequestInfo docResponseEncryption",
            ),
            extensions = fields.filterKeys { it !in STANDARD_DOC_REQUEST_INFO_KEYS },
        )
    }
}

private val STANDARD_DOC_REQUEST_INFO_KEYS = setOf(
    "alternativeDataElements", "issuerIdentifiers", "uniqueDocSetRequired", "maximumResponseSize",
    "zkRequest", "docResponseEncryption",
)

private fun <T> T.toRequestElement(serializer: KSerializer<T>): CborElement = toCborElement(serializer)

private fun <T> CborElement.fromRequestElement(serializer: KSerializer<T>): T = fromCborElement(serializer)

@Serializable(with = AlternativeDataElementsSetSerializer::class)
data class AlternativeDataElementsSet(
    val requestedElement: ElementReference,
    val alternativeElementSets: List<List<ElementReference>>,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(alternativeElementSets.isNotEmpty() && alternativeElementSets.all { it.isNotEmpty() }) {
            "Alternative element sets must not be empty"
        }
        require(alternativeElementSets.flatten().none { it == requestedElement }) {
            "An alternative cannot repeat the requested element"
        }
        require(alternativeElementSets.distinct().size == alternativeElementSets.size) {
            "Alternative element sets must be unique"
        }
        require(alternativeElementSets.all { it.distinct().size == it.size }) {
            "An alternative element set cannot repeat an element"
        }
        requireNoExtensionCollisions(extensions, ALTERNATIVE_DATA_ELEMENTS_SET_FIELDS, "AlternativeDataElementsSet")
    }
}

object AlternativeDataElementsSetSerializer : KSerializer<AlternativeDataElementsSet> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: AlternativeDataElementsSet) {
        val fields = linkedMapOf(
            "requestedElement" to value.requestedElement.toCborElement(ElementReference.serializer()),
            "alternativeElementSets" to value.alternativeElementSets.toCborElement(
                ListSerializer(ListSerializer(ElementReference.serializer()))
            ),
        )
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): AlternativeDataElementsSet {
        val fields = decoder.decodeTextMap("AlternativeDataElementsSet")
        return AlternativeDataElementsSet(
            requestedElement = fields["requestedElement"]?.fromCborElement(ElementReference.serializer())
                ?: throw SerializationException("AlternativeDataElementsSet requestedElement is required"),
            alternativeElementSets = fields["alternativeElementSets"]?.fromCborElement(
                ListSerializer(ListSerializer(ElementReference.serializer()))
            ) ?: throw SerializationException("AlternativeDataElementsSet alternativeElementSets is required"),
            extensions = fields.extensionsExcluding(ALTERNATIVE_DATA_ELEMENTS_SET_FIELDS),
        )
    }
}

private val ALTERNATIVE_DATA_ELEMENTS_SET_FIELDS = setOf("requestedElement", "alternativeElementSets")

@Serializable
@CborObjectAsArray
data class ElementReference(val namespace: String, val elementIdentifier: String) {
    init {
        require(namespace.isNotBlank() && elementIdentifier.isNotBlank()) { "Element references must not be blank" }
    }
}

@Serializable(with = ZkRequestSerializer::class)
data class ZkRequest(
    val zkRequired: Boolean,
    val systemSpecs: List<ZkSystemSpec>,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(systemSpecs.isNotEmpty()) { "ZkRequest must offer at least one proof system" }
        require(systemSpecs.map { it.zkSystemId }.distinct().size == systemSpecs.size) {
            "ZKP system identifiers must be unique within a request"
        }
        requireNoExtensionCollisions(extensions, ZK_REQUEST_FIELDS, "ZkRequest")
    }
}

object ZkRequestSerializer : KSerializer<ZkRequest> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ZkRequest) {
        val fields = linkedMapOf(
            "zkRequired" to value.zkRequired.toCborElement(Boolean.serializer()),
            "systemSpecs" to value.systemSpecs.toCborElement(ListSerializer(ZkSystemSpec.serializer())),
        )
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): ZkRequest {
        val fields = decoder.decodeTextMap("ZkRequest")
        return ZkRequest(
            zkRequired = fields["zkRequired"]?.fromCborElement(Boolean.serializer())
                ?: throw SerializationException("ZkRequest zkRequired is required"),
            systemSpecs = fields["systemSpecs"]?.fromCborElement(ListSerializer(ZkSystemSpec.serializer()))
                ?: throw SerializationException("ZkRequest systemSpecs is required"),
            extensions = fields.extensionsExcluding(ZK_REQUEST_FIELDS),
        )
    }
}

private val ZK_REQUEST_FIELDS = setOf("zkRequired", "systemSpecs")

@Serializable(with = ZkSystemSpecSerializer::class)
data class ZkSystemSpec(
    val zkSystemId: String,
    val system: String,
    val params: CborMap = CborMap(emptyMap()),
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(zkSystemId.isNotBlank() && system.isNotBlank()) { "ZKP system identifiers must not be blank" }
        requireNoExtensionCollisions(extensions, ZK_SYSTEM_SPEC_FIELDS, "ZkSystemSpec")
    }
}

object ZkSystemSpecSerializer : KSerializer<ZkSystemSpec> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ZkSystemSpec) {
        val fields = linkedMapOf<String, CborElement>(
            "zkSystemId" to CborString(value.zkSystemId),
            "system" to CborString(value.system),
            "params" to value.params,
        )
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): ZkSystemSpec {
        val fields = decoder.decodeTextMap("ZkSystemSpec")
        return ZkSystemSpec(
            zkSystemId = (fields["zkSystemId"] as? CborString)?.value
                ?: throw SerializationException("ZkSystemSpec zkSystemId is required and must be text"),
            system = (fields["system"] as? CborString)?.value
                ?: throw SerializationException("ZkSystemSpec system is required and must be text"),
            params = fields["params"] as? CborMap
                ?: throw SerializationException("ZkSystemSpec params is required and must be a map"),
            extensions = fields.extensionsExcluding(ZK_SYSTEM_SPEC_FIELDS),
        )
    }
}

private val ZK_SYSTEM_SPEC_FIELDS = setOf("zkSystemId", "system", "params")

@Serializable(with = EncryptionParametersSerializer::class)
data class EncryptionParameters(
    val recipientPublicKey: CoseKey,
    val nonce: ByteArray? = null,
    val recipientCertificate: List<ByteArray>? = null,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(recipientPublicKey.d == null) { "Encryption recipient key must contain public material only" }
        require(recipientCertificate == null || recipientCertificate.isNotEmpty())
        require(recipientCertificate.orEmpty().all { it.isNotEmpty() })
        requireNoExtensionCollisions(extensions, ENCRYPTION_PARAMETERS_FIELDS, "EncryptionParameters")
    }

    override fun equals(other: Any?): Boolean = other is EncryptionParameters &&
        recipientPublicKey == other.recipientPublicKey && nonce.contentEquals(other.nonce) &&
        recipientCertificate.contentEqualsNested(other.recipientCertificate) && extensions == other.extensions

    override fun hashCode(): Int = listOf(
        recipientPublicKey,
        nonce?.contentHashCode(),
        recipientCertificate?.contentHashCodeNested(),
        extensions,
    ).hashCode()
}

object EncryptionParametersSerializer : KSerializer<EncryptionParameters> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: EncryptionParameters) {
        val fields = linkedMapOf<String, CborElement>(
            "recipientPublicKey" to value.recipientPublicKey.toCborElement(CoseKey.serializer()),
        )
        value.nonce?.let { fields["nonce"] = CborByteString(it) }
        value.recipientCertificate?.let { certificates ->
            fields["recipientCertificate"] = CborArray(certificates.map(::CborByteString))
        }
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): EncryptionParameters {
        val fields = decoder.decodeTextMap("EncryptionParameters")
        return EncryptionParameters(
            recipientPublicKey = fields["recipientPublicKey"]?.fromCborElement(CoseKey.serializer())
                ?: throw SerializationException("EncryptionParameters recipientPublicKey is required"),
            nonce = fields["nonce"]?.asByteArray("EncryptionParameters nonce"),
            recipientCertificate = fields["recipientCertificate"]?.asByteArrayList(
                "EncryptionParameters recipientCertificate"
            ),
            extensions = fields.extensionsExcluding(ENCRYPTION_PARAMETERS_FIELDS),
        )
    }
}

private val ENCRYPTION_PARAMETERS_FIELDS = setOf("recipientPublicKey", "nonce", "recipientCertificate")

@Serializable(with = ZkDocumentSerializer::class)
data class ZkDocument(
    val documentData: ByteStringWrapper<ZkDocumentData>,
    val proof: ByteArray,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(documentData.serialized.isNotEmpty()) { "ZkDocumentData must retain its exact bytes" }
        require(proof.isNotEmpty()) { "ZKP proof must not be empty" }
        requireNoExtensionCollisions(extensions, ZK_DOCUMENT_FIELDS, "ZkDocument")
    }

    override fun equals(other: Any?): Boolean = other is ZkDocument &&
        documentData.value == other.documentData.value &&
        documentData.serialized.contentEquals(other.documentData.serialized) && proof.contentEquals(other.proof) &&
        extensions == other.extensions

    override fun hashCode(): Int = listOf(
        documentData.value,
        documentData.serialized.contentHashCode(),
        proof.contentHashCode(),
        extensions,
    ).hashCode()
}

object ZkDocumentSerializer : KSerializer<ZkDocument> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ZkDocument) {
        val fields = linkedMapOf<String, CborElement>(
            "documentData" to value.documentData.toTaggedByteString(ZkDocumentData.serializer()),
            "proof" to CborByteString(value.proof),
        )
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): ZkDocument {
        val fields = decoder.decodeTextMap("ZkDocument")
        return ZkDocument(
            documentData = fields["documentData"]?.fromTaggedByteString(
                ZkDocumentData.serializer(),
                "ZkDocument documentData",
            ) ?: throw SerializationException("ZkDocument documentData is required"),
            proof = fields["proof"]?.asByteArray("ZkDocument proof")
                ?: throw SerializationException("ZkDocument proof is required"),
            extensions = fields.extensionsExcluding(ZK_DOCUMENT_FIELDS),
        )
    }
}

private val ZK_DOCUMENT_FIELDS = setOf("documentData", "proof")

@Serializable(with = ZkDocumentDataSerializer::class)
data class ZkDocumentData(
    val docType: String,
    val zkSystemId: String,
    val timestamp: LocalDate,
    val issuerSigned: Map<String, List<ZkSignedItem>>? = null,
    val deviceSigned: Map<String, List<ZkSignedItem>>? = null,
    val msoX5chain: List<ByteArray>? = null,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(docType.isNotBlank() && zkSystemId.isNotBlank())
        require(issuerSigned == null || issuerSigned.isNotEmpty() && issuerSigned.values.all { it.isNotEmpty() })
        require(deviceSigned == null || deviceSigned.isNotEmpty() && deviceSigned.values.all { it.isNotEmpty() })
        require(msoX5chain == null || msoX5chain.isNotEmpty() && msoX5chain.all { it.isNotEmpty() })
        requireNoExtensionCollisions(extensions, ZK_DOCUMENT_DATA_FIELDS, "ZkDocumentData")
    }

    override fun equals(other: Any?): Boolean = other is ZkDocumentData &&
        docType == other.docType && zkSystemId == other.zkSystemId && timestamp == other.timestamp &&
        issuerSigned == other.issuerSigned && deviceSigned == other.deviceSigned &&
        msoX5chain.contentEqualsNested(other.msoX5chain) && extensions == other.extensions

    override fun hashCode(): Int = listOf(
        docType,
        zkSystemId,
        timestamp,
        issuerSigned,
        deviceSigned,
        msoX5chain?.contentHashCodeNested(),
        extensions,
    ).hashCode()
}

object ZkDocumentDataSerializer : KSerializer<ZkDocumentData> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    private val signedItemsSerializer = MapSerializer(String.serializer(), ListSerializer(ZkSignedItem.serializer()))

    override fun serialize(encoder: Encoder, value: ZkDocumentData) {
        val fields = linkedMapOf<String, CborElement>(
            "docType" to CborString(value.docType),
            "zkSystemId" to CborString(value.zkSystemId),
            "timestamp" to value.timestamp.toCborElement(LocalDate.serializer()),
        )
        value.issuerSigned?.let { fields["issuerSigned"] = it.toCborElement(signedItemsSerializer) }
        value.deviceSigned?.let { fields["deviceSigned"] = it.toCborElement(signedItemsSerializer) }
        value.msoX5chain?.let { certificates ->
            fields["msoX5chain"] = CborArray(certificates.map(::CborByteString))
        }
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): ZkDocumentData {
        val fields = decoder.decodeTextMap("ZkDocumentData")
        return ZkDocumentData(
            docType = (fields["docType"] as? CborString)?.value
                ?: throw SerializationException("ZkDocumentData docType is required and must be text"),
            zkSystemId = (fields["zkSystemId"] as? CborString)?.value
                ?: throw SerializationException("ZkDocumentData zkSystemId is required and must be text"),
            timestamp = fields["timestamp"]?.fromCborElement(LocalDate.serializer())
                ?: throw SerializationException("ZkDocumentData timestamp is required"),
            issuerSigned = fields["issuerSigned"]?.fromCborElement(signedItemsSerializer),
            deviceSigned = fields["deviceSigned"]?.fromCborElement(signedItemsSerializer),
            msoX5chain = fields["msoX5chain"]?.asByteArrayList("ZkDocumentData msoX5chain"),
            extensions = fields.extensionsExcluding(ZK_DOCUMENT_DATA_FIELDS),
        )
    }
}

private val ZK_DOCUMENT_DATA_FIELDS = setOf(
    "docType", "zkSystemId", "timestamp", "issuerSigned", "deviceSigned", "msoX5chain",
)

@Serializable(with = ZkSignedItemSerializer::class)
data class ZkSignedItem(
    val elementIdentifier: String,
    val elementValue: CborElement,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(elementIdentifier.isNotBlank())
        requireNoExtensionCollisions(extensions, ZK_SIGNED_ITEM_FIELDS, "ZkSignedItem")
    }
}

object ZkSignedItemSerializer : KSerializer<ZkSignedItem> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ZkSignedItem) {
        val fields = linkedMapOf<String, CborElement>(
            "elementIdentifier" to CborString(value.elementIdentifier),
            "elementValue" to value.elementValue,
        )
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): ZkSignedItem {
        val fields = decoder.decodeTextMap("ZkSignedItem")
        return ZkSignedItem(
            elementIdentifier = (fields["elementIdentifier"] as? CborString)?.value
                ?: throw SerializationException("ZkSignedItem elementIdentifier is required and must be text"),
            elementValue = fields["elementValue"]
                ?: throw SerializationException("ZkSignedItem elementValue is required"),
            extensions = fields.extensionsExcluding(ZK_SIGNED_ITEM_FIELDS),
        )
    }
}

private val ZK_SIGNED_ITEM_FIELDS = setOf("elementIdentifier", "elementValue")

private fun CborElement.asByteArray(fieldName: String): ByteArray =
    (this as? CborByteString)?.toByteArray()
        ?: throw SerializationException("$fieldName must be a byte string")

private fun CborElement.asByteArrayList(fieldName: String): List<ByteArray> =
    (this as? CborArray)?.map { it.asByteArray(fieldName) }
        ?: throw SerializationException("$fieldName must be an array of byte strings")

@Serializable
data class EncryptedDocuments(
    @ByteString val enc: ByteArray,
    @SerialName("cipherText") @ByteString val cipherText: ByteArray,
    val docRequestID: UInt,
) {
    init {
        require(enc.isNotEmpty() && cipherText.isNotEmpty()) { "Encrypted document fields must not be empty" }
    }

    override fun equals(other: Any?): Boolean = other is EncryptedDocuments &&
        enc.contentEquals(other.enc) && cipherText.contentEquals(other.cipherText) && docRequestID == other.docRequestID

    override fun hashCode(): Int = listOf(enc.contentHashCode(), cipherText.contentHashCode(), docRequestID).hashCode()
}

@Serializable(with = EncryptedDocumentsPlaintextSerializer::class)
data class EncryptedDocumentsPlaintext(
    val documents: List<Document>? = null,
    val zkDocuments: List<ZkDocument>? = null,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(documents == null || documents.isNotEmpty())
        require(zkDocuments == null || zkDocuments.isNotEmpty())
        require(documents != null || zkDocuments != null) {
            "EncryptedDocumentsPlaintext must contain at least one document"
        }
        require(documents.orEmpty().all { it.deviceSigned != null }) {
            "Every response Document must contain deviceSigned"
        }
        requireNoExtensionCollisions(
            extensions,
            ENCRYPTED_DOCUMENTS_PLAINTEXT_FIELDS,
            "EncryptedDocumentsPlaintext",
        )
    }
}

object EncryptedDocumentsPlaintextSerializer : KSerializer<EncryptedDocumentsPlaintext> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: EncryptedDocumentsPlaintext) {
        val fields = linkedMapOf<String, CborElement>()
        value.documents?.let {
            fields["documents"] = it.toCborElement(
                kotlinx.serialization.builtins.ListSerializer(Document.serializer())
            )
        }
        value.zkDocuments?.let {
            fields["zkDocuments"] = it.toCborElement(
                kotlinx.serialization.builtins.ListSerializer(ZkDocument.serializer())
            )
        }
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): EncryptedDocumentsPlaintext {
        val fields = decoder.decodeTextMap("EncryptedDocumentsPlaintext")
        return EncryptedDocumentsPlaintext(
            documents = fields["documents"]?.fromCborElement(
                kotlinx.serialization.builtins.ListSerializer(Document.serializer())
            ),
            zkDocuments = fields["zkDocuments"]?.fromCborElement(
                kotlinx.serialization.builtins.ListSerializer(ZkDocument.serializer())
            ),
            extensions = fields.extensionsExcluding(ENCRYPTED_DOCUMENTS_PLAINTEXT_FIELDS),
        )
    }
}

private val ENCRYPTED_DOCUMENTS_PLAINTEXT_FIELDS = setOf("documents", "zkDocuments")

private fun List<ByteArray>?.contentEqualsNested(other: List<ByteArray>?): Boolean = when {
    this == null -> other == null
    other == null || size != other.size -> false
    else -> zip(other).all { (left, right) -> left.contentEquals(right) }
}

private fun List<ByteArray>.contentHashCodeNested(): Int = fold(1) { value, bytes ->
    31 * value + bytes.contentHashCode()
}
