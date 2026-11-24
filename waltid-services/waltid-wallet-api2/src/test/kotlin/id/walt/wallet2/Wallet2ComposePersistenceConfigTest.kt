package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.wallet2.persistence.Wallet2PersistenceConfig
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class Wallet2ComposePersistenceConfigTest {
    @Test
    fun `shipped Compose persistence file loads PostgreSQL settings`() {
        val configFile = assertNotNull(javaClass.getResource("/compose-wallet2-persistence.conf"))
        val properties = mapOf(
            "config.file.wallet2-persistence" to Path.of(configFile.toURI()).toString(),
            "POSTGRES_DB_HOST" to "postgres",
            "DB_NAME" to "wallet2-test",
            "DB_USERNAME" to "wallet2-user",
            "DB_PASSWORD" to "synthetic-database-password",
        )
        val previous = properties.keys.associateWith(System::getProperty)

        try {
            ConfigManager.preclear()
            properties.forEach { (name, value) -> System.setProperty(name, value) }
            val config = assertIs<Wallet2PersistenceConfig>(
                ConfigManager.loadConfig(
                    ConfigManager.ConfigData("wallet2-persistence", Wallet2PersistenceConfig::class),
                    emptyArray(),
                ).getOrThrow()
            )
            assertEquals("jdbc:postgresql://postgres:5432/wallet2-test", config.jdbcUrl)
            assertEquals("org.postgresql.Driver", config.driverClassName)
            assertEquals("wallet2-user", config.username)
            assertEquals("synthetic-database-password", config.password)
        } finally {
            previous.forEach { (name, value) ->
                if (value == null) System.clearProperty(name) else System.setProperty(name, value)
            }
            ConfigManager.preclear()
        }
    }
}
