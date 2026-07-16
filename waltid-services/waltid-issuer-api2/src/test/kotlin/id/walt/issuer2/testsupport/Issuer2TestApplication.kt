package id.walt.issuer2.testsupport

import id.walt.commons.config.ConfigManager
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.issuer2Module
import id.walt.issuer2.service.openid4vci.CredentialProofKeyAcceptance
import id.walt.issuer2.web.plugins.issuer2AuthenticationPluginAmendment
import io.ktor.server.application.Application
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking

fun ApplicationTestBuilder.installIssuer2WithConfigFiles(
    credentialProofKeyAcceptance: CredentialProofKeyAcceptance? = null,
    configureServiceConfig: (Issuer2ServiceConfig) -> Issuer2ServiceConfig = { it },
) {
    loadIssuer2ConfigFiles()
    val serviceConfig = ConfigManager.getConfig<Issuer2ServiceConfig>()
    ConfigManager.loadedConfigurations["issuer-service" to Issuer2ServiceConfig::class] =
        configureServiceConfig(serviceConfig)
    application {
        install(ServerContentNegotiation) {
            json(issuer2TestJson)
        }
        installIssuer2AuthenticationForTests()
        issuer2Module(
            withPlugins = true,
            credentialProofKeyAcceptance = credentialProofKeyAcceptance,
        )
    }
}

fun Application.installIssuer2AuthenticationForTests() {
    runBlocking { issuer2AuthenticationPluginAmendment() }
    AuthenticationServiceModule.run { enable() }
}

fun ApplicationTestBuilder.apiClient() = createClient {
    followRedirects = false
    install(ClientContentNegotiation) {
        json(issuer2TestJson)
    }
}
