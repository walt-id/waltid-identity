package id.walt.test.integration.environment

import id.walt.commons.ServiceConfiguration
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.testing.E2ETest
import id.walt.commons.web.plugins.httpJson
import id.walt.issuer.feat.lspPotential.lspPotentialIssuanceTestApi
import id.walt.issuer.issuerModule
import id.walt.test.integration.environment.api.issuer.IssuerApi
import id.walt.test.integration.environment.api.verifier.VerifierApi
import id.walt.test.integration.environment.api.wallet.WalletApi
import id.walt.verifier.lspPotential.lspPotentialVerificationTestApi
import id.walt.verifier.verifierModule
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.webWalletModule
import io.klogging.Klogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.minutes

val defaultTestTimeout = 5.minutes

/*
In the future, also a RemoteCommunityStackEnvironment might be implemented
 */
@OptIn(InternalAPI::class)
class InMemoryCommunityStackEnvironment private constructor(val e2e: E2ETest) : Klogging {
    private val startupCompleted = CompletableDeferred<Unit>()
    private val shutdownInitialized = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(Dispatchers.Default)

    private val e2eTestModule: Application.() -> Unit = {
        webWalletModule(true)
        issuerModule(false)
        lspPotentialIssuanceTestApi()
        verifierModule(false)
        lspPotentialVerificationTestApi()
    }

    constructor(
        host: String = "localhost",
        port: Int = 22222,
    ) : this(E2ETest(host, port, true))

    val defaultEmailAccount = EmailAccountRequest(
        name = "Max Mustermann",
        email = "user@email.com",
        password = "password"
    )

    suspend fun start() {
        scope.launch {
            e2e.testBlock(
                config = ServiceConfiguration("e2e-test"),
                features = listOf(
                    id.walt.issuer.FeatureCatalog,
                    id.walt.verifier.FeatureCatalog,
                    id.walt.webwallet.FeatureCatalog
                ),
                featureAmendments = mapOf(
                    CommonsFeatureCatalog.authenticationServiceFeature to id.walt.webwallet.web.plugins.walletAuthenticationPluginAmendment,
                    // CommonsFeatureCatalog.authenticationServiceFeature to issuerAuthenticationPluginAmendment
                ),
                init = {
                    id.walt.webwallet.webWalletSetup()
                    id.walt.did.helpers.WaltidServices.minimalInit()
                    id.walt.webwallet.db.Db.start()
                },
                module = e2eTestModule,
                timeout = defaultTestTimeout,
                block = {
                    logger.info { "================= Startup complete =============================" }
                    startupCompleted.complete(Unit)
                    logger.error("Wait for Shutting down InMemoryCommunityStackEnvironment")
                    shutdownInitialized.await()
                }
            )
        }
        logger.error("============== ASYNC STARTED ===============")
        logger.error("============== Waiting til startup complete ================")
        withTimeout(30_000) {
            startupCompleted.await()
        }
    }

    suspend fun shutdown() {
        logger.info("Shutting down InMemoryCommunityStackEnvironment")
        shutdownInitialized.complete(Unit)
    }

    fun testHttpClient(token: String? = null, doFollowRedirects: Boolean = true) = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(httpJson)
        }
        install(DefaultRequest) {
            contentType(ContentType.Application.Json)
            host = e2e.host
            port = e2e.port

            if (token != null) bearerAuth(token)
        }
        install(Logging) {
            level = LogLevel.ALL
        }
        followRedirects = doFollowRedirects
    }

    fun getVerifierApi() =
        VerifierApi(e2e, testHttpClient())

    fun getIssuerApi() =
        IssuerApi(e2e, testHttpClient())


    fun getWalletApi(token: String? = null): WalletApi =
        WalletApi(
            defaultEmailAccount,
            clientFactory = { token: String? ->
                testHttpClient(
                    token,
                    true
                )
            },
            e2e,
            token
        )

    suspend fun getDefaultAccountWalletApi(): WalletApi =
        getWalletApi().loginWithDefaultUser()

}