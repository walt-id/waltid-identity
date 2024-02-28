import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class E2EWalletTestDeployed : WalletApiTeste2eBase() {
    private lateinit var deployedClient: HttpClient
    private var deployedWalletUrl: String = "https://wallet.walt.id"
    private var deployedIssuerUrl: String = "https://issuer.portal.walt.id"
    
    private fun newClient(contentType: Boolean = false, token: String? = null) = HttpClient {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
        followRedirects = false
        defaultRequest {
            if (contentType) {
                contentType(ContentType.Application.Json)
            }
            if (token != null) {
                header("Authorization", "Bearer $token")
            }
        }
    }
    
    @Test
    fun e2eTestRegisterNewUser() = runTest {
        deployedClient = newClient(true)
        println("*********email = $email")
        
        testCreateUser(User(name="tester", email=email, password=password, accountType="email"))
    }
    
    @Test
    fun e2eTestLogin() = runTest {
        // test creation of a randomly generated user account
        deployedClient = newClient(true)
        
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
//        testCreateUser(User(name="tester", email="tester@email.com", password="password", accountType="email"))
        
        login(User(name = "tester", email = "tester@email.com", password = "password", accountType = "email"))
        getTokenFor()
        testUserInfo()
        testUserSession()
        deployedClient = newClient(false, token)
        listAllWalletsForUser()
        
        println("WalletId (Deployed Wallet API) = $walletId")
    }
    
    @Test
    fun e2eTestKeys() = runTest {
        deployedClient = newClient()
        login()
        getTokenFor()
        deployedClient = newClient(false, token)
        
        listAllWalletsForUser()
        
        testKeys()
        
        testExampleKey()
    }
    
    @Test
    fun e2eTestDids() = runTest(timeout = 60.seconds) {
        deployedClient = newClient()
        login()
        getTokenFor()
        deployedClient = newClient(false, token)
        
        listAllWalletsForUser()
        
        // delete dids first to avoid duplicates when we create new ones
        var availableDids = listAllDids()
        deleteAllDids(availableDids)
        
        // create a did, one of each of the main types we support
        createDids()
        availableDids = listAllDids()
        deleteAllDids(availableDids)
    }
    
    @Test
    fun e2eTestIssuance() = runTest {
        deployedClient = newClient()
        login()
        getTokenFor()
        
        deployedClient = newClient(false, token)
        
        // list all wallets for this user
        listAllWalletsForUser()
        
        // create a did for issuance and
        // list all Dids for this user and set default for credential issuance
        createDid("key")
        val availableDids = listAllDids()
        
        deployedClient = newClient(true, token)
        
        val issuanceUri = issueJwtCredential()
        println("Issuance Offer uri = $issuanceUri")
//
        // Request credential and store in wallet
        requestCredential(issuanceUri, availableDids.first().did)
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
    
}