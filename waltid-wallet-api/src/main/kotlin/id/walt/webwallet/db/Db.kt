package id.walt.webwallet.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.config.DatasourceJsonConfiguration
import id.walt.webwallet.db.models.*
import id.walt.webwallet.service.account.AccountsService
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.utils.IssuanceExamples
import id.walt.webwallet.utils.JsonUtils
import id.walt.webwallet.web.model.EmailAccountRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
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
import kotlin.random.Random

object Db {

    private lateinit var datasourceConfig: DatasourceJsonConfiguration
    private val log = KotlinLogging.logger { }

    internal const val SQLITE_PREFIX = "jdbc:sqlite:"

    private fun connect() {
        datasourceConfig = ConfigManager.getConfig<DatasourceJsonConfiguration>()

        if (datasourceConfig.jdbcUrl?.contains("sqlite") == true) {
            log.info { "Will use sqlite database (${datasourceConfig.jdbcUrl}), working directory: ${Path(".").absolutePathString()}" }
        }

        val hikariDataSourceConfig = createHikariDataSource(datasourceConfig.hikariDataSource)

        // connect
        log.info { "Connecting to database at \"${datasourceConfig.jdbcUrl}\"..." }

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

        SLF4JBridgeHandler.removeHandlersForRootLogger()
        SLF4JBridgeHandler.install()

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

    private fun createHikariDataSource(json: JsonObject) = HikariDataSource(HikariConfig().apply {
        jdbcUrl = JsonUtils.tryGetData(json, DatasourceJsonConfiguration.JdbcUrl)!!.jsonPrimitive.content
        driverClassName =
            JsonUtils.tryGetData(json, DatasourceJsonConfiguration.DriveClassName)!!.jsonPrimitive.content
        username = JsonUtils.tryGetData(json, DatasourceJsonConfiguration.Username)!!.jsonPrimitive.content
        password = JsonUtils.tryGetData(json, DatasourceJsonConfiguration.Password)!!.jsonPrimitive.content
        JsonUtils.tryGetData(json, DatasourceJsonConfiguration.TransactionIsolation)?.jsonPrimitive?.content?.let { transactionIsolation = it }
        JsonUtils.tryGetData(json, DatasourceJsonConfiguration.MaximumPoolSize)?.jsonPrimitive?.content?.toIntOrNull()
            ?.let { maximumPoolSize = it }
        JsonUtils.tryGetData(json, DatasourceJsonConfiguration.MinimumIdle)?.jsonPrimitive?.content?.toIntOrNull()?.let { minimumIdle = it }
        JsonUtils.tryGetData(json, DatasourceJsonConfiguration.MaxLifetime)?.jsonPrimitive?.content?.toLongOrNull()?.let { maxLifetime = it }
        JsonUtils.tryGetData(json, DatasourceJsonConfiguration.AutoCommit)?.jsonPrimitive?.content?.toBoolean()?.let { isAutoCommit = it }
        JsonUtils.tryGetData(json, DatasourceJsonConfiguration.DataSource.JournalMode.fullName())?.jsonPrimitive?.content?.let {
            dataSourceProperties.setProperty(DatasourceJsonConfiguration.DataSource.JournalMode.value, it)
        }
        JsonUtils.tryGetData(json, DatasourceJsonConfiguration.DataSource.FullColumnNames.fullName())?.jsonPrimitive?.content?.let {
            dataSourceProperties.setProperty(DatasourceJsonConfiguration.DataSource.FullColumnNames.value, it)
        }
    })
}
