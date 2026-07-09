package id.waltid.openid4vp.wallet.response

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handles JWE encryption for OpenID4VP authorization responses.
 *
 * Per OID4VP 1.0 §6, when `response_mode` is `direct_post.jwt` or `dc_api.jwt`,
 * the wallet MUST encrypt the authorization response using the verifier's
 * public key from `client_metadata.jwks`.
 */
object ResponseEncryptionHandler {

    private val log = KotlinLogging.logger { }

    /**
     * Configuration for encrypting an authorization response.
     *
     * @property verifierKey The verifier's public key used for encryption (from client_metadata.jwks).
     * @property encAlgorithm Content encryption algorithm (e.g., "A128GCM", "A256GCM").
     * @property algAlgorithm Key agreement algorithm, defaults to "ECDH-ES" per spec.
     */
    data class EncryptionConfig(
        val verifierKey: JWKKey,
        val encAlgorithm: String,
        val algAlgorithm: String = "ECDH-ES"
    )

    /**
     * Extracts encryption configuration from the AuthorizationRequest's client_metadata.
     *
     * Returns `null` if response_mode is not an encrypted mode (e.g., `direct_post` without JWT).
     * Throws if response_mode requires encryption but client_metadata is missing or malformed.
     *
     * @param authorizationRequest The resolved authorization request.
     * @return Encryption config if encryption is required, null otherwise.
     */
    suspend fun extractEncryptionConfig(
        authorizationRequest: AuthorizationRequest
    ): Result<EncryptionConfig?> = runCatching {
        val responseMode = authorizationRequest.responseMode
        
        // Only encrypted response modes require encryption configuration
        if (responseMode !in OpenID4VPResponseMode.ENCRYPTED_RESPONSES) {
            log.trace { "Response mode $responseMode does not require encryption" }
            return@runCatching null
        }

        log.trace { "Extracting encryption config for response_mode=$responseMode" }

        val clientMetadata = authorizationRequest.clientMetadata
            ?: throw IllegalArgumentException(
                "client_metadata is required for response_mode=$responseMode to obtain encryption keys"
            )

        // Select verifier's encryption key:
        // - Prefer a key explicitly marked for encryption (use=enc)
        // - Fall back to the first available key in JWKS
        val verifierJwkData = clientMetadata.jwks?.keys
            ?.firstOrNull { it["use"]?.jsonPrimitive?.content == "enc" }
            ?: clientMetadata.jwks?.keys?.firstOrNull()
            ?: throw IllegalArgumentException(
                "No suitable encryption key found in client_metadata.jwks"
            )

        log.trace { "Selected verifier encryption key: ${verifierJwkData["kid"]}" }

        val verifierKey = JWKKey.importJWK(verifierJwkData.toString()).getOrThrow()

        // Select content encryption algorithm
        // Default is A128GCM per OID4VP spec
        val encAlg = clientMetadata.encryptedResponseEncValuesSupported?.firstOrNull()
            ?: "A128GCM"

        log.trace { "Using encryption algorithm: alg=ECDH-ES, enc=$encAlg" }

        EncryptionConfig(
            verifierKey = verifierKey,
            encAlgorithm = encAlg
        )
    }

    /**
     * Encrypts the authorization response payload as a JWE.
     *
     * The payload is encrypted using ECDH-ES key agreement with the verifier's
     * public key, producing a compact JWE serialization.
     *
     * @param payload JSON object containing vp_token, optional id_token, and state.
     * @param config Encryption configuration extracted from client_metadata.
     * @return JWE compact serialization string.
     */
    suspend fun encryptResponse(
        payload: JsonObject,
        config: EncryptionConfig
    ): String {
        log.trace { "Encrypting authorization response payload" }
        return config.verifierKey.encryptJwe(
            payload.toString().encodeToByteArray(),
            config.encAlgorithm
        )
    }
}
