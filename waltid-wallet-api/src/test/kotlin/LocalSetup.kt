import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import id.walt.issuer.base.config.OIDCIssuerServiceConfig
import id.walt.issuer.issuerModule
import id.walt.verifier.base.config.OIDCVerifierServiceConfig
import id.walt.verifier.verifierModule
import id.walt.webwallet.config.DatasourceConfiguration
import id.walt.webwallet.db.Db
import id.walt.webwallet.utils.WalletHttpClients
import id.walt.webwallet.webWalletModule
import id.walt.webwallet.webWalletSetup
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import id.walt.issuer.base.config.ConfigManager as IssuerConfigManager
import id.walt.webwallet.config.ConfigManager as WalletConfigManager
import id.walt.webwallet.config.WebConfig as WalletWebConfig
import id.walt.verifier.base.config.ConfigManager as VerifierConfigManager
import kotlin.test.assertNotNull


class LocalSetup {
    private lateinit var client: HttpClient

    companion object {
        init {
            Files.createDirectories(Paths.get("./data"))
            assertTrue(File("./data").exists())
            val config = DatasourceConfiguration(
                hikariDataSource = HikariDataSource(HikariConfig().apply {
                    jdbcUrl = "jdbc:sqlite:data/wallet.db"
                    driverClassName = "org.sqlite.JDBC"
                    username = ""
                    password = ""
                    transactionIsolation = "TRANSACTION_SERIALIZABLE"
                    isAutoCommit = true
                }),
                recreateDatabaseOnStart = true
            )
            
            WalletConfigManager.preloadConfig(
                "db.sqlite", config
            )
            
            WalletConfigManager.preloadConfig(
                "web", WalletWebConfig()
            )
            webWalletSetup()
            WalletConfigManager.loadConfigs(emptyArray())
        }
    }
    
    private fun ApplicationTestBuilder.newClient(token: String? = null) = createClient {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
        followRedirects = false
        defaultRequest {
            contentType(ContentType.Application.Json)
            if (token != null) {
                header("Authorization", "Bearer $token")
            }
        }
    }
    
    fun initTestApplication() = testApplication {
        runApplication()
        this@LocalSetup.client = newClient()
    }
    
    fun initClient(token: String) = testApplication {
        newClient(token)
    }
    
    fun getClient(): HttpClient {
        return this@LocalSetup.client
    }
    
    private fun ApplicationTestBuilder.runApplication() = run {
        println("Running in ${Path(".").absolutePathString()}")
        this@LocalSetup.client = newClient()
        
        WalletHttpClients.defaultMethod = {
            newClient()
        }
        setupTestWebWallet()
        
        println("Setup issuer...")
        setupTestIssuer()
        
        println("Setup issuer...")
        setupTestVerifier()
        
        println("Starting application...")
        application {
            webWalletModule()
            issuerModule(withPlugins = false)
            verifierModule(withPlugins = false)
        }
    }
    
    private fun setupTestWebWallet() {
        // TODO moving this into init{} causes error 400 status code in issuance test
        Db.start()
    }
    
    private fun setupTestIssuer() {
        id.walt.issuer.base.config.ConfigManager.preloadConfig("issuer-service", OIDCIssuerServiceConfig("http://localhost"))
        
        id.walt.issuer.base.config.ConfigManager.loadConfigs(emptyArray())
    }
    
    private fun setupTestVerifier() {
        id.walt.verifier.base.config.ConfigManager.preloadConfig("verifier-service", OIDCVerifierServiceConfig("http://localhost"))
        
        id.walt.verifier.base.config.ConfigManager.loadConfigs(emptyArray())
    }
    
}