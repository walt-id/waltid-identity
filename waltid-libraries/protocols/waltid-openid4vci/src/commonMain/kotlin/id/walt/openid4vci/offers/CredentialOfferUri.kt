package id.walt.openid4vci.offers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CredentialOfferUri(
    @SerialName("credential_offer_uri") val credentialOfferUri: String,
)
