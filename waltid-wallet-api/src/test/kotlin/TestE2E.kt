import Credential.Companion.testCredential
import id.walt.issuer.base.config.OIDCIssuerServiceConfig
import id.walt.issuer.issuerModule
import id.walt.verifier.verifierModule
import id.walt.webwallet.db.Db
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.service.account.AuthenticationResult
import id.walt.webwallet.utils.WalletHttpClients
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.web.model.LoginRequestJson
import id.walt.webwallet.webWalletModule
import id.walt.webwallet.webWalletSetup
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import id.walt.issuer.base.config.ConfigManager as IssuerConfigManager
import id.walt.webwallet.config.ConfigManager as WalletConfigManager
import id.walt.webwallet.config.WebConfig as WalletWebConfig

class TestE2E: WalletApiTeste2eBase() {
    
    companion object {
        lateinit var localWalletClient: HttpClient
        var localWalletUrl: String = ""
        var localIssuerUrl: String = "http://localhost:7002"
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
    
    
    private fun setupTestWebWallet() {
        WalletConfigManager.preloadConfig("web", WalletWebConfig())
        
        webWalletSetup()
        WalletConfigManager.loadConfigs(emptyArray())
        
        Db.start()
    }
    
    private fun setupTestIssuer() {
        IssuerConfigManager.preloadConfig("issuer-service", OIDCIssuerServiceConfig("http://localhost"))
        
        IssuerConfigManager.loadConfigs(emptyArray())
    }
    
    private fun ApplicationTestBuilder.runApplication() = run {
        println("Running in ${Path(".").absolutePathString()}")
        localWalletClient = newClient()
        
        WalletHttpClients.defaultMethod = {
            newClient()
        }
        
        println("Setup web wallet...")
        setupTestWebWallet()
        
        println("Setup issuer...")
        setupTestIssuer()
        
        println("Starting application...")
        application {
            webWalletModule()
            issuerModule(withPlugins = false)
            verifierModule(withPlugins = false)
        }
    }
    
    @Test
    fun x() = testApplication {
        runApplication()
        login()
        getTokenFor()
        
        localWalletClient = newClient(token)
        
        // list all wallets for this user
        getWallets()
        
        // list al Dids for this user and set default for credential issuance
        val availableDids = listAllDids()
        
        val issuanceUri = issueJwtCredential()

        // Request credential and store in wallet
        requestCredential(issuanceUri, availableDids.first().did)
        
        // TODO list credentials in wallet e2e test to verify the credential is there
    }
    override var walletClient: HttpClient
        get() = localWalletClient
        set(value) {
            walletClient = value
        }
    
    override var walletUrl: String
        get() = localWalletUrl
        set(value) {
            localWalletUrl = value
        }
    
    override var issuerUrl: String
        get() = localIssuerUrl
        set(value) {
            localIssuerUrl = value
        }
}