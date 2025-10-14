package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientMetadata
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext

/**
 * Handles `verifier_attestation` prefix per OpenID4VP 1.0, Section 5.9.3 and 12.
 */
class VerifierAttestation(override val context: RequestContext, private val sub: String) : ClientId {
    override suspend fun validate(): ClientValidationResult {
        // The request MUST be signed.
        val jws = context.requestObjectJws ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)

        // The Verifier attestation JWT MUST be added to the 'jwt' JOSE Header of the request object.
        // TODO: Use a JOSE/JWT library to get the 'jwt' header.
        // val attestationJwt: String = parseJwsHeader(jws, "jwt")
        val attestationJwt = "eyJhbGci..." // Stub

        // TODO: Use a JOSE/JWT library to parse and validate the attestation JWT.
        // 1. The Wallet MUST validate the signature on the Verifier attestation JWT.
        //    (This requires a pre-trusted issuer key).
        // 2. The `iss` claim MUST identify a party the Wallet trusts.
        // 3. The `sub` claim in the attestation MUST equal the `id` from the client_id.
        // 4. Extract the public key from the `cnf` (confirmation) claim.
        // val attestationClaims = verifyAndParseAttestation(attestationJwt, trustedIssuerKeys)
        // if (attestationClaims.sub != sub) {
        //     return ClientValidationResult.Failure(ClientIdError.AttestationError("Subject mismatch"))
        // }
        // val publicKeyFromCnf = attestationClaims.cnf.publicKey

        // TODO: Verify the signature of the main request object (JWS) using the public key from the `cnf` claim.
        // This serves as proof of possession of the key bound to the attestation.
        // if (!verifyJwsSignature(jws, publicKeyFromCnf)) {
        //     return ClientValidationResult.Failure(ClientIdError.InvalidSignature)
        // }

        // All Verifier metadata MUST be obtained from the client_metadata parameter.
        return context.clientMetadataJson?.let {
            ClientValidationResult.Success(ClientMetadata(it))
        } ?: ClientValidationResult.Failure(ClientIdError.MissingClientMetadata)
    }
}
