import id.walt.crypto.utils.JsonUtils.toJsonElement
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.test.*

abstract class WalletApiTeste2eBase {
  
  private val didMethodsToTest = listOf("key", "jwk", "web", "cheqd")
  private val alphabet = ('a'..'z')
  private lateinit var token: String
  private lateinit var walletId: String
  
  
  private fun randomString(length: Int) = (1..length).map { alphabet.random() }.toTypedArray().contentToString()
  
  protected val email = randomString(8) + "@example.org"
  protected val password = randomString(16)
  
  abstract var walletClient: HttpClient
  abstract var issuerClient: HttpClient
  abstract var apiUrl: String
  
  protected suspend fun testCreateUser(user: User) = run {
    println("\nUse Case -> Register User $user\n")
    val endpoint = "$apiUrl/wallet-api/auth/create"
    println("POST ($endpoint)\n")
    
    walletClient.post(endpoint) {
      contentType(ContentType.Application.Json)
      setBody(
        mapOf(
          "name" to "tester",
          "email" to user.email,
          "password" to user.password,
          "type" to "email"
        )
      )
    }.let { response ->
      assertEquals(HttpStatusCode.Created, response.status)
    }
  }
  
  private suspend fun testIssueJwtCredential() = run {
    println("\nUse Case -> Issue JWT Credential")
    val endpoint = "$apiUrl/openid4vc/jwt/issue"
    println("POST ($endpoint)")
    println("Credential for Issuance = ${Credential().testCredential}")
    issuerClient.post(endpoint) {
      contentType(ContentType.Application.Json)
      setBody(Credential().testCredential)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
    }
  }
  
  private suspend fun testExampleKey() = run {
    println("\nUse Case -> Create Example Key")
    val endpoint = "$apiUrl/example-key"
    println("GET ($endpoint)")
    issuerClient.get(endpoint) {
      contentType(ContentType.Application.Json)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
    }
  }
  private suspend fun testLogin(user: User) = run {
    println("\nUse Case -> Login with user $user")
    val endpoint = "$apiUrl/wallet-api/auth/login"
    println("POST ($endpoint)")
    token = walletClient.post(endpoint) {
      contentType(ContentType.Application.Json)
      setBody(
        mapOf(
          "name" to user.name,
          "email" to user.email,
          "password" to user.password,
          "type" to user.accountType
        )
      )
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      response.body<JsonObject>()["token"]?.jsonPrimitive?.content ?: error("No token responded")
    }
    println("Login Successful.")
    println("> Response JSON body token: $token")
  }
  
  private suspend fun testListWallets(): JsonElement {
    println("\nUse Case -> List Wallets for Account\n")
    val endpoint = "$apiUrl/wallet-api/wallet/accounts/wallets"
    println("GET($endpoint)")
    return walletClient.get(endpoint) {
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      response.body<JsonObject>()["wallets"]?.jsonArray?.elementAt(0) ?: error("No wallets found")
    }
  }
  
