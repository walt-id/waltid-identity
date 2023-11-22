package id.walt.oid4vc.providers

import id.walt.oid4vc.data.CredentialSupported
import kotlinx.serialization.Serializable

@Serializable
open class CredentialIssuerConfig(
    val credentialsSupported: List<CredentialSupported> = listOf()
) : OpenIDProviderConfig()
