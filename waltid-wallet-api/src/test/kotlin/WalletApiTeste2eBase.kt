import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

abstract class WalletApiTeste2eBase {
//  private val didMethodsToTest = listOf("key", "jwk", "web", "cheqd") //22/02/24 cheqd resolver broken awaiting fix
  
  private val defaultTestUser = User("tester", "user@email.com", "password", "email")
  
  private val didMethodsToTest = listOf("key", "jwk", "web")
  
  private val alphabet = ('a'..'z')
  private lateinit var token: String
  private lateinit var walletId: String
  
  private fun randomString(length: Int) = (1..length).map { alphabet.random() }.toTypedArray().contentToString()
  
  protected val email = randomString(8) + "@example.org"
  protected val password = randomString(16)
  
  abstract var walletClient: HttpClient
  abstract var issuerClient: HttpClient
  abstract var walletUrl: String
  abstract var issuerUrl: String
  
  protected suspend fun testCreateUser(user: User) {
    println("\nUse Case -> Register User $user\n")
    val endpoint = "$walletUrl/wallet-api/auth/create"
    println("POST ($endpoint)\n")
    
    walletClient.post(endpoint) {
      contentType(ContentType.Application.Json)
      setBody(
        mapOf(
          "name" to "tester",
          "email" to user.email,
          "password" to user.password,
          "type" to "email"
        ),
      )
    }.let { response ->
      assertEquals(HttpStatusCode.Created, response.status)
    }
  }
  
  private suspend fun testUseOfferRequest(offerUri: String) {
    println("\nUse Case -> Use Offer Request")
    val endpoint = "$walletUrl/wallet-api/wallet/$walletId/exchange/useOfferRequest"
    println("POST ($endpoint)")
  }
  
  private suspend fun testIssueJwtCredential(): String = run {
    println("\nUse Case -> Issue JWT Credential")
    val endpoint = "$issuerUrl/openid4vc/jwt/issue"
    println("POST ($endpoint)")
    println("Credential for Issuance = ${Credential.testCredential}")
    return issuerClient.post("$issuerUrl/openid4vc/jwt/issue") {
      contentType(ContentType.Application.Json)
      setBody(Credential.testCredential)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      response.body<String>()
    }
  }
  
  private suspend fun testExampleKey() = run {
    println("\nUse Case -> Create Example Key")
    val endpoint = "$walletUrl/example-key"
    println("GET ($endpoint)")
    issuerClient.get(endpoint) {
      contentType(ContentType.Application.Json)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
    }
  }
  
  private suspend fun getTokenFor(user: User) = run {
    println("\nUse Case -> Login with user $user")
    val endpoint = "$walletUrl/wallet-api/auth/login"
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
  
  private suspend fun getWallets() {
    println("\nUse Case -> List Wallets for Account\n")
    val endpoint = "$walletUrl/wallet-api/wallet/accounts/wallets"
    println("GET($endpoint)")
    walletClient.get(endpoint) {
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      val wallets = response.body<JsonObject>()["wallets"]?.jsonArray?.elementAt(0) ?: error("No wallets found")
      walletId = wallets.jsonObject["id"]?.jsonPrimitive?.content.toString()
    }
  }
  
  private suspend fun createDid(didType: String): String {
    val did = walletClient.post("$walletUrl/wallet-api/wallet/$walletId/dids/create/$didType") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      response.bodyAsText()
    }
    println("did:$didType created, did = $did")
    assertNotNull(did)
    assertTrue(did.startsWith("did:$didType"))
    return did
  }
  
  private suspend fun createDids() {
    didMethodsToTest.forEach {
      println("\nUse Case -> Create a did:$it\n")
      createDid(it)
    }
  }
  
  private suspend fun testUserInfo() {
    println("\nUse Case -> User Info\n")
    val endpoint = "$walletUrl/wallet-api/auth/user-info"
    println("GET ($endpoint)")
    walletClient.get(endpoint) {
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
    }
  }
  
  private suspend fun testUserSession() {
    println("\nUse Case -> Session\n")
    val endpoint = "$walletUrl/wallet-api/auth/session"
    println("GET ($endpoint")
    walletClient.get(endpoint) {
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
    }
  }
  
  private suspend fun listCredentials(): JsonArray = run {
    getWallets()
    println("\nUse -> List credentials for wallet, id = $walletId\n")
    
    val endpoint = "$walletUrl/wallet-api/wallet/${walletId}/credentials"
    
    println("GET $endpoint")
    walletClient.get(endpoint) {
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      response.body<JsonArray>()
    }
  }
  
