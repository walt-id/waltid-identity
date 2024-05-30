package id.walt.webwallet.config

import id.walt.webwallet.db.Db
import id.walt.webwallet.db.SerializableHikariConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists

@Serializable
data class DatabaseConfiguration(
    val database: String
) : WalletConfig

@Serializable
data class DatasourceJsonConfiguration(
    val hikariDataSource: SerializableHikariConfiguration,
    val recreateDatabaseOnStart: Boolean = false
) : WalletConfig {

    companion object {
        private val log = KotlinLogging.logger { }
    }

    init {
        if (hikariDataSource.jdbcUrl?.startsWith(Db.SQLITE_PREFIX) == true) {
            val path = Path(hikariDataSource.jdbcUrl.removePrefix(Db.SQLITE_PREFIX))
            if (path.notExists()) {
                log.info { "Creating directory for sqlite database: $path" }
                path.createParentDirectories()
            }
        }
    }
}