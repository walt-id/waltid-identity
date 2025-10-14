package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.openid4vp.clientidprefix.ClientMetadata
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext

/**
 * Handles `openid_federation` prefix per OpenID4VP 1.0, Section 5.9.3. 
 */
class OpenIdFederation(override val context: RequestContext, private val entityId: String) : ClientId {
    override suspend fun validate(): ClientValidationResult {
        // The `client_metadata` parameter, if present, MUST be ignored. 
        // The final metadata is obtained from resolving the trust chain. 

        // TODO: Use a full-featured OpenID Federation library to resolve the trust chain.
        // This is a complex process involving:
        // 1. Fetching the entity statement for `entityId`.
        // 2. Recursively fetching entity statements from trust anchors.
        // 3. Applying policies to produce the final, trusted metadata.
        // val resolvedMetadataJson = federationResolver.resolve(entityId, context.trust_chain)
        // if (resolvedMetadataJson == null) {
        //    return ClientValidationResult.Failure(ClientIdError.FederationError("Trust chain resolution failed."))
        // }

        val resolvedMetadataJson = "{ \"client_name\": \"Federated Client\" }" // Stub

        // If the request is signed, the signature MUST be validated against keys in the resolved metadata.
        if (context.requestObjectJws != null) {
            // TODO: Use JOSE/JWT library to verify signature against keys from `resolvedMetadataJson`.
            // if (!verifyJwsSignatureWithMetadata(context.requestObjectJws, resolvedMetadataJson)) { ... }
        }

        return ClientValidationResult.Success(ClientMetadata(resolvedMetadataJson))
    }
}
