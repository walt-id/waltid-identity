package id.walt.oid4vc.providers

import id.walt.oid4vc.data.ClientIdScheme
import kotlinx.serialization.Serializable

@Serializable
class CredentialVerifierConfig(
    val redirectUri: String,
    val defaultClientIdScheme: ClientIdScheme = ClientIdScheme.RedirectUri,
    val defaultClientId: String = redirectUri,
    val responseUrl: String? = null,
    val clientIdMap: Map<ClientIdScheme, String> = mapOf()
) : OpenIDProviderConfig()
