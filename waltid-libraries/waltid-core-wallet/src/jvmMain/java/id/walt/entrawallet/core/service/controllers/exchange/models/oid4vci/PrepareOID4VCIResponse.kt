package id.walt.webwallet.web.controllers.exchange.models.oid4vci

import id.walt.entrawallet.core.service.exchange.IssuanceServiceExternalSignatures
import kotlinx.serialization.Serializable

@Serializable
data class PrepareOID4VCIResponse(
    val did: String? = null,
    val offerURL: String,
    val accessToken: String? = null,
    val offeredCredentialsProofRequests: List<IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossessionParameters>,
    val credentialIssuer: String,
) {

    companion object {

        fun build(
            request: PrepareOID4VCIRequest,
            offeredCredentialsProofRequests: List<IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossessionParameters>,
            credentialIssuer: String,
            accessToken: String? = null,
        ) = PrepareOID4VCIResponse(
            did = request.did,
            offerURL = request.offerURL,
            accessToken = accessToken,
            offeredCredentialsProofRequests = offeredCredentialsProofRequests,
            credentialIssuer = credentialIssuer,
        )
    }
}
