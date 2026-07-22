package id.walt.credentials.keyresolver.resolvers

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object WellKnownKeyResolver : BaseKeyResolver {
    private val log = KotlinLogging.logger { }

    private val web = WebDataFetcher(WebDataFetcherId.WELL_KNOWN_KEY_RESOLVER)

    @Deprecated(
        "Use resolveJwkFromWellKnown for crypto2 key material",
        ReplaceWith("resolveJwkFromWellKnown(issuerId, header)"),
    )
    suspend fun resolveKeyFromWellKnown(issuerId: String, header: JsonObject?): JWKKey {
        val jwk = resolveJwkFromWellKnown(issuerId, header)
        return JWKKey.importJWK(jwk.data.toByteArray().decodeToString()).getOrThrow()
    }

    suspend fun resolveJwkFromWellKnown(issuerId: String, header: JsonObject?): EncodedKey.Jwk {
        log.debug { "Resolving issuer key via JWT VC Issuer Metadata for: $issuerId" }
        try {
            // Construct the well-known URL
            val issuerUrl = Url(issuerId)
            val wellKnownUrl = URLBuilder(issuerUrl).apply {
                encodedPath = "/.well-known/jwt-vc-issuer" + encodedPath.removeSuffix("/")
            }.buildString()

            log.debug { "Fetching metadata from: $wellKnownUrl" }
            val metadata = web.fetch<JsonObject>(wellKnownUrl).body

            // Find the JWKS (either inline or via URI)
            val jwks = when {
                "jwks_uri" in metadata -> {
                    val jwksUri = metadata["jwks_uri"]?.jsonPrimitive?.contentOrNull
                        ?: throw IllegalArgumentException("Metadata 'jwks_uri' is not a valid string.")
                    log.debug { "Fetching JWKS from: $jwksUri" }
                    web.fetch<JsonObject>(jwksUri).body
                }

                "jwks" in metadata -> metadata["jwks"]?.jsonObject
                    ?: throw IllegalArgumentException("Metadata 'jwks' is not a valid JSON object.")

                else -> throw IllegalArgumentException("Issuer metadata must contain 'jwks' or 'jwks_uri'.")
            }

            // Find the specific key using the 'kid' from the JWS header if present,
            // otherwise fall back to the first key in the JWKS.
            val keys = jwks["keys"]?.jsonArray
                ?: throw IllegalArgumentException("JWKS for issuer $issuerId contains no keys.")
            val kid = header?.get("kid")?.jsonPrimitive?.contentOrNull
            val keyJwk = if (kid != null) {
                keys.singleOrNull { it.jsonObject["kid"]?.jsonPrimitive?.contentOrNull == kid }
                    ?: throw IllegalArgumentException("Key with kid '$kid' not found in JWKS for issuer $issuerId.")
            } else {
                require(keys.size == 1) { "JWT VC issuer metadata with multiple keys must be resolved with kid: $issuerId" }
                keys.single()
            }

            return EncodedKey.Jwk(
                data = BinaryData(Json.encodeToString(keyJwk.jsonObject).encodeToByteArray()),
                privateMaterial = false,
            )
        } catch (e: Exception) {
            log.error(e) { "Failed to retrieve key via JWT VC Issuer Metadata for $issuerId." }
            throw e // Re-throw to indicate failure to the caller
        }
    }
}
