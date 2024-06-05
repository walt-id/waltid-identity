package id.walt.issuer.config

import id.walt.config.WaltConfig

data class OIDCIssuerServiceConfig(
    val baseUrl: String
) : WaltConfig()
