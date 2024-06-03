package id.walt

import id.walt.issuer.base.config.CredentialTypeConfig
import id.walt.issuer.base.config.OIDCIssuerServiceConfig

val testConfigs = listOf(
    "issuer-service" to OIDCIssuerServiceConfig::class,
    "credential-issuer-metadata" to CredentialTypeConfig::class
)
