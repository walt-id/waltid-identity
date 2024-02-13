import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.configurePlugins
import id.walt.webwallet.db.Db
import id.walt.webwallet.web.controllers.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import java.security.Security
import kotlin.io.path.createDirectories
import kotlin.test.*

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WalletApiTest {
  
  private val didMethodsToTest = listOf("key", "jwk", "web", "cheqd")
  
  companion object {
    
    @JvmStatic
    @BeforeClass
    fun initDb() {
      Security.addProvider(BouncyCastleProvider())
      runCatching { Db.dataDirectoryPath.createDirectories() }
      
      val args = emptyArray<String>()
      ConfigManager.loadConfigs(args)
      
      Db.start()
      
      setUpServer()
    }
    
    private fun setUpServer() = testApplication {
      println("Server Starting...")
      val client = createClient {
        install(ContentNegotiation) {
          json()
        }
      }
      application {
        configurePlugins()
        auth()
        accounts()
      }
    }
  

  }
  
  private val alphabet = ('a'..'z')
  private fun randomString(length: Int) = (1..length).map { alphabet.random() }.toTypedArray().contentToString()
  
  private suspend fun login(client: HttpClient): String = run {
    return client.post("/wallet-api/auth/login") {
      contentType(ContentType.Application.Json)
      setBody(
        mapOf(
          "name" to "mike",
          "email" to "user@email.com",
          "password" to "password",
          "type" to "email"
        )
      )
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      response.body<JsonObject>()["token"]?.jsonPrimitive?.content ?: error("No token responded")
    }
  }
  
  private suspend fun getWalletFor(client: HttpClient, token: String): JsonElement {
    return client.get("/wallet-api/wallet/accounts/wallets") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      response.body<JsonObject>()["wallets"]?.jsonArray?.elementAt(0) ?: error("No wallets found")
    }
  }
  
  private suspend fun createDids(client: HttpClient, token: String, walletId: String): String {
    var defaultDid: String = ""
    didMethodsToTest.forEach {
      println("\nUse Case -> Create a did:$it\n")
      val did = client.post("/wallet-api/wallet/$walletId/dids/create/$it") {
        contentType(ContentType.Application.Json)
        bearerAuth(token)
      }.let { response ->
        assertEquals(HttpStatusCode.OK, response.status)
        response.bodyAsText()
      }
      println("did:$it created, did = $did")
      assertNotNull(did)
      assertTrue(did.startsWith("did:$it"))
      defaultDid = did
    }
    return defaultDid
  }
  
  @Test
  fun test() {
    println("ok")
  }
  
  @Test
  fun testAuthentication() = testApplication {
    val client = createClient {
      install(ContentNegotiation) {
        json()
      }
    }
    application {
      configurePlugins()
      auth()
      accounts()
    }

//    initDb()
    
    println("\nUSE CASE -> REGISTRATION\n")
    
    val email = randomString(8) + "@example.org"
    val password = randomString(16)
    
    client.post("/wallet-api/auth/create") {
      contentType(ContentType.Application.Json)
      setBody(
        mapOf(
          "name" to "mike",
          "email" to email,
          "password" to password,
          "type" to "email"
        )
      )
    }.let { response ->
      assertEquals(HttpStatusCode.Created, response.status)
    }
    
    println("\nUSE CASE -> LOGIN\n")
    
    val token = login(client)
    
    println("Login Successful.")
    
    println("> Response JSON body token: $token")
    
    println("\nUSE CASE -> USER-INFO\n")
    client.get("/wallet-api/auth/user-info") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
    }
    
    println("\nUSE CASE -> SESSION\n")
    client.get("/wallet-api/auth/session") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
    }
    
    println("\nUSE CASE -> LIST WALLETS FOR ACCOUNT\n")
    getWalletFor(client, token)
  }
  
  @Test
  fun testCredentials() = testApplication {
    val client = createClient {
      install(ContentNegotiation) {
        json()
      }
    }
    application {
      configurePlugins()
      auth()
      accounts()
      credentials()
    }
//    init
    
    // login with preset user
    val token = login(client)
    
    println("Login Successful.")
    
    // get the wallet for that user
    val wallets = getWalletFor(client, token)
    val walletId = wallets.jsonObject["id"]?.jsonPrimitive?.content
    
    
    println("\nUSE CASE -> LIST CREDENTIALS FOR WALLET, id = $walletId\n")
    
    val id = client.get("/wallet-api/wallet/${walletId}/credentials") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      assertNotEquals(response.body<JsonArray>().size, 0)
      response.body<JsonArray>()[0].jsonObject["id"]?.jsonPrimitive?.content ?: error("No credentials found")
    }
    
    val endpoint = "/wallet-api/wallet/${walletId}/credentials/$id"
    println(">>>>>>>>>>>>>>>>>>>>>> endpoint for view -> $endpoint")
    println("\nUSE CASE -> VIEW CREDENTIAL BY ID\n")
    
    val vc = client.get(endpoint) {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      response.body<JsonObject>()["document"]?.jsonPrimitive?.content ?: error("No document found")
    }
    
    println("Found Credential -> $vc")
    
    println("\nUSE CASE -> DELETE CREDENTIAL\n")
    
    println(">>>>>>>>>>>>>>>>>>>>>> endpoint for delete -> $endpoint")
    
    client.delete(endpoint) {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.Accepted, response.status)
    }
    
    // rerun the list credential to make sure it really has been removed
    client.get("/wallet-api/wallet/${walletId}/credentials") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      assertEquals(response.body<JsonArray>().size, 0) // make sure no credentials in this wallet
    }
  }
  
  
  @Test
  fun testDids() = testApplication {
    val client = createClient {
      install(ContentNegotiation) {
        json()
      }
    }
    application {
      configurePlugins()
      auth()
      accounts()
      dids()
    }
//    init
    
    // login with preset user
    val token = login(client)
    
    println("TestDids: Login Successful.")
    
    // get the wallet for that user
    val wallets = getWalletFor(client, token)
    val walletId = wallets.jsonObject["id"]?.jsonPrimitive?.content
    
    println("Use Case -> List DIDs")
    val did = client.get("/wallet-api/wallet/$walletId/dids") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      response.body<JsonArray>()[0].jsonObject["did"]?.jsonPrimitive?.content ?: error("No dids found")
    }
    assertNotNull(walletId)
    assertTrue(did.startsWith("did:key:z6Mk"))
    
    println("Use Case -> Show a specific DID")
    client.get("/wallet-api/wallet/$walletId/dids/$did") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      println("DID found!")
    }
    
    println("Use Case -> Delete a DID")
    client.delete("/wallet-api/wallet/$walletId/dids/$did") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.Accepted, response.status)
      println("DID deleted!")
    }
    
    println("Use Case -> Show deleted DID fails")
    client.get("/wallet-api/wallet/$walletId/dids/$did") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.BadRequest, response.status)
      println("Invalid authentication: DID not found!")
    }
    
    val defaultDid = createDids(client, token, walletId)
    
    println("\nUse Case -> Set default did to $defaultDid\n")
    client.post("/wallet-api/wallet/$walletId/dids/default?did=$defaultDid") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.Accepted, response.status)
    }
  }
  
  @Test
  fun testKeys() = testApplication {
    val client = createClient {
      install(ContentNegotiation) {
        json()
      }
    }
    application {
      configurePlugins()
      auth()
      accounts()
      keys()
    }
    // login with preset user
    val token = login(client)
    
    println("TestKeys: Login Successful.")
    
    // get the wallet for that user
    val wallets = getWalletFor(client, token)
    val walletId = wallets.jsonObject["id"]?.jsonPrimitive?.content
    assertNotNull(walletId)
    
    println("Use Case -> List Keys")
    val keys = client.get("/wallet-api/wallet/$walletId/keys") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      val rsaKeys = response.body<JsonArray>().filter { item -> item.jsonObject["algorithm"]?.jsonPrimitive?.content == "RSA" }
      assertEquals(0, rsaKeys.size) // ensure no RSA keys in the list of keys for this wallet
      response.body<JsonArray>()[0].jsonObject
    }
    val algorithm = keys["algorithm"]?.jsonPrimitive?.content
    assertEquals("Ed25519", algorithm)
    
    
    println("Use Case -> Generate new key of type RSA")
    client.post("/wallet-api/wallet/$walletId/keys/generate?type=RSA") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
    }
    
    // list keys again to ensure the RSA key is there
    client.get("/wallet-api/wallet/$walletId/keys") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      val rsaKeys = response.body<JsonArray>().filter { item -> item.jsonObject["algorithm"]?.jsonPrimitive?.content == "RSA" }
      assertEquals(1, rsaKeys.size) // ensure now 1 RSA key in the list of keys for this wallet
    }
    
  }
}
