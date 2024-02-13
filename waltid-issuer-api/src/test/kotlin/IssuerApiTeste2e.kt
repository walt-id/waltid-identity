
import id.walt.issuer.OidcApi.oidcApi
import id.walt.issuer.configurePlugins
import id.walt.issuer.entraIssuance
import id.walt.issuer.issuerApi
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class IssuerApiTeste2e {
  
  private val didMethodsToTest = listOf("key", "jwk", "web", "cheqd")
  
  companion object {
    var ktorClient: HttpClient? = null
    
    private fun setUpServer() = testApplication {
      println("Server Starting...")
      
      ktorClient = createClient {
        install(ContentNegotiation) {
          json()
        }
      }
      application {
        configurePlugins()
        oidcApi()
        issuerApi()
        entraIssuance()
      }
    }
  }
  
  @Test
  fun myTest() = runTest {
    println("ok!")
  }
  
  
  @Test
  fun testAuthentication() = runTest {
//    println("\nUSE CASE -> REGISTRATION\n")
//
//    val email = randomString(8) + "@example.org"
//    val password = randomString(16)
//
//    ktorClient?.post("/wallet-api/auth/create") {
//      contentType(ContentType.Application.Json)
//      setBody(
//        mapOf(
//          "name" to "mike",
//          "email" to email,
//          "password" to password,
//          "type" to "email"
//        )
//      )
//    }.let { response ->
//      assertEquals(HttpStatusCode.Created, response?.status)
//    }

//    println("\nUSE CASE -> LOGIN\n")
//
//    val token = ktorClient?.let { login(it) }
//
//    println("Login Successful.")
//
//    println("> Response JSON body token: $token")
//
//    println("\nUSE CASE -> USER-INFO\n")
//    client.get("/wallet-api/auth/user-info") {
//      contentType(ContentType.Application.Json)
//      bearerAuth(token)
//    }.let { response ->
//      assertEquals(HttpStatusCode.OK, response.status)
//    }
//
//    println("\nUSE CASE -> SESSION\n")
//    client.get("/wallet-api/auth/session") {
//      contentType(ContentType.Application.Json)
//      bearerAuth(token)
//    }.let { response ->
//      assertEquals(HttpStatusCode.OK, response.status)
//    }
//
//    println("\nUSE CASE -> LIST WALLETS FOR ACCOUNT\n")
//    getWalletFor(client, token)
  }
  
}
