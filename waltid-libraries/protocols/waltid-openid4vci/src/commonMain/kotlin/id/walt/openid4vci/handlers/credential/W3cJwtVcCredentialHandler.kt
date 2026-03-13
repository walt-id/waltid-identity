package id.walt.openid4vci.handlers.credential

import id.walt.crypto.keys.Key
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.DisplayProperties
import id.walt.oid4vc.data.CredentialFormat as Oid4vcCredentialFormat
import id.walt.oid4vc.data.ProofOfPossession
import id.walt.sdjwt.SDMap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * W3C JWT VC credential response handler using OpenID4VCI.generateW3CJwtVC.
 * Supports JWT VC formats (jwt_vc_json, jwt_vc).
 */
class W3cJwtVcCredentialHandler : CredentialEndpointHandler {
    override suspend fun sign(
        request: CredentialRequest,
        configuration: CredentialConfiguration,
        issuerKey: Key,
        issuerId: String,
        credentialData: JsonObject,
        dataMapping: JsonObject?,
        selectiveDisclosure: SDMap?,
        x5Chain: List<String>?,
        display: List<DisplayProperties>?,
    ): CredentialResponseResult {
        return try {
            val oid4vcFormat = when (configuration.format) {
                CredentialFormat.JWT_VC_JSON -> Oid4vcCredentialFormat.jwt_vc_json
                CredentialFormat.JWT_VC -> Oid4vcCredentialFormat.jwt_vc
                else -> {
                    return CredentialResponseResult.Failure(
                        OAuthError("unsupported_credential_configuration", "Unsupported format ${configuration.format.value}")
                    )
                }
            }

            val proofJwt = request.proofs?.jwt?.firstOrNull()
                ?: return CredentialResponseResult.Failure(
                    OAuthError("invalid_request", "Missing JWT proof in proofs"),
                )
            val proof = ProofOfPossession.JWTProofBuilder(issuerId).build(proofJwt)

            val jwtVc = OpenID4VCI.generateW3CJwtVC(
                credentialRequest = id.walt.oid4vc.requests.CredentialRequest(
                    format = oid4vcFormat,
                    proof = proof,
                ),
                credentialData = credentialData,
                issuerId = issuerId,
                issuerKey = issuerKey,
                selectiveDisclosure = selectiveDisclosure,
                dataMapping = dataMapping,
                x5Chain = x5Chain,
                display = display,
            )

            CredentialResponseResult.Success(
                CredentialResponse(
                    credentials = listOf(
                        IssuedCredential(credential = JsonPrimitive(jwtVc)),
                    ),
                )
            )
        } catch (e: Exception) {
            CredentialResponseResult.Failure(OAuthError("invalid_request", e.message))
        }
    }
}
