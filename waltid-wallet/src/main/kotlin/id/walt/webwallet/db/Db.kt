package id.walt.webwallet.db

import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.config.DatasourceConfiguration
import id.walt.webwallet.db.models.todo.Issuers
import id.walt.webwallet.service.account.AccountsService
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.db.models.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.bridge.SLF4JBridgeHandler
import java.sql.Connection
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

object Db {

    private val log = KotlinLogging.logger { }

    private fun connect() {
        val databaseConfig = ConfigManager.getConfig<DatasourceConfiguration>()
        val datasourceConfig = databaseConfig.hikariDataSource

        /*val databaseConfig = ConfigManager.getConfig<DatabaseConfiguration>()

        //migrate
        Flyway.configure()
            .locations(databaseConfig.database.replace(".", "/"))
            .dataSource(datasourceConfig.hikariDataSource)
            .load()
            .migrate()*/

        // connect
        log.info { "Connecting to database at \"${datasourceConfig.jdbcUrl}\"..." }

        if (datasourceConfig.jdbcUrl.contains("sqlite")) {
            println("Will use sqlite database (${datasourceConfig.jdbcUrl}), working directory: ${Path(".").absolutePathString()}")
        }

        Database.connect(datasourceConfig)
        TransactionManager.manager.defaultIsolationLevel =
            toTransactionIsolationLevel(datasourceConfig.transactionIsolation)
    }

    fun start() {
        connect()

        SLF4JBridgeHandler.removeHandlersForRootLogger()
        SLF4JBridgeHandler.install()

        transaction {
            addLogger(StdOutSqlLogger)

            SchemaUtils.drop(
                Issuers,
                WalletOperationHistories,
                WalletDids,
                WalletKeys,
                WalletCredentials,
                AccountWalletMappings,
                Wallets,
                //AccountWeb3WalletMappings,
                Accounts,
                Web3Wallets
            )
            SchemaUtils.create(
                Web3Wallets,
                Accounts,
                //AccountWeb3WalletMappings,
                Wallets,
                AccountWalletMappings,
                WalletCredentials,
                WalletKeys,
                WalletDids,
                WalletOperationHistories,
                Issuers
            )

            /*SchemaUtils.create(Web3Wallets)
            SchemaUtils.create(Accounts)
            //AccountWeb3WalletMappings,
            SchemaUtils.create(Wallets)
            SchemaUtils.create(AccountWalletMappings)
            SchemaUtils.create(WalletCredentials)
            SchemaUtils.create(WalletKeys)
            SchemaUtils.create(WalletDids)
            SchemaUtils.create(WalletOperationHistories)
            SchemaUtils.create(Issuers)*/

            runBlocking {
                AccountsService.register(request = EmailAccountRequest("Max Mustermann", "string@string.string", "string"))
                AccountsService.register(request = EmailAccountRequest("Max Mustermann", "user@email.com", "password"))
            }
        }

    }

    private fun toTransactionIsolationLevel(value: String): Int = when (value) {
        "TRANSACTION_NONE" -> Connection.TRANSACTION_NONE
        "TRANSACTION_READ_UNCOMMITTED" -> Connection.TRANSACTION_READ_UNCOMMITTED
        "TRANSACTION_READ_COMMITTED" -> Connection.TRANSACTION_READ_COMMITTED
        "TRANSACTION_REPEATABLE_READ" -> Connection.TRANSACTION_REPEATABLE_READ
        "TRANSACTION_SERIALIZABLE" -> Connection.TRANSACTION_SERIALIZABLE
        else -> Connection.TRANSACTION_SERIALIZABLE
    }
}
