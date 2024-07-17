package id.walt

import id.walt.commons.config.list.WebConfig
import id.walt.issuer.config.CredentialTypeConfig
import id.walt.issuer.config.OIDCIssuerServiceConfig

val testConfigs = listOf(
    "web" to WebConfig::class,
    "issuer-service" to OIDCIssuerServiceConfig::class,
    "credential-issuer-metadata" to CredentialTypeConfig::class
)
