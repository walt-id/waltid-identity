package id.walt.webwallet.web.controllers.exchange.models.oid4vci

import id.walt.webwallet.service.exchange.IssuanceServiceExternalSignatures
import kotlinx.serialization.Serializable

@Serializable
data class SubmitOID4VCIRequest(
    val did: String? = null,
    val offerURL: String,
    val requireUserInput: Boolean? = false,
    val accessToken: String? = null,
    val offeredCredentialProofsOfPossession: List<IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession>,
    val credentialIssuer: String,
)