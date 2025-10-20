package id.walt.credentials.signatures

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.jwk.JWKKey.Companion.convertX5cToPemCertificate
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*

interface JwtBasedSignature {
    val jwtHeader: JsonObject?

    companion object {
        private val log = KotlinLogging.logger { }
    }

    /**
     * Get issuer key from credential data or jwt header
     */
    suspend fun getJwtBasedIssuer(credentialData: JsonObject): Key? {
        val issuer = credentialData["iss"] ?: credentialData["issuer"]
        val issuerId = when (issuer) {
            is JsonObject -> issuer.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            is JsonPrimitive -> issuer.jsonPrimitive.contentOrNull
            else -> null
        }
        log.trace { "JWT based issuer retrieval: iss = $issuer" }
        val x5c = jwtHeader?.get("x5c")
        log.trace { "JWT based issuer retrieval: x5c = $x5c" }

        val key: Key? = when {
            issuerId != null && DidUtils.isDidUrl(issuerId) ->
                DidService.resolveToKeys(issuerId).getOrThrow().first()

            x5c != null -> {
                val x5cContent = x5c.jsonArray.first().jsonPrimitive.content.trim()
                val pem = if (!x5cContent.startsWith("-----BEGIN")) {
                    val pem = convertX5cToPemCertificate(x5cContent)
                    log.trace { "Converted x5c to PEM: $pem" }
                    pem
                } else x5cContent
                JWKKey.importPEM(pem).getOrThrow()
            }

            else -> null
        }

        return key
    }
}
