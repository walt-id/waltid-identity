package id.walt.oid4vc.providers

import id.walt.oid4vc.data.ClientIdScheme
import kotlinx.serialization.Serializable

@Serializable
class CredentialVerifierConfig(
    val clientId: String,
    val clientIdScheme: ClientIdScheme? = ClientIdScheme.RedirectUri,
    val redirectUri: String? = null,
    val responseUrl: String? = null
) : OpenIDProviderConfig()
