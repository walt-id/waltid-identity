package id.walt.policies2.vc.policies.status.signature

import id.walt.cose.coseCompliantCbor
import id.walt.policies2.vc.policies.status.model.StatusVerificationError
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * draft-ietf-oauth-status-list-21 §8.3: the status-list subject must be the referenced URI,
 * and `exp` / `nbf`, when present, follow RFC 7519 and RFC 8392.
 * `ttl` is a cache lifetime, not a token expiry.
 */
object IetfStatusListTokenClaims {
    fun validate(payload: JsonObject, statusListUri: String, nowEpochSeconds: Long) {
        val subject = (payload["sub"] as? JsonPrimitive)?.content
        requireSubject(subject, statusListUri)
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
        requireCurrent(claims.exp, claims.nbf, nowEpochSeconds)
    }

    private fun requireSubject(subject: String?, statusListUri: String) {
        if (subject != statusListUri) {
            throw StatusVerificationError(
                "IETF status-list token subject does not match referenced status-list URI"
            )
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

    private fun JsonObject.epochSeconds(name: String): Long? {
        if (name !in this) return null
        val primitive = this[name] as? JsonPrimitive
            ?: throw StatusVerificationError("IETF status-list token $name is not an integer time")
        return primitive.longOrNull
            ?: primitive.content.toLongOrNull()
            ?: primitive.doubleOrNull?.toLong()
            ?: throw StatusVerificationError("IETF status-list token $name is not an integer time")
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class CwtStatusClaims(
    @CborLabel(2) val sub: String? = null,
    @CborLabel(4) val exp: Long? = null,
    @CborLabel(5) val nbf: Long? = null,
)
