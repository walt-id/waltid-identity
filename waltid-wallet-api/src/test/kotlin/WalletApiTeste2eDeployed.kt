import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlin.test.*

class WalletApiTeste2eDeployed : WalletApiTeste2eBase() {
  
  companion object {
    var deployedWalletUrl: String = "https://wallet.walt.id"
    var deployedIssuerUrl: String = "https://issuer.portal.walt.id"
    lateinit var deployedClient: HttpClient
    lateinit var deployedIssuerClient: HttpClient
    
    init {
      setUpHttp()
    }
    
    private fun setUpHttp() {
      println("Using client for Deployed WaltId Wallet and Issuer Api...")
      deployedClient = HttpClient {
        install(ContentNegotiation) {
          json()
        }
        install(HttpTimeout) {
          requestTimeoutMillis = 30 * 1000
        }
      }
      deployedIssuerClient = HttpClient {
        install(ContentNegotiation) {
          json()
        }
        defaultRequest {
          header(HttpHeaders.ContentType, ContentType.Application.Json)
        }
        install(HttpTimeout) {
          requestTimeoutMillis = 30 * 1000
        }
      }
    }
  }
  
  @Test
  fun testLogin() = runTest {
    // test creation of a randomly generated user account
    super.testCreateUser(
      User(
        "tester",
        email,
        password,
        "email"
      )
    )
  }
  
  @Test
  fun testAuthentication() = runTest {
    // this uses the permanent test account
    super.testAuthenticationEndpoints(
      User(
        "tester",
        "user@email.com",
        "password",
        "email"
      )
    )
  }
  
  @Test
  fun testCredentials() = runTest {
    val response: JsonArray = testCredentialEndpoints(User("tester", "user@email.com", "password", "email"))
    assertEquals(response.size, 0)
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
    get() = deployedClient
    set(value) {
      deployedClient = value
    }
  
  override var issuerClient: HttpClient
    get() = deployedIssuerClient
    set(value) {
      deployedIssuerClient = value
    }
  
  override var walletUrl: String
    get() = deployedWalletUrl
    set(value) {
      deployedWalletUrl = value
    }
  
  override var issuerUrl: String
    get() = deployedIssuerUrl
    set(value) {
      deployedIssuerUrl = value
    }
}