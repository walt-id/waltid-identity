package id.walt.statuslist.token

import id.walt.statuslist.codec.TokenStatusListCodec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.time.Instant

/**
 * Decoded JWT Token Status List claims and entries, following draft-ietf-oauth-status-list-21.
 *
 * This checks structure, not authenticity. The caller must verify the JWT signature and authorize
 * its signer before relying on these claims. Neither keys nor trust are discovered from the token.
 */
class IetfStatusListToken private constructor(
    val validity: Validity,
    val bits: Int,
    private val bytes: ByteArray,
) {
    /** Reads every referenced entry independently; negative and out-of-range indices are rejected. */
    fun readStatus(index: Int): UInt = TokenStatusListCodec.readStatus(bytes, index, bits)

    /** Signed time claims. Freshness is evaluated again whenever the caller uses the list. */
    class Validity internal constructor(
        val issuedAt: Double,
        val expiresAt: Double?,
        val notBefore: Double?,
        val ttl: Double?,
    ) {
        /**
         * [resolvedAt] is the time the signed list was retrieved, not its issuance time. A caller
         * can impose [maximumAgeSeconds] in addition to signed expiry and TTL. Fractional NumericDate
         * values are retained; expiry and TTL boundaries are exclusive.
         */
        fun isValidAt(current: Instant, resolvedAt: Instant, maximumAgeSeconds: Long? = null): Boolean {
            require(maximumAgeSeconds == null || maximumAgeSeconds > 0) { "Maximum list age must be positive" }
            val now = current.fractionalEpochSeconds()
            return issuedAt <= now &&
                (maximumAgeSeconds == null || now - issuedAt <= maximumAgeSeconds) &&
                (expiresAt == null || now < expiresAt) &&
                (notBefore == null || now >= notBefore) &&
                (ttl == null || now - resolvedAt.fractionalEpochSeconds() < ttl)
        }
    }

    companion object {
        const val DEFAULT_MAXIMUM_LIST_BYTES: Int = 16 * 1024 * 1024

        /**
         * Parses decoded JWT components. [expectedUri] must be the original status reference; the
         * signed subject must match it exactly. [maximumOutputBytes] bounds zlib decompression.
         * Throws [IllegalArgumentException] for invalid claims or unsupported encodings.
         */
        fun fromJwt(
            header: JsonObject,
            payload: JsonObject,
            expectedUri: String,
            maximumOutputBytes: Int = DEFAULT_MAXIMUM_LIST_BYTES,
        ): IetfStatusListToken {
            val type = (header["typ"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            require(type?.lowercase()?.removePrefix("application/") == "statuslist+jwt") {
                "Invalid status-list JWT type"
            }
            require((payload["sub"] as? JsonPrimitive)?.takeIf { it.isString }?.content == expectedUri) {
                "Status-list subject mismatch"
            }
            val validity = Validity(
                issuedAt = numericDate(payload, "iat", required = true)!!,
                expiresAt = numericDate(payload, "exp"),
                notBefore = numericDate(payload, "nbf"),
                ttl = numericDate(payload, "ttl"),
            )
            require(validity.expiresAt == null || validity.expiresAt > validity.issuedAt) { "Status-list expiry must follow issuance" }
            require(validity.ttl == null || validity.ttl > 0) { "Status-list TTL must be positive" }
            val list = payload["status_list"] as? JsonObject ?: throw IllegalArgumentException("Status list is missing")
            val bits = (list["bits"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            require(bits in setOf(1, 2, 4, 8)) { "Unsupported status bit width" }
            val encoded = (list["lst"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            require(!encoded.isNullOrEmpty() && encoded.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }) {
                "Status list must use unpadded base64url encoding"
            }
            val bytes = TokenStatusListCodec.decode(encoded, maximumOutputBytes)
            return IetfStatusListToken(validity, requireNotNull(bits), bytes)
        }

        private fun numericDate(payload: JsonObject, name: String, required: Boolean = false): Double? {
            val value = payload[name] ?: return if (required) throw IllegalArgumentException("Status-list $name is required") else null
            return (value as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull?.takeIf { it.isFinite() }
                ?: throw IllegalArgumentException("Status-list $name must be a finite JSON number")
        }
    }
}

private fun Instant.fractionalEpochSeconds(): Double = epochSeconds.toDouble() + nanosecondsOfSecond / 1_000_000_000.0
