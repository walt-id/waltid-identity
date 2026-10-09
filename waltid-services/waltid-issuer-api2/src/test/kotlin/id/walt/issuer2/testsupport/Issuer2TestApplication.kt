package id.walt.issuer2.testsupport

import id.walt.commons.config.ConfigManager
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.issuer2.config.Issuer2ProfilesConfig
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
import kotlinx.serialization.json.Json

fun ApplicationTestBuilder.installIssuer2WithConfigFiles(
    credentialProofKeyAcceptance: CredentialProofKeyAcceptance? = null,
    json: Json = issuer2TestJson,
    configureProfilesConfig: (Issuer2ProfilesConfig) -> Issuer2ProfilesConfig = { it },
    configureServiceConfig: (Issuer2ServiceConfig) -> Issuer2ServiceConfig = { it },
) {
    loadIssuer2ConfigFiles()
    val serviceConfig = ConfigManager.getConfig<Issuer2ServiceConfig>()
    ConfigManager.loadedConfigurations["issuer-service" to Issuer2ServiceConfig::class] =
        configureServiceConfig(serviceConfig)
    val profilesConfig = ConfigManager.getConfig<Issuer2ProfilesConfig>()
    ConfigManager.loadedConfigurations["issuer2-profiles" to Issuer2ProfilesConfig::class] =
        configureProfilesConfig(profilesConfig)
    application {
        install(ServerContentNegotiation) {
            json(json)
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
