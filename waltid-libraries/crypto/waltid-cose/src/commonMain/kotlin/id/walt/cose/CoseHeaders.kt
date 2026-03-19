package id.walt.cose

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.cbor.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents COSE Header Parameters.
 * See: https://www.iana.org/assignments/cose/cose.xhtml#header-parameters
 *
 * **IMPORTANT**: For COSE compliance, properties **MUST** be declared in
 * ascending order of their integer `@CborLabel`. This class adheres to that rule.
 * The library relies on `kotlinx.serialization`'s behaviour of serializing properties
 * in their declaration order.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CoseHeaders(
    /** 1: Cryptographic algorithm to use */
    @CborLabel(1) @SerialName("alg") val algorithm: Int? = null,
    /** 2: Critical headers to be understood */
    @CborLabel(2) @SerialName("crit") val criticalHeaders: List<Int>? = null,
    //@CborLabel(2) @SerialName("crit") val criticalHeaders: String? = null,
    /** 3: Content type of the payload */
    @CborLabel(3) @SerialName("content type") val contentType: CoseContentType? = null,
    /** 4: Key identifier */
    @CborLabel(4) @SerialName("kid") @ByteString val kid: ByteArray? = null,
    /** 5: Full Initialization Vector */
    @CborLabel(5) @SerialName("IV") @ByteString val iv: ByteArray? = null,
    /** 6: Partial Initialization Vector */
    @CborLabel(6) @SerialName("Partial IV") @ByteString val partialIv: ByteArray? = null,
    /** 10: Identifies the context for the key identifier (RFC 8613) */
    @CborLabel(10) @ByteString val kidContext: ByteArray? = null,
    /** 16: Content type of the complete COSE object (RFC 9596) */
    @CborLabel(16) @SerialName("typ") val type: CoseContentType? = null,
    /** 32: An unordered bag of X.509 certificates (RFC 9360) */
    //@CborLabel(32) val x5bag: List<ByteArray>? = null,
    /**
     * 33: An ordered chain of X.509 certificates (RFC 9360)
     * = array (a list) of one or more X.509 certificates - each certificate in that list is a byte string
     *
     * */
    @Serializable(CoseCertificateSerializer::class)
    @CborLabel(33) @SerialName("x5chain") val x5chain: List<CoseCertificate>? = null,
    /** 45: Hash of an X.509 certificate (RFC 9360) */
    //@CborLabel(34) @ByteString val x5t: ByteArray? = null,
    /** 35: URI pointing to an X.509 certificate (RFC 9360) */
    //@CborLabel(35) val x5u: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CoseHeaders) return false

        if (algorithm != other.algorithm) return false
        if (criticalHeaders != other.criticalHeaders) return false
        if (contentType != other.contentType) return false
        if (!kid.contentEquals(other.kid)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!partialIv.contentEquals(other.partialIv)) return false
        if (!kidContext.contentEquals(other.kidContext)) return false
        if (type != other.type) return false
        if (x5chain != other.x5chain) return false

        return true
    }

    override fun hashCode(): Int {
        var result = algorithm ?: 0
        result = 31 * result + (criticalHeaders?.hashCode() ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + (kid?.contentHashCode() ?: 0)
        result = 31 * result + (iv?.contentHashCode() ?: 0)
        result = 31 * result + (partialIv?.contentHashCode() ?: 0)
        result = 31 * result + (kidContext?.contentHashCode() ?: 0)
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + (x5chain?.hashCode() ?: 0)
        return result
    }
}


/**
 * Represents the COSE Content Type header, which can be an Int or a String.
 * This is used for both the 'content type' (3) and 'typ' (16) headers.
 */
@Serializable(with = CoseContentTypeSerializer::class)
sealed class CoseContentType {
    data class AsInt(val value: Int) : CoseContentType()
    data class AsString(val value: String) : CoseContentType()
}

@OptIn(ExperimentalSerializationApi::class)
object CoseContentTypeSerializer : KSerializer<CoseContentType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CoseContentType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CoseContentType) {
        when (value) {
            is CoseContentType.AsInt -> encoder.encodeInt(value.value)
            is CoseContentType.AsString -> encoder.encodeString(value.value)
        }
    }

    override fun deserialize(decoder: Decoder): CoseContentType {
        val element = decoder.decodeSerializableValue(CborElement.serializer())

        return when (element) {
            is CborString -> CoseContentType.AsString(element.value)
            is CborInteger -> CoseContentType.AsInt(element.long.toInt())
            else -> throw IllegalArgumentException("Expected string or int for content type, got ${element::class.simpleName}")
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
data class CoseCertificate(
    @ByteString val rawBytes: ByteArray
) {
    override fun toString(): String = "(hex) ${rawBytes.toHexString()}"

    // Override equals/hashCode for ByteArray comparison
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as CoseCertificate
        return rawBytes.contentEquals(other.rawBytes)
    }

    override fun hashCode(): Int {
        return rawBytes.contentHashCode()
    }
}

@OptIn(ExperimentalSerializationApi::class)
object CoseCertificateSerializer : KSerializer<List<CoseCertificate>> {
    override val descriptor: SerialDescriptor =
        ListSerializer(ByteArraySerializer()).descriptor

    override fun serialize(encoder: Encoder, value: List<CoseCertificate>) {
        if (value.size == 1) {
            encoder.encodeSerializableValue(CborElement.serializer(), CborByteString(value.first().rawBytes))
        } else {
            val cborArray = CborArray(value.map { CborByteString(it.rawBytes) })
            encoder.encodeSerializableValue(CborElement.serializer(), cborArray)
        }
    }

    override fun deserialize(decoder: Decoder): List<CoseCertificate> {
        val element = decoder.decodeSerializableValue(CborElement.serializer())

        return when (element) {
            is CborArray -> element.map {
                require(it is CborByteString) { "x5chain array elements must be byte strings" }
                CoseCertificate(it.value)
            }

            is CborByteString -> listOf(CoseCertificate(element.value))
            else -> throw IllegalArgumentException("Expected array or bytestring for x5chain, got ${element::class.simpleName}")
        }
    }
}
