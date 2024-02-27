import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class WalletApiTeste2eDeployed : WalletApiTeste2eBase() {
    
    fun ApplicationTestBuilder.newClient(token: String?): HttpClient {
        TODO("Not yet implemented")
    }
    companion object {
        var deployedWalletUrl: String = "https://wallet.walt.id"
        var deployedIssuerUrl: String = "https://issuer.portal.walt.id"
        var deployedClient: HttpClient
        
        init {
            println("Using client for Deployed WaltId Wallet and Issuer Api...")
            deployedClient = HttpClient {
                install(ContentNegotiation) {
                    json()
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 30 * 1000
                }
            }
//            deployedIssuerClient = HttpClient {
//                install(ContentNegotiation) {
//                    json()
//                }
//                defaultRequest {
//                    header(HttpHeaders.ContentType, ContentType.Application.Json)
//                }
//                install(HttpTimeout) {
//                    requestTimeoutMillis = 30 * 1000
//                }
//            }
        }
    }
    
    @Test
    fun testLogin() = runTest {
        // test creation of a randomly generated user account
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
    
    // this won't work until ability to add new credential in the tests
    // is ready
//    @Test
//    fun testCredentials() = runTest {
//        testCredentialEndpoints(User("tester", "user@email.com", "password", "email"))
//    }
    
    @Test
    fun testListDids() = runTest {
        testDidsList()
    }
    
    @Test
    fun testDeleteDids() = runTest(timeout = 60.seconds) {
        testDidsDelete()
    }
    
    @Test
    fun testCreateDids() = runTest(timeout = 60.seconds) {
        testDidsDelete()
        testDidsCreate()
    }
    
    @Test
    fun testDidDefault() = runTest(timeout = 60.seconds) {
        testDidsDelete()
        testDidsCreate()
        testDefaultDid()
    }
    
    @Test
    fun testIssuance() = runTest {
        testCredentialIssuance()
    }
    
    @Test
    fun testKey() = runTest {
        testKeyEndpoints()
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