package id.walt.wallet2.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File

private val log = KotlinLogging.logger {}

private fun ensureSqliteParentDir(jdbcUrl: String) {
    val prefix = "jdbc:sqlite:"
    if (!jdbcUrl.startsWith(prefix)) return

    val path = jdbcUrl.removePrefix(prefix).substringBefore('?')
    if (path.isBlank() || path.startsWith(":")) return // e.g. ":memory:"

    val parent = File(path).absoluteFile.parentFile ?: return
    if (!parent.exists() && parent.mkdirs()) {
        log.info { "Created SQLite data directory: $parent" }
    }
}

/**
 * HOCON configuration for the wallet2 persistence layer.
 *
 * Example (SQLite — default):
 * ```hocon
 * wallet2-persistence {
 *   jdbcUrl = "jdbc:sqlite:wallet2.db"
 *   driverClassName = "org.sqlite.JDBC"
 * }
 * ```
 *
 * Example (PostgreSQL):
 * ```hocon
 * wallet2-persistence {
 *   jdbcUrl = "jdbc:postgresql://localhost:5432/wallet2"
 *   driverClassName = "org.postgresql.Driver"
 *   username = "wallet"
 *   password = "secret"
 *   maximumPoolSize = 10
 * }
 * ```
 */
@Serializable
data class Wallet2PersistenceConfig(
    val jdbcUrl: String = "jdbc:sqlite:wallet2.db",
    val driverClassName: String = "org.sqlite.JDBC",
    val username: String = "",
    val password: String = "",
    val maximumPoolSize: Int = 5,
    val minimumIdle: Int = 1,
)

/**
 * Initialises the Exposed [Database] connection pool and creates any missing tables.
 *
 * Call once at application startup before using any Exposed-backed store.
 *
 * @param config Datasource configuration. Defaults to an in-file SQLite database.
 * @return The initialised [Database] instance (also set as Exposed thread-local default).
 */
fun initWallet2Database(
    config: Wallet2PersistenceConfig = Wallet2PersistenceConfig()
): Database {
    log.info { "Initialising wallet2 database: ${config.jdbcUrl}" }

    ensureSqliteParentDir(config.jdbcUrl)

    val hikari = HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        driverClassName = config.driverClassName
        if (config.username.isNotBlank()) username = config.username
        if (config.password.isNotBlank()) password = config.password
        maximumPoolSize = config.maximumPoolSize
        minimumIdle = config.minimumIdle
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_SERIALIZABLE"
        validate()
    }

    val db = Database.connect(HikariDataSource(hikari))

    transaction(db) {
        SchemaUtils.createMissingTablesAndColumns(*Wallet2Tables.ALL)
        log.info { "wallet2 schema ready" }
    }

    return db
}