  private suspend fun listAllDids(): List<String> {
    val endpoint = "$walletUrl/wallet-api/wallet/$walletId/dids"
    println("GET $endpoint")
    val list = arrayListOf<String>()
    
    walletClient.get(endpoint) {
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      response.body<JsonArray>().forEach() {
        it.jsonObject["did"]?.jsonPrimitive?.content?.let { it1 -> list.add(it1) }
      }
    }
    return list
  }
  
  private suspend fun deleteAllDids(dids: List<String>) {
    println("\nUse Case -> Delete DIDs\n")
    
    dids.forEach {
      val endpoint = "$walletUrl/wallet-api/wallet/$walletId/dids/$it"
      println("DELETE $endpoint")
      walletClient.delete(endpoint) {
        bearerAuth(token)
      }.let { response ->
        assertEquals(HttpStatusCode.Accepted, response.status)
        println("DID deleted!")
      }
    }
  }
  
  
  private suspend fun testDids() {
    
    println("\nUse Case -> Show a specific DID\n")
    var endpoint = "$walletUrl/wallet-api/wallet/$walletId/dids"
    println("GET $endpoint")
    walletClient.get(endpoint) {
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      println("DID found!")
    }

//    println("\nUse Case -> Show deleted DID fails when retrieved\n")
//    endpoint = "$walletUrl/wallet-api/wallet/$walletId/dids/$did"
//    println("GET $endpoint")
//    walletClient.get(endpoint) {
//      bearerAuth(token)
//    }.let { response ->
//      assertEquals(HttpStatusCode.BadRequest, response.status)
//      println("Invalid did: not found!")
//    }
//    val defaultDid = createDids()

//    println("Dids created, default = $defaultDid")
//
  
  }
  
  private suspend fun testKeys() {
    println("\nUse Case -> List Keys\n")
    var endpoint = "$walletUrl/wallet-api/wallet/$walletId/keys"
    println("GET $endpoint")
    val keys = walletClient.get(endpoint) {
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
      assertEquals(HttpStatusCode.OK, response.status)
      response.body<JsonArray>()[0].jsonObject
    }
    val algorithm = keys["algorithm"]?.jsonPrimitive?.content
    assertEquals("Ed25519", algorithm)
    
    println("\nUse Case -> Generate new key of type RSA\n")
    endpoint = "$walletUrl/wallet-api/wallet/$walletId/keys/generate?type=RSA"
    println("POST $endpoint")
    walletClient.post(endpoint) {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
    }.let { response ->
      assertEquals(HttpStatusCode.OK, response.status)
    }
    
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
  }
  
  suspend fun testAuthenticationEndpoints(user: User) {
    getTokenFor(user)
    testUserInfo()
    testUserSession()
    getWallets()
  }
  
  suspend fun testCredentialEndpoints(user: User = defaultTestUser): JsonArray {
    getTokenFor(user)
    getWallets()
    return listCredentials()
  }
  
  suspend fun testCredentialIssuance(user: User = defaultTestUser) {
    getTokenFor(user)
    getWallets()
    val offerUri = testIssueJwtCredential()
    testUseOfferRequest(offerUri)
  }
  
  suspend fun testDidsList(user: User = defaultTestUser) = run {
    getTokenFor(user)
    getWallets()
    println("\nUse Case -> List DIDs\n")
    println("Number of Dids found: ${listAllDids().size}")
  }
  
  suspend fun testDefaultDid(user: User = defaultTestUser) {
    getTokenFor(user)
    getWallets()
    println("\nUse Case -> Delete DIDs\n")
   
    listAllDids().let { dids ->
      assertNotEquals(0, dids.size)
      val defaultDid = dids[0]
      println("\nUse Case -> Set default did to $defaultDid\n")
      val endpoint = "$walletUrl/wallet-api/wallet/$walletId/dids/default?did=$defaultDid"
      println("POST $endpoint")
      walletClient.post(endpoint) {
        bearerAuth(token)
      }.let { response ->
        assertEquals(HttpStatusCode.Accepted, response.status)
      }
    }
  }
  
  suspend fun testDidsDelete(user: User = defaultTestUser) = run {
    getTokenFor(user)
    getWallets()
    println("\nUse Case -> Delete DIDs\n")
    listAllDids().let { dids ->
      println("Number of Dids found: ${dids.size}")
      dids.forEach {
        println(" DID: $it")
      }
      deleteAllDids(dids)
//    testDids()
    }
  }
  
  suspend fun testDidsCreate(user: User = defaultTestUser) = run {
    getTokenFor(user)
    getWallets()
    println("\nUse Case -> Create DIDs\n")
    createDids()
  }
  
  suspend fun testKeyEndpoints(user: User = defaultTestUser) {
    getTokenFor(user)
    getWallets()
    testKeys()
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