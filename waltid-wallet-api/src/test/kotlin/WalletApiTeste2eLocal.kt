//import io.ktor.client.*
//import io.ktor.client.plugins.contentnegotiation.*
//import io.ktor.serialization.kotlinx.json.*
//import kotlinx.coroutines.test.runTest
//import kotlin.test.Test
//import io.ktor.client.engine.cio.*
//import kotlin.time.Duration.Companion.seconds
//
//
//class WalletApiTeste2eLocal : WalletApiTeste2eBase() {
//
//    companion object {
//        var localWalletUrl: String = ""
//        var localIssuerUrl: String = "http://localhost:7002"
//
//        //        private var issuer = IssuerApiTeste2e()
////        var localIssuerClient: HttpClient
//
//        var nonTestAppIssuerClient = HttpClient(CIO) {
//            install(ContentNegotiation) {
//                json()
//            }
//            followRedirects = false
//        }
//        lateinit var localWalletClient: HttpClient
//
//        init {
//            println("Init finished")
//        }
//    }
//
//    @Test
//    fun testLogin() = runTest {
//        // test creation of a randomly generated user account
//        super.testCreateUser(
//            User(
//                "tester",
//                email,
//                password,
//                "email"
//            )
//        )
//    }
//
//    @Test
//    fun testAuthentication() = runTest {
//        testCreateUser(User("tester", email, password, "email"))
//        testAuthenticationEndpoints(User("tester", "user@email.com", "password", "email"))
//    }
//
//    @Test
//    fun testCredentials() = runTest {
//        testCredentialEndpoints()
//    }
//
//    @Test
//    fun testListDids() = runTest {
//        testDidsList()
//    }
//
//    @Test
//    fun testDeleteDids() = runTest {
//        testDidsDelete()
//    }
//
//    @Test
//    fun testCreateDids() = runTest {
//        testDidsDelete()
//        testDidsCreate()
//    }
//
//    @Test
//    fun testDidDefault() = runTest {
//        testDidsDelete()
//        testDidsCreate()
//        testDefaultDid()
//    }
//
//    @Test
//    fun testIssuance() = runTest(timeout = 600.seconds) {
//        testCredentialIssuance()
//    }
//
//    @Test
//    fun testKey() = runTest {
//        testKeyEndpoints()
//    }
//
//
//    override var walletClient: HttpClient
//        get() = localWalletClient
//        set(value) {
//            walletClient = value
//        }
//
//    //    override var issuerClient: HttpClient
////        get() = localIssuerClient
////        set(value) {
////            localIssuerClient = value
////        }
////    override var issuerClient: HttpClient
////        get() = nonTestAppIssuerClient
////        set(value) {
////            issuerClient = value
////        }
//    override var walletUrl: String
//        get() = localWalletUrl
//        set(value) {
//            localWalletUrl = value
//        }
//
//    override var issuerUrl: String
//        get() = localIssuerUrl
//        set(value) {
//            localIssuerUrl = value
//        }
//}
