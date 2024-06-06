package id.walt.issuer.config

import id.walt.commons.config.WaltConfig

data class CredentialTypeConfig(
    val supportedCredentialTypes: Map<String, List<String>>
) : WaltConfig()
