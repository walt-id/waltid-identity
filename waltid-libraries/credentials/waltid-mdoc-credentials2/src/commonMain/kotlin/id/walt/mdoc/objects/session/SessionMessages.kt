@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.objects.session

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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.ValueTags
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** First encrypted reader-to-device message in an ISO mdoc proximity session. */
@Serializable(with = SessionEstablishmentSerializer::class)
data class SessionEstablishment(
    @SerialName("eReaderKey")
    @ValueTags(24u)
    val eReaderKey: ByteStringWrapper<CoseKey>,
    @ByteString
    val data: ByteArray,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(eReaderKey.serialized.isNotEmpty()) { "EReaderKey must retain its exact encoded COSE_Key bytes" }
        require(eReaderKey.value.d == null) { "SessionEstablishment must not contain reader private key material" }
        require(data.size >= AUTHENTICATION_TAG_BYTES) { "Encrypted session data is shorter than the AES-GCM tag" }
        requireNoExtensionCollisions(extensions, SESSION_ESTABLISHMENT_FIELDS, "SessionEstablishment")
    }

    override fun equals(other: Any?): Boolean = other is SessionEstablishment &&
        eReaderKey == other.eReaderKey && data.contentEquals(other.data) && extensions == other.extensions

    override fun hashCode(): Int = listOf(eReaderKey, data.contentHashCode(), extensions).hashCode()
}

/** Subsequent encrypted data and/or status in either direction. */
@Serializable(with = SessionDataSerializer::class)
data class SessionData(
    @ByteString
    val data: ByteArray? = null,
    val status: UInt? = null,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(data != null || status != null) { "SessionData must contain data or status" }
        require(data == null || data.size >= AUTHENTICATION_TAG_BYTES) { "Encrypted session data is shorter than the AES-GCM tag" }
        require(status !in TERMINAL_ERROR_STATUSES || data == null) {
            "Session encryption and CBOR errors cannot carry encrypted data"
        }
        requireNoExtensionCollisions(extensions, SESSION_DATA_FIELDS, "SessionData")
    }

    val statusCode: SessionStatusCode? get() = status?.let(SessionStatusCode::fromCode)

    override fun equals(other: Any?): Boolean = other is SessionData &&
        data.contentEquals(other.data) && status == other.status && extensions == other.extensions

    override fun hashCode(): Int = listOf(data?.contentHashCode(), status, extensions).hashCode()
}

object SessionEstablishmentSerializer : KSerializer<SessionEstablishment> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: SessionEstablishment) {
        val fields = linkedMapOf<String, CborElement>()
        fields["eReaderKey"] = value.eReaderKey.toTaggedByteString(CoseKey.serializer())
        fields["data"] = value.data.toCborElement(ByteArraySerializer())
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): SessionEstablishment {
        val fields = decoder.decodeTextMap("SessionEstablishment")
        return SessionEstablishment(
            eReaderKey = fields["eReaderKey"]?.fromTaggedByteString(CoseKey.serializer(), "EReaderKeyBytes")
                ?: throw SerializationException("SessionEstablishment eReaderKey is required"),
            data = fields["data"]?.fromCborElement(ByteArraySerializer())
                ?: throw SerializationException("SessionEstablishment data is required"),
            extensions = fields.extensionsExcluding(SESSION_ESTABLISHMENT_FIELDS),
        )
    }
}

object SessionDataSerializer : KSerializer<SessionData> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: SessionData) {
        val fields = linkedMapOf<String, CborElement>()
        value.data?.let { fields["data"] = it.toCborElement(ByteArraySerializer()) }
        value.status?.let { fields["status"] = it.toCborElement(UInt.serializer()) }
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): SessionData {
        val fields = decoder.decodeTextMap("SessionData")
        return SessionData(
            data = fields["data"]?.fromCborElement(ByteArraySerializer()),
            status = fields["status"]?.fromCborElement(UInt.serializer()),
            extensions = fields.extensionsExcluding(SESSION_DATA_FIELDS),
        )
    }
}

enum class SessionStatusCode(val code: UInt, val terminal: Boolean) {
    SESSION_ENCRYPTION_ERROR(10u, true),
    CBOR_DECODING_ERROR(11u, true),
    SESSION_TERMINATION(20u, true),
    ;

    companion object {
        fun fromCode(code: UInt): SessionStatusCode? = entries.firstOrNull { it.code == code }
    }
}

const val AUTHENTICATION_TAG_BYTES: Int = 16
private val SESSION_ESTABLISHMENT_FIELDS = setOf("eReaderKey", "data")
private val SESSION_DATA_FIELDS = setOf("data", "status")
private val TERMINAL_ERROR_STATUSES = setOf(
    SessionStatusCode.SESSION_ENCRYPTION_ERROR.code,
    SessionStatusCode.CBOR_DECODING_ERROR.code,
)
