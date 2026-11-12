package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.ConfigManager.ConfigData
import id.walt.commons.testing.E2ETest

/** Publish the listening socket's actual address before the first verification request. */
fun E2ETest.useBoundVerifierAddress() {
    val config = ConfigManager.getConfig<OSSVerifier2ServiceConfig>()
    ConfigManager.preloadConfig("verifier-service", config.copy(urlPrefix = "${getBaseURL()}/verification-session"))
    ConfigManager.loadConfig(ConfigData("verifier-service", OSSVerifier2ServiceConfig::class), emptyArray()).getOrThrow()
}