  private suspend fun createDids(walletId: String): String {
    var defaultDid: String = ""
    didMethodsToTest.forEach {
      println("\nUse Case -> Create a did:$it\n")
      val did = walletClient.post("/wallet-api/wallet/$walletId/dids/create/$it") {
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
  
  private suspend fun testUserInfo() = run {
    println("\nUse Case -> User Info\n")
    val endpoint = "$apiUrl/wallet-api/auth/user-info"
    println("GET ($endpoint)")
    walletClient.get(endpoint) {
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
    }
  }
  
  private suspend fun testUserSession() = run {
    println("\nUse Case -> Session\n")
    val endpoint = "$apiUrl/wallet-api/auth/session"
    println("GET ($endpoint")
    walletClient.get(endpoint) {
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
    }
  }
  
  private suspend fun listCredentials(): JsonArray = run {
    val wallets = testListWallets()
    walletId = wallets.jsonObject["id"]?.jsonPrimitive?.content.toString()
    
    println("\nUse -> List credentials for wallet, id = $walletId\n")
    
    val endpoint = "$apiUrl/wallet-api/wallet/${walletId}/credentials"
    
    println("GET $endpoint")
    walletClient.get(endpoint) {
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      response.body<JsonArray>()
    }
  }
  
  
  //  @Test
//  fun testDids() = testApplication {
//    val client = createClient {
//      install(ContentNegotiation) {
//        json()
//      }
//    }
//    application {
//      configurePlugins()
//      auth()
//      accounts()
//      dids()
//    }
////    init
//
//    // login with preset user
//    val token = login(client)
//
//    println("TestDids: Login Successful.")
//
//    // get the wallet for that user
//    val wallets = getWalletFor(client, token)
//    val walletId = wallets.jsonObject["id"]?.jsonPrimitive?.content
//
//    println("Use Case -> List DIDs")
//    val did = client.get("/wallet-api/wallet/$walletId/dids") {
//      contentType(ContentType.Application.Json)
//      bearerAuth(token)
//    }.let { response ->
//      assertEquals(HttpStatusCode.OK, response.status)
//      response.body<JsonArray>()[0].jsonObject["did"]?.jsonPrimitive?.content ?: error("No dids found")
//    }
//    assertNotNull(walletId)
//    assertTrue(did.startsWith("did:key:z6Mk"))
//
//    println("Use Case -> Show a specific DID")
//    client.get("/wallet-api/wallet/$walletId/dids/$did") {
//      contentType(ContentType.Application.Json)
//      bearerAuth(token)
//    }.let { response ->
//      assertEquals(HttpStatusCode.OK, response.status)
//      println("DID found!")
//    }
//
//    println("Use Case -> Delete a DID")
//    client.delete("/wallet-api/wallet/$walletId/dids/$did") {
//      contentType(ContentType.Application.Json)
//      bearerAuth(token)
//    }.let { response ->
//      assertEquals(HttpStatusCode.Accepted, response.status)
//      println("DID deleted!")
//    }
//
//    println("Use Case -> Show deleted DID fails")
//    client.get("/wallet-api/wallet/$walletId/dids/$did") {
//      contentType(ContentType.Application.Json)
//      bearerAuth(token)
//    }.let { response ->
//      assertEquals(HttpStatusCode.BadRequest, response.status)
//      println("Invalid authentication: DID not found!")
//    }
//
//    val defaultDid = createDids(client, token, walletId)
//
//    println("\nUse Case -> Set default did to $defaultDid\n")
//    client.post("/wallet-api/wallet/$walletId/dids/default?did=$defaultDid") {
//      contentType(ContentType.Application.Json)
//      bearerAuth(token)
//    }.let { response ->
//      assertEquals(HttpStatusCode.Accepted, response.status)
//    }
//  }
//
//  @Test
//  fun testKeys() = testApplication {
//    val client = createClient {
//      install(ContentNegotiation) {
//        json()
//      }
//    }
//    application {
//      configurePlugins()
//      auth()
//      accounts()
//      keys()
//    }
//    // login with preset user
//    val token = login(client)
//
//    println("TestKeys: Login Successful.")
//
//    // get the wallet for that user
//    val wallets = getWalletFor(client, token)
//    val walletId = wallets.jsonObject["id"]?.jsonPrimitive?.content
//    assertNotNull(walletId)
//
//    println("Use Case -> List Keys")
//    val keys = client.get("/wallet-api/wallet/$walletId/keys") {
//      contentType(ContentType.Application.Json)
//      bearerAuth(token)
//    }.let { response ->
//      assertEquals(HttpStatusCode.OK, response.status)
//      val rsaKeys =
//        response.body<JsonArray>().filter { item -> item.jsonObject["algorithm"]?.jsonPrimitive?.content == "RSA" }
//      assertEquals(0, rsaKeys.size) // ensure no RSA keys in the list of keys for this wallet
//      response.body<JsonArray>()[0].jsonObject
//    }
//    val algorithm = keys["algorithm"]?.jsonPrimitive?.content
//    assertEquals("Ed25519", algorithm)
//
//
//    println("Use Case -> Generate new key of type RSA")
//    client.post("/wallet-api/wallet/$walletId/keys/generate?type=RSA") {
//      contentType(ContentType.Application.Json)
//      bearerAuth(token)
//    }.let { response ->
//      assertEquals(HttpStatusCode.OK, response.status)
//    }
//
//    // list keys again to ensure the RSA key is there
//    client.get("/wallet-api/wallet/$walletId/keys") {
//      contentType(ContentType.Application.Json)
//      bearerAuth(token)
//    }.let { response ->
//      assertEquals(HttpStatusCode.OK, response.status)
//      val rsaKeys =
//        response.body<JsonArray>().filter { item -> item.jsonObject["algorithm"]?.jsonPrimitive?.content == "RSA" }
//      assertEquals(1, rsaKeys.size) // ensure now 1 RSA key in the list of keys for this wallet
//    }
//
//  }
  suspend fun testAuthenticationEndpoints(user: User) {
    testLogin(user)
    testUserInfo()
    testUserSession()
    testListWallets()
  }
  
  suspend fun testCredentialEndpoints(user: User = User("tester", "user@email.com", "password", "email")): JsonArray {
    testLogin(user)
    testListWallets()
    return listCredentials()
  }
  
  suspend fun testCredentialIssuance() = run {
    testIssueJwtCredential()
  }
  
  suspend fun testKeys() = run {
    testExampleKey()
  }
//    val id = listCredentials()
//    val endpoint = "/wallet-api/wallet/${walletId}/credentials/$id"
//    println(">>>>>>>>>>>>>>>>>>>>>> endpoint for view -> $endpoint")
//    println("\nUSE CASE -> VIEW CREDENTIAL BY ID\n")
//
//    val vc = client.get(endpoint) {
//      contentType(ContentType.Application.Json)
//      bearerAuth(token)
//    }.let { response ->
//      assertEquals(HttpStatusCode.OK, response.status)
//      response.body<JsonObject>()["document"]?.jsonPrimitive?.content ?: error("No document found")
//    }
//
//    println("Found Credential -> $vc")
//
//    println("\nUSE CASE -> DELETE CREDENTIAL\n")
//
//    println(">>>>>>>>>>>>>>>>>>>>>> endpoint for delete -> $endpoint")
//
//    client.delete(endpoint) {
//      contentType(ContentType.Application.Json)
//      bearerAuth(token)
//    }.let { response ->
//      assertEquals(HttpStatusCode.Accepted, response.status)
//    }
//
//    // rerun the list credential to make sure it really has been removed
//    client.get("/wallet-api/wallet/${walletId}/credentials") {
//      contentType(ContentType.Application.Json)
//      bearerAuth(token)
//    }.let { response ->
//      assertEquals(HttpStatusCode.OK, response.status)
//      assertEquals(response.body<JsonArray>().size, 0) // make sure no credentials in this wallet
//    }
//  }
//  }
}