package id.walt.webwallet.e2e

import id.walt.issuer.base.config.OIDCIssuerServiceConfig
import id.walt.issuer.issuerModule
import id.walt.verifier.base.config.OIDCVerifierServiceConfig
import id.walt.verifier.verifierModule
import id.walt.webwallet.db.Db
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.utils.WalletHttpClients
import id.walt.webwallet.webWalletModule
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import id.walt.issuer.base.config.ConfigManager as IssuerConfigManager
import id.walt.verifier.base.config.ConfigManager as VerifierConfigManager

open class E2EWalletTestLocal : E2EWalletTestBase() {

    private lateinit var localWalletClient: HttpClient
    private var localWalletUrl: String = ""
    private var localIssuerUrl: String = ""
    private var localVerifierUrl: String = ""

//    companion object {
//        init {
//            Files.createDirectories(Paths.get("./data"))
//            assertTrue(File("./data").exists())
//            val config = DatasourceConfiguration(
//                hikariDataSource = HikariDataSource(HikariConfig().apply {
//                    jdbcUrl = "jdbc:sqlite:data/wallet.db"
//                    driverClassName = "org.sqlite.JDBC"
//                    username = ""
//                    password = ""
//                    transactionIsolation = "TRANSACTION_SERIALIZABLE"
//                    isAutoCommit = true
//                }),
//                recreateDatabaseOnStart = true
//            )
//
//            WalletConfigManager.preloadConfig(
//                "db.sqlite", config
//            )
//
//            WalletConfigManager.preloadConfig(
//                "web", WalletWebConfig()
//            )
//            webWalletSetup()
//            WalletConfigManager.loadConfigs(emptyArray())
//        }
//    }

    private fun ApplicationTestBuilder.newClient(token: String? = null) = createClient {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
        followRedirects = false
        defaultRequest {
            contentType(ContentType.Application.Json)
            if (token != null) {
                header("Authorization", "Bearer $token")
            }
        }
    }

    suspend fun ApplicationTestBuilder.runApplication() = run {
        println("Running in ${Path(".").absolutePathString()}")
        localWalletClient = newClient()

        WalletHttpClients.defaultMethod = {
            newClient()
        }
        setupTestWebWallet()

        println("Setup issuer...")
        setupTestIssuer()

        println("Setup verifier...")
        setupTestVerifier()

        println("Starting application...")
        application {
            webWalletModule()
            issuerModule(withPlugins = false)
            verifierModule(withPlugins = false)
        }
        getUserToken()
        localWalletClient = newClient(token)

        // list all wallets for this user (sets wallet id)
        listAllWalletsSetWalletId()
    }

    private fun setupTestWebWallet() {
        // TODO moving this into init{} causes error 400 status code in issuance test
        Db.start()
    }

    private fun setupTestIssuer() {
        IssuerConfigManager.preloadConfig("issuer-service", OIDCIssuerServiceConfig("http://localhost"))

        IssuerConfigManager.loadConfigs(emptyArray())
    }

    open fun setupTestVerifier() {
        VerifierConfigManager.preloadConfig("verifier-service", OIDCVerifierServiceConfig("http://localhost"))

        VerifierConfigManager.loadConfigs(emptyArray())
    }

    @Test
    fun e2eTestRegisterNewUser() = testApplication {
        runApplication()
        testCreateUser(User(name = "tester", email = "tester@email.com", password = "password", accountType = "email"))
    }

    @Test
    fun e2eTestAuthentication() = testApplication {
        runApplication()
        testUserInfo()
        testUserSession()
    }

    @Test
    fun e2eTestPermissions() = testApplication {
        runApplication()

        val user1 = User(name = "tester", email = "tester@email.com", password = "password", accountType = "email")
        val user2 = User(name = "tester2", email = "tester2@email.com", password = "password", accountType = "email")

        testCreateUser(user1)
        getUserToken(user1)
        localWalletClient = newClient(token)

        fun List<AccountWalletListing.WalletListing>.getUserWallet(): UUID {
            check(this.isNotEmpty()) { "No wallet found" }
            return this.first().id
        }

        listAllWalletsSetWalletId()
        println("User1: $walletId")
        val user1Wallet = walletId


        testCreateUser(user2)
        getUserToken(user2)
        localWalletClient = newClient(token)

        listAllWalletsSetWalletId()
        println("User2: $walletId")
        val user2Wallet = walletId

        println("Check accessing own wallet...")
        check(localWalletClient.get("/wallet-api/wallet/$user2Wallet/credentials").status == HttpStatusCode.OK) { "Accessing own wallet does not work" }


        println("Check accessing strangers wallet...")
        check(localWalletClient.get("/wallet-api/wallet/$user1Wallet/credentials").status == HttpStatusCode.Forbidden) { "Accessing strangers wallet should not work" }
    }

    @Test
    fun e2eTestKeys() = testApplication {
        runApplication()
        deleteKeys()
        testCreateRSAKey()
        testKeys()
    }

    @Test
    fun e2eTestDids() = testApplication {
        runTest(timeout = 60.seconds) {
            runApplication()
            // create a did, one of each of the main types we support
            createDids()
            testDefaultDid()
            val availableDids = listAllDids()
            deleteAllDids(availableDids)
        }
    }

    @Test
    fun e2eTestWalletCredentials() = testApplication {
        runApplication()
        val response: JsonArray = listCredentials()
        assertNotEquals(response.size, 0)
        val id = response[0].jsonObject["id"]?.jsonPrimitive?.content ?: error("No credentials found")
        viewCredential(id)
        deleteCredential(id)
    }

    @Test
    fun e2eDeleteWalletCredentials() = testApplication {
        runApplication()
        var response: JsonArray = listCredentials()
        assertNotEquals(response.size, 0)
        response.forEach {
            val id = it.jsonObject["id"]?.jsonPrimitive?.content ?: error("No credentials found")
            deleteCredential(id)
        }
        val resp: JsonArray = listCredentials()
        assertEquals(resp.size, 0)
    }

    // Issuer Tests
    @Test
    fun e2eTestIssuance() = testApplication {
        runApplication()

        // list all Dids for this user and set default for credential issuance
        val availableDids = listAllDids()

        val issuanceUri = issueJwtCredential()
        println("Issuance Offer uri = $issuanceUri")

        // Request credential and store in wallet
        val vc: JsonObject = requestCredential(issuanceUri, availableDids.first().did)

        val credential = vc["parsedDocument"].toString()
        assertNotNull(credential)

        val id = vc["id"]?.jsonPrimitive?.content
        println("credential id = $id")
        assertNotNull(id)

        // demonstrate that the newly issued credential is in the user wallet
        viewCredential(id)
        println("****************************************")
        println("vc issued and stored in wallet: $vc")
        println("****************************************")
    }

    @Test
    fun e2eTestIssuerOnboarding() = testApplication {
        runApplication()
        onboardIssuer()
    }

    override var walletClient: HttpClient
        get() = localWalletClient
        set(value) {
            localWalletClient = value
        }

    override var walletUrl: String
        get() = localWalletUrl
        set(value) {
            localWalletUrl = value
        }

    override var issuerUrl: String
        get() = localIssuerUrl
        set(value) {
            localIssuerUrl = value
        }

    override var verifierUrl: String
        get() = localVerifierUrl
        set(value) {
            localVerifierUrl = value
        }
}
