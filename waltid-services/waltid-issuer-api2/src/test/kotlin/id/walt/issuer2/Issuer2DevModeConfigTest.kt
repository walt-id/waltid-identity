package id.walt.issuer2

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.list.DevModeConfig
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Issuer2DevModeConfigTest {

    @BeforeEach
    fun resetConfig() {
        clearIssuer2TestEnvironment()
    }

    @AfterEach
    fun clearConfig() {
        clearIssuer2TestEnvironment()
    }

    @Test
    fun devModeFeatureUsesCommonsDevModeConfig() {
        assertEquals(
            DevModeConfig::class,
            FeatureCatalog.devModeFeature.configs["dev-mode"],
        )
        assertFalse(FeatureCatalog.devModeFeature.default.value)
    }

    @Test
    fun devModeConfigMatchesIssuer1DevelopmentDefaults() {
        System.setProperty("config.file.dev-mode", issuer2ConfigFile().toString())

        ConfigManager.registerConfig("dev-mode", DevModeConfig::class)
        ConfigManager.loadConfigs()

        val devModeConfig = ConfigManager.getConfig<DevModeConfig>()
        assertFalse(devModeConfig.enableDidWebResolverHttps)
    }

    private fun issuer2ConfigFile(fileName: String = "dev-mode.conf"): Path =
        listOf(
            Path.of("config"),
            Path.of("waltid-services/waltid-issuer-api2/config"),
            Path.of("waltid-identity/waltid-services/waltid-issuer-api2/config"),
        )
            .map { it.toAbsolutePath().normalize().resolve(fileName) }
            .firstOrNull(Files::isRegularFile)
            ?: error("Could not locate waltid-issuer-api2 config file: $fileName")
}