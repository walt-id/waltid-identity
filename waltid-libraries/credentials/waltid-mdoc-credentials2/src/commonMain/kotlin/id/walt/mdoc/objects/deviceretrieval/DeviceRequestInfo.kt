@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.objects.deviceretrieval

import id.walt.mdoc.encoding.decodeTextMap
import id.walt.mdoc.encoding.encodeTextMap
import id.walt.mdoc.encoding.extensionsExcluding
import id.walt.mdoc.encoding.fromCborElement
import id.walt.mdoc.encoding.requireNoExtensionCollisions
import id.walt.mdoc.encoding.toCborElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents the DeviceRequestInfo CDDL structure.
 * CDDL: DeviceRequestInfo = { ? "useCases" : [+UseCase], * tstr => any }
 */
@Serializable(with = DeviceRequestInfoSerializer::class)
data class DeviceRequestInfo(
    @SerialName("useCases")
    val useCases: List<UseCase>? = null,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(useCases == null || useCases.isNotEmpty()) {
            "\"useCases\" must be non-empty when provided."
        }
        require(useCases != null || extensions.isNotEmpty()) { "DeviceRequestInfo must contain at least one field" }
        require("useCases" !in extensions) { "DeviceRequestInfo extension collides with useCases" }
    }
}

object DeviceRequestInfoSerializer : KSerializer<DeviceRequestInfo> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: DeviceRequestInfo) {
        val fields = linkedMapOf<String, CborElement>()
        value.useCases?.let { fields["useCases"] = it.toCborElement(ListSerializer(UseCase.serializer())) }
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): DeviceRequestInfo {
        val fields = decoder.decodeTextMap("DeviceRequestInfo")
        return DeviceRequestInfo(
            useCases = fields["useCases"]?.fromCborElement(ListSerializer(UseCase.serializer())),
            extensions = fields.extensionsExcluding(DEVICE_REQUEST_INFO_FIELDS),
        )
    }
}

private val DEVICE_REQUEST_INFO_FIELDS = setOf("useCases")

/**
 * Represents the UseCase CDDL structure.
 * CDDL: UseCase = { "mandatory": boolean, ? "purposeHints": {+ Type => any}, "documentSets": [+ DocumentSet] }
 */
@Serializable(with = UseCaseSerializer::class)
data class UseCase(
    @SerialName("mandatory")
    val mandatory: Boolean,

    @SerialName("purposeHints")
    val purposeHints: Map<String, Int>? = null,

    // DocumentSet is defined as [+ DocRequestID] where DocRequestID = uint.
    // Thus, [+ DocumentSet] becomes a List of Lists of UInts.
    @SerialName("documentSets")
    val documentSets: List<List<UInt>>,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(documentSets.isNotEmpty() && documentSets.all { it.isNotEmpty() }) {
            "UseCase document sets must not be empty"
        }
        require(documentSets.all { set -> set.distinct().size == set.size }) {
            "A document set cannot repeat a DocRequest identifier"
        }
        require(documentSets.distinct().size == documentSets.size) {
            "A use case cannot repeat a document set"
        }
        require(purposeHints == null || purposeHints.isNotEmpty())
        require(purposeHints.orEmpty().keys.all { it.isNotBlank() })
        requireNoExtensionCollisions(extensions, USE_CASE_FIELDS, "UseCase")
    }

    override fun toString(): String {
        return "[DeviceRequestInfo UseCase] mandatory=$mandatory, documentSets=$documentSets, purposeHints=$purposeHints"
    }
}

object UseCaseSerializer : KSerializer<UseCase> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: UseCase) {
        val fields = linkedMapOf(
            "mandatory" to value.mandatory.toCborElement(Boolean.serializer()),
            "documentSets" to value.documentSets.toCborElement(
                ListSerializer(ListSerializer(UInt.serializer()))
            ),
        )
        value.purposeHints?.let {
            fields["purposeHints"] = it.toCborElement(
                kotlinx.serialization.builtins.MapSerializer(String.serializer(), Int.serializer())
            )
        }
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): UseCase {
        val fields = decoder.decodeTextMap("UseCase")
        return UseCase(
            mandatory = fields["mandatory"]?.fromCborElement(Boolean.serializer())
                ?: throw SerializationException("UseCase mandatory is required"),
            purposeHints = fields["purposeHints"]?.fromCborElement(
                kotlinx.serialization.builtins.MapSerializer(String.serializer(), Int.serializer())
            ),
            documentSets = fields["documentSets"]?.fromCborElement(
                ListSerializer(ListSerializer(UInt.serializer()))
            ) ?: throw SerializationException("UseCase documentSets is required"),
            extensions = fields.extensionsExcluding(USE_CASE_FIELDS),
        )
    }
}

private val USE_CASE_FIELDS = setOf("mandatory", "purposeHints", "documentSets")
