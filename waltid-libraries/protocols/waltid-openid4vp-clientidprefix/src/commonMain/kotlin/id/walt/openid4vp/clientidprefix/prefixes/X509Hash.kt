package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientMetadata
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext

/**
 * Handles `x509_hash` prefix per OpenID4VP 1.0, Section 5.9.3.
 */
class X509Hash(override val context: RequestContext, private val hash: String) : ClientId {
    override suspend fun validate(): ClientValidationResult {
        // The request MUST be signed.
        val jws = context.requestObjectJws ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)

        // TODO: Use a JOSE/JWT library to parse the 'x5c' header and get the leaf certificate.
        // val x5c: List<String> = parseJwsHeader(jws, "x5c")
        // val leafCertDerBytes = Base64.decode(x5c.first())
        val leafCertDerBytes = ByteArray(0) // Stub for the DER-encoded certificate bytes

        // TODO: Use a cryptography library to validate the signature of the JWS using the certificate.
        // if (!verifyJwsSignature(jws, leafCertDerBytes.toPublicKey())) {
        //    return ClientValidationResult.Failure(ClientIdError.InvalidSignature)
        // }

        // TODO: Use a cryptography library to calculate the SHA-256 hash of the certificate.
        // val calculatedHash = sha256(leafCertDerBytes).toBase64Url()
        val calculatedHash = "Uvo3HtuIxuhC92rShpgqcT3YXwrqRxWEviRiA0OZszk" // Stub value from spec example

        // The value MUST match the base64url-encoded SHA-256 hash of the DER-encoded certificate.
        if (hash != calculatedHash) {
            return ClientValidationResult.Failure(ClientIdError.X509HashMismatch)
        }

        // All Verifier metadata other than the public key MUST be obtained from client_metadata.
        return context.clientMetadataJson?.let {
            ClientValidationResult.Success(ClientMetadata(it))
        } ?: ClientValidationResult.Failure(ClientIdError.MissingClientMetadata)
    }
}
