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
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/**
 * Test helper for the public walt.id issuer2/verifier2 demo stack.
 */
object DemoTestBackend {

    private const val ISSUER_BASE_URL = "https://issuer2.demo.walt.id"
    private const val VERIFIER_BASE_URL = "https://verifier2.demo.walt.id"
    const val TRANSACTION_DATA_PROFILES_URL = "https://wallet.demo.walt.id/wallet-api/transaction-data-profiles"
    private const val EUDI_PID_SD_JWT_VCT = "$ISSUER_BASE_URL/openid4vci/urn:eudi:pid:1"
    private const val PAYMENT_AUTHORIZATION_TYPE = "org.waltid.transaction-data.payment-authorization"
    private val requiredPaymentAuthorizationFields = setOf("amount", "currency", "payee")

    val scenarios = listOf(
        CredentialScenario(
            id = "eudi-pid-sdjwt",
            displayName = "EUDI PID SD-JWT VC",
            profileId = "eudiPidSdJwt",
            credentialConfigurationId = "urn:eudi:pid:1",
            format = "dc+sd-jwt",
            verifierCredentialQuery = sdJwtQuery(
                id = "pid",
                vct = EUDI_PID_SD_JWT_VCT,
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

    val presentationScenarios = scenarios

    val optionalBirthDatePresentationScenario = scenarios.first { it.id == "eudi-pid-sdjwt" }.copy(
        id = "eudi-pid-sdjwt-optional-birth-date",
        verifierCredentialQuery = sdJwtOptionalBirthDateQuery(
            id = "pid",
            vct = EUDI_PID_SD_JWT_VCT,
        ),
    )

    val transactionDataPresentationScenario = scenarios.first { it.id == "eudi-pid-sdjwt" }

    val persistenceScenario = scenarios.first { it.id == "eudi-pid-mdoc" }

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

    suspend fun createOffer(
        scenario: CredentialScenario,
        withGeneratedTransactionCode: Boolean = false,
    ): GeneratedOffer {
        val payload = buildJsonObject {
            put("profileId", scenario.profileId)
            put("authMethod", "PRE_AUTHORIZED")
            if (withGeneratedTransactionCode) {
                putJsonObject("txCode") {
                    put("input_mode", "numeric")
                    put("length", 6)
                    put("description", "Enter the transaction code shown by the issuer")
                }
            }
        }

        val response = requestJson(
            url = "$ISSUER_BASE_URL/issuer2/credential-offers",
            body = payload,
        )
        val offerUrl = response["credentialOffer"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing credentialOffer in public demo issuer2 response: $response")
        val txCode = response["txCodeValue"]?.jsonPrimitive?.contentOrNull
            ?: response["txCode"]?.jsonPrimitive?.contentOrNull
        check(!withGeneratedTransactionCode || txCode != null) {
            "Public demo issuer2 did not return the requested transaction code: $response"
        }

        return GeneratedOffer(offerUrl = offerUrl, txCode = txCode)
    }

    suspend fun createVerifierSession(
        scenario: CredentialScenario,
        encryptedResponse: Boolean = false,
    ): VerifierSession {
        return createVerifierSession(scenario.verifierCredentialQuery, encryptedResponse)
    }

    suspend fun createResponseBoundVerifierSession(scenario: CredentialScenario): VerifierSession {
        return createVerifierSession(
            credentialQuery = scenario.verifierCredentialQuery,
            transactionData = emptyList(),
            bindClientIdToResponseUri = true,
        )
    }

    suspend fun createVerifierSession(
        credentialQuery: JsonObject,
        encryptedResponse: Boolean = false,
    ): VerifierSession {
        return createVerifierSession(
            credentialQuery = credentialQuery,
            transactionData = emptyList(),
            encryptedResponse = encryptedResponse,
        )
    }

    suspend fun createTransactionDataVerifierSession(
        scenario: CredentialScenario = transactionDataPresentationScenario,
    ): VerifierSession {
        val paymentAuthorizationFields = transactionDataProfileFields(PAYMENT_AUTHORIZATION_TYPE)
        check(paymentAuthorizationFields.containsAll(requiredPaymentAuthorizationFields)) {
            "Public demo transaction data profile '$PAYMENT_AUTHORIZATION_TYPE' is missing required fields: " +
                (requiredPaymentAuthorizationFields - paymentAuthorizationFields).joinToString()
        }
        return createVerifierSession(
            credentialQuery = scenario.verifierCredentialQuery,
            transactionData = listOf(paymentAuthorizationTransactionData("pid", paymentAuthorizationFields)),
            encryptedResponse = false,
        )
    }

    suspend fun transactionDataProfileFields(type: String): Set<String> {
        val response = client.get(TRANSACTION_DATA_PROFILES_URL) {
            accept(ContentType.Application.Json)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("HTTP ${response.status.value} from public demo transaction data profiles endpoint: $body")
        }
        return json.parseToJsonElement(body)
            .jsonArray
            .firstOrNull { profile -> profile.jsonObject["type"]?.jsonPrimitive?.content == type }
            ?.jsonObject
            ?.get("fields")
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
            ?: error("Missing public demo transaction data profile: $type")
    }

    private suspend fun createVerifierSession(
        credentialQuery: JsonObject,
        transactionData: List<JsonObject>,
        encryptedResponse: Boolean = false,
        bindClientIdToResponseUri: Boolean = false,
    ): VerifierSession {
        val requestedSessionId = Uuid.random().toString().takeIf { bindClientIdToResponseUri }
        val payload = buildJsonObject {
            put("flow_type", "cross_device")
            putJsonObject("core_flow") {
                requestedSessionId?.let { sessionId ->
                    val responseUri = "$VERIFIER_BASE_URL/verification-session/$sessionId/response"
                    put("sessionId", sessionId)
                    put("clientId", "redirect_uri:$responseUri")
                }
                if (encryptedResponse) put("encrypted_response", true)
                putJsonObject("dcql_query") {
                    putJsonArray("credentials") {
                        add(credentialQuery)
                    }
                }
            }
            if (transactionData.isNotEmpty()) {
                putJsonObject("openid") {
                    putJsonArray("transactionData") {
                        transactionData.forEach { add(it) }
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
        check(requestedSessionId == null || requestedSessionId == sessionId) {
            "Public demo verifier2 did not preserve the requested session ID"
        }
        val authorizationRequestUri = response["bootstrapAuthorizationRequestUrl"]?.jsonPrimitive?.contentOrNull
            ?: response["authorizationRequestUrl"]?.jsonPrimitive?.contentOrNull
            ?: response["fullAuthorizationRequestUrl"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing inline authorization request URL in public demo verifier2 response: $response")

        return VerifierSession(sessionId, authorizationRequestUri)
    }

    private fun paymentAuthorizationTransactionData(
        credentialId: String,
        fields: Set<String>,
    ): JsonObject = buildJsonObject {
        put("type", JsonPrimitive(PAYMENT_AUTHORIZATION_TYPE))
        putJsonArray("credential_ids") {
            add(JsonPrimitive(credentialId))
        }
        put("require_cryptographic_holder_binding", JsonPrimitive(true))
        putJsonArray("transaction_data_hashes_alg") {
            add(JsonPrimitive("sha-256"))
        }
        putProfileField(fields, "amount", "42.00")
        putProfileField(fields, "currency", "EUR")
        putProfileField(fields, "payee", "ACME Corp")
    }

    suspend fun verifierSessionInfo(sessionId: String): JsonObject {
        val response = client.get("$VERIFIER_BASE_URL/verification-session/$sessionId/info") {
            accept(ContentType.Application.Json)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("HTTP ${response.status.value} from public demo verifier2 session info for $sessionId: $body")
        }
        return json.parseToJsonElement(body).jsonObject
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

    suspend fun waitForVerifierFailure(
        sessionId: String,
        expectedError: String,
        timeoutMs: Long = 90_000,
    ): JsonObject {
        val mark = TimeSource.Monotonic.markNow()
        while (true) {
            if (mark.elapsedNow() > timeoutMs.milliseconds) {
                error("public demo verifier2 did not report $expectedError within ${timeoutMs}ms for session $sessionId")
            }

            val info = verifierSessionInfo(sessionId)
            val status = info["status"]?.jsonPrimitive?.contentOrNull
                ?: info["session"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
            when (status?.uppercase()) {
                "FAILED" -> {
                    val failure = info["failure"]?.jsonObject
                        ?: error("public demo verifier2 omitted failure details for session $sessionId: $info")
                    val actualError = failure["error"]?.jsonPrimitive?.contentOrNull
                    check(actualError == expectedError) {
                        "public demo verifier2 reported $actualError instead of $expectedError for session $sessionId: $info"
                    }
                    return info
                }

                "SUCCESSFUL", "ERROR", "EXPIRED" ->
                    error("public demo verifier2 reported $status instead of $expectedError for session $sessionId: $info")
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

    private fun sdJwtOptionalBirthDateQuery(
        id: String,
        vct: String,
    ): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("format", JsonPrimitive("dc+sd-jwt"))
        putJsonObject("meta") {
            putJsonArray("vct_values") {
                add(JsonPrimitive(vct))
            }
        }
        putJsonArray("claims") {
            add(claimQuery(id = "given_name", path = listOf("given_name")))
            add(claimQuery(id = "family_name", path = listOf("family_name")))
            add(claimQuery(id = "birth_date", path = listOf("birth_date")))
        }
        putJsonArray("claim_sets") {
            add(claimSet("given_name", "family_name"))
            add(claimSet("given_name", "family_name", "birth_date"))
        }
    }

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

    private fun claimQuery(id: String, path: List<String>): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        putJsonArray("path") {
            path.forEach { add(JsonPrimitive(it)) }
        }
    }

    private fun claimSet(vararg claimIds: String) = kotlinx.serialization.json.buildJsonArray {
        claimIds.forEach { add(JsonPrimitive(it)) }
    }

    private fun JsonObjectBuilder.putProfileField(fields: Set<String>, key: String, value: String) {
        if (key in fields) {
            put(key, JsonPrimitive(value))
        }
    }

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
    }
}
