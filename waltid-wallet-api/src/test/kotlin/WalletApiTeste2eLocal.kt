import WalletApiTeste2eDeployed.Companion.localClient
import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.configurePlugins
import id.walt.webwallet.db.Db
import id.walt.webwallet.web.controllers.*
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlinx.serialization.json.*
import kotlin.test.*
import id.walt.issuer.utils.IssuerApiTeste2e


class WalletApiTeste2eLocal : WalletApiTeste2eBase() {
  
  companion object {
    var localUrl: String = ""
    private var issuer = IssuerApiTeste2e()
    var localIssuerClient: HttpClient
    lateinit var localWalletClient: HttpClient
    init {
      Security.addProvider(BouncyCastleProvider())
      runCatching { Db.dataDirectoryPath.createDirectories() }
      
      val args = emptyArray<String>()
      ConfigManager.loadConfigs(args)
      
      Db.start()
      
      // creates two test applications, for wallet and issuer
      setUpHttpClient()
     
      localIssuerClient = issuer.getHttpClient()
      println("Init finished")
    }
    
    private fun setUpHttpClient() {
      val testApp = TestApplication {
        application {
          configurePlugins()
          auth()
          accounts()
          credentials()
          dids()
          keys()
        }
      }
      localWalletClient = testApp.createClient {
        install(ContentNegotiation) {
          json()
        }
      }
    }
  }
  
  @Test
  fun myTest() = runTest {
    println("ok, issuerClient = $issuerClient")
  }
  
  @Test
  fun testAuthentication() = runTest {
    testCreateUser(User("tester", email, password, "email"))
    testAuthenticationEndpoints(User("tester", "user@email.com", "password", "email"))
  }
  
  @Test
  fun testCredentials() = runTest {
    val response:JsonArray = testCredentialEndpoints(User("tester", "user@email.com", "password", "email"))
    assertNotEquals(response.size, 0)
    response[0].jsonObject["id"]?.jsonPrimitive?.content ?: error("No credentials found")
  }
  
  @Test
  fun testIssuance() = runTest {
    testCredentialIssuance()
  }
  @Test
  fun testKey() = runTest {
    testKeys()
  }
  
  
  override var walletClient: HttpClient
    get() = localWalletClient
    set(value) {
      walletClient = value
    }
  override var issuerClient: HttpClient
    get() = localIssuerClient
    set(value) {
      localIssuerClient = value
    }
  override var apiUrl: String
    get() = localUrl
    set(value) {
      localUrl = value
    }
}