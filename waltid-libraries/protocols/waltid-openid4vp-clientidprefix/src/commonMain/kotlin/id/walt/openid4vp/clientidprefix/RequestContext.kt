package id.walt.openid4vp.clientidprefix

/**
 * Holds all necessary parameters from the authorization request for client ID validation.
 */
data class RequestContext(
    val clientId: String,
    val clientMetadataJson: String? = null,
    val requestObjectJws: String? = null, // The full, signed Request Object JWT
    val redirectUri: String? = null,
    val responseUri: String? = null
)

/**
 * Represents the successful outcome of client validation.
 */
data class ClientMetadata(val rawJson: String)

/**
 * A sealed class representing all possible validation errors for clear, type-safe error handling.
 */
sealed class ClientIdError(val message: String) {
    object MissingRequestObject : ClientIdError("Signed request object is required but was not provided.")
    object InvalidSignature : ClientIdError("Request object signature validation failed.")
    object MissingClientMetadata : ClientIdError("client_metadata parameter is required for this prefix but was not provided.")
    object SanDnsMismatch : ClientIdError("The client_id DNS name does not match any dNSName SAN in the certificate.")
    object X509HashMismatch : ClientIdError("The client_id hash does not match the hash of the provided certificate.")
    data class DidResolutionFailed(val reason: String) : ClientIdError("DID resolution failed: $reason")
    data class AttestationError(val reason: String) : ClientIdError("Verifier Attestation JWT is invalid: $reason")
    data class FederationError(val reason: String) : ClientIdError("OpenID Federation trust chain resolution failed: $reason")
    data class PreRegisteredClientNotFound(val id: String) : ClientIdError("Pre-registered client '$id' not found.")
    data class UnsupportedPrefix(val prefix: String) : ClientIdError("Client ID prefix '$prefix' is not supported.")
}

/**
 * Represents the result of the client ID processing and validation.
 */
sealed class ClientValidationResult {
    data class Success(val clientMetadata: ClientMetadata) : ClientValidationResult()
    data class Failure(val error: ClientIdError) : ClientValidationResult()
}
