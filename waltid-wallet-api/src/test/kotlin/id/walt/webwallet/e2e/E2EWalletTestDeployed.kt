package id.walt.webwallet.e2e

import id.walt.verifier.VerifierApiExamples
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import id.walt.webwallet.e2e.PresentationDefinitionFixtures.Companion.presentationDefinitionExample1
import id.walt.webwallet.Values

class E2EWalletTestDeployed : E2EWalletTestBase() {
    private lateinit var deployedClient: HttpClient
    private var deployedWalletUrl: String = "https://wallet.walt.id"
    private var deployedIssuerUrl: String = "https://issuer.portal.walt.id"
    private var deployedVerifierUrl: String = "https://verifier.portal.walt.id"
    
    private suspend fun initialise() = runTest {
        deployedClient = newClient()
        getUserToken()
        deployedClient = newClient(token)
        configureApis()
    }
    
    private fun newClient(token: String? = null) = HttpClient {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
        followRedirects = false
        defaultRequest {
            if (token != null) {
                header("Authorization", "Bearer $token")
            }
        }
    }
    
    @Test
    fun e2eTestRegisterNewUser() = runTest {
        deployedClient = newClient()
        testCreateUser(User(name = "tester", email = email, password = password, accountType = "email"))
    }
    
    @Test
    fun e2eTestLogin() = runTest {
        // test creation of a randomly generated user account
        deployedClient = newClient()
        
        testCreateUser(User(name = "tester", email = email, password = password, accountType = "email"))
        login(
            User(
                "tester",
                email,
                password,
                "email"
            )
        )
    }
    
    @Test
    fun e2eTestAuthentication() = runTest {
        deployedClient = newClient()
        testCreateUser(User(name = "tester", email = "tester@email.com", password = "password", accountType = "email"))
        
        login(User(name = "tester", email = "tester@email.com", password = "password", accountType = "email"))
        getUserToken()
        deployedClient = newClient(token)
        listAllWalletsAndSetWalletId()
        println("WalletId (Deployed Wallet API) = $walletId")
    }
    
    @Test
    fun e2eTestKeys() = runTest(timeout = 120.seconds) {
        initialise()
        
        listAllWalletsAndSetWalletId()
        
        runKeyTests()
    }
    
    
    @Test
    fun e2eIssuerOnboarding() = runTest {
        if (versionCheck(1.1)) {
            initialise()
            onboardIssuer()
        } else {
            println("Test e2eIssuerOnboarding() skipped")
        }
    }
    
    // check if the given version number is greater than or equal to the release version
    // @See id.walt.webwallet.Values
    private fun versionCheck(ver: Double): Boolean {
        return Values.versionNumber >= ver
    }
    
    @Test
    fun e2eTestDids() = runTest(timeout = 120.seconds) {
        initialise()

        listAllWalletsAndSetWalletId()
        
        // delete dids first to avoid duplicates when we create new ones
        var availableDids = listAllDids()
        deleteAllDids(availableDids)
        
        // create a did, one of each of the main types we support
        createDids()
        testDefaultDid()
        availableDids = listAllDids()
        deleteAllDids(availableDids)
    }
    
    @Test
    fun e2eTestWalletCredentials() = testApplication {
        initialise()
        
        listAllWalletsAndSetWalletId()
        
        val response: JsonArray = listCredentials()
        assertNotEquals(response.size, 0)
        val id = response[0].jsonObject["id"]?.jsonPrimitive?.content ?: error("No credentials found")
        viewCredential(id)
        deleteCredential(id)
    }
    
    @Test
    fun e2eTestIssuance() = runTest {
        initialise()
        listAllWalletsAndSetWalletId()
        
        // create a did for issuance and
        // list all Dids for this user and set default for credential issuance
        createDid("key")
        val availableDids = listAllDids()
        
        deployedClient = newClient(token)
        
        val issuanceUri = issueJwtCredential()
        println("Issuance Offer uri = $issuanceUri")
//
        // Request credential and store in wallet
        requestCredential(issuanceUri, availableDids.first().did)
    }
    
    // Verifier Tests
    @Test
    fun e2eTestPolicyList() = runTest {
        deployedClient = newClient()
        val list: JsonObject = testPolicyList()
        
        assertNotEquals(0, list.size)
        list.keys.forEach {
            println("$it -> ${list[it]?.jsonPrimitive?.content}")
        }
    }
    
