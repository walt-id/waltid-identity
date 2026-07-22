@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.crypto2.jose.CompactJws
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Instant

internal fun requireKbJwtType(keyBindingJwt: String) {
    val type = CompactJws.decodeUnverified(keyBindingJwt).protectedHeader["typ"]?.jsonPrimitive?.contentOrNull
    require(type == "kb+jwt") { "KB-JWT typ must be 'kb+jwt'" }
}

internal fun requireFreshKbJwtIssuedAt(
    keyBindingJwt: String,
    verificationTime: Instant,
    maxSkew: Duration,
): Double {
    val payload = CompactJws.decodeUnverified(keyBindingJwt).payload.payloadJson()
    val issuedAtPrimitive = payload["iat"]?.jsonPrimitive
        ?: throw IllegalArgumentException("KB-JWT is missing required iat claim")
    require(!issuedAtPrimitive.isString) { "KB-JWT iat claim must be numeric" }
    val issuedAt = issuedAtPrimitive.doubleOrNull
        ?: throw IllegalArgumentException("KB-JWT iat claim must be numeric")
    require(issuedAt.isFinite()) { "KB-JWT iat claim must be finite" }
    val wholeSeconds = floor(issuedAt).toLong()
    val nanoseconds = ((issuedAt - wholeSeconds) * 1_000_000_000).toLong()
    val issuedAtInstant = Instant.fromEpochSeconds(wholeSeconds, nanoseconds)
    require(issuedAtInstant >= verificationTime - maxSkew && issuedAtInstant <= verificationTime + maxSkew) {
        "KB-JWT iat is outside the accepted freshness window"
    }
    return issuedAt
}

internal fun requireSupportedSdAlgorithm(sdJwt: String) {
    val algorithm = CompactJws.decodeUnverified(sdJwt).payload.payloadJson()["_sd_alg"]?.jsonPrimitive?.contentOrNull
        ?: throw IllegalArgumentException("SD-JWT is missing required _sd_alg claim")
    require(algorithm.equals("sha-256", ignoreCase = true)) {
        "Unsupported SD-JWT disclosure hash algorithm: $algorithm"
    }
}

private fun ByteArray.payloadJson(): JsonObject =
    Json.parseToJsonElement(decodeToString(throwOnInvalidSequence = true)) as? JsonObject
        ?: throw IllegalArgumentException("JWT payload must be a JSON object")
