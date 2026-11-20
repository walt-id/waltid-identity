package id.walt.issuer2.config

import id.walt.commons.config.ConfigManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticationServiceConfigTest {
    @AfterEach
    fun clearConfig() {
        System.clearProperty("config.file.authentication-service")
        ConfigManager.preclear()
    }

    @Test
    fun `configuration resolves from environment or defaults to empty`() {
        val expected = System.getenv("KEYCLOAK_CLIENT_SECRET").orEmpty()
        for (path in listOf(
            Path.of("config/authentication-service.conf"),
            Path.of("../../docker-compose/issuer-api2/config/authentication-service.conf"),
        )) {
            ConfigManager.preclear()
            System.setProperty("config.file.authentication-service", path.toAbsolutePath().toString())
            ConfigManager.registerConfig("authentication-service", AuthenticationServiceConfig::class)
            ConfigManager.loadConfigs()

            assertEquals(expected, ConfigManager.getConfig<AuthenticationServiceConfig>().clientSecret)
        }
    }

    @Test
    fun `configuration logging redacts the client secret`() {
        val config = AuthenticationServiceConfig(clientSecret = "test-client-secret")

        assertFalse(config.toString().contains("test-client-secret"))
        assertTrue(config.toString().contains("clientSecret=[REDACTED]"))
    }
}
