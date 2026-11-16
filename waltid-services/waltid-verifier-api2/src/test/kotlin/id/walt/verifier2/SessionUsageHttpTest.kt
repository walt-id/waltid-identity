package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.statics.RunConfiguration
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.commons.web.plugins.configureStatusPages
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.data.VerificationSessionUsage
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class SessionUsageHttpTest {
    private val files = mutableListOf<Path>()
    private val repository = InMemoryVerificationSessionRepository()

    @AfterTest
    fun cleanup() {
        for (id in listOf("verifier-service", "web")) System.clearProperty("config.file.$id")
        FeatureManager.preclear()
        ConfigManager.preclear()
        files.forEach { it.deleteIfExists() }
    }

    private suspend fun ApplicationTestBuilder.service(): HttpClient {
        for ((id, contents) in mapOf(
            "verifier-service" to """
                clientId = "verifier"
                urlPrefix = "http://localhost/verification-session"
                urlHost = "openid4vp://authorize"
            """.trimIndent(),
            "web" to "humanReadableResultEncoding = false",
        )) {
            val file = Files.createTempFile("wal1662-$id", ".conf")
            files.add(file)
            Files.writeString(file, contents)
            System.setProperty("config.file.$id", file.toString())
        }
        ConfigManager.preclear()
        FeatureManager.preclear()
        FeatureManager.registerCatalog(object : ServiceFeatureCatalog {
            override val baseFeatures = listOf(CommonsFeatureCatalog.webFeature, OSSVerifier2FeatureCatalog.verifierService)
            override val optionalFeatures = emptyList<OptionalFeature>()
        })
        RunConfiguration.args = emptyArray()
        RunConfiguration.configArgs = emptyArray()
        FeatureManager.load(emptyMap())
        application {
            install(ContentNegotiation) { json() }
            install(SSE)
            configureStatusPages()
            routing { Verifier2Service.run { registerRoute(repository) } }
        }
        return createClient { install(ClientContentNegotiation) { json() } }
    }

    private suspend fun stored(id: String): Verification2Session = assertNotNull(repository.get(id)).session

    @Test
    fun `wallet errors close strict sessions while other modes permit another attempt`() = testApplication {
        val http = service()
        for (mode in VerificationSessionUsage.entries) {
            val setup: VerificationSessionSetup = CrossDeviceFlowSetup(core = GeneralFlowConfig(sessionUsage = mode))
            val createdResponse = http.post("/verification-session/create") { contentType(ContentType.Application.Json); setBody(setup) }
            assertEquals(HttpStatusCode.OK, createdResponse.status)
            val id = createdResponse.body<VerificationSessionCreationResponse>().sessionId
            val request = http.get("/verification-session/$id/request").body<AuthorizationRequest>()
            assertEquals(HttpStatusCode.BadRequest, http.post("/verification-session/$id/response") {
                contentType(ContentType.Application.FormUrlEncoded); setBody("error=access_denied&state=wrong-state")
            }.status)
            assertFalse(stored(id).attempted)
            val form = listOf("error" to "access_denied", "state" to requireNotNull(request.state)).formUrlEncode()
            assertEquals(HttpStatusCode.OK, http.post("/verification-session/$id/response") {
                contentType(ContentType.Application.FormUrlEncoded); setBody(form)
            }.status)
            val failed = stored(id)
            assertEquals(Verification2Session.VerificationSessionStatus.FAILED, failed.status)
            assertEquals(mode, failed.sessionUsage)
            val retry = http.post("/verification-session/$id/response") {
                contentType(ContentType.Application.FormUrlEncoded); setBody(form)
            }
            assertEquals(if (mode == VerificationSessionUsage.SINGLE_ATTEMPT) HttpStatusCode.Conflict else HttpStatusCode.OK, retry.status)
            assertEquals(if (mode == VerificationSessionUsage.SINGLE_ATTEMPT) HttpStatusCode.BadRequest else HttpStatusCode.OK,
                http.get("/verification-session/$id/request").status)
            if (mode == VerificationSessionUsage.SINGLE_ATTEMPT) {
                assertEquals(failed.presentationId, stored(id).presentationId)
                assertEquals(failed.failure, stored(id).failure)
            }
            assertEquals(HttpStatusCode.OK, http.get("/verification-session/$id/info").status)
        }
    }

    @Test
    fun `an admitted invalid credential consumes a strict session`() = testApplication {
        val http = service()
        val setup: VerificationSessionSetup = CrossDeviceFlowSetup(core = GeneralFlowConfig(
            sessionUsage = VerificationSessionUsage.SINGLE_ATTEMPT,
            dcqlQuery = DcqlQuery(listOf(CredentialQuery(id = "credential", format = CredentialFormat.JWT_VC_JSON, meta = NoMeta))),
        ))
        val id = http.post("/verification-session/create") { contentType(ContentType.Application.Json); setBody(setup) }
            .body<VerificationSessionCreationResponse>().sessionId
        val request = http.get("/verification-session/$id/request").body<AuthorizationRequest>()
        val body = listOf("vp_token" to """{"credential":["not-a-credential"]}""", "state" to requireNotNull(request.state)).formUrlEncode()
        assertEquals(HttpStatusCode.BadRequest, http.post("/verification-session/$id/response") {
            contentType(ContentType.Application.FormUrlEncoded); setBody(body)
        }.status)
        assertEquals(Verification2Session.VerificationSessionStatus.FAILED, stored(id).status)
        assertEquals(HttpStatusCode.Conflict, http.post("/verification-session/$id/response") {
            contentType(ContentType.Application.FormUrlEncoded); setBody(body)
        }.status)
    }

    @Test
    fun `unknown policies and sessions return client errors`() = testApplication {
        val http = service()
        assertEquals(HttpStatusCode.BadRequest, http.post("/verification-session/create") {
            contentType(ContentType.Application.Json)
            setBody("""{"flow_type":"cross_device","core_flow":{"session_usage":"unknown"}}""")
        }.status)
        assertEquals(HttpStatusCode.NotFound, http.post("/verification-session/missing/response").status)
    }
}
