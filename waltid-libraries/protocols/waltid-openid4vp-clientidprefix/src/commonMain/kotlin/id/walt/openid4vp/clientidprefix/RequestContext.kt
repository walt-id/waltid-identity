package id.walt.openid4vp.clientidprefix

import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.x509.CertificateDer
import kotlinx.serialization.Serializable

/** Wallet-controlled policy for authenticating an X.509 Request Object chain. */
data class X509TrustPolicy(
    val trustAnchors: List<CertificateDer>,
    val enableSystemTrustAnchors: Boolean = false,
    val enableRevocation: Boolean = false,
    /** Empty means no extra ecosystem restriction; HAIP deployments use `setOf("ES256")`. */
    val allowedRequestObjectAlgorithms: Set<String> = emptySet(),
    /** HAIP requires the trust anchor to be omitted from x5c. */
    val requireTrustAnchorOmittedFromX5c: Boolean = false,
    /** HAIP forbids a self-signed Request Object signing certificate. */
    val rejectLeafTrustAnchor: Boolean = false,
) {
    init {
        require(trustAnchors.isNotEmpty() || enableSystemTrustAnchors) {
            "At least one explicit or system trust anchor source is required"
        }
        if (requireTrustAnchorOmittedFromX5c || rejectLeafTrustAnchor) {
            require(trustAnchors.isNotEmpty()) {
                "HAIP X.509 checks require explicit trust anchors"
            }
        }
    }
}

/**
 * Holds all necessary parameters from the authorization request for client ID validation.
 */
data class RequestContext(
    val clientId: String,
    val clientMetadata: ClientMetadata? = null,
    val requestObjectJws: String? = null, // The full, signed Request Object JWT
    val redirectUri: String? = null,
    val responseUri: String? = null,
    val x509TrustPolicy: X509TrustPolicy? = null,
) {
    constructor(
        clientId: String,
        clientMetadataString: String?,
        requestObjectJws: String? = null, // The full, signed Request Object JWT
        redirectUri: String? = null,
        responseUri: String? = null
    ) : this(clientId, clientMetadataString?.let { ClientMetadata.fromJson(it).getOrThrow() }, requestObjectJws, redirectUri, responseUri)
}

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
    object MissingX509TrustPolicy : ClientIdError("No wallet-controlled X.509 trust policy is configured.")
    object UntrustedCertificateChain : ClientIdError("The Request Object certificate chain is not trusted.")
    object TrustAnchorIncludedInX5c : ClientIdError("The Request Object x5c contains a configured trust anchor.")
    object SelfSignedLeafCertificate : ClientIdError("The Request Object signing certificate must not be self-signed.")

    @Serializable
    data class UnsupportedRequestObjectAlgorithm(val algorithm: String?) :
        ClientIdError("Request Object algorithm is not allowed by the wallet trust policy: $algorithm")

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
    data class SanDnsMismatch(val clientIdDnsName: String, val certificateDnsNames: List<String>) :
        ClientIdError("The client_id DNS name does not match any dNSName SAN in the certificate: Client ID DNS name '${clientIdDnsName}' not found in certificate SANs ($certificateDnsNames).")
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
