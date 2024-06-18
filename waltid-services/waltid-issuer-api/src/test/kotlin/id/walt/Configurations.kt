package id.walt

import id.walt.issuer.config.CredentialTypeConfig
import id.walt.issuer.config.OIDCIssuerServiceConfig

val testConfigs = listOf(
    "issuer-service" to OIDCIssuerServiceConfig::class,
    "credential-issuer-metadata" to CredentialTypeConfig::class
)
