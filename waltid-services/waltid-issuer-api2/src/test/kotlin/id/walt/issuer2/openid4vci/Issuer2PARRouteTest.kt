package id.walt.issuer2.openid4vci

import id.walt.issuer2.application.openid4vci.OpenId4VciModule
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.controller.OpenId4VciController
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.notifications.IssuanceNotificationService
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.issuer2.service.CredentialOfferService
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.service.openid4vci.MetadataService
import id.walt.issuer2.service.openid4vci.OpenId4VciProtocolService
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRecord
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class Issuer2PARRouteTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    @Test
    fun `par route returns no-store response and request uri payload`() = testApplication {
        application {
            install(ServerContentNegotiation) {
                json(json)
            }
            install(Authentication) {
                bearer("auth-oauth") {}
            }
            routing {
                testController().register(this)
            }
        }
        val client = createClient {
            install(ClientContentNegotiation) {
                json(json)
            }
        }

        val response = client.post("/openid4vci/par") {
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("client_id", "test-client")
                        append("response_type", "code")
                        append("redirect_uri", "https://wallet.example/callback")
                        append("scope", "openid")
                        append("state", "state123")
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("no-cache", response.headers[HttpHeaders.Pragma])

        val payload = response.body<JsonObject>()
        val requestUri = assertNotNull(payload["request_uri"]?.jsonPrimitive?.contentOrNull)
        assertEquals(true, requestUri.startsWith("urn:ietf:params:oauth:request_uri:"))
        assertEquals("90", payload["expires_in"]?.jsonPrimitive?.content)
    }

    private fun testController(): OpenId4VciController {
        val serviceConfig = Issuer2ServiceConfig(baseUrl = "http://localhost")
        val metadataConfig = Issuer2MetadataConfig()
        val profileService = CredentialProfileService(
            profilesConfig = Issuer2ProfilesConfig(),
            metadataConfig = metadataConfig,
        )
        val sessionService = IssuanceSessionService(NoopIssuanceSessionRepository)
        val notificationService = IssuanceNotificationService()
        val openId4VciModule = OpenId4VciModule.create(
            config = serviceConfig,
            authorizationCodeRepository = NoopAuthorizationCodeRepository,
            preAuthorizedCodeRepository = NoopPreAuthorizedCodeRepository,
            parRepository = InMemoryPARRepository(),
        )
        val metadataService = MetadataService(
            serviceConfig = serviceConfig,
            metadataConfig = metadataConfig,
            profileService = profileService,
            sessionService = sessionService,
        )

        return OpenId4VciController(
            metadataService = metadataService,
            protocolService = OpenId4VciProtocolService(
                oauth2Provider = openId4VciModule.oauth2Provider,
                sessionService = sessionService,
                profileService = profileService,
                metadataService = metadataService,
                notificationService = notificationService,
            ),
            offerService = CredentialOfferService(
                profileService = profileService,
                sessionService = sessionService,
                preAuthorizedCodeIssuer = openId4VciModule.preAuthorizedCodeIssuer,
                config = serviceConfig,
                notificationService = notificationService,
            ),
        )
    }

    private object NoopAuthorizationCodeRepository : AuthorizationCodeRepository {
        override suspend fun save(record: AuthorizationCodeRecord) = Unit
        override suspend fun consume(code: String): AuthorizationCodeRecord? = null
    }

    private object NoopPreAuthorizedCodeRepository : PreAuthorizedCodeRepository {
        override suspend fun save(record: PreAuthorizedCodeRecord) = Unit
        override suspend fun get(code: String): PreAuthorizedCodeRecord? = null
        override suspend fun consume(code: String): PreAuthorizedCodeRecord? = null
    }

    private object NoopIssuanceSessionRepository : IssuanceSessionRepository {
        override suspend fun save(session: IssuanceSession): IssuanceSession = session
        override suspend fun get(sessionId: String): IssuanceSession? = null
        override suspend fun list(): List<IssuanceSession> = emptyList()
        override suspend fun remove(sessionId: String) = Unit
    }
}