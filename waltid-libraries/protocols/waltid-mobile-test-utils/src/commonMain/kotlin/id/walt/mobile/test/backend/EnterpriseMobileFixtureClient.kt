package id.walt.mobile.test.backend

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class EnterpriseMobileFixtureClient(
    private val baseUrl: String,
    private val client: HttpClient = defaultClient(),
) {
    suspend fun scenarios(): List<EnterpriseMobileScenario> {
        val response = client.get("$baseUrl/scenarios") {
            accept(ContentType.Application.Json)
        }
        return json.decodeFromString(ListSerializer(EnterpriseMobileScenario.serializer()), response.requireBody())
    }

    suspend fun createOffer(
        scenario: EnterpriseMobileScenario,
        platform: EnterpriseMobilePlatform,
    ): EnterpriseMobileOffer = requestJson<CreateEnterpriseMobileOfferRequest, EnterpriseMobileOffer>(
        path = "/offers",
        body = CreateEnterpriseMobileOfferRequest(scenario.id, platform),
    )

    suspend fun createVerifierSession(
        scenario: EnterpriseMobileScenario,
        platform: EnterpriseMobilePlatform,
    ): EnterpriseMobileVerifierSession =
        requestJson<CreateEnterpriseMobileVerificationSessionRequest, EnterpriseMobileVerifierSession>(
            path = "/verification-sessions",
            body = CreateEnterpriseMobileVerificationSessionRequest(scenario.id, platform),
        )

    suspend fun waitForVerifierSuccess(sessionId: String, timeoutMs: Long = 90_000) {
        val mark = TimeSource.Monotonic.markNow()
        while (true) {
            if (mark.elapsedNow() > timeoutMs.milliseconds) {
                error("Enterprise verifier2 did not confirm presentation within ${timeoutMs}ms for session $sessionId")
            }

            val response = client.get("$baseUrl/verification-sessions/$sessionId") {
                accept(ContentType.Application.Json)
            }
            val body = response.requireBody()
            val status = json.parseToJsonElement(body).jsonObject["status"]?.jsonPrimitive?.contentOrNull
            when (status?.uppercase()) {
                "SUCCESSFUL" -> return
                "FAILED", "ERROR", "EXPIRED" -> error("Enterprise verifier2 reported $status for session $sessionId: $body")
            }

            delay(2_000.milliseconds)
        }
    }

    private suspend inline fun <reified TRequest, reified TResponse> requestJson(path: String, body: TRequest): TResponse {
        val response = client.post("$baseUrl$path") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            accept(ContentType.Application.Json)
            setBody(json.encodeToString(body))
        }
        return json.decodeFromString(response.requireBody())
    }

    private suspend fun HttpResponse.requireBody(): String {
        val responseBody = bodyAsText()
        if (!status.isSuccess()) {
            error("HTTP ${status.value} from $baseUrl: $responseBody")
        }
        return responseBody
    }

    companion object {
        private fun defaultClient() = HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 30_000
            }
        }

        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

@Serializable
data class EnterpriseMobileScenario(
    val id: String,
    val displayName: String,
    val format: String,
    val supportsPresentation: Boolean,
    val requiresClientAttestation: Boolean,
)

@Serializable
enum class EnterpriseMobilePlatform {
    ANDROID,
    IOS,
}

@Serializable
data class EnterpriseMobileAttestationConfig(
    val baseUrl: String,
    val attesterPath: String,
    val bearerToken: String = "",
    val hostHeader: String = "",
)

@Serializable
data class EnterpriseMobileOffer(
    val offerUrl: String,
    val txCode: String? = null,
    val attestation: EnterpriseMobileAttestationConfig? = null,
)

@Serializable
data class EnterpriseMobileVerifierSession(
    val sessionId: String,
    val authorizationRequestUri: String,
)

@Serializable
private data class CreateEnterpriseMobileOfferRequest(
    val scenarioId: String,
    val platform: EnterpriseMobilePlatform,
)

@Serializable
private data class CreateEnterpriseMobileVerificationSessionRequest(
    val scenarioId: String,
    val platform: EnterpriseMobilePlatform,
)
