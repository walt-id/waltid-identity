package id.waltid.openid4vp.wallet.response

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonCanonicalizationUtils
import id.walt.crypto.utils.ShaUtils
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*

/**
 * Handles JWE encryption for OpenID4VP authorization responses.
 *
 * Per OID4VP 1.0 §8.3, when `response_mode` is `direct_post.jwt` or `dc_api.jwt`,
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
     * The selected JWK and all negotiated values are retained together so mdoc
     * transcript generation and final JWE encryption cannot select different keys.
     */
    data class EncryptionConfig(
        val verifierJwk: JsonObject,
        val verifierKey: JWKKey,
        val keyId: String?,
        val encAlgorithm: String,
        val algAlgorithm: String,
        val verifierKeyThumbprint: String,
    )

    private const val SUPPORTED_ALG = "ECDH-ES"
    private val supportedEncAlgorithms = setOf("A128GCM", "A256GCM")

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

        val keys = clientMetadata.jwks?.keys.orEmpty()
        require(keys.isNotEmpty()) { "No encryption keys found in client_metadata.jwks" }

        val eligibleKeys = keys.filter(::isSupportedEncryptionKey).map { jwk ->
            val key = JWKKey.importJWK(jwk.toString()).getOrThrow()
            EligibleEncryptionKey(jwk, key, calculateRfc7638Thumbprint(key))
        }
        val selected = eligibleKeys.sortedWith(
            compareBy<EligibleEncryptionKey>(
                { it.thumbprint },
                { it.jwk["kid"]?.jsonPrimitive?.contentOrNull.orEmpty() },
            )
        ).firstOrNull() ?: throw IllegalArgumentException(
            "No supported encryption JWK (EC/P-256/$SUPPORTED_ALG) found in client_metadata.jwks"
        )

        val verifierJwkData = selected.jwk
        val keyId = verifierJwkData["kid"]?.jsonPrimitive?.contentOrNull
        val alg = requireNotNull(verifierJwkData["alg"]?.jsonPrimitive?.contentOrNull)
        log.trace { "Selected verifier encryption key: ${keyId ?: selected.thumbprint}" }

        // Select content encryption algorithm
        // Default is A128GCM per OID4VP spec
        val advertisedEnc = clientMetadata.encryptedResponseEncValuesSupported
        val encAlg = if (advertisedEnc == null) {
            "A128GCM"
        } else {
            require(advertisedEnc.isNotEmpty()) { "encrypted_response_enc_values_supported must not be empty" }
            listOf("A256GCM", "A128GCM").firstOrNull { it in advertisedEnc && it in supportedEncAlgorithms }
                ?: throw IllegalArgumentException("No supported content encryption algorithm was advertised")
        }

        log.trace { "Using encryption algorithm: alg=$alg, enc=$encAlg" }

        EncryptionConfig(
            verifierJwk = verifierJwkData,
            verifierKey = selected.key,
            keyId = keyId,
            encAlgorithm = encAlg,
            algAlgorithm = alg,
            verifierKeyThumbprint = selected.thumbprint,
        )
    }

    private data class EligibleEncryptionKey(
        val jwk: JsonObject,
        val key: JWKKey,
        val thumbprint: String,
    )

    // Derive the raw RFC 7638 value here because some platform crypto backends
    // expose the same digest wrapped in an RFC 9278 URN.
    private suspend fun calculateRfc7638Thumbprint(key: JWKKey): String =
        ShaUtils.calculateSha256Base64Url(
            JsonCanonicalizationUtils.convertToRequiredMembersJsonString(key)
        )

    private fun isSupportedEncryptionKey(key: JsonObject): Boolean {
        val use = key["use"]?.jsonPrimitive?.contentOrNull
        return key["alg"]?.jsonPrimitive?.contentOrNull == SUPPORTED_ALG &&
            key["kty"]?.jsonPrimitive?.contentOrNull == "EC" &&
            key["crv"]?.jsonPrimitive?.contentOrNull == "P-256" &&
            (use == null || use == "enc") &&
            key["x"]?.jsonPrimitive?.contentOrNull != null &&
            key["y"]?.jsonPrimitive?.contentOrNull != null
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
        require(config.algAlgorithm == SUPPORTED_ALG) {
            "Unsupported JWE alg ${config.algAlgorithm}"
        }
        log.trace { "Encrypting authorization response payload" }
        return config.verifierKey.encryptJwe(
            payload.toString().encodeToByteArray(),
            config.encAlgorithm
        )
    }
}
