
package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionNextStepRedirectData
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.wallet2.auth.configureWallet2Auth
import id.walt.wallet2.auth.registerWallet2AuthRoutes
import id.walt.wallet2.config.registerWallet2ConfigDecoders
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the operator's config.file.auth -> HOCON -> ConfigManager ->
 * configureWallet2Auth() -> actual Ktor route registration path.
 *
 * Existing OIDC integration tests cover code exchange, JWT validation and UserInfo;
 * these tests ensure real deployment configuration reaches those routes.
 */
class Wallet2OidcConfigStartupTest {
    private var configFile: Path? = null

    @AfterEach
    fun cleanup() {
        System.clearProperty("config.file.auth")
        ConfigManager.preclear()
        configFile?.let(Files::deleteIfExists)
        configFile = null
    }

    @Test
    fun `OIDC HOCON settings survive startup and enable login route`() {
        val host = "127.0.0.1"
        val idpPort = freePort()
        val idpBase = "http://$host:$idpPort"
        val discoveryUrl = "$idpBase/.well-known/openid-configuration"
        val callbackUri = "http://$host:7006/auth/oidc/callback"

        // A local discovery server ensures the test never depends on external Keycloak.
        val idp = embeddedServer(CIO, host = host, port = idpPort) {
            routing {
                get("/.well-known/openid-configuration") {
                    call.respondText(
                        """{
                            "issuer":"$idpBase",
                            "authorization_endpoint":"$idpBase/authorize",
                            "token_endpoint":"$idpBase/token",
                            "userinfo_endpoint":"$idpBase/userinfo",
                            "jwks_uri":"$idpBase/jwks",
                            "id_token_signing_alg_values_supported":["ES256"]
                        }""".trimIndent(),
                        ContentType.Application.Json,
                    )
                }
            }
        }.start(wait = false)

        try {
            loadAuthConfig(
                """
                oidc {
                    openIdConfigurationUrl = "$discoveryUrl"
                    clientId = "wallet2-hocon-test"
                    clientSecret = "test-only-secret"
                    callbackUri = "$callbackUri"
                    pkceEnabled = false
                }
                """.trimIndent()
            )

            val loaded = ConfigManager.getConfig<OSSWallet2AuthConfig>()
            val oidc = assertNotNull(loaded.oidc)
            assertEquals("wallet2-hocon-test", oidc.clientId)
            assertEquals("test-only-secret", oidc.clientSecret)
            assertEquals(callbackUri, oidc.callbackUri)
            assertEquals(false, oidc.pkceEnabled)
            assertEquals(discoveryUrl, oidc.openIdConfigurationUrl.toString())

            // Resolve discovery metadata and verify the token endpoint.
            assertEquals(
                "$idpBase/token",
                runBlocking { oidc.getOpenIdConfiguration() }.tokenEndpoint
            )

            testApplication {
                application { configureAuthRoutesFromHocon() }

                val response = client.get("/auth/oidc/auth")
                assertEquals(HttpStatusCode.OK, response.status)

                val session = Json.decodeFromString<AuthSessionInformation>(
                    response.bodyAsText()
                )
                assertEquals(
                    AuthSessionStatus.CONTINUE_NEXT_STEP,
                    session.status
                )
                val redirect = assertIs<AuthSessionNextStepRedirectData>(
                    session.nextStep
                )
                assertEquals(
                    "wallet2-hocon-test",
                    redirect.url.parameters["client_id"]
                )
                assertEquals(
                    callbackUri,
                    redirect.url.parameters["redirect_uri"]
                )
                assertNull(redirect.url.parameters["code_challenge"])
            }
        } finally {
            idp.stop(
                gracePeriodMillis = 1000,
                timeoutMillis = 3000
            )
        }
    }

    @Test
    fun `HOCON without OIDC keeps email password login available`() {
        loadAuthConfig(oidcBlock = "")

        val loaded = ConfigManager.getConfig<OSSWallet2AuthConfig>()
        assertNull(loaded.oidc)

        testApplication {
            application { configureAuthRoutesFromHocon() }

            assertEquals(
                HttpStatusCode.NotFound,
                client.get("/auth/oidc/auth").status
            )

            val email = "oidc-absent-${UUID.randomUUID()}@example.com"
            val password = "test-password"

            val registration = client.post("/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"email":"$email","password":"$password"}"""
                )
            }
            assertEquals(HttpStatusCode.Created, registration.status)

            val login = client.post("/auth/emailpass") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"email":"$email","password":"$password"}"""
                )
            }
            assertEquals(HttpStatusCode.OK, login.status)

            val session = Json.decodeFromString<AuthSessionInformation>(
                login.bodyAsText()
            )
            assertEquals(AuthSessionStatus.SUCCESS, session.status)
            assertTrue(!session.token.isNullOrBlank())
        }
    }

    private fun loadAuthConfig(oidcBlock: String) {
        ConfigManager.preclear()

        val signingKey = runBlocking {
            JWKKey.generate(KeyType.secp256r1)
        }

        val content = """
            signingKey = ${KeySerialization.serializeKey(signingKey)}
            tokenExpiry = "PT1H"
            $oidcBlock
        """.trimIndent()

        configFile = Files.createTempFile(
            "wallet2-oidc-auth",
            ".conf"
        )
        Files.writeString(requireNotNull(configFile), content)

        System.setProperty(
            "config.file.auth",
            requireNotNull(configFile).toString()
        )

        registerWallet2ConfigDecoders()
        ConfigManager.registerConfig(
            "auth",
            OSSWallet2AuthConfig::class
        )
        ConfigManager.loadConfigs()
    }

    private fun Application.configureAuthRoutesFromHocon() {
        // Follow Wallet2's production auth initialization path.
        val authConfig = runBlocking {
            configureWallet2Auth()
        }

        install(ContentNegotiation) {
            json()
        }
        install(Authentication) {
            ktorAuthnz("ktor-authnz") { }
        }

        routing {
            registerWallet2AuthRoutes(
                tokenExpiry = authConfig.tokenExpiry,
                walletResolver = OSSWallet2Service.resolver,
                oidcConfig = authConfig.oidc,
            )
        }
    }
}
