package id.walt.issuer.config

import id.walt.commons.config.WaltConfig

data class OIDCIssuerServiceConfig(
    val baseUrl: String
) : WaltConfig()
