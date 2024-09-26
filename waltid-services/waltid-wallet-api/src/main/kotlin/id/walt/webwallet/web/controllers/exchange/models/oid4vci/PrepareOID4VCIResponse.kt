package id.walt.webwallet.web.controllers.exchange.models.oid4vci

import id.walt.webwallet.service.exchange.IssuanceServiceExternalSignatures
import kotlinx.serialization.Serializable

@Serializable
data class PrepareOID4VCIResponse(
    val did: String? = null,
    val offerURL: String,
    val accessToken: String? = null,
    val offeredCredentialsProofRequests: List<IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossessionParameters>,
    val credentialIssuer: String,
)