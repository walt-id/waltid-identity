/*
class WalletApiTeste2eLocal : WalletApiTeste2eBase() {
    companion object {
        var localUrl: String = ""
//        private var issuer = IssuerApiTeste2e()
        //var localIssuerClient: HttpClient

        var nonTestAppIssuerClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
            followRedirects = false
        }
        lateinit var localWalletClient: HttpClient

        init {
            Security.addProvider(BouncyCastleProvider())
            runCatching { Db.dataDirectoryPath.createDirectories() }

            ConfigManager.loadConfigs(emptyArray())

            Db.start()

            // creates two test applications, for wallet and issuer
            setUpWalletAPITestApplication()

//            localIssuerClient = issuer.getHttpClient()
            //localIssuerClient = EndToEndTestController.getClient()
            println("Init finished")
        }

        private fun setUpWalletAPITestApplication() {
            println("Wallet API : Test Application starting...")

            val testApp = TestApplication {
                application {
                    configurePlugins()
                    auth()
                    accounts()
                    credentials()
                    exchange()
                    dids()
                    keys()
                }
            }
            localWalletClient = testApp.createClient {
                install(ContentNegotiation) {
                    json()
                }
            }
        }
    }

    @Test
    fun testLogin() = runTest {
        // test creation of a randomly generated user account
        super.testCreateUser(
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
        testCreateUser(User("tester", email, password, "email"))
        testAuthenticationEndpoints(User("tester", "user@email.com", "password", "email"))
    }

    @Test
    fun testCredentials() = runTest {
        testCredentialEndpoints()
    }

    @Test
    fun testListDids() = runTest {
        testDidsList()
    }

    @Test
    fun testDeleteDids() = runTest {
        testDidsDelete()
    }

    @Test
    fun testCreateDids() = runTest {
        testDidsDelete()
        testDidsCreate()
    }

    @Test
    fun testDidDefault() = runTest {
        testDidsDelete()
        testDidsCreate()
        testDefaultDid()
    }

    @Test
    fun testIssuance() = runTest(timeout = 600.seconds) {
        testCredentialIssuance()
    }

    @Test
    fun testKey() = runTest {
        testKeyEndpoints()
    }


    override var walletClient: HttpClient
        get() = localWalletClient
        set(value) {
            walletClient = value
        }
  */
/*  override var issuerClient: HttpClient
        get() = localIssuerClient
        set(value) {
            localIssuerClient = value
        }*//*

    override var walletUrl: String
        get() = localUrl
        set(value) {
            localUrl = value
        }

    override var issuerUrl: String = walletUrl
}
*/
