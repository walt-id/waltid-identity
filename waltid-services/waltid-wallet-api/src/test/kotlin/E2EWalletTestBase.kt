@file:OptIn(ExperimentalUuidApi::class)

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.issuer.issuance.IssuerOnboardingResponse
import id.walt.issuer.issuance.openapi.issuerapi.IssuanceExamples
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

abstract class E2EWalletTestBase {
    private val didMethodsToTest = listOf("key", "jwk", "web")

    private val defaultTestUser = User("tester", "user@email.com", "password", "email")

    private val alphabet = ('a'..'z')
    protected lateinit var token: String
    protected lateinit var walletId: Uuid
    private lateinit var firstDid: String


    private fun randomString(length: Int) = (1..length).map { alphabet.random() }.toTypedArray().joinToString("")

    protected val email = randomString(8) + "@example.org"
    protected val password = randomString(16)

    abstract var walletClient: HttpClient

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
                    "name" to user.name,
                    "email" to user.email,
                    "password" to user.password,
                    "type" to user.accountType
                ),
            )
        }
    }

    protected suspend fun requestCredential(issuanceUri: String, did: String): String {
        println("\nUse Case -> Use Offer Request")
        val result = walletClient.post("$walletUrl/wallet-api/wallet/$walletId/exchange/useOfferRequest") {
            parameter("did", did)
            contentType(ContentType.Text.Plain)
            setBody(issuanceUri)
        }
        println("Claim result: $result")
        assertEquals(HttpStatusCode.OK, result.status)
        val credentials = result.body<JsonArray>()
        assertTrue(credentials.isNotEmpty(), "No credentials returned after claim")
        return credentials[0].jsonObject["document"]?.jsonPrimitive?.content ?: error("No document in claimed credential")
    }

    protected suspend fun issueSdJwtCredential(): String = run {
        val endpoint = "$issuerUrl/openid4vc/sdjwt/issue"
        println("POST ($endpoint)\n")

        println("Calling issuer...")
        val issuanceUri = walletClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(IssuanceExamples.sdJwtVCData)
        }.bodyAsText()

        println("Issuance (Offer) URI: $issuanceUri\n")
        return issuanceUri
    }

    protected suspend fun issueJwtCredential(): String = run {

        val endpoint = "$issuerUrl/openid4vc/jwt/issue"
        println("POST ($endpoint)\n")

        println("Calling issuer...")
        val issuanceUri = walletClient.post(endpoint) {
            //language=JSON
            contentType(ContentType.Application.Json)
            setBody(
                IssuanceExamples.openBadgeCredentialIssuance
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
                IssuanceExamples.issuerOnboardingRequestDefaultSecp256r1Example
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
                Json.encodeToString(
                    EmailAccountRequest(
                        email = user.email, password = user.password
                    ) as AccountRequest
                )
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    protected suspend fun getTokenFor(user: User = defaultTestUser) = run {
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

    protected suspend fun listAllWalletsForUser(): List<AccountWalletListing.WalletListing> {
        println("\nUse Case -> List Wallets for Account\n")
        val endpoint = "$walletUrl/wallet-api/wallet/accounts/wallets"
        println("GET($endpoint)")

        val walletListing = walletClient.get(endpoint)
            .body<AccountWalletListing>()
        println("Wallet listing: $walletListing\n")

        val availableWallets = walletListing.wallets
        assertTrue { availableWallets.isNotEmpty() }
        walletId = availableWallets.first().id

        return availableWallets
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
        walletClient.get(endpoint) {
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    protected suspend fun testUserSession() {
        println("\nUse Case -> Session\n")
        val endpoint = "$walletUrl/wallet-api/auth/session"
        println("GET ($endpoint")
        walletClient.get(endpoint) {
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
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
            val vc = response.body<JsonObject>()["document"]?.jsonPrimitive?.content ?: error("No document found")
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

    private suspend fun testKey(keyGenerationRequest: KeyGenerationRequest) {
        println("Testing creation of ${keyGenerationRequest.keyType} with ${keyGenerationRequest.backend}...")
        val result = walletClient.post("$walletUrl/wallet-api/wallet/$walletId/keys/generate") {
            setBody(keyGenerationRequest)
        }
        println("Result for ${keyGenerationRequest.keyType} with ${keyGenerationRequest.backend}: ${result.status}")
        assertEquals(HttpStatusCode.OK, result.status)
    }

    protected suspend fun testKeys() {
        println("\nUse Case -> List Keys\n")
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/keys"
        println("GET $endpoint")
        val keys = walletClient.get(endpoint).let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(HttpStatusCode.OK, response.status)
            response.body<JsonArray>()[0].jsonObject
        }
        val algorithm = keys["algorithm"]?.jsonPrimitive?.content
        assertEquals("Ed25519", algorithm)

        println("\nUse Case -> Generate new key of type Ed25519\n")
        listOf(
            KeyGenerationRequest(backend = "jwk", keyType = KeyType.Ed25519)
        ).forEach {
            testKey(it)
        }
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

    /**
     * Import a credential (W3C JWT or SD-JWT) into the wallet.
     * The credential must be bound to a DID that exists in this wallet.
     */
    protected suspend fun importCredential(credential: String, associatedDid: String): String {
        println("\nUse Case -> Import Credential\n")
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/credentials/import"
        println("POST $endpoint")

        val response = walletClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(mapOf(
                "jwt" to credential,
                "associated_did" to associatedDid
            ))
        }
        
        assertEquals(HttpStatusCode.Created, response.status)
        val result = response.body<JsonObject>()
        val credentialId = result["id"]?.jsonPrimitive?.content ?: error("No credential ID in response")
        
        println("Credential imported successfully with ID: $credentialId")
        return credentialId
    }

    /**
     * Import a W3C JWT VC into the wallet
     */
    protected suspend fun importW3cJwtCredential(jwtVc: String, associatedDid: String): String {
        println("\nUse Case -> Import W3C JWT VC Credential\n")
        return importCredential(jwtVc, associatedDid)
    }

    /**
     * Import an SD-JWT VC into the wallet
     * The credential string should include the JWT and any disclosures (format: jwt~disclosure1~disclosure2~)
     */
    protected suspend fun importSdJwtCredential(sdJwtVc: String, associatedDid: String): String {
        println("\nUse Case -> Import SD-JWT VC Credential\n")
        assertTrue(sdJwtVc.contains("~"), "SD-JWT credential should contain disclosures (~ separator)")
        return importCredential(sdJwtVc, associatedDid)
    }

    /**
     * Test importing a credential with a DID mismatch (should fail)
     */
    protected suspend fun testImportCredentialWithMismatchedDid(credential: String, wrongDid: String) {
        println("\nUse Case -> Test Import Credential with Mismatched DID (should fail)\n")
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/credentials/import"
        println("POST $endpoint")

        val response = walletClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(mapOf(
                "jwt" to credential,
                "associated_did" to wrongDid
            ))
        }
        
        // Should fail with BadRequest or NotFound
        assertTrue(
            response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.NotFound,
            "Expected BadRequest or NotFound but got ${response.status}"
        )
        println("Import correctly failed with status: ${response.status}")
    }

    /**
     * Test importing a credential with an invalid JWT (should fail)
     */
    protected suspend fun testImportInvalidCredential(invalidJwt: String, associatedDid: String) {
        println("\nUse Case -> Test Import Invalid Credential (should fail)\n")
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/credentials/import"
        println("POST $endpoint")

        val response = walletClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(mapOf(
                "jwt" to invalidJwt,
                "associated_did" to associatedDid
            ))
        }
        
        assertEquals(HttpStatusCode.BadRequest, response.status, "Invalid JWT should return BadRequest")
        println("Import correctly failed with BadRequest")
    }

    /**
     * Test importing a duplicate credential (should fail with Conflict)
     */
    protected suspend fun testImportDuplicateCredential(credential: String, associatedDid: String) {
        println("\nUse Case -> Test Import Duplicate Credential (should fail)\n")
        
        // First import should succeed
        importCredential(credential, associatedDid)
        
        // Second import should fail with Conflict
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/credentials/import"
        println("POST $endpoint (second attempt)")

        val response = walletClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(mapOf(
                "jwt" to credential,
                "associated_did" to associatedDid
            ))
        }
        
        assertEquals(HttpStatusCode.Conflict, response.status, "Duplicate credential should return Conflict")
        println("Duplicate import correctly failed with Conflict")
    }
}
