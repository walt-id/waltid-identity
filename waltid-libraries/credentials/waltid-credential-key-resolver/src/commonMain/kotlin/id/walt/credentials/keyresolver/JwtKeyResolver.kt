package id.walt.credentials.keyresolver

import id.walt.credentials.keyresolver.resolvers.DidKeyResolver
import id.walt.credentials.keyresolver.resolvers.WellKnownKeyResolver
import id.walt.credentials.keyresolver.resolvers.X5CKeyResolver
import id.walt.crypto.keys.Key
import id.walt.did.dids.DidUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resolves the public key from a JWT header and/or payload, regardless of whether
 * the JWT represents a credential (issuer key) or a presentation (holder key).
 *
 * Priority order:
 * 1. DID - if `iss` / `issuer` is a DID URL
 * 2. x5c - if the JWT header contains an `x5c` certificate chain
 * 3. HTTPS well-known - if `iss` / `issuer` is an HTTPS URL (JWT VC Issuer Metadata)
 */
object JwtKeyResolver {

    private val log = KotlinLogging.logger { }

    @Deprecated(
        "Use Crypto2JwtKeyResolver",
        ReplaceWith(
            "Crypto2JwtKeyResolver().resolveFromJwt(jwtHeader, jwtPayload)?.key",
            "id.walt.credentials.keyresolver.Crypto2JwtKeyResolver",
        ),
    )
    suspend fun resolveFromJwt(jwtHeader: JsonObject?, jwtPayload: JsonObject): Key? {
        val signerIdentifier = extractSignerIdentifier(jwtPayload)
        log.trace { "Attempting to resolve JWT signer key for: $signerIdentifier" }

        return try {
            val kid = jwtHeader?.get("kid")?.jsonPrimitive?.contentOrNull

            when {
                // 1. DID Resolution - pass kid for multi-key DID documents.
                signerIdentifier != null && DidUtils.isDidUrl(signerIdentifier) ->
                    DidKeyResolver.resolveKeyFromDid(signerIdentifier, kid)

                // 2. Inline X.509 Certificate Chain
                jwtHeader?.contains("x5c") == true
                    -> X5CKeyResolver.resolveKeyFromX5c(jwtHeader["x5c"]!!.jsonArray)

                // 3. HTTPS well-known JWT VC Issuer Metadata
                signerIdentifier != null && signerIdentifier.startsWith("https://")
                    -> WellKnownKeyResolver.resolveKeyFromWellKnown(signerIdentifier, jwtHeader)

                else -> {
                    log.warn { "No supported key resolution method found for JWT signer: $signerIdentifier" }
                    null
                }
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            log.debug { "Could not resolve JWT signer key: ${cause.stackTraceToString()}" }
            null
        }
    }

    /**
     * Extracts the signer identifier from `iss` or `issuer` claim.
     * Handles plain strings and objects with an `id` field (W3C VCDM issuer object).
     */
    private fun extractSignerIdentifier(payload: JsonObject): String? =
        (payload["iss"] ?: payload["issuer"])?.let { element ->
            when (element) {
                is JsonNull -> null
                is JsonPrimitive -> element.contentOrNull
                is JsonObject -> element["id"]?.jsonPrimitive?.contentOrNull
                else -> null
            }
        }

}

/** Backward-compatibility alias for the v1 key resolver. */
@Deprecated("Use Crypto2JwtKeyResolver")
typealias IssuerKeyResolver = JwtKeyResolver
