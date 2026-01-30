package id.walt.openid4vci.handlers.credential

import id.walt.crypto.keys.Key
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.oid4vc.OpenID4VCI
import id.walt.openid4vci.CredentialFormat
import id.walt.oid4vc.data.CredentialFormat as Oid4vcCredentialFormat
import id.walt.oid4vc.data.ProofOfPossession
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * SD-JWT VC credential response handler using OpenID4VCI.generateSdJwtVC.
 * The issuer application must provide issuer DID, signing key, and credential data (payload).
 */
class SdJwtVcCredentialHandler : CredentialEndpointHandler {
    override suspend fun sign(
        request: CredentialRequest,
        configuration: CredentialConfiguration,
        issuerKey: Key,
        issuerId: String,
        credentialData: JsonObject,
    ): CredentialResponseResult {
        return try {
            val oid4vcFormat = when (configuration.format) {
                CredentialFormat.JWT_VC_JSON -> Oid4vcCredentialFormat.jwt_vc_json
                CredentialFormat.JWT_VC -> Oid4vcCredentialFormat.jwt_vc
                CredentialFormat.SD_JWT_VC -> Oid4vcCredentialFormat.sd_jwt_vc
                CredentialFormat.MSO_MDOC -> Oid4vcCredentialFormat.mso_mdoc
                CredentialFormat.JWT_VC_JSON_LD -> Oid4vcCredentialFormat.jwt_vc_json_ld
                CredentialFormat.LDP_VC -> Oid4vcCredentialFormat.ldp_vc
            }

            val proofJwt = request.proofs?.jwt?.firstOrNull()
                ?: return CredentialResponseResult.Failure(
                    OAuthError("invalid_request", "Missing JWT proof in proofs"),
                )
            val proof = ProofOfPossession.JWTProofBuilder(issuerId).build(proofJwt)
            val sdJwt = OpenID4VCI.generateSdJwtVC(
                credentialRequest = id.walt.oid4vc.requests.CredentialRequest(
                    format = oid4vcFormat,
                    proof = proof,
                    vct = configuration.id,
                ),
                credentialData = credentialData,
                issuerId = issuerId,
                issuerKey = issuerKey,
            )

            CredentialResponseResult.Success(
                CredentialResponse(
                    credentials = listOf(
                        IssuedCredential(credential = JsonPrimitive(sdJwt)),
                    ),
                )
            )
        } catch (e: Exception) {
            CredentialResponseResult.Failure(OAuthError("invalid_request", e.message))
        }
    }
}
