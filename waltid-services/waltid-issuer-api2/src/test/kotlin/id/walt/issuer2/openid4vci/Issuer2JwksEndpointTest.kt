package id.walt.issuer2.openid4vci

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.WaltConfig
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.crypto.keys.KeyManager
import id.walt.issuer2.config.AuthenticationServiceConfig
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.config.registerIssuer2ConfigDecoders
import id.walt.issuer2.issuer2Module
import id.walt.issuer2.web.plugins.issuer2AuthenticationPluginAmendment
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Issuer2JwksEndpointTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    @AfterEach
    fun clearConfig() {
        configFiles.forEach { (id, _) -> System.clearProperty("config.file.$id") }
        ConfigManager.preclear()
        FeatureManager.preclear()
    }

    @Test
    fun shouldExposeConfiguredPublicJwks() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()

        val response = client.get("/openid4vci/jwks")

        assertEquals(HttpStatusCode.OK, response.status)
        val jwks = response.body<JsonObject>()
        assertPublicJwks(jwks)
        assertConfiguredKeysAreExposed(jwks)
    }

    private suspend fun assertConfiguredKeysAreExposed(jwks: JsonObject) {
        val configuredKids = configuredPublicKeyIds()
        val exposedKids = jwks.keysArray()
            .mapNotNull { key -> key.jsonObject["kid"]?.jsonPrimitive?.contentOrNull }
            .toSet()

        assertEquals(configuredKids, exposedKids)
    }

    private suspend fun configuredPublicKeyIds(): Set<String> {
        val serviceConfig = ConfigManager.getConfig<Issuer2ServiceConfig>()
        val profilesConfig = ConfigManager.getConfig<Issuer2ProfilesConfig>()

        return buildSet {
            add(KeyManager.resolveSerializedKey(serviceConfig.ciTokenKey).getKeyId())
            profilesConfig.profiles.values.forEach { profile ->
                add(KeyManager.resolveSerializedKey(profile.issuerKey).getKeyId())
            }
        }
    }

    private fun assertPublicJwks(jwks: JsonObject) {
        val keys = jwks.keysArray()
        assertTrue(keys.isNotEmpty(), "Expected JWKS to expose configured public keys")

        val kids = keys.mapNotNull { key -> key.jsonObject["kid"]?.jsonPrimitive?.contentOrNull }
        assertEquals(kids.toSet().size, kids.size, "Expected JWKS keys to be deduplicated by kid")
        keys.forEachIndexed { index, key ->
            val jwk = key.jsonObject
            assertNotNull(jwk["kid"], "Expected JWKS key $index to contain kid")
            assertFalse("d" in jwk, "Expected JWKS key $index to expose public material only")
        }
    }

    private fun JsonObject.keysArray() =
        assertNotNull(this["keys"]?.jsonArray, "Expected JWKS keys array")

    private fun ApplicationTestBuilder.installIssuer2WithConfigFiles() {
        loadIssuer2ConfigFiles()
        application {
            install(ServerContentNegotiation) {
                json(json)
            }
            runBlocking { issuer2AuthenticationPluginAmendment() }
            AuthenticationServiceModule.run { enable() }
            issuer2Module(withPlugins = true)
        }
    }

    private fun ApplicationTestBuilder.apiClient() = createClient {
        followRedirects = false
        install(ClientContentNegotiation) {
            json(json)
        }
    }

    private fun loadIssuer2ConfigFiles() {
        ConfigManager.preclear()
        FeatureManager.preclear()
        registerIssuer2ConfigDecoders()
        configFiles.forEach { (id, _) -> System.clearProperty("config.file.$id") }

        val configDir = issuer2ConfigDir()
        configFiles.forEach { (id, type) ->
            System.setProperty("config.file.$id", configDir.resolve("$id.conf").toString())
            ConfigManager.registerConfig(id, type)
        }
        ConfigManager.loadConfigs()
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

    private companion object {
        val configFiles: List<Pair<String, KClass<out WaltConfig>>> = listOf(
            "issuer-service" to Issuer2ServiceConfig::class,
            "authentication-service" to AuthenticationServiceConfig::class,
            "credential-issuer-metadata" to Issuer2MetadataConfig::class,
            "issuer2-profiles" to Issuer2ProfilesConfig::class,
        )
    }
}