    @Test
    fun e2eTestVerify() = runTest {
        deployedClient = newClient()
        
        var url = testVerifyCredential(VerifierApiExamples.minimal)
        assertTrue(url.startsWith("openid4vp://authorize?response_type=vp_token"))
        println("minimal presentation definition: verify Url = $url")
        
        url = testVerifyCredential(VerifierApiExamples.vpPolicies)
        assertTrue(url.startsWith("openid4vp://authorize?response_type=vp_token"))
        println("vp policy definition :verify Url = $url")
        
        url = testVerifyCredential(VerifierApiExamples.vpGlobalVcPolicies)
        assertTrue(url.startsWith("openid4vp://authorize?response_type=vp_token"))
        println("vp policy definition with global Vcs:verify Url = $url")
        
        url = testVerifyCredential(VerifierApiExamples.vcVpIndividualPolicies)
        assertTrue(url.startsWith("openid4vp://authorize?response_type=vp_token"))
        println("vp policy definition with specific Vc credential policies:verify Url = $url")
        
        url = testVerifyCredential(VerifierApiExamples.maxExample)
        assertTrue(url.startsWith("openid4vp://authorize?response_type=vp_token"))
        println("vp policy definition explicit presentation definition:verify Url = $url")
        
        url = testVerifyCredential(VerifierApiExamples.presentationDefinitionPolicy)
        assertTrue(url.startsWith("openid4vp://authorize?response_type=vp_token"))
        println("vp policy definition explicit presentation definition:verify Url = $url")
    }
    
    @Test
    fun e2eTestPresentationSession() = runTest {
        deployedClient = newClient()
        val url = testVerifyCredential(VerifierApiExamples.minimal)
        val startStr = "state="
        val endStr = "&presentation"
        assertTrue(url.startsWith("openid4vp://authorize?response_type=vp_token"))
        
        val start = url.indexOf(startStr) + startStr.length
        val end = url.indexOf(endStr)
        
        // extract 'state' from URL
        val state = url.substring(start, end)
        println("session id (state) = $state")
        testSession(state)
    }
    
    @Test
    fun e2eTestPresentationRequest() = runTest {
        initialise()
        
        listAllWalletsAndSetWalletId() // sets the walletId
        val url = testVerifyCredential(VerifierApiExamples.minimal)
        assertTrue(url.startsWith("openid4vp://authorize?response_type=vp_token"))
        println("verify Url = $url")
        
        val parsedRequest = resolvePresentationRequest(url)
        println("Parsed Request = $parsedRequest")
    }
    
    @Test
    fun e2eTestMatchCredentialsForPresentationDefinition() = runTest {
        initialise()
        listAllWalletsAndSetWalletId() // sets the wallet id
        
        val response: JsonArray = listCredentials()
        matchCredentialByPresentationDefinition(presentationDefinitionExample1)
        // TODO ensure num matched credentials is equal to credential list size when no match found for type
    }

    @Test
    fun e2eTestDeleteCredentials()= testApplication {
        initialise()
        listAllWalletsAndSetWalletId()
        deleteAllCredentials()

    }
    
    @Test
    fun e2eTestFullPresentationUseCaseForDidJwk() = testApplication {
        e2eTestFullPresentationUseCase("jwk")
    }
    
    @Test
    fun e2eTestFullPresentationUseCaseForDidKey() = testApplication {
        e2eTestFullPresentationUseCase() // uses default (generated) did
    }
    
    private fun e2eTestFullPresentationUseCase(didType: String? = null) = run {
        runTest(timeout = 120.seconds) {
            initialise()
            listAllWalletsAndSetWalletId()
            deleteAllCredentials()
            
            if (didType != null) {
                val newDid = createDid(didType)
                issueNewCredentialForDid(newDid)
            } else {
                issueNewCredential() // uses default did:key
            }
            
            // full e2e run of verification endpoints
            
            // 1. /verify in the Verifier API
            val url = testVerifyCredential(VerifierApiExamples.minimal)
            println("Verify Url = $url")
            
            // 2. resolve presentation request with URI from 1)
            
            val parsedRequest = resolvePresentationRequest(url)
            println("Parsed Request = $parsedRequest")
            
            // 3. Match credentials of the required type, e.g., OpenBadgeCredential
            val matchedCredentials = matchCredentialByPresentationDefinition(presentationDefinitionExample1)
            val id = matchedCredentials[0].jsonObject["id"]?.jsonPrimitive?.content
            assertNotNull(id)
            
            var holderDid: String? =
                matchedCredentials[0].jsonObject["parsedDocument"]?.jsonObject?.get("credentialSubject")?.jsonObject?.get(
                    "id"
                )?.jsonPrimitive?.content
            assertNotNull(holderDid)
            holderDid = holderDid.split("#")[0]
            
            // 4. Use output of 2) and 3) to create presentation to return to relying party
            val json = """
           {
                "did": "$holderDid",
                "presentationRequest": "$parsedRequest",
                "selectedCredentials": [
                    "$id"
                ]
            }
        """.trimIndent()
            usePresentationRequest(json)
        }
    }
    
    override var walletClient: HttpClient
        get() = deployedClient
        set(value) {
            deployedClient = value
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
    
    override var verifierUrl: String
        get() = deployedVerifierUrl
        set(value) {
            deployedVerifierUrl = value
        }
}