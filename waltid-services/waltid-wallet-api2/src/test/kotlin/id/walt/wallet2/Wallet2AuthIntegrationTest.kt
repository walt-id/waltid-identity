package id.walt.wallet2

import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.FeatureConfig
import id.walt.commons.testing.E2ETest
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.did.dids.DidService
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.wallet2.auth.OSSWallet2AccountStore
import id.walt.wallet2.auth.RegisterRequest
import id.walt.wallet2.server.handlers.WalletCreatedResponse
import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.ktorauthnz.auth.ktorAuthnz
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the OSS Wallet2 optional auth feature.
 *
 * Verifies the full auth lifecycle:
 * 1. Register an account
 * 2. Login and obtain a session token
 * 3. Create a wallet while authenticated → wallet is auto-linked to account
 * 4. List wallets for account → created wallet is present
 * 5. Access wallet without token → 401
 * 6. Access wallet with wrong-account token → 403
 * 7. Logout → token is invalidated
 */
class Wallet2AuthIntegrationTest {

    private val host = "127.0.0.1"
    private val port = 17050

    @Test
    fun testAuthFlow() {
        // Reset the in-memory wallet store between tests so port reuse is safe
        OSSWallet2Service.walletStore = id.walt.wallet2.stores.inmemory.InMemoryWalletStore()

        E2ETest(host, port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            featureAmendments = mapOf(
                // The authservice feature's amendment runs before AuthenticationServiceModule.enable()
                // is called by WebService. We set customAuthentication here so that when
                // enable() installs Authentication, it includes the ktor-authnz provider.
                CommonsFeatureCatalog.authenticationServiceFeature to suspend {
                    AuthenticationServiceModule.AuthenticationServiceConfig.customAuthentication = {
                        ktorAuthnz("ktor-authnz") { }
                    }
                }
            ),
            preload = {
                // Enable the optional auth feature
                ConfigManager.preloadConfig("_features", FeatureConfig(enabledFeatures = listOf("auth")))
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port"))
                )
                ConfigManager.preloadConfig(
                    "auth",
                    OSSWallet2AuthConfig(jwtSecret = "test-secret-do-not-use-in-production-32x")
                )
            },
            init = {
                DidService.minimalInit()
                // Initialize the account store explicitly since configureWallet2Auth()
                // won't run (auth module setup happens via featureAmendment above).
                id.walt.ktorauthnz.KtorAuthnzManager.accountStore = OSSWallet2AccountStore
            },
            module = { wallet2Module(withPlugins = false) }
        ) {
            val http = testHttpClient()

            val email = "alice@example.com"
            val password = "correct-horse-battery-staple"

            // 1. Register
            testAndReturn("Register account") {
                http.post("/auth/register") {
                    setBody(RegisterRequest(email = email, password = password))
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
            }

            // 2. Login → obtain token
            val token = testAndReturn("Login") {
                http.post("/auth/emailpass") {
                    setBody(mapOf("email" to email, "password" to password))
                }.also { assertEquals(HttpStatusCode.OK, it.status) }
                    .body<AuthSessionInformation>()
                    .also {
                        assertEquals(AuthSessionStatus.SUCCESS, it.status)
                        assertNotNull(it.token, "Login succeeded but no token was returned")
                    }
                    .token!!
            }

            // 3. Create wallet while authenticated → auto-linked to account
            val walletId = testAndReturn("Create wallet (authenticated)") {
                http.post("/wallet") {
                    bearerAuth(token)
                    setBody(CreateWalletRequest())
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
                    .body<WalletCreatedResponse>().walletId
            }

            // 4. Account info → wallet appears in owned wallet list
            testAndReturn("Wallet listed under account") {
                val walletIds = http.get("/auth/account/wallets") {
                    bearerAuth(token)
                }.also { assertEquals(HttpStatusCode.OK, it.status) }
                    .body<List<String>>()
                assertTrue(walletId in walletIds, "Expected $walletId in $walletIds")
            }

            // 5. Access wallet with valid token → 200
            testAndReturn("Access wallet with valid token") {
                http.get("/wallet/$walletId") {
                    bearerAuth(token)
                }.also { assertEquals(HttpStatusCode.OK, it.status) }
            }

            // 6. Access wallet without token → 401
            testAndReturn("Access wallet without token → 401") {
                http.get("/wallet/$walletId")
                    // E2ETest testHttpClient follows redirects; 401 is returned directly
                    .also { assertEquals(HttpStatusCode.Unauthorized, it.status) }
            }

            // 7. Register a second account and verify it cannot access the first account's wallet
            val email2 = "bob@example.com"
            val password2 = "hunter2"
            testAndReturn("Register second account") {
                http.post("/auth/register") {
                    setBody(RegisterRequest(email = email2, password = password2))
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
            }
            val token2 = testAndReturn("Login as second account") {
                http.post("/auth/emailpass") {
                    setBody(mapOf("email" to email2, "password" to password2))
                }.body<AuthSessionInformation>().token!!
            }
            testAndReturn("Second account cannot access first account's wallet → 403") {
                http.get("/wallet/$walletId") {
                    bearerAuth(token2)
                }.also { assertEquals(HttpStatusCode.Forbidden, it.status) }
            }

            // 8. Logout → token is invalidated
            testAndReturn("Logout") {
                http.post("/auth/logout") {
                    bearerAuth(token)
                }.also { assertEquals(HttpStatusCode.OK, it.status) }
            }
            testAndReturn("Token rejected after logout → 401") {
                http.get("/wallet/$walletId") {
                    bearerAuth(token)
                }.also { assertEquals(HttpStatusCode.Unauthorized, it.status) }
            }
        }
    }
}
