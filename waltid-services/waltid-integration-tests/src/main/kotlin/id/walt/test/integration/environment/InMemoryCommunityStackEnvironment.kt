@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment

import id.walt.commons.ServiceConfiguration
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.testing.E2ETest
import id.walt.commons.web.plugins.httpJson
import id.walt.did.dids.DidService
import id.walt.issuer.issuerModule
import id.walt.test.integration.environment.api.issuer.IssuerApi
import id.walt.test.integration.environment.api.verifier.VerifierApi
import id.walt.test.integration.environment.api.wallet.WalletApi
import id.walt.test.integration.environment.api.wallet.WalletContainerApi
import id.walt.test.integration.expectSuccess
import id.walt.verifier.verifierModule
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.webWalletModule
import io.klogging.Klogging
import io.ktor.client.*
import io.ktor.client.call.*
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
        verifierModule(false)
    }

    constructor(
        host: String = "localhost",
        port: Int = 22323,
    ) : this(E2ETest(host, port, true, loglevelOption = "config-file"))

    val defaultEmailAccount = EmailAccountRequest(
        name = "Max Mustermann",
        email = "user@email.com",
        password = "password"
    )

    val mdocEmailAccount = EmailAccountRequest(
        name = "Mdoc Mustermann",
        email = "mdoc@email.com",
        password = "password"
    )

    suspend fun start() {
        scope.launch {
            e2e.testBlock(
                config = ServiceConfiguration("e2e-test"),
                logConfig = "config-file",
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
                    DidService.minimalInit()
                    id.walt.webwallet.db.Db.start()
                },
                module = e2eTestModule,
                timeout = defaultTestTimeout,
                block = {
                    logger.error { "================= Startup complete =============================" }
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

    fun getWalletContainerApi(): WalletContainerApi =
        WalletContainerApi(
            clientFactory = { token: String? ->
                testHttpClient(
                    token,
                    true
                )
            },
            e2e,
            null,
            null
        )


    fun getWalletContainerApi(token: String, accountId: Uuid): WalletContainerApi =
        WalletContainerApi(
            clientFactory = { token: String? ->
                testHttpClient(
                    token,
                    true
                )
            },
            e2e,
            token,
            accountId
        )

    suspend fun getDefaultAccountWalletContainerApi(): WalletContainerApi =
        getWalletContainerApi().login(defaultEmailAccount)

    suspend fun getMdocWalletApi(): WalletApi {
        val walletApi = getWalletContainerApi()
        var loginResponse = walletApi.loginEmailAccountUserRaw(mdocEmailAccount)
        if (loginResponse.status == HttpStatusCode.Unauthorized) {
            //Mdoc User doesn't exist ... need to register
            walletApi.register(mdocEmailAccount)
            loginResponse = walletApi.loginEmailAccountUserRaw(mdocEmailAccount)
        }
        loginResponse.expectSuccess()
        val token = assertNotNull(loginResponse.body<JsonObject>()["token"]?.jsonPrimitive?.content)
        val accountId = Uuid.parse(assertNotNull(loginResponse.body<JsonObject>()["id"]?.jsonPrimitive?.content))
        val mdocWalletContainer = getWalletContainerApi(token, accountId)
        val defaulMdocWallet = mdocWalletContainer.selectDefaultWallet()
        MDocPreparedWallet.ensureWalletIsPreparedForMdoc(defaulMdocWallet)
        return defaulMdocWallet
    }
}