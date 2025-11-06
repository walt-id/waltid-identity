package id.walt.credentials.keyresolver

import id.walt.credentials.CredentialParser.getItAsStringOrId
import id.walt.credentials.keyresolver.resolvers.DidKeyResolver
import id.walt.credentials.keyresolver.resolvers.WellKnownKeyResolver
import id.walt.credentials.keyresolver.resolvers.X5CKeyResolver
import id.walt.crypto.keys.Key
import id.walt.did.dids.DidUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

object IssuerKeyResolver {

    private val log = KotlinLogging.logger { }

    suspend fun resolveForJwtSignedCredential(jwtHeader: JsonObject?, credentialData: JsonObject): Key? {
        val issuerId = (credentialData["iss"] ?: credentialData["issuer"]).getItAsStringOrId()
        log.trace { "Attempting to resolve issuer key for: $issuerId" }

        return runCatching {
            when {
                // 1. DID Resolution
                issuerId != null && DidUtils.isDidUrl(issuerId)
                    -> DidKeyResolver.resolveKeyFromDid(issuerId)

                // 2. Inline X.509 Certificate Chain
                jwtHeader?.contains("x5c") == true
                    -> X5CKeyResolver.resolveKeyFromX5c(jwtHeader["x5c"]!!.jsonArray)

                // 3. JWT VC Issuer Metadata (for SD-JWT VC)
                issuerId != null && issuerId.startsWith("https://")
                    -> WellKnownKeyResolver.resolveKeyFromWellKnown(issuerId, jwtHeader)

                else -> {
                    log.warn { "No supported issuer key resolution method found for issuer: $issuerId" }
                    null
                }
            }
        }.getOrElse {
            log.debug { "Could not resolve signer key: ${it.stackTraceToString()}" }
            null
        }
    }

}
