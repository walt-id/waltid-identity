package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientMetadata
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext

/**
 * Handles `decentralized_identifier` prefix per OpenID4VP 1.0, Section 5.9.3.
 */
class DecentralizedIdentifier(override val context: RequestContext, private val did: String) : ClientId {
    override suspend fun validate(): ClientValidationResult {
        // The request MUST be signed.
        val jws = context.requestObjectJws ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)

        // TODO: stub for JOSE/JWT library to get the 'kid'
        val kid = "did:example:123#1"

        // TODO: stub for a DID resolver library
        // val didDocument = resolveDid(did)
        // val publicKey = didDocument.findVerificationMethod(kid)?.publicKey
        // if (publicKey == null) return ClientValidationResult.Failure(...)

        // TODO: stub for crypto library to verify signature
        // if (!verifyJwsSignature(jws, publicKey)) { ... }

        // All Verifier metadata other than the public key MUST be obtained from client_metadata.
        return context.clientMetadataJson?.let {
            ClientValidationResult.Success(ClientMetadata(it))
        } ?: ClientValidationResult.Failure(ClientIdError.MissingClientMetadata)
    }
}
