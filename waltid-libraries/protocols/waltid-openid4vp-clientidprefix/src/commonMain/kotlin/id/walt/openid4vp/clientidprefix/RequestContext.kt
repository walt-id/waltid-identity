package id.walt.openid4vp.clientidprefix

import id.walt.verifier.openid.models.authorization.ClientMetadata
import kotlinx.serialization.Serializable

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
 * A sealed class representing all possible validation errors for clear, type-safe error handling.
 */
@Serializable
sealed class ClientIdError(val message: String) {
    object MissingRequestObject : ClientIdError("Signed request object is required but was not provided.")
    object InvalidSignature : ClientIdError("Request object signature validation failed.")
    object DoesNotSupportSignature : ClientIdError("This client id prefix does not support signatures.")
    object InvalidJws : ClientIdError("JWS cannot be parsed.")
    object MissingX5cHeader : ClientIdError("Missing 'x5c' header in JWS.")
    object EmptyX5cHeader : ClientIdError("Empty 'x5c' header in JWS.")
    object MissingClientMetadata : ClientIdError("client_metadata parameter is required for this prefix but was not provided.")
    object CannotExtractSanDnsNamesFromDer : ClientIdError("Could not extract SAN dNSNames from DER (leaf cert DER of x5c header).")
    object X509HashMismatch : ClientIdError("The client_id hash does not match the hash of the provided certificate.")
    @Serializable
    data class DidResolutionFailed(val reason: String) : ClientIdError("DID resolution failed: $reason")
    @Serializable
    data class AttestationError(val reason: String) : ClientIdError("Verifier Attestation JWT is invalid: $reason")
    @Serializable
    data class FederationError(val reason: String) : ClientIdError("OpenID Federation trust chain resolution failed: $reason")
    @Serializable
    data class PreRegisteredClientNotFound(val id: String) : ClientIdError("Pre-registered client '$id' not found.")
    @Serializable
    data class UnsupportedPrefix(val prefix: String) : ClientIdError("Client ID prefix '$prefix' is not supported.")
    @Serializable
    data class InvalidMetadata(val reason: String) : ClientIdError("Client metadata is invalid: $reason")
    @Serializable
    data class SanDnsMismatch(val clientIdDnsName: String, val certificateDnsNames: List<String>) : ClientIdError("The client_id DNS name does not match any dNSName SAN in the certificate: Client ID DNS name '${clientIdDnsName}' not found in certificate SANs ($certificateDnsNames).")
}

/**
 * Represents the result of the client ID processing and validation.
 */
@Serializable
sealed class ClientValidationResult {
    @Serializable
    data class Success(val clientMetadata: ClientMetadata) : ClientValidationResult()
    @Serializable
    data class Failure(val error: ClientIdError) : ClientValidationResult()
}
