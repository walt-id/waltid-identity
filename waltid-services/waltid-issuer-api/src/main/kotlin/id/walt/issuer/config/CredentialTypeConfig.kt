package id.walt.issuer.config

import id.walt.config.WaltConfig

data class CredentialTypeConfig(
    val supportedCredentialTypes: Map<String, List<String>>
) : WaltConfig()
