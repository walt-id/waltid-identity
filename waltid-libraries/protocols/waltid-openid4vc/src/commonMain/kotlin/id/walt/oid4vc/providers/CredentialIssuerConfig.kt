package id.walt.oid4vc.providers

import id.walt.oid4vc.data.CredentialSupported
import kotlinx.serialization.Serializable

@Serializable
class CredentialIssuerConfig(
    var credentialConfigurationsSupported: Map<String, CredentialSupported> = mapOf()
) : OpenIDProviderConfig()
