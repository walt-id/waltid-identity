package id.walt.webwallet.web.controllers.exchange.models.oid4vci

import id.walt.entrawallet.core.service.exchange.IssuanceServiceExternalSignatures
import kotlinx.serialization.Serializable

@Serializable
data class SubmitOID4VCIRequest(
    val did: String? = null,
    val offerURL: String,
    val requireUserInput: Boolean? = false,
    val accessToken: String? = null,
    val offeredCredentialProofsOfPossession: List<IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession>,
    val credentialIssuer: String,
) {

    companion object {

        fun build(
            response: PrepareOID4VCIResponse,
            offeredCredentialProofsOfPossession: List<IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession>,
            credentialIssuer: String,
            requireUserInput: Boolean? = false,
        ) = SubmitOID4VCIRequest(
            did = response.did,
            offerURL = response.offerURL,
            requireUserInput = requireUserInput,
            accessToken = response.accessToken,
            offeredCredentialProofsOfPossession = offeredCredentialProofsOfPossession,
            credentialIssuer = credentialIssuer,
        )
    }
}
