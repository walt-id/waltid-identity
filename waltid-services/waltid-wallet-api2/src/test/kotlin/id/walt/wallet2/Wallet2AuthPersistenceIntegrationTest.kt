package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureConfig
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.testing.E2ETest
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.wallet2.auth.AccountInfoResponse
import id.walt.wallet2.auth.RegisterRequest
import id.walt.wallet2.auth.configureWallet2Auth
import id.walt.wallet2.persistence.Wallet2PersistenceConfig
import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.wallet2.server.handlers.WalletCreatedResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class Wallet2AuthPersistenceIntegrationTest {
    @Test
    fun `accounts authenticate as the same wallet owner after startup recreation`(@TempDir directory: Path) {
        val config = Wallet2PersistenceConfig(jdbcUrl = "jdbc:sqlite:${directory.resolve("wallet.db")}")
        val auth = OSSWallet2AuthConfig(
            signingKey = DirectSerializedKey(runBlocking { JWKKey.generate(KeyType.Ed25519) })
        )
        val email = "owner@example.test"
        val passwords = listOf("synthetic-original-password", "synthetic-duplicate-password")
        lateinit var owner: AccountInfoResponse
        lateinit var winningPassword: String
        lateinit var oldToken: String

        // Historical databases stored owner IDs and wallets but had no account credential tables.
        DriverManager.getConnection(config.jdbcUrl).use { connection ->
            connection.createStatement().use {
                it.execute("CREATE TABLE wallet2_wallets(id VARCHAR(128) PRIMARY KEY)")
                it.execute(
                    "CREATE TABLE wallet2_account_wallets(account_id VARCHAR(256) NOT NULL, " +
                            "wallet_id VARCHAR(128) NOT NULL REFERENCES wallet2_wallets(id), PRIMARY KEY(account_id, wallet_id))"
                )
                it.execute("INSERT INTO wallet2_wallets(id) VALUES ('legacy-wallet')")
                it.execute("INSERT INTO wallet2_account_wallets VALUES ('legacy-owner', 'legacy-wallet')")
            }
        }

        try {
            withWallet2(config, auth) { http ->
                assertEquals(listOf("legacy-wallet"), OSSWallet2Service.resolver.getWalletIdsForAccount("legacy-owner"))

                val registrations = coroutineScope {
                    passwords.map { password -> async { http.register(email, password) } }.awaitAll()
                }
                assertEquals(
                    setOf(HttpStatusCode.Created, HttpStatusCode.Conflict),
                    registrations.map { it.status }.toSet(),
                )
                val winner = registrations.indexOfFirst { it.status == HttpStatusCode.Created }
                winningPassword = passwords[winner]
                val accountId = registrations[winner].body<Map<String, String>>().getValue("accountId")
                oldToken = http.login(email, winningPassword)
                val wallet = http.post("/wallet") {
                    bearerAuth(oldToken)
                    setBody(CreateWalletRequest())
                }
                assertEquals(HttpStatusCode.Created, wallet.status)
                owner = AccountInfoResponse(accountId, email, listOf(wallet.body<WalletCreatedResponse>().walletId))
            }

            OSSWallet2Service.configureInMemory()
            withWallet2(config, auth) { http ->
                val newToken = http.login(email, winningPassword)
                assertEquals(owner, http.get("/auth/account") { bearerAuth(newToken) }.body<AccountInfoResponse>())
                assertEquals(
                    HttpStatusCode.OK,
                    http.get("/wallet/${owner.walletIds.single()}") { bearerAuth(newToken) }.status,
                )
                assertEquals(
                    HttpStatusCode.OK,
                    http.get("/wallet/${owner.walletIds.single()}") { bearerAuth(oldToken) }.status,
                )
                assertEquals(HttpStatusCode.Conflict, http.register(email, "synthetic-replacement-password").status)
                val losingPassword = passwords.single { it != winningPassword }
                val rejectedLogin = http.post("/auth/emailpass") {
                    setBody(mapOf("email" to email, "password" to losingPassword))
                }
                assertEquals(HttpStatusCode.Unauthorized, rejectedLogin.status)
                assertEquals(HttpStatusCode.Forbidden, http.get("/wallet/legacy-wallet") { bearerAuth(newToken) }.status)
                assertEquals(listOf("legacy-wallet"), OSSWallet2Service.resolver.getWalletIdsForAccount("legacy-owner"))
            }
        } finally {
            OSSWallet2Service.configureInMemory()
            ConfigManager.preclear()
            FeatureManager.preclear()
        }
    }

    private fun withWallet2(
        persistence: Wallet2PersistenceConfig,
        auth: OSSWallet2AuthConfig,
        exercise: suspend (HttpClient) -> Unit,
    ) {
        val host = "127.0.0.1"
        val port = freePort()
        E2ETest(host, port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            featureAmendments = mapOf(
                CommonsFeatureCatalog.authenticationServiceFeature to suspend {
                    AuthenticationServiceModule.AuthenticationServiceConfig.customAuthentication = {
                        ktorAuthnz("ktor-authnz") { }
                    }
                }
            ),
            preload = {
                ConfigManager.preloadConfig("_features", FeatureConfig(enabledFeatures = listOf("auth", "wallet2-persistence")))
                ConfigManager.preloadConfig("wallet-service", OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port")))
                ConfigManager.preloadConfig("wallet2-persistence", persistence)
                ConfigManager.preloadConfig("auth", auth)
            },
            init = {
                DidService.minimalInit()
                configureWallet2Storage()
            },
            module = { wallet2Module(withPlugins = false, authConfig = runBlocking { configureWallet2Auth() }) },
            host = host,
            port = port,
        ) {
            testAndReturn("Serve Wallet2 with a newly opened database") {
                testHttpClient().use { exercise(it) }
            }
        }
    }

    private suspend fun HttpClient.register(email: String, password: String) =
        post("/auth/register") { setBody(RegisterRequest(email, password)) }

    private suspend fun HttpClient.login(email: String, password: String): String {
        val response = post("/auth/emailpass") { setBody(mapOf("email" to email, "password" to password)) }
        assertEquals(HttpStatusCode.OK, response.status)
        return assertNotNull(response.body<AuthSessionInformation>().token)
    }
}
