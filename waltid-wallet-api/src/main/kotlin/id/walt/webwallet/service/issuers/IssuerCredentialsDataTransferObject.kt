package id.walt.webwallet.service.issuers

import kotlinx.serialization.Serializable

@Serializable
data class IssuerCredentialsDataTransferObject(
    val issuer: IssuerDataTransferObject,
    val credentials: List<CredentialDataTransferObject>,
)
