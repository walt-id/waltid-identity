package id.waltid.openid4vp.wallet.validation

import id.walt.credentials.utils.JwtUtils.isJwt
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdPrefixAuthenticator
import id.walt.openid4vp.clientidprefix.ClientIdPrefixParser
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.openid4vp.clientidprefix.X509TrustPolicy
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

/**
 * Validates signed authorization request objects per OID4VP 1.0 §5.
 *
 * This validator enforces:
 * 1. JOSE typ header == "oauth-authz-req+jwt" (§5.3)
 * 2. Mandatory aud claim with correct value (§5.8)
 * 3. Mandatory, identical outer and inner client_id values (RFC 9101 §6.3)
 * 4. JWT signature verification via client_id prefix authentication
 * 5. wallet_nonce validation for request_uri_method=post (§5.6)
 * 6. Optional exp and nbf temporal claim validation (RFC 7519 §4.1.4, §4.1.5)
 */
object SignedRequestValidator {

    private val log = KotlinLogging.logger { }

    /**
     * Static Discovery audience value per OID4VP 1.0 §5.8.
     */
    private const val SELF_ISSUED_AUD = "https://self-issued.me/v2"

    /**
     * Required JOSE typ header value for signed authorization requests per OID4VP 1.0 §5.3.
     */
    private const val REQUIRED_TYP = "oauth-authz-req+jwt"

    private const val DEFAULT_CLOCK_SKEW_SECONDS = 60L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    /**
     * Exception thrown when signed authorization request validation fails.
     */
    class SignedRequestValidationException(
        val error: ClientIdError?,
        message: String
    ) : IllegalArgumentException(message)

    /**
     * Result of signed request validation.
     */
    sealed class ValidationResult {
        data class Success(
            val authorizationRequest: AuthorizationRequest,
            val clientMetadata: ClientMetadata?
        ) : ValidationResult()

        data class Failure(
            val error: ClientIdError?,
            val message: String
        ) : ValidationResult()
    }

    /**
     * Policy for handling unsigned (alg=none) authorization requests.
     */
    enum class UnsignedRequestObjectPolicy {
        /** Allow unsigned requests (NOT recommended for production). */
        ALLOW_UNSIGNED,
        /** Require all requests to be signed. */
        REQUIRE_SIGNED,
    }

    /**
     * Validates a signed authorization request per OID4VP 1.0 §5.
     *
     * @param requestObjectJwt The signed JWT request object.
     * @param outerClientId The mandatory client_id from the outer Authorization Request.
     * @param expectedWalletNonce If set, validates that the JWT contains this wallet_nonce claim.
     *        Required for request_uri_method=post per OID4VP 1.0 §5.6.
     * @param expectedAudience The discovery-mode-specific audience. Static Discovery uses
     *        `https://self-issued.me/v2`; Dynamic Discovery uses the discovered Wallet issuer.
     * @param unsignedPolicy Policy for handling unsigned (alg=none) requests.
     * @return ValidationResult containing the decoded AuthorizationRequest or error details.
     */
    suspend fun validate(
        requestObjectJwt: String,
        outerClientId: String,
        expectedWalletNonce: String? = null,
        expectedAudience: String = SELF_ISSUED_AUD,
        x509TrustPolicy: X509TrustPolicy? = null,
        unsignedPolicy: UnsignedRequestObjectPolicy = UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
    ): ValidationResult {
        log.trace { "Validating signed authorization request" }

        // 1. Verify it's a valid JWT
        if (!requestObjectJwt.isJwt()) {
            return ValidationResult.Failure(
                error = null,
                message = "Authorization request object must be a JWT"
            )
        }

        val decodedJws = try {
            requestObjectJwt.decodeJws()
        } catch (e: Exception) {
            return ValidationResult.Failure(
                error = null,
                message = "Failed to decode JWT: ${e.message}"
            )
        }

        // 2. Every Request Object, including an explicitly allowed unsecured one,
        // must satisfy the common OID4VP/JAR checks before algorithm branching.
        val typ = decodedJws.header["typ"]?.jsonPrimitive?.contentOrNull
        if (typ != REQUIRED_TYP) {
            return ValidationResult.Failure(
                error = null,
                message = "Invalid or missing typ header: expected '$REQUIRED_TYP', got '$typ'"
            )
        }

        // 3. Validate wallet_nonce if expected (for request_uri_method=post)
        if (expectedWalletNonce != null) {
            val receivedWalletNonce = decodedJws.payload["wallet_nonce"]?.jsonPrimitive?.contentOrNull
            if (receivedWalletNonce != expectedWalletNonce) {
                return ValidationResult.Failure(
                    error = null,
                    message = "wallet_nonce mismatch: expected '$expectedWalletNonce' but received '$receivedWalletNonce'. " +
                            "Possible replay attack."
                )
            }
            log.trace { "wallet_nonce validated successfully" }
        }

        // 4. Validate the discovery-mode-specific audience per OID4VP 1.0 §5.8.
        val aud = decodedJws.payload["aud"]?.jsonPrimitive?.contentOrNull
        if (aud == null) {
            return ValidationResult.Failure(
                error = null,
                message = "Missing required 'aud' claim in signed authorization request"
            )
        }
        if (aud != expectedAudience) {
            return ValidationResult.Failure(
                error = null,
                message = "Invalid aud claim: expected '$expectedAudience', got '$aud'"
            )
        }

        validateTemporalClaims(decodedJws.payload)?.let { message ->
            return ValidationResult.Failure(error = null, message = message)
        }

        // 5. RFC 9101 §6.3 requires outer and Request Object client_id values to match.
        val clientId = decodedJws.payload["client_id"]?.jsonPrimitive?.contentOrNull
            ?: return ValidationResult.Failure(
                error = null,
                message = "Missing client_id in signed authorization request"
            )

        if (outerClientId != clientId) {
            return ValidationResult.Failure(
                error = null,
                message = "client_id mismatch: outer request has '$outerClientId' but " +
                        "signed request object contains '$clientId'"
            )
        }

        val authRequest = runCatching {
            json.decodeFromJsonElement(AuthorizationRequest.serializer(), decodedJws.payload)
                .also { it.dcqlQuery?.precheck() }
        }.getOrElse {
            return ValidationResult.Failure(null, "Invalid Authorization Request payload: ${it.message}")
        }

        // 6. Apply the explicit policy for unsecured Request Objects only after all
        // common Request Object validation has completed.
        val jwtAlg = decodedJws.header["alg"]?.jsonPrimitive?.contentOrNull
            ?: return ValidationResult.Failure(null, "Missing required alg header")
        if (jwtAlg == "none") {
            val authenticatedPrefixRequiresSignature = clientId.substringBefore(':') in setOf(
                "x509_hash",
                "x509_san_dns",
                "decentralized_identifier",
                "verifier_attestation",
            )
            if (unsignedPolicy == UnsignedRequestObjectPolicy.REQUIRE_SIGNED || authenticatedPrefixRequiresSignature) {
                return ValidationResult.Failure(
                    error = null,
                    message = "Authorization request JWT uses alg=none — unsigned requests are not accepted for client_id '$clientId'"
                )
            }
            return ValidationResult.Success(authRequest, null)
        }

        log.trace { "Authenticating signed request with client_id: $clientId" }

        val clientIdPrefix = ClientIdPrefixParser.parse(clientId).getOrElse { e ->
            return ValidationResult.Failure(
                error = null,
                message = "Could not parse client_id prefix: $clientId - ${e.message}"
            )
        }

        val clientMetadata = decodedJws.payload["client_metadata"]?.let { element ->
            ClientMetadata.fromJson(element).getOrElse { e ->
                return ValidationResult.Failure(
                    error = null,
                    message = "Could not parse client_metadata: ${e.message}"
                )
            }
        }

        val redirectUri = decodedJws.payload["redirect_uri"]?.jsonPrimitive?.contentOrNull
        val responseUri = decodedJws.payload["response_uri"]?.jsonPrimitive?.contentOrNull

        val context = RequestContext(
            clientId = clientId,
            clientMetadata = clientMetadata,
            requestObjectJws = requestObjectJwt,
            redirectUri = redirectUri,
            responseUri = responseUri,
            x509TrustPolicy = x509TrustPolicy,
        )

        // 7. Perform client_id prefix authentication (signature verification happens here).
        return when (val result = ClientIdPrefixAuthenticator.authenticate(clientIdPrefix, context)) {
            is ClientValidationResult.Success -> {
                ValidationResult.Success(authRequest, result.clientMetadata)
            }

            is ClientValidationResult.Failure -> {
                ValidationResult.Failure(
                    error = result.error,
                    message = "Client authentication failed: ${result.error::class.simpleName} - ${result.error.message}"
                )
            }
        }
    }

    private fun validateTemporalClaims(
        payload: kotlinx.serialization.json.JsonObject,
    ): String? {
        val now = Clock.System.now().epochSeconds

        payload["exp"]?.let { claim ->
            val expiration = (claim as? JsonPrimitive)?.longOrNull
                ?: return "Invalid exp claim: expected a NumericDate"
            if (expiration < now - DEFAULT_CLOCK_SKEW_SECONDS) {
                return "Authorization request object is expired: exp=$expiration, now=$now"
            }
        }

        payload["nbf"]?.let { claim ->
            val notBefore = (claim as? JsonPrimitive)?.longOrNull
                ?: return "Invalid nbf claim: expected a NumericDate"
            if (notBefore > now + DEFAULT_CLOCK_SKEW_SECONDS) {
                return "Authorization request object is not yet valid: nbf=$notBefore, now=$now"
            }
        }

        return null
    }
}
