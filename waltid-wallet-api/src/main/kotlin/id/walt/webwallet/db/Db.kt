package id.walt.webwallet.db

import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.config.DatasourceConfiguration
import id.walt.webwallet.config.DatasourceJsonConfiguration
import id.walt.webwallet.db.models.*
import id.walt.webwallet.service.account.AccountsService
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.utils.IssuanceExamples
import id.walt.webwallet.web.model.EmailAccountRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.random.Random

object Db {

    private val log = KotlinLogging.logger { }

    lateinit var datasourceConfig: DatasourceConfiguration

    internal const val SQLITE_PREFIX = "jdbc:sqlite:"

    private fun connect() {
        val jdbcUrl = ConfigManager.getConfig<DatasourceJsonConfiguration>().jdbcUrl

        if (jdbcUrl?.contains("sqlite") == true) {
            log.info { "Will use sqlite database (${jdbcUrl}), working directory: ${Path(".").absolutePathString()}" }
        }

        datasourceConfig = ConfigManager.getConfig<DatasourceConfiguration>()
        val hikariDataSourceConfig = datasourceConfig.hikariDataSource

        // connect
        log.info { "Connecting to database at \"${hikariDataSourceConfig.jdbcUrl}\"..." }

        Database.connect(hikariDataSourceConfig)
        TransactionManager.manager.defaultIsolationLevel =
            toTransactionIsolationLevel(hikariDataSourceConfig.transactionIsolation)
    }

    // Make sure the creation order is correct (references / foreignKeys have to exist)
    val tables = listOf(
        Accounts,
        Wallets,
        WalletOperationHistories,
        WalletDids,
        WalletKeys,
        WalletCredentials,
        AccountWalletMappings,

        Web3Wallets,
        WalletIssuers,
        Events,
        WalletCategory,
        WalletCredentialCategoryMap,

        OidcLogins,
        WalletSettings,
        WalletNotifications,
        EntityNameResolutionCache,
    ).toTypedArray()


    private fun recreateDatabase() {
        transaction {
            addLogger(StdOutSqlLogger)

            SchemaUtils.drop(*(tables.reversedArray()))
            SchemaUtils.create(*tables)

            runBlocking {

                AccountsService.register(request = EmailAccountRequest("Max Mustermann", "string@string.string", "string"))
                val accountResult = AccountsService.register(request = EmailAccountRequest("Max Mustermann", "user@email.com", "password"))
                val accountId = accountResult.getOrNull()?.id!!
                val walletResult = AccountsService.getAccountWalletMappings("", accountId)
                val walletId = walletResult.wallets[0].id

                CredentialsService().add(
                    wallet = walletId,
                    WalletCredential(
                        wallet = walletId,
                        id = "urn:uuid:" + UUID.generateUUID(Random),
                        document = IssuanceExamples.universityDegreeCredential,
                        disclosures = null,
                        addedOn = Clock.System.now(),
                        manifest = null,
                        deletedOn = null,
                    )
                )
            }
        }
    }

    fun start() {
        connect()

        if (datasourceConfig.recreateDatabaseOnStart) {
            recreateDatabase()
        } else {
            transaction {
                SchemaUtils.drop(WalletCredentialCategoryMap, WalletCategory, WalletIssuers)// migration
                SchemaUtils.createMissingTablesAndColumns(*tables)
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
