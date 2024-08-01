package id.walt

import id.walt.commons.config.list.WebConfig
import id.walt.verifier.config.OIDCVerifierServiceConfig

val testConfigs = listOf(
    "web" to WebConfig::class,
    "verifier-service" to OIDCVerifierServiceConfig::class,
)
