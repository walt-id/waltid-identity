package id.walt.webwallet.e2e

import id.walt.issuer.IssuanceExamples.issuerOnboardingRequestDefaultExample
import id.walt.issuer.IssuerOnboardingResponse
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.utils.IssuanceExamples
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.web.model.LoginRequestJson
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import id.walt.webwallet.config.DatasourceConfiguration
import id.walt.webwallet.webWalletSetup
import io.ktor.server.testing.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import id.walt.webwallet.config.ConfigManager as WalletConfigManager
import id.walt.webwallet.config.WebConfig as WalletWebConfig

abstract class E2EWalletTestBase {
    private val didMethodsToTest = listOf("key", "jwk", "web")
    
    private val defaultTestUser = User("tester", "user@email.com", "password", "email")
    
    private val alphabet = ('a'..'z')
    protected lateinit var token: String
    protected lateinit var walletId: UUID
    private lateinit var firstDid: String
    
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
    
    private fun randomString(length: Int) = (1..length).map { alphabet.random() }.toTypedArray().joinToString("")
    
    protected val email = randomString(8) + "@example.org"
    protected val password = randomString(16)
    
    abstract var walletClient: HttpClient
    
    abstract var walletUrl: String
    abstract var issuerUrl: String
    abstract var verifierUrl: String
    
    protected fun deleteAllCredentials() = testApplication {
        val response: JsonArray = listCredentials()
        assertNotEquals(response.size, 0)
        response.forEach {
            val id = it.jsonObject["id"]?.jsonPrimitive?.content ?: error("No credentials found")
            deleteCredential(id)
        }
        val resp: JsonArray = listCredentials()
        assertEquals(resp.size, 0)
    }
    
