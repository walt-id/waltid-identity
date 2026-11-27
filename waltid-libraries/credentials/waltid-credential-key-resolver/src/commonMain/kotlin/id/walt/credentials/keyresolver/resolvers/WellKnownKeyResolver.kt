package id.walt.credentials.keyresolver.resolvers

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import io.ktor.client.plugins.expectSuccess
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.encodedPath
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object WellKnownKeyResolver : BaseKeyResolver {
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
        // Construct the well-known URL
        val issuerUrl = Url(issuerId)
        val wellKnownUrl = URLBuilder(issuerUrl).apply {
            encodedPath = "/.well-known/jwt-vc-issuer" + encodedPath.removeSuffix("/")
        }.build()

        val metadata = fetchDocument(wellKnownUrl)

        // Find the JWKS (either inline or via URI)
        val jwks = when {
            "jwks_uri" in metadata -> {
                val jwksUri = metadata["jwks_uri"]?.jsonPrimitive?.contentOrNull
                    ?: throw IllegalArgumentException("Metadata 'jwks_uri' is not a valid string.")

                fetchDocument(keySetRetrievalUrl(issuerUrl, jwksUri))
            }

            "jwks" in metadata -> metadata["jwks"]?.jsonObject
                ?: throw IllegalArgumentException("Metadata 'jwks' is not a valid JSON object.")

            else -> throw IllegalArgumentException("Issuer metadata must contain 'jwks' or 'jwks_uri'.")
        }

        // Find the specific key using the 'kid' from the JWS header if present,
        // an unqualified key set must contain exactly one key.
        val keys = jwks["keys"]?.jsonArray
            ?: throw IllegalArgumentException("JWKS contains no keys.")
        val kid = header?.get("kid")?.jsonPrimitive?.contentOrNull
        val keyJwk = if (kid != null) {
            keys.singleOrNull { it.jsonObject["kid"]?.jsonPrimitive?.contentOrNull == kid }
                ?: throw IllegalArgumentException("Key with kid '$kid' not found in JWKS.")
        } else {
            require(keys.size == 1) { "JWT VC issuer metadata with multiple keys must be resolved with kid" }
            keys.single()
        }

        return EncodedKey.Jwk(
            data = BinaryData(Json.encodeToString(keyJwk.jsonObject).encodeToByteArray()),
            privateMaterial = false,
        )
    }

    private suspend fun fetchDocument(url: Url): JsonObject = try {
        web.fetch<JsonObject>(url) { expectSuccess = true }.body
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Exception) {
        // Transport and decoding errors can include the full retrieval URL or response body.
        throw IllegalArgumentException("Could not retrieve issuer key document")
    }
}

internal fun keySetRetrievalUrl(issuer: Url, location: String): Url {
    val keySet = try {
        Url(location)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Issuer metadata contains an invalid key-set URL")
    }
    require(issuer.protocol != URLProtocol.HTTPS || keySet.protocol == URLProtocol.HTTPS) {
        "An HTTPS issuer must reference an HTTPS key set"
    }
    return keySet
}
