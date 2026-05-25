package id.walt.credentials.keyresolver

import id.walt.credentials.CredentialParser.getItAsStringOrId
import id.walt.credentials.keyresolver.resolvers.DidKeyResolver
import id.walt.credentials.keyresolver.resolvers.WellKnownKeyResolver
import id.walt.credentials.keyresolver.resolvers.X5CKeyResolver
import id.walt.crypto.keys.Key
import id.walt.did.dids.DidUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resolves the public key from a JWT header and/or payload, regardless of whether
 * the JWT represents a credential (issuer key) or a presentation (holder key).
 *
 * Priority order:
 * 1. DID — if `iss` / `issuer` is a DID URL
 * 2. x5c — if the JWT header contains an `x5c` certificate chain
 * 3. HTTPS well-known — if `iss` / `issuer` is an HTTPS URL (JWT VC Issuer Metadata)
 */
object JwtKeyResolver {

    private val log = KotlinLogging.logger { }

    suspend fun resolveFromJwt(jwtHeader: JsonObject?, jwtPayload: JsonObject): Key? {
        val signerIdentifier = (jwtPayload["iss"] ?: jwtPayload["issuer"]).getItAsStringOrId()
        log.trace { "Attempting to resolve JWT signer key for: $signerIdentifier" }

        return runCatching {
            val kid = jwtHeader?.get("kid")?.jsonPrimitive?.contentOrNull

            when {
                // 1. DID Resolution — pass kid for multi-key DID documents
                signerIdentifier != null && DidUtils.isDidUrl(signerIdentifier)
                    -> DidKeyResolver.resolveKeyFromDid(signerIdentifier, kid)

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
        }.getOrElse {
            log.debug { "Could not resolve JWT signer key: ${it.stackTraceToString()}" }
            null
        }
    }

}

/** Backward-compat alias — prefer [JwtKeyResolver]. */
@Deprecated("Renamed to JwtKeyResolver", ReplaceWith("JwtKeyResolver", "id.walt.credentials.keyresolver.JwtKeyResolver"))
typealias IssuerKeyResolver = JwtKeyResolver
