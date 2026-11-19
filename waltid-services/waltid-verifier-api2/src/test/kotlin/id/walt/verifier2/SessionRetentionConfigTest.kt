package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class SessionRetentionConfigTest {
    @Test
    fun `retention startup accepts Hoplite durations and rejects ISO durations`() {
        for (duration in listOf("30d", "P30D")) {
            val file = Files.createTempFile("verifier-retention", ".conf")
            val previous = System.getProperty("config.file.verifier-service")
            try {
                Files.writeString(file, """
                    clientId = "retention-test"
                    urlPrefix = "http://localhost/verification-session"
                    urlHost = "openid4vp://authorize"
                    sessionRetention = "$duration"
                """.trimIndent())
                System.setProperty("config.file.verifier-service", file.toString())
                ConfigManager.preclear()
                ConfigManager.registerConfig("verifier-service", OSSVerifier2ServiceConfig::class)
                val loaded = ConfigManager.loadConfig(ConfigManager.ConfigData("verifier-service", OSSVerifier2ServiceConfig::class), emptyArray())
                if (duration == "30d") {
                    loaded.getOrThrow()
                    assertEquals(30.days, ConfigManager.getConfig<OSSVerifier2ServiceConfig>().sessionRetention)
                } else {
                    assertTrue(loaded.isFailure)
                    assertContains(loaded.exceptionOrNull()!!.message.orEmpty(), "P30D")
                }
            } finally {
                if (previous == null) System.clearProperty("config.file.verifier-service")
                else System.setProperty("config.file.verifier-service", previous)
                ConfigManager.preclear()
                file.deleteIfExists()
            }
        }
    }
}
