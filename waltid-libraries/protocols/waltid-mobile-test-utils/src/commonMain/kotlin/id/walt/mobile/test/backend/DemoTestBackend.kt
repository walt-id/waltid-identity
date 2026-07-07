package id.walt.mobile.test.backend

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Test helper for the public walt.id issuer2/verifier2 demo stack.
 */
object DemoTestBackend {

    private const val ISSUER_BASE_URL = "https://issuer2.demo.walt.id"
    private const val VERIFIER_BASE_URL = "https://verifier2.demo.walt.id"

    val scenarios = listOf(
        CredentialScenario(
            id = "eudi-pid-sdjwt",
            displayName = "EUDI PID SD-JWT VC",
            profileId = "eudiPidSdJwt",
            credentialConfigurationId = "urn:eudi:pid:1",
            format = "dc+sd-jwt",
            verifierCredentialQuery = sdJwtQuery(
                id = "pid",
                vct = "urn:eudi:pid:1",
            ),
        ),
        CredentialScenario(
            id = "eudi-pid-mdoc",
            displayName = "EUDI PID mdoc",
            profileId = "eudiPidMdoc",
            credentialConfigurationId = "eu.europa.ec.eudi.pid.1",
            format = "mso_mdoc",
            verifierCredentialQuery = mdocQuery(
                id = "pid_mdoc",
                doctype = "eu.europa.ec.eudi.pid.1",
                namespace = "eu.europa.ec.eudi.pid.1",
                claims = listOf("given_name", "family_name"),
            ),
        ),
        CredentialScenario(
            id = "iso-mdl",
            displayName = "ISO mDL",
            profileId = "isoMdl",
            credentialConfigurationId = "org.iso.18013.5.1.mDL",
            format = "mso_mdoc",
            verifierCredentialQuery = mdocQuery(
                id = "mdl",
                doctype = "org.iso.18013.5.1.mDL",
                namespace = "org.iso.18013.5.1",
                claims = listOf("given_name", "family_name"),
            ),
        ),
    )

    // Keep SD-JWT in issuer coverage, but limit verifier2 presentation to the
    // public demo formats the mobile wallet currently fulfills end to end.
    val presentationScenarios = scenarios.filter { it.format == "mso_mdoc" }

    val persistenceScenario = presentationScenarios.first()

    private val client by lazy {
        HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 30_000
            }
        }
    }

    data class CredentialScenario(
        val id: String,
        val displayName: String,
        val profileId: String,
        val credentialConfigurationId: String,
        val format: String,
        val verifierCredentialQuery: JsonObject,
    )

    data class GeneratedOffer(val offerUrl: String, val txCode: String?)

    data class VerifierSession(val sessionId: String, val authorizationRequestUri: String)

    suspend fun createOffer(scenario: CredentialScenario): GeneratedOffer {
        val payload = buildJsonObject {
            put("profileId", scenario.profileId)
            put("authMethod", "PRE_AUTHORIZED")
        }

        val response = requestJson(
            url = "$ISSUER_BASE_URL/issuer2/credential-offers",
            body = payload,
        )
        val offerUrl = response["credentialOffer"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing credentialOffer in public demo issuer2 response: $response")
        val txCode = response["txCode"]?.jsonPrimitive?.contentOrNull

        return GeneratedOffer(offerUrl = offerUrl, txCode = txCode)
    }

    suspend fun createVerifierSession(scenario: CredentialScenario): VerifierSession {
        val payload = buildJsonObject {
            put("flow_type", "cross_device")
            putJsonObject("core_flow") {
                putJsonObject("dcql_query") {
                    putJsonArray("credentials") {
                        add(scenario.verifierCredentialQuery)
                    }
                }
            }
        }

        val response = requestJson(
            url = "$VERIFIER_BASE_URL/verification-session/create",
            body = payload,
        )
        val sessionId = response["sessionId"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing sessionId in public demo verifier2 response: $response")
        val authorizationRequestUri = response["bootstrapAuthorizationRequestUrl"]?.jsonPrimitive?.contentOrNull
            ?: response["authorizationRequestUrl"]?.jsonPrimitive?.contentOrNull
            ?: response["fullAuthorizationRequestUrl"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing authorization request URL in public demo verifier2 response: $response")

        return VerifierSession(sessionId, authorizationRequestUri)
    }

    suspend fun waitForVerifierSuccess(sessionId: String, timeoutMs: Long = 90_000) {
        val mark = TimeSource.Monotonic.markNow()
        while (true) {
            if (mark.elapsedNow() > timeoutMs.milliseconds) {
                error("public demo verifier2 did not confirm presentation within ${timeoutMs}ms for session $sessionId")
            }

            val response = client.get("$VERIFIER_BASE_URL/verification-session/$sessionId/info") {
                accept(ContentType.Application.Json)
            }
            val body = response.bodyAsText()
            if (response.status.isSuccess() && body.isNotBlank()) {
                val status = runCatching {
                    val json = json.parseToJsonElement(body).jsonObject
                    json["status"]?.jsonPrimitive?.contentOrNull
                        ?: json["session"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
                }.getOrNull()

                when (status?.uppercase()) {
                    "SUCCESSFUL" -> return
                    "FAILED", "ERROR", "EXPIRED" -> error("public demo verifier2 reported $status for session $sessionId: $body")
                }
            }

            delay(2_000.milliseconds)
        }
    }

    private suspend fun requestJson(url: String, body: JsonObject): JsonObject {
        val response: HttpResponse = client.post(url) {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            accept(ContentType.Application.Json)
            setBody(body.toString())
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("HTTP ${response.status.value} from $url: $responseBody")
        }
        return json.parseToJsonElement(responseBody).jsonObject
    }

    private fun sdJwtQuery(
        id: String,
        vct: String,
    ): JsonObject = credentialQuery(
        id = id,
        format = "dc+sd-jwt",
        meta = buildJsonObject {
            putJsonArray("vct_values") {
                add(JsonPrimitive(vct))
            }
        },
        // The public demo verifier accepts vct-only SD-JWT requests; claim-path
        // filtering here currently causes wallet presentation matching to miss.
        claimPaths = emptyList(),
    )

    private fun mdocQuery(
        id: String,
        doctype: String,
        namespace: String,
        claims: List<String>,
    ): JsonObject = credentialQuery(
        id = id,
        format = "mso_mdoc",
        meta = buildJsonObject {
            put("doctype_value", doctype)
        },
        claimPaths = claims.map { claim -> listOf(namespace, claim) },
    )

    private fun credentialQuery(
        id: String,
        format: String,
        meta: JsonObject,
        claimPaths: List<List<String>>,
    ): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("format", JsonPrimitive(format))
        put("meta", meta)
        if (claimPaths.isNotEmpty()) {
            putJsonArray("claims") {
                claimPaths.forEach { path ->
                    add(
                        buildJsonObject {
                            putJsonArray("path") {
                                path.forEach { add(JsonPrimitive(it)) }
                            }
                        }
                    )
                }
            }
        }
    }

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
    }
}
