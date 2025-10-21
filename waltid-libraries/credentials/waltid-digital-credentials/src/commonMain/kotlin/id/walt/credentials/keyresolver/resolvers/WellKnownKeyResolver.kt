package id.walt.credentials.keyresolver.resolvers

import id.walt.crypto.keys.jwk.JWKKey
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*

object WellKnownKeyResolver : BaseKeyResolver {
    private val log = KotlinLogging.logger { }

    private val httpClient = HttpClient()

    suspend fun resolveKeyFromWellKnown(issuerId: String, header: JsonObject?): JWKKey {
        log.debug { "Resolving issuer key via JWT VC Issuer Metadata for: $issuerId" }
        try {
            // Construct the well-known URL
            val issuerUrl = Url(issuerId)
            val wellKnownUrl = URLBuilder(issuerUrl).apply {
                encodedPath = "/.well-known/jwt-vc-issuer" + encodedPath.removeSuffix("/")
            }.buildString()

            log.debug { "Fetching metadata from: $wellKnownUrl" }
            val metadata = httpClient.get(wellKnownUrl).body<JsonObject>()

            // Find the JWKS (either inline or via URI)
            val jwks = when {
                "jwks_uri" in metadata -> {
                    val jwksUri = metadata["jwks_uri"]?.jsonPrimitive?.contentOrNull
                        ?: throw IllegalArgumentException("Metadata 'jwks_uri' is not a valid string.")
                    log.debug { "Fetching JWKS from: $jwksUri" }
                    httpClient.get(jwksUri).body<JsonObject>()
                }
                "jwks" in metadata -> metadata["jwks"]?.jsonObject
                    ?: throw IllegalArgumentException("Metadata 'jwks' is not a valid JSON object.")
                else -> throw IllegalArgumentException("Issuer metadata must contain 'jwks' or 'jwks_uri'.")
            }

            // Find the specific key using the 'kid' from the JWS header
            val kid = header?.get("kid")?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("JWS header must have a 'kid' to find the key in the JWKS.")

            val keyJwk = jwks["keys"]?.jsonArray?.firstOrNull { it.jsonObject["kid"]?.jsonPrimitive?.contentOrNull == kid }
                ?: throw IllegalArgumentException("Key with kid '$kid' not found in JWKS for issuer $issuerId.")

            return JWKKey.importJWK(keyJwk.jsonObject.toString()).getOrThrow()
        } catch (e: Exception) {
            log.error(e) { "Failed to retrieve key via JWT VC Issuer Metadata for $issuerId." }
            throw e // Re-throw to indicate failure to the caller
        }
    }
}
