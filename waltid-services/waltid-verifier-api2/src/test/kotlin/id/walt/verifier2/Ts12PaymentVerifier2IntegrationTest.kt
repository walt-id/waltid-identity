package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.commons.web.plugins.configureStatusPages
import id.walt.dcql.models.*
import id.walt.dcql.models.meta.NoMeta
import id.walt.verifier.openid.transactiondata.*
import id.walt.verifier2.data.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import kotlin.test.*

class Ts12PaymentVerifier2IntegrationTest {
    @Test fun invalidPayloadsNeverCreateASessionAndValidSchedulesKeepTheirNumericTokens() = testApplication {
        val repository = InMemoryVerificationSessionRepository()
        val setup = CrossDeviceFlowSetup(
            core = GeneralFlowConfig(dcqlQuery = DcqlQuery(listOf(CredentialQuery("payment", CredentialFormat.DC_SD_JWT, meta = NoMeta)))),
        )
        ConfigManager.registerConfig("verifier-service", OSSVerifier2ServiceConfig::class)
        ConfigManager.loadedConfigurations["verifier-service" to OSSVerifier2ServiceConfig::class] = OSSVerifier2ServiceConfig(
            urlPrefix = "https://verifier.example/verification-session", urlHost = "openid4vp://authorize")
        application {
            install(ContentNegotiation) { json() }; install(SSE); configureStatusPages()
            routing { Verifier2Service.run { registerRoute(repository, setOf(Verifier2RouteSurface.SESSION_MANAGEMENT)) } }
        }
        try {
            val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
            val base = Json.parseToJsonElement("""{"transaction_id":"api-payment","payee":{"name":"Shop","id":"merchant"},"currency":"EUR","amount":9007199254740993.01}""").jsonObject
            val profile = TransactionDataProfile(Ts12Payment.TYPE, fields = listOf("payload"))
            assertNotNull(profile.toResponse().payloadSchema)
            assertNull(TransactionDataProfile("custom").toResponse().payloadSchema)
            for ((index, extra) in listOf(
                """{"execution_date":"2027-01-01"}""",
                """{"recurrence":{}}""",
                """{"execution_date":"2000-01-01T12:00:00Z"}""",
                """{"recurrence":{"frequency":"MNTH","unknown":1}}""",
                """{"execution_date":"2099-01-01T12:00:00Z","recurrence":{"frequency":"MNTH"}}""",
                """{"execution_date":"2099-01-01T12:00:00Z"}""",
                """{"recurrence":{"frequency":"MNTH","mit_options":{"apr":0.1234567890123456789}}}""",
            ).withIndex()) {
                val sessionId = "payment-$index"
                val item = buildJsonObject {
                    put("type", Ts12Payment.TYPE); put("credential_ids", buildJsonArray { add("payment") })
                    put("payload", JsonObject(base + Json.parseToJsonElement(extra).jsonObject))
                }
                val request = setup.copy(core = setup.core.copy(sessionId = sessionId),
                    openid = OpenId4VPConfig(transactionData = listOf(item)))
                val response = client.post("/verification-session/create") {
                    contentType(ContentType.Application.Json); setBody<VerificationSessionSetup>(request)
                }
                val body = response.bodyAsText()
                if (index < 5) {
                    assertEquals(HttpStatusCode.BadRequest, response.status, body)
                    assertTrue(body.contains("payload"), "Expected payload validation error for case $index: $body")
                    assertNull(repository.get(sessionId))
                } else {
                    assertEquals(HttpStatusCode.OK, response.status, body)
                    val stored = assertNotNull(repository.get(sessionId)).session
                    val payload = decode(stored.authorizationRequest.transactionData!!.single()).details.getValue("payload").jsonObject
                    assertEquals("9007199254740993.01", payload.getValue("amount").jsonPrimitive.content)
                }
            }
        } finally { ConfigManager.preclear() }
    }
}
