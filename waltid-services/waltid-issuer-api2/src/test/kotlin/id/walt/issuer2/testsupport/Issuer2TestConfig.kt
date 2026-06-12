package id.walt.issuer2.testsupport

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.WaltConfig
import id.walt.commons.featureflag.FeatureManager
import id.walt.did.dids.DidService
import id.walt.issuer2.config.AuthenticationServiceConfig
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.config.registerIssuer2ConfigDecoders
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass

val issuer2TestJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}

val issuer2ConfigFiles: List<Pair<String, KClass<out WaltConfig>>> = listOf(
    "issuer-service" to Issuer2ServiceConfig::class,
    "authentication-service" to AuthenticationServiceConfig::class,
    "credential-issuer-metadata" to Issuer2MetadataConfig::class,
    "issuer2-profiles" to Issuer2ProfilesConfig::class,
)

fun clearIssuer2TestEnvironment() {
    System.clearProperty("config.file._features")
    System.clearProperty("config.file.dev-mode")
    issuer2ConfigFiles.forEach { (id, _) -> System.clearProperty("config.file.$id") }
    ConfigManager.preclear()
    FeatureManager.preclear()
}

fun loadIssuer2ConfigFiles(baseUrlOverride: String? = KTOR_TEST_APPLICATION_BASE_URL) {
    clearIssuer2TestEnvironment()
    registerIssuer2ConfigDecoders()
    runBlocking { DidService.minimalInit() }

    val configDir = issuer2ConfigDir()
    issuer2ConfigFiles.forEach { (id, type) ->
        System.setProperty("config.file.$id", configDir.resolve("$id.conf").toString())
        ConfigManager.registerConfig(id, type)
    }
    ConfigManager.loadConfigs()
    baseUrlOverride?.let { overrideIssuer2BaseUrl(it) }
}

fun overrideIssuer2BaseUrl(baseUrl: String) {
    val serviceConfig = ConfigManager.getConfig<Issuer2ServiceConfig>()
    ConfigManager.loadedConfigurations["issuer-service" to Issuer2ServiceConfig::class] =
        serviceConfig.copy(baseUrl = baseUrl)
}

private fun issuer2ConfigDir(): Path =
    listOf(
        Path.of("config"),
        Path.of("waltid-services/waltid-issuer-api2/config"),
        Path.of("waltid-identity/waltid-services/waltid-issuer-api2/config"),
    )
        .map { it.toAbsolutePath().normalize() }
        .firstOrNull { Files.isRegularFile(it.resolve("issuer-service.conf")) }
        ?: error("Could not locate waltid-issuer-api2 config directory")

const val KTOR_TEST_APPLICATION_BASE_URL = "http://localhost"