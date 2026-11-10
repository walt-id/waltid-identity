package id.walt.policies2.vc.policies.status.signature

import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.crypto2.jose.CompactJws
import id.walt.policies2.vc.policies.status.model.StatusVerificationError
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborFloat
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborNull
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.cbor.longOrNull
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * draft-ietf-oauth-status-list-21 §5 and §8.3: the protected type header and `iat` are required,
 * the status-list subject must be the referenced URI, and `exp` / `nbf`, when present, follow
 * RFC 7519 and RFC 8392. `ttl` is a cache lifetime, not a token expiry.
 */
object IetfStatusListTokenClaims {
    fun validate(payload: JsonObject, statusListUri: String, nowEpochSeconds: Long) {
        val subject = (payload["sub"] as? JsonPrimitive)?.content
        requireSubject(subject, statusListUri)
        requireIssuedAt(payload.issuedAt())
        requireCurrent(
            exp = payload.epochSeconds("exp"),
            nbf = payload.epochSeconds("nbf"),
            nowEpochSeconds = nowEpochSeconds,
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun validate(payload: ByteArray, statusListUri: String, nowEpochSeconds: Long) {
        val claims = try {
            coseCompliantCbor.decodeFromByteArray<CwtStatusClaims>(payload)
        } catch (cause: Exception) {
            throw StatusVerificationError("IETF status-list CWT claims could not be read: ${cause.message}")
        }
        requireSubject(claims.sub, statusListUri)
        requireIssuedAt(claims.iat)
        requireCurrent(claims.exp, claims.nbf, nowEpochSeconds)
    }

    /**
     * §5.1: `typ` must be `statuslist+jwt`. RFC 7515 §4.1.9 compares it case-insensitively
     * and allows the `application/` prefix.
     */
    fun requireJwtType(jwt: String) {
        val type = try {
            (CompactJws.decodeUnverified(jwt).protectedHeader["typ"] as? JsonPrimitive)?.contentOrNull
        } catch (cause: Exception) {
            throw StatusVerificationError("IETF status-list JWT header could not be read: ${cause.message}")
        }
        if (type?.lowercase()?.removePrefix("application/") != JWT_TYPE) {
            throw StatusVerificationError("IETF status-list JWT typ must be $JWT_TYPE")
        }
    }

    /**
     * §5.2: protected header 16 must be `application/statuslist+cwt` or CoAP content-format 279.
     * The text key `typ` is not that header.
     */
    @OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)
    fun requireCwtType(cwt: ByteArray) {
        val type = try {
            protectedHeader(cwt)[CborInteger(TYPE_LABEL)]
        } catch (cause: Exception) {
            throw StatusVerificationError("IETF status-list CWT protected header could not be read: ${cause.message}")
        }
        val accepted = when (type) {
            is CborString -> type.value.lowercase() == CWT_TYPE
            is CborInteger -> type.longOrNull == CWT_CONTENT_FORMAT
            else -> false
        }
        if (!accepted) {
            throw StatusVerificationError("IETF status-list CWT type must be $CWT_TYPE")
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun protectedHeader(cwt: ByteArray): CborMap {
        val header = coseCompliantCbor.decodeFromByteArray(
            CborElement.serializer(),
            CoseSign1.fromTagged(cwt).protected,
        )
        return header as? CborMap ?: throw IllegalArgumentException("protected header is not a map")
    }

    private fun requireSubject(subject: String?, statusListUri: String) {
        if (subject != statusListUri) {
            throw StatusVerificationError(
                "IETF status-list token subject does not match referenced status-list URI"
            )
        }
    }

    private fun requireIssuedAt(iat: Double?) {
        if (iat == null) {
            throw StatusVerificationError("IETF status-list token has no iat")
        }
        if (!iat.isFinite()) {
            throw StatusVerificationError("IETF status-list token iat is not a number")
        }
    }

    private fun requireCurrent(exp: Long?, nbf: Long?, nowEpochSeconds: Long) {
        if (nbf != null && nowEpochSeconds < nbf) {
            throw StatusVerificationError("IETF status-list token is not yet valid")
        }
        if (exp != null && nowEpochSeconds >= exp) {
            throw StatusVerificationError("IETF status-list token is expired")
        }
    }

    /** RFC 7519 §4.1.6: `iat` is a NumericDate, a finite JSON number. */
    private fun JsonObject.issuedAt(): Double? {
        if ("iat" !in this) return null
        val primitive = this["iat"] as? JsonPrimitive
            ?: throw StatusVerificationError("IETF status-list token iat is not a number")
        if (primitive.isString) {
            throw StatusVerificationError("IETF status-list token iat is not a number")
        }
        val number = primitive.doubleOrNull
            ?: throw StatusVerificationError("IETF status-list token iat is not a number")
        if (!number.isFinite()) {
            throw StatusVerificationError("IETF status-list token iat is not a number")
        }
        return number
    }

    private fun JsonObject.epochSeconds(name: String): Long? {
        if (name !in this) return null
        val primitive = this[name] as? JsonPrimitive
            ?: throw StatusVerificationError("IETF status-list token $name is not an integer time")
        return primitive.longOrNull
            ?: primitive.content.toLongOrNull()
            ?: primitive.doubleOrNull?.toLong()
            ?: throw StatusVerificationError("IETF status-list token $name is not an integer time")
    }

    private const val JWT_TYPE = "statuslist+jwt"
    private const val CWT_TYPE = "application/statuslist+cwt"
    private const val TYPE_LABEL = 16L

    /** IANA CoAP content-format for `application/statuslist+cwt`. */
    private const val CWT_CONTENT_FORMAT = 279L
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class CwtStatusClaims(
    @CborLabel(2) val sub: String? = null,
    @CborLabel(4) val exp: Long? = null,
    @CborLabel(5) val nbf: Long? = null,
    @Serializable(with = CwtIssuedAtSerializer::class)
    @CborLabel(6) val iat: Double? = null,
)

/**
 * RFC 8392 §4: a CWT NumericDate is an integer or a floating-point number of seconds.
 * Absent and null claims stay null so the caller can report a missing `iat`.
 */
@OptIn(ExperimentalSerializationApi::class)
private object CwtIssuedAtSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CwtIssuedAt", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Double?) {
        when {
            value == null -> encoder.encodeNull()
            value.isFinite() && value % 1.0 == 0.0 &&
                value in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble() -> encoder.encodeLong(value.toLong())
            else -> encoder.encodeDouble(value)
        }
    }

    override fun deserialize(decoder: Decoder): Double? =
        when (val element = decoder.decodeSerializableValue(CborElement.serializer())) {
            is CborNull -> null
            is CborInteger -> element.longOrNull?.toDouble()
                ?: throw SerializationException("CWT iat is not a number")
            is CborFloat -> element.value
            else -> throw SerializationException("CWT iat is not a number")
        }
}
