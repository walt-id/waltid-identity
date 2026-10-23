package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

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
    fun `omitted mode is network-only and private networks stay refused`() {
        loadConfig("")
        val config = ConfigManager.getConfig<OSSVerifier2ServiceConfig>()
        assertEquals(VicalFetchMode.NETWORK_ONLY, VicalFetchMode.parse(config.vicalFetchMode))
        assertFalse(config.allowPrivateNetworkVical)
    }

    @Test
    fun `hyphenated and underscored mode names both load`() {
        loadConfig("""vicalFetchMode = "filesystem-only" """)
        assertEquals(VicalFetchMode.FILESYSTEM_ONLY, VicalFetchMode.parse(ConfigManager.getConfig<OSSVerifier2ServiceConfig>().vicalFetchMode))

        ConfigManager.preclear()
        loadConfig("""vicalFetchMode = network_only""")
        assertEquals(VicalFetchMode.NETWORK_ONLY, VicalFetchMode.parse(ConfigManager.getConfig<OSSVerifier2ServiceConfig>().vicalFetchMode))
    }

    @Test
    fun `an unknown mode fails configuration`() {
        assertFailsWith<IllegalArgumentException> {
            OSSVerifier2ServiceConfig(
                urlPrefix = "http://localhost:7003/verification-session",
                urlHost = "openid4vp://authorize",
                vicalFetchMode = "anywhere",
            )
        }
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
