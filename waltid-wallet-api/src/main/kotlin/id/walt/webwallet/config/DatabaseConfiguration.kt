package id.walt.webwallet.config

import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.json.JsonObject

data class DatabaseConfiguration(
    val database: String
) : WalletConfig

data class DatasourceConfiguration(
    val hikariDataSource: HikariDataSource,
    val recreateDatabaseOnStart: Boolean = false
) : WalletConfig

data class DatasourceJsonConfiguration(
    val hikariDataSource: JsonObject,
    val recreateDatabaseOnStart: Boolean = false
) : WalletConfig