    protected fun issueNewCredential() = testApplication {
        val availableDids = listAllDids()
        
        val issuanceUri = issueJwtCredential()
        println("Issuance Offer uri = $issuanceUri")
        
        // Request credential and store in wallet
        val vc: JsonObject = requestCredential(issuanceUri, availableDids.first().did)
    }
    protected suspend fun testCreateUser(user: User) {
        println("\nUse Case -> Register User $user\n")
        val endpoint = "$walletUrl/wallet-api/auth/create"
        println("POST ($endpoint)\n")
        
        walletClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "name" to user.name,
                    "email" to user.email,
                    "password" to user.password,
                    "type" to user.accountType
                ),
            )
        }
    }
    
    protected suspend fun matchCredentialByPresentationDefinition(presentationDefinition: String): JsonArray {
        println("\nUse Case -> Match Credentials for Presentation Definition\n")
        
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/exchange/matchCredentialsForPresentationDefinition"
        println("POST ($endpoint)\n")
        
        return walletClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(
               presentationDefinition
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            println("Number credentials matched = ${response.body<JsonArray>().size}")
            response.body<JsonArray>()
        }
    }
    
    protected suspend fun requestCredential(issuanceUri: String, did: String): JsonObject {
        println("\nUse Case -> Use Offer Request")
        return walletClient.post("$walletUrl/wallet-api/wallet/$walletId/exchange/useOfferRequest") {
            parameter("did", did)
            contentType(ContentType.Text.Plain)
            setBody(issuanceUri)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            response.body<JsonArray>()[0].jsonObject
        }
    }
    
    protected suspend fun issueJwtCredential(): String = run {
        
        val endpoint = "$issuerUrl/openid4vc/jwt/issue"
        println("POST ($endpoint)\n")
        
        println("Calling issuer...")
        val issuanceUri = walletClient.post(endpoint) {
            //language=JSON
            contentType(ContentType.Application.Json)
            setBody(
                IssuanceExamples.testCredential
            )
        }.bodyAsText()

        println("Issuance (Offer) URI: $issuanceUri\n")
        return issuanceUri
    }

    protected suspend fun onboardIssuer() = run {

        val endpoint = "$issuerUrl/onboard/issuer"
        println("POST ($endpoint)\n")

        println("Calling issuer...")
        val issuerOnboardingRespStr = walletClient.post(endpoint) {
            //language=JSON
            contentType(ContentType.Application.Json)
            setBody(
                issuerOnboardingRequestDefaultExample
            )
        }.also { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }.bodyAsText()

        val issuerResponse = Json.decodeFromString<IssuerOnboardingResponse>(issuerOnboardingRespStr)

        println("Onboarding returned: $issuerResponse\n")

    }

    protected suspend fun login(user: User = defaultTestUser) = run {
        println("Running login...")
        walletClient.post("$walletUrl/wallet-api/auth/login") {
            setBody(
                LoginRequestJson.encodeToString(
                    EmailAccountRequest(
                        email = user.email, password = user.password
                    ) as AccountRequest
                )
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
    
    protected suspend fun getUserToken(user: User = defaultTestUser) = run {
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
    
    protected suspend fun listAllWallets() {
        println("\nUse Case -> List Wallets for Account\n")
        val endpoint = "$walletUrl/wallet-api/wallet/accounts/wallets"
        println("GET($endpoint)")
        
        val walletListing = walletClient.get(endpoint)
            .body<AccountWalletListing>()
        println("Wallet listing: $walletListing\n")
        
        val availableWallets = walletListing.wallets
        assertTrue { availableWallets.isNotEmpty() }
        walletId = availableWallets.first().id
    }
    
    protected suspend fun createDid(didType: String): String {
        val did = walletClient.post("$walletUrl/wallet-api/wallet/$walletId/dids/create/$didType").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            response.bodyAsText()
        }
        println("did:$didType created, did = $did")
        assertNotNull(did)
        assertTrue(did.startsWith("did:$didType"))
        return did
    }
    
    protected suspend fun createDids() {
        didMethodsToTest.forEach {
            println("\nUse Case -> Create a did:$it\n")
            createDid(it)
        }
    }
    
    protected suspend fun testUserInfo() {
        println("\nUse Case -> User Info\n")
        val endpoint = "$walletUrl/wallet-api/auth/user-info"
        println("GET ($endpoint)")
        assertEquals(HttpStatusCode.OK, walletClient.get(endpoint){}.status)
    }
    
    protected suspend fun testUserSession() {
        println("\nUse Case -> Session\n")
        val endpoint = "$walletUrl/wallet-api/auth/session"
        println("GET ($endpoint")
        assertEquals(HttpStatusCode.OK, walletClient.get(endpoint){}.status)
    }
    
    protected suspend fun deleteCredential(credentialId: String) {
        println("\nUse Case -> Delete Credential\n")
        
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/credentials/$credentialId"
        println("DELETE ($endpoint")
        
        assertEquals(HttpStatusCode.Accepted, walletClient.delete(endpoint).status)
    }
    
    protected suspend fun viewCredential(credentialId: String) {
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/credentials/$credentialId"
        println("GET ($endpoint")
        println("\nUse Case -> View Credential By Id\n")
        
        walletClient.get(endpoint).let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val vc = response.body<JsonObject>()["parsedDocument"]
            println("Found Credential -> $vc")
        }
    }
    
    protected suspend fun listCredentials(): JsonArray = run {
        println("\nUse -> List credentials for wallet, id = $walletId\n")
        
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/credentials"
        
        println("GET $endpoint")
        walletClient.get(endpoint).let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            response.body<JsonArray>()
        }
    }
    
    protected suspend fun listAllDids(): List<WalletDid> {
        println("Running DID listing...")
        val availableDids = walletClient.get("$walletUrl/wallet-api/wallet/$walletId/dids").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            println("DID deleted!")
            response.body<List<WalletDid>>()
        }
        
        println("DID listing: $availableDids\n")
        
        if (availableDids.isNotEmpty()) {
            firstDid = availableDids.first().did
        }
        return availableDids
    }
    
    protected suspend fun deleteAllDids(dids: List<WalletDid>) {
        println("\nUse Case -> Delete DIDs\n")
        
        dids.forEach {
            val endpoint = "$walletUrl/wallet-api/wallet/$walletId/dids/${it.did}"
            println("DELETE $endpoint")
            walletClient.delete(endpoint).let { response ->
                assertEquals(HttpStatusCode.Accepted, response.status)
                println("DID deleted!")
            }
        }
    }
    
    protected suspend fun testCreateRSAKey() {
        println("\nUse Case -> Generate new key of type RSA\n")
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/keys/generate?type=RSA"
        println("POST $endpoint")
        assertEquals(HttpStatusCode.OK, walletClient.post(endpoint).status)
    }
    
    protected suspend fun deleteKeys() {
        println("\nUse Case -> Delete Keys\n")
        listAllKeys().let { keys ->
            keys.forEach{
                val keyId = it.jsonObject["keyId"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                val endpoint = "$walletUrl/wallet-api/wallet/$walletId/keys/$keyId"
                println("DELETE $endpoint")
                walletClient.delete(endpoint).let { response ->
                    assertEquals(HttpStatusCode.Accepted, response.status)
                    println("Key deleted!")
                }
            }
        }
    }
    
    private suspend fun listAllKeys(): JsonArray {
        println("\nUse Case -> List Keys\n")
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/keys"
        println("GET $endpoint")
        return walletClient.get(endpoint).let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            response.body<JsonArray>()
        }
    }
    
    protected suspend fun testKeys() {
        println("\nUse Case -> List Keys\n")
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/keys"
        println("GET $endpoint")
        val keys = walletClient.get(endpoint).let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            response.body<JsonArray>()[0].jsonObject
        }
        val algorithm = keys["algorithm"]?.jsonPrimitive?.content
        assertEquals("RSA", algorithm)
    }
    
    suspend fun testDefaultDid() {
        println("\nUse Case -> Delete DIDs\n")
        listAllDids().let { dids ->
            assertNotEquals(0, dids.size)
            val defaultDid = dids[0]
            println("\nUse Case -> Set default did to ${defaultDid.did}\n")
            val endpoint = "$walletUrl/wallet-api/wallet/$walletId/dids/default?did=${defaultDid.did}"
            println("POST $endpoint")
            walletClient.post(endpoint) {
                contentType(ContentType.Application.Json)
            }.let { response ->
                assertEquals(HttpStatusCode.Accepted, response.status)
            }
        }
    }
    // Verifier Tests
    protected suspend fun testPolicyList(): JsonObject = run {
        println("\nUse Case -> List Verification Policies\n")
        val endpoint = "$verifierUrl/openid4vc/policy-list"
        println("GET $endpoint")
        return walletClient.get(endpoint).let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            response.body<JsonObject>()
        }
    }
    
    protected suspend fun testVerifyCredential(presentationDefinition: String): String = run {
        val endpoint = "$verifierUrl/openid4vc/verify"
        println("POST ($endpoint)\n")
        
        println("Calling verifier...")
        val verifyUri = walletClient.post(endpoint) {
            //language=JSON
            contentType(ContentType.Application.Json)
            setBody(
                presentationDefinition
            )
        }.bodyAsText()
        return verifyUri
    }
    
    protected suspend fun testSession(id: String) = run {
        println("\nUse Case -> Test Verification Session\n")
        val endpoint = "$verifierUrl/openid4vc/session/$id"
        println("GET $endpoint")
        assertEquals(HttpStatusCode.OK, walletClient.get(endpoint).status)
    }
    
    protected suspend fun resolvePresentationRequest(url: String): String = run {
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/exchange/resolvePresentationRequest"
        
        println("POST ($endpoint)\n")
        
        val presentationRequest = walletClient.post(endpoint) {
            //language=JSON
            contentType(ContentType.Application.Json)
            setBody(
                url
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            response.bodyAsText()
        }
        return presentationRequest
    }
    
    protected suspend fun usePresentationRequest(json: String) = run {
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/exchange/usePresentationRequest"
        
        println("POST ($endpoint)\n")
        
        walletClient.post(endpoint) {
            //language=JSON
            contentType(ContentType.Application.Json)
            setBody(
                json
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
}
