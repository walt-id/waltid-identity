package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VicalFetchModeConfigTest {

    private val tempFiles = mutableListOf<Path>()

    @AfterTest
    fun cleanup() {
        System.clearProperty("config.file.verifier-service")
        ConfigManager.preclear()
        tempFiles.forEach { it.deleteIfExists() }
        tempFiles.clear()
    }

    @Test
    fun `omitted settings enable fetch and refuse private networks`() {
        loadConfig("")
        val config = ConfigManager.getConfig<OSSVerifier2ServiceConfig>()
        assertTrue(config.vicalFetchEnabled)
        assertFalse(config.allowPrivateNetworkVical)
    }

    @Test
    fun `fetch can be turned off`() {
        loadConfig("vicalFetchEnabled = false")
        assertFalse(ConfigManager.getConfig<OSSVerifier2ServiceConfig>().vicalFetchEnabled)
    }

    private fun loadConfig(extraLines: String) {
        val configFile = Files.createTempFile("verifier-service", ".conf")
        tempFiles.add(configFile)
        Files.writeString(
            configFile,
            """
            urlPrefix = "http://localhost:7003/verification-session"
            urlHost = "openid4vp://authorize"
            """.trimIndent() + "\n" + extraLines + "\n"
        )
        System.setProperty("config.file.verifier-service", configFile.toString())
        ConfigManager.preclear()
        ConfigManager.registerConfig("verifier-service", OSSVerifier2ServiceConfig::class)
        ConfigManager.loadConfigs()
    }
}
