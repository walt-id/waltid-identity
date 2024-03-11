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
import kotlin.time.Duration.Companion.seconds

class E2EWalletTestDeployed : E2EWalletTestBase() {
    private lateinit var deployedClient: HttpClient
    private var deployedWalletUrl: String = "https://wallet.walt.id"
    private var deployedIssuerUrl: String = "https://issuer.portal.walt.id"
    private var deployedVerifierUrl: String = "https://verifier.portal.walt.id"
    
    
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
        testCreateUser(User(name="tester", email=email, password=password, accountType="email"))
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
        testCreateUser(User(name="tester", email="tester@email.com", password="password", accountType="email"))
        
        login(User(name = "tester", email = "tester@email.com", password = "password", accountType = "email"))
        getUserToken()
        testUserInfo()
        testUserSession()
        deployedClient = newClient(token)
        listAllWallets()
        
        println("WalletId (Deployed Wallet API) = $walletId")
    }
    
    @Test
    fun e2eTestKeys() = runTest {
        deployedClient = newClient()
        login()
        getUserToken()
        deployedClient = newClient(token)
        
        listAllWallets()
        
        testKeys()
        
        testCreateRSAKey()
        
        testExampleKey()
    }
    
    @Test
    fun e2eTestDids() = runTest(timeout = 120.seconds) {
        deployedClient = newClient()
        login()
        getUserToken()
        deployedClient = newClient(token)
        
        listAllWallets()
        
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
        deployedClient = newClient()
        login()
        getUserToken()
        deployedClient = newClient(token)
        
        // list all wallets for this user
        listAllWallets()
        val response: JsonArray = listCredentials()
        assertNotEquals(response.size, 0)
        val id = response[0].jsonObject["id"]?.jsonPrimitive?.content ?: error("No credentials found")
        viewCredential(id)
        deleteCredential(id)
    }
    
    @Test
    fun e2eTestIssuance() = runTest {
        deployedClient = newClient()
        getUserToken()
        
        deployedClient = newClient(token)
        
        // list all wallets for this user
        listAllWallets()
        
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
    fun e2eTestPolicyList() = testApplication {
        deployedClient = newClient()
        val list: JsonObject = testPolicyList()
        
        assertNotEquals(0, list.size)
        list.keys.forEach {
            println("$it -> ${list[it]?.jsonPrimitive?.content}")
        }
    }
    
    @Test
    fun e2eTestVerify() = testApplication {
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
    fun e2eTestPresentationSession() = testApplication {
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
    fun e2eTestPresentationRequest() = testApplication {
        deployedClient = newClient()
        getUserToken()
        deployedClient = newClient(token)
        
        listAllWallets() // sets the walletId
        val url = testVerifyCredential(VerifierApiExamples.minimal)
        assertTrue(url.startsWith("openid4vp://authorize?response_type=vp_token"))
        println("verify Url = $url")
        
        val parsedRequest = testResolvePresentationRequest(url)
        println("Parsed Request = $parsedRequest")
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