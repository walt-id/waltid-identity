package id.walt.webwallet.config

import com.zaxxer.hikari.HikariDataSource

data class DatabaseConfiguration(
    val database: String
) : WalletConfig

data class DatasourceConfiguration(
    val hikariDataSource: HikariDataSource,
    val recreateDatabaseOnStart: Boolean = false
) : WalletConfig
