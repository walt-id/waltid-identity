package id.walt.policies2.vc.policies.status.signature

import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.crypto2.jose.CompactJws
import id.walt.statuslist.token.IetfStatusListToken
import kotlin.time.Instant
import id.walt.policies2.vc.policies.status.model.StatusVerificationError
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborFloat
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.cbor.longOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * draft-ietf-oauth-status-list-21 §5 and §8.3: the protected type header and `iat` are required,
 * the status-list subject must be the referenced URI, and `exp` / `nbf`, when present, follow
 * RFC 7519 and RFC 8392. `ttl` is a cache lifetime, not a token expiry.
 */
object IetfStatusListTokenClaims {
    fun validate(payload: JsonObject, statusListUri: String, nowEpochSeconds: Long) =
        validate(payload, statusListUri, Instant.fromEpochSeconds(nowEpochSeconds))

    fun validate(payload: ByteArray, statusListUri: String, nowEpochSeconds: Long) =
        validate(payload, statusListUri, Instant.fromEpochSeconds(nowEpochSeconds))

    fun validate(payload: JsonObject, statusListUri: String, now: Instant) =
        requireCurrent(jwtValidity(payload, statusListUri), now)

    fun validate(payload: ByteArray, statusListUri: String, now: Instant) =
        requireCurrent(cwtValidity(payload, statusListUri), now)

    fun jwtValidity(payload: JsonObject, statusListUri: String): IetfStatusListToken.Validity = try {
        IetfStatusListToken.Validity.fromJwt(payload, statusListUri)
    } catch (cause: IllegalArgumentException) {
        throw StatusVerificationError(cause.message ?: "Invalid status-list claims")
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun cwtValidity(payload: ByteArray, statusListUri: String): IetfStatusListToken.Validity = try {
        val claims = coseCompliantCbor.decodeFromByteArray(CborElement.serializer(), payload) as? CborMap
            ?: throw IllegalArgumentException("CWT claims are not a map")
        requireSubject((claims[CborInteger(2)] as? CborString)?.value, statusListUri)
        fun date(label: Long, name: String): Double? = claims[CborInteger(label)]?.let {
            val number = when (it) {
                is CborInteger -> it.longOrNull?.toDouble()
                is CborFloat -> it.value
                else -> null
            }
            number?.takeIf(Double::isFinite) ?: throw IllegalArgumentException("IETF status-list token $name is not a number")
        }
        val ttl = claims[CborInteger(65534)]?.let {
            (it as? CborInteger)?.longOrNull?.takeIf { value -> value > 0 }
                ?: throw IllegalArgumentException("CWT TTL must be a positive unsigned integer")
        }
        IetfStatusListToken.Validity(date(6, "iat") ?: throw IllegalArgumentException("IETF status-list token has no iat"),
            date(4, "exp"), date(5, "nbf"), ttl?.toDouble())
    } catch (cause: StatusVerificationError) {
        throw cause
    } catch (cause: Exception) {
        throw StatusVerificationError(cause.message ?: "Invalid status-list CWT claims")
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

    private fun requireCurrent(claims: IetfStatusListToken.Validity, at: Instant) {
        val now = at.epochSeconds.toDouble() + at.nanosecondsOfSecond / 1_000_000_000.0
        if (claims.notBefore?.let { now < it } == true) throw StatusVerificationError("IETF status-list token is not yet valid")
        if (claims.expiresAt?.let { now >= it } == true) throw StatusVerificationError("IETF status-list token is expired")
    }

    private const val JWT_TYPE = "statuslist+jwt"
    private const val CWT_TYPE = "application/statuslist+cwt"
    private const val TYPE_LABEL = 16L

    /** IANA CoAP content-format for `application/statuslist+cwt`. */
    private const val CWT_CONTENT_FORMAT = 279L
}
