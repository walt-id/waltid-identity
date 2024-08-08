import id.walt.commons.config.ConfigManager
import id.walt.commons.config.list.WebConfig
import id.walt.webwallet.config.DatasourceConfiguration
import id.walt.webwallet.config.KeyGenerationDefaultsConfig
import id.walt.webwallet.config.RegistrationDefaultsConfig
import id.walt.webwallet.db.Db
import id.walt.webwallet.utils.WalletHttpClients
import id.walt.webwallet.webWalletSetup
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.test.Test

class E2EWalletTestLocal : E2EWalletTestBase() {

    private lateinit var localWalletClient: HttpClient
    private var localWalletUrl: String = ""
    private var localIssuerUrl: String = ""

    companion object {
        init {
            ConfigManager.preloadAndRegisterConfig(
                "db.sqlite", DatasourceConfiguration(
                    dataSource = Db.SerializableHikariConfiguration(
                        jdbcUrl = "jdbc:sqlite:data/wallet.db",
                        driverClassName = "org.sqlite.JDBC",
                        username = "",
                        password = "",
                        transactionIsolation = "TRANSACTION_SERIALIZABLE",
                        isAutoCommit = true
                    ),
                    recreateDatabaseOnStart = true
                )
            )

            ConfigManager.preloadAndRegisterConfig("web", WebConfig(webPort = 4545))
            ConfigManager.preloadAndRegisterConfig("registration-defaults", RegistrationDefaultsConfig())
            ConfigManager.preloadAndRegisterConfig("key-generation-defaults", KeyGenerationDefaultsConfig(emptyMap()))
            webWalletSetup()
            ConfigManager.loadConfigs(emptyArray())
        }
    }

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

    private fun ApplicationTestBuilder.runApplication() = run {
        println("Running in ${Path(".").absolutePathString()}")
        localWalletClient = newClient()

        WalletHttpClients.defaultMethod = {
            newClient()
        }
        setupTestWebWallet()

        /* FIXME
        println("Setup issuer...")
        setupTestIssuer()

        println("Starting application...")
        application {
            webWalletModule()
            issuerModule(withPlugins = false)
            verifierModule(withPlugins = false)
        }*/
    }

    private fun setupTestWebWallet() {
        // TODO moving this into init{} causes error 400 status code in issuance test
        Db.start()
    }

    /* FIXME
       private fun setupTestIssuer() {
        IssuerConfigManager.preloadConfig("issuer-service", OIDCIssuerServiceConfig("http://localhost"))

        IssuerConfigManager.loadConfigs(emptyArray())
    }*/

    @Test
    fun e2eTestRegisterNewUser() = testApplication {
        runApplication()
        testCreateUser(User(name = "tester", email = "tester@email.com", password = "password", accountType = "email"))
    }

    /* @Test FIXME
     fun e2eTestAuthentication() = testApplication {
         runApplication()

         login()
         getTokenFor()
         testUserInfo()
         testUserSession()
         localWalletClient = newClient(token)

         // list all wallets for this user
         listAllWalletsForUser()
     }

     @Test
     fun e2eTestPermissions() = testApplication {
         runApplication()

         val user1 = User(name = "tester", email = "tester@email.com", password = "password", accountType = "email")
         val user2 = User(name = "tester2", email = "tester2@email.com", password = "password", accountType = "email")

         testCreateUser(user1)
         getTokenFor(user1)
         localWalletClient = newClient(token)

         fun List<AccountWalletListing.WalletListing>.getUserWallet(): UUID {
             check(this.isNotEmpty()) { "No wallet found" }
             return this.first().id
         }

         val user1Wallets = listAllWalletsForUser()
         println("User1: $user1Wallets")
         val user1Wallet = user1Wallets.getUserWallet()


         testCreateUser(user2)
         getTokenFor(user2)
         localWalletClient = newClient(token)

         val user2Wallets = listAllWalletsForUser()
         println("User2: $user2Wallets")
         val user2Wallet = user2Wallets.getUserWallet()

         println("Check accessing own wallet...")
         check(localWalletClient.get("/wallet-api/wallet/$user2Wallet/credentials").status == HttpStatusCode.OK) { "Accessing own wallet does not work" }


         println("Check accessing strangers wallet...")
         check(localWalletClient.get("/wallet-api/wallet/$user1Wallet/credentials").status == HttpStatusCode.Forbidden) { "Accessing strangers wallet should not work" }
     }

     @Test
     fun e2eTestKeys() = testApplication {
         runApplication()
         login()
         getTokenFor()
         localWalletClient = newClient(token)

         // list all wallets for this user
         listAllWalletsForUser()

         testKeys()
     }

     @Test
     fun e2eIssuerOnboarding() = testApplication {
         runApplication()
         login()
         getTokenFor()
         localWalletClient = newClient(token)

         onboardIssuer()
     }

     @Test
     fun e2eTestDids() = testApplication {
         runTest(timeout = 60.seconds) {
             runApplication()
             login()
             getTokenFor()
             localWalletClient = newClient(token)

             // list all wallets for this user
             listAllWalletsForUser()

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
         login()
         getTokenFor()

         localWalletClient = newClient(token)

         // list all wallets for this user
         listAllWalletsForUser()
         val response: JsonArray = listCredentials()
         assertNotEquals(response.size, 0)
         val id = response[0].jsonObject["id"]?.jsonPrimitive?.content ?: error("No credentials found")
         viewCredential(id)
         deleteCredential(id)
     }

     @Test
     fun e2eTestIssuance() = testApplication {
         runApplication()
         login()
         getTokenFor()

         localWalletClient = newClient(token)

         // list all wallets for this user
         listAllWalletsForUser()

         // list all Dids for this user and set default for credential issuance
         val availableDids = listAllDids()
         println("Available DIDs: ${availableDids.map { it.did }}")

         val issuanceUri = issueJwtCredential()
         println("Issuance Offer uri = $issuanceUri")
         check(issuanceUri.startsWith("openid-credential-offer://")) { "Issuance offer URI is invalid!" }

         // Request credential and store in wallet
         // FIXME: requestCredential(issuanceUri, availableDids.first().did) // WaltId-MikeRichardson: temporarily disabled due to failure caused by ktor client
     }*/

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
}
