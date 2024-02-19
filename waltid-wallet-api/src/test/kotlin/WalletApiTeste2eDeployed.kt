import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlin.test.*

class WalletApiTeste2eDeployed : WalletApiTeste2eBase() {
  
  companion object {
    var localUrl: String = "https://wallet.walt.id"
    lateinit var localClient: HttpClient
    
    init {
      setUpHttp()
    }
    
    private fun setUpHttp() {
      println("Using Deployed WaltId Wallet Api...")
      localClient = HttpClient {
        install(ContentNegotiation) {
          json()
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
  
  override var walletClient: HttpClient
    get() = localClient
    set(value) {
      localClient = value
    }
  override var issuerClient: HttpClient
    get() = TODO("Not yet implemented")
    set(value) {}
  override var apiUrl: String
    get() = localUrl
    set(value) {
      localUrl = value
    }
}