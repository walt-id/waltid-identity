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
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import id.walt.issuer.base.config.ConfigManager as IssuerConfigManager
import id.walt.webwallet.config.ConfigManager as WalletConfigManager
import id.walt.webwallet.config.WebConfig as WalletWebConfig

class TestE2E: WalletApiTeste2eBase() {
    
    companion object {
        lateinit var localWalletClient: HttpClient
        var localWalletUrl: String = ""
        var localIssuerUrl: String = "http://localhost:7002"
        
        init {
            initKtorTestApplication()
            println("Init finished")
        }
        
        private fun initKtorTestApplication() = testApplication {
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
            
            println("Running login...")
            val authResult = localWalletClient.post("/wallet-api/auth/login") {
                setBody(LoginRequestJson.encodeToString(EmailAccountRequest(email = "user@email.com", password = "password") as AccountRequest))
            }.body<AuthenticationResult>()
            println("Login result: $authResult\n")
            
            localWalletClient = newClient(authResult.token)
          
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
    }
  
    
   
    
    @Test
    fun x() = runTest {
        println("Running wallet listing...")
        val walletListing = localWalletClient.get("/wallet-api/wallet/accounts/wallets")
            .body<AccountWalletListing>()
        println("Wallet listing: $walletListing\n")
        
        val availableWallets = walletListing.wallets
        assertTrue { availableWallets.isNotEmpty() }
        val walletId = availableWallets.first().id
        
        println("Running DID listing...")
        val availableDids = localWalletClient.get("/wallet-api/wallet/$walletId/dids")
            .body<List<WalletDid>>()
        println("DID listing: $availableDids\n")

        assertTrue { availableDids.isNotEmpty() }
        val did = availableDids.first().did

        // Issuer
        println("Calling issuer...")
        val issuanceUrl = localWalletClient.post("/openid4vc/jwt/issue") {
            //language=JSON
            setBody(
               testCredential
            )
        }.bodyAsText()

//
        println("Issuance URL: $issuanceUrl\n")

        // Wallet
        println("Claiming credential...")
        val result = localWalletClient.post("/wallet-api/wallet/$walletId/exchange/useOfferRequest") {
            parameter("did", did)

            contentType(ContentType.Text.Plain)
            setBody(issuanceUrl)
        }
        println("Claim result: $result")
        assertEquals(HttpStatusCode.OK, result.status)
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