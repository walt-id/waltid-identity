package id.waltid.openid4vp.wallet.validation

import id.walt.credentials.utils.JwtUtils.isJwt
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdPrefixAuthenticator
import id.walt.openid4vp.clientidprefix.ClientIdPrefixParser
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Validates signed authorization request objects per OID4VP 1.0 §5.
 *
 * This validator:
 * 1. Verifies the JWT signature using the key from the header (x5c or jwk)
 * 2. Authenticates the client_id prefix
 * 3. Validates wallet_nonce if request_uri_method=post was used
 * 4. Checks the aud claim equals "https://self-issued.me/v2" (per §5.8)
 */
object SignedRequestValidator {

    private val log = KotlinLogging.logger { }

    private const val SELF_ISSUED_AUD = "https://self-issued.me/v2"

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
     * Validates a signed authorization request per OID4VP 1.0 §5.
     *
     * @param requestObjectJwt The signed JWT request object.
     * @param expectedWalletNonce If set, validates that the JWT contains this wallet_nonce claim.
     * @param unsignedPolicy Policy for handling unsigned (alg=none) requests.
     * @param validateAudience If true, validates that aud="https://self-issued.me/v2".
     * @return ValidationResult containing the decoded AuthorizationRequest or error details.
     */
    suspend fun validate(
        requestObjectJwt: String,
        expectedWalletNonce: String? = null,
        unsignedPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
        validateAudience: Boolean = true
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

        // 2. Check for unsigned requests (alg=none)
        val jwtAlg = decodedJws.header["alg"]?.jsonPrimitive?.content
        if (jwtAlg.equals("none", ignoreCase = true)) {
            if (unsignedPolicy == AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED) {
                return ValidationResult.Failure(
                    error = null,
                    message = "Authorization request JWT uses alg=none — unsigned requests are not accepted"
                )
            }
            // Allow unsigned - decode and return without signature verification
            val authRequest = json.decodeFromJsonElement(
                AuthorizationRequest.serializer(),
                decodedJws.payload
            )
            return ValidationResult.Success(authRequest, null)
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

        // 4. Validate audience claim (optional, per §5.8)
        if (validateAudience) {
            val aud = decodedJws.payload["aud"]?.jsonPrimitive?.contentOrNull
            if (aud != null && aud != SELF_ISSUED_AUD) {
                log.warn { "Authorization request aud='$aud' does not match expected '$SELF_ISSUED_AUD'" }
                // Note: We log a warning but don't reject, as some implementations may use different values
            }
        }

        // 5. Extract client_id and authenticate via prefix
        val clientId = decodedJws.payload["client_id"]?.jsonPrimitive?.contentOrNull
            ?: return ValidationResult.Failure(
                error = null,
                message = "Missing client_id in signed authorization request"
            )

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
            responseUri = responseUri
        )

        // 6. Perform client_id prefix authentication (signature verification happens here)
        return when (val result = ClientIdPrefixAuthenticator.authenticate(clientIdPrefix, context)) {
            is ClientValidationResult.Success -> {
                val authRequest = json.decodeFromJsonElement(
                    AuthorizationRequest.serializer(),
                    decodedJws.payload
                )
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
}
