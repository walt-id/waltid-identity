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
    fun `shipped Compose persistence file uses the mounted SQLite directory`() {
        val configFile = assertNotNull(javaClass.getResource("/compose-wallet2-persistence.conf"))
        val property = "config.file.wallet2-persistence"
        val previous = System.getProperty(property)

        try {
            ConfigManager.preclear()
            System.setProperty(property, Path.of(configFile.toURI()).toString())
            val config = assertIs<Wallet2PersistenceConfig>(
                ConfigManager.loadConfig(
                    ConfigManager.ConfigData("wallet2-persistence", Wallet2PersistenceConfig::class),
                    emptyArray(),
                ).getOrThrow()
            )
            assertEquals("jdbc:sqlite:/waltid-wallet-api2/data/wallet2.db", config.jdbcUrl)
            assertEquals("org.sqlite.JDBC", config.driverClassName)
        } finally {
            if (previous == null) System.clearProperty(property) else System.setProperty(property, previous)
            ConfigManager.preclear()
        }
    }
}
