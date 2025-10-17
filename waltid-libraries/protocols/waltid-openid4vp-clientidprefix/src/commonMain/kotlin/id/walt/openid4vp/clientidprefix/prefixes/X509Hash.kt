package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.verifier.openid.models.authorization.ClientMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.kotlincrypto.hash.sha2.SHA256

/**
 * Handles `x509_hash` prefix per OpenID4VP 1.0, Section 5.9.3.
 */
@Serializable
data class X509Hash(val hash: String, override val rawValue: String) : ClientId {
    init {
        // Check if it's a valid Base64URL string, common for hashes.
        val b64UrlRegex = "^[A-Za-z0-9_-]+$".toRegex()
        require(b64UrlRegex.matches(hash)) { "Hash must be a valid Base64URL string." }
    }

    suspend fun authenticateX509Hash(clientId: X509Hash, context: RequestContext): ClientValidationResult {
        val jws = context.requestObjectJws
            ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)

        return runCatching {
            val x5cHeader = jws.decodeJws().header["x5c"]?.jsonArray
                ?: throw IllegalStateException("Missing 'x5c' header in JWS.")

            val leafCertDer = x5cHeader.first().jsonPrimitive.content.decodeFromBase64()

            // 1. Verify JWS signature.
            JWKKey.importFromDerCertificate(leafCertDer).getOrThrow().verifyJws(jws).getOrThrow()

            // 2. Calculate the certificate hash using the isolated JCA utility function.
            val calculatedHash = SHA256().digest(leafCertDer).encodeToBase64Url()

            // 3. Compare with the hash from the client_id.
            if (clientId.hash != calculatedHash) {
                throw IllegalArgumentException("Provided hash does not match certificate hash.")
            }

            val metadataJson = context.clientMetadataJson
                ?: throw IllegalStateException("client_metadata parameter is required.")

            ClientMetadata.fromJson(metadataJson).getOrThrow()
        }.fold(
            onSuccess = { ClientValidationResult.Success(it) },
            onFailure = { ClientValidationResult.Failure(ClientIdError.X509HashMismatch) }
        )
    }
}
