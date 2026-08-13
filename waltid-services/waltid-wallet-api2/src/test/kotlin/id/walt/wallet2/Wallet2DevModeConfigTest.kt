package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.list.DevModeConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Wallet2DevModeConfigTest {

    @BeforeEach
    fun resetConfig() {
        clearWallet2DevModeTestEnvironment()
    }

    @AfterEach
    fun clearConfig() {
        clearWallet2DevModeTestEnvironment()
    }

    @Test
    fun devModeFeatureUsesCommonsDevModeConfig() {
        assertEquals(
            DevModeConfig::class,
            OSSWallet2FeatureCatalog.devModeFeature.configs["dev-mode"],
        )
        assertFalse(OSSWallet2FeatureCatalog.devModeFeature.default.value)
    }

    @Test
    fun devModeConfigMatchesWallet2DevelopmentDefaults() {
        System.setProperty("config.file.dev-mode", wallet2ConfigFile().toString())

        ConfigManager.registerConfig("dev-mode", DevModeConfig::class)
        ConfigManager.loadConfigs()

        val devModeConfig = ConfigManager.getConfig<DevModeConfig>()
        assertFalse(devModeConfig.enableDidWebResolverHttps)
    }

    private fun clearWallet2DevModeTestEnvironment() {
        System.clearProperty("config.file.dev-mode")
        ConfigManager.preclear()
    }

    private fun wallet2ConfigFile(fileName: String = "dev-mode.conf"): Path =
        listOf(
            Path.of("config"),
            Path.of("waltid-services/waltid-wallet-api2/config"),
            Path.of("waltid-identity/waltid-services/waltid-wallet-api2/config"),
        )
            .map { it.toAbsolutePath().normalize().resolve(fileName) }
            .firstOrNull(Files::isRegularFile)
            ?: error("Could not locate waltid-wallet-api2 config file: $fileName")
}
