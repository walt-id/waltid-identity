package id.walt.mobile.test.backend

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Test backend utilities for Local Enterprise stack.
 * Provides authentication, credential offers, and verifier session management.
 */
object LocalEnterpriseTestBackend {

    @Serializable
    data class BackendConfig(
        val apiBaseUrl: String,
        val ngrokBaseUrl: String,
        val apiHostHeader: String? = null,
        val adminEmail: String,
        val adminPassword: String,
        val org: String,
        val tenant: String,
        val issuerProfile: String,
        val verifier: String,
    ) {
        val tenantPath: String get() = "$org.$tenant"
    }

    @Serializable
    data class VerifierSession(
        val sessionId: String,
        val bootstrapAuthorizationRequestUrl: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Authenticate with enterprise API and get admin token.
     */
    suspend fun getAdminToken(config: BackendConfig, client: HttpClient): String {
        val payload = buildJsonObject {
            put("email", config.adminEmail)
            put("password", config.adminPassword)
        }

        val response = client.post("${config.apiBaseUrl}/auth/account/emailpass") {
            contentType(ContentType.Application.Json)
            headers {
                if (config.apiBaseUrl.startsWith("https://")) {
                    append("ngrok-skip-browser-warning", "true")
                }
            }
            setBody(payload.toString())
        }

        val responseJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
        return responseJson["token"]?.jsonPrimitive?.content
            ?: error("Auth response missing token: $responseJson")
    }

    /**
     * Create a pre-authorized credential offer.
     */
    suspend fun createPreAuthorizedOffer(config: BackendConfig, token: String, client: HttpClient): String {
        val payload = buildJsonObject {
            put("authMethod", "PRE_AUTHORIZED")
        }

        val response = client.post(
            "${config.ngrokBaseUrl}/v2/${config.tenantPath}.${config.issuerProfile}/issuer-service-api/credentials/offers"
        ) {
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
                append("ngrok-skip-browser-warning", "true")
            }
            setBody(payload.toString())
        }

        val responseJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
        return responseJson["credentialOffer"]?.jsonPrimitive?.content
            ?: error("Offer response missing credentialOffer: $responseJson")
    }

    /**
     * Create a verifier session for presentation.
     */
    suspend fun createVerifierSession(config: BackendConfig, token: String, client: HttpClient): VerifierSession {
        val payload = buildJsonObject {
            put("flow_type", "cross_device")
            putJsonObject("core_flow") {
                putJsonObject("dcql_query") {
                    putJsonArray("credentials") {
                        addJsonObject {
                            put("id", "my_mdl")
                            put("format", "mso_mdoc")
                            putJsonObject("meta") {
                                put("doctype_value", "org.iso.18013.5.1.mDL")
                            }
                            putJsonArray("claims") {
                                addJsonObject {
                                    putJsonArray("path") {
                                        add("org.iso.18013.5.1")
                                        add("family_name")
                                    }
                                }
                                addJsonObject {
                                    putJsonArray("path") {
                                        add("org.iso.18013.5.1")
                                        add("given_name")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val response = client.post(
            "${config.ngrokBaseUrl}/v1/${config.tenantPath}.${config.verifier}/verifier2-service-api/verification-session/create"
        ) {
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
                append("ngrok-skip-browser-warning", "true")
            }
            setBody(payload.toString())
        }

        val responseJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val sessionId = responseJson["sessionId"]?.jsonPrimitive?.content
        val requestUrl = responseJson["bootstrapAuthorizationRequestUrl"]?.jsonPrimitive?.content

        require(!sessionId.isNullOrBlank()) { "Verifier response missing sessionId: $responseJson" }
        require(!requestUrl.isNullOrBlank()) { "Verifier response missing bootstrapAuthorizationRequestUrl: $responseJson" }

        return VerifierSession(sessionId = sessionId, bootstrapAuthorizationRequestUrl = requestUrl)
    }

    /**
     * Poll verifier session status until success or timeout.
     * Returns the final status string: "SUCCESSFUL", "FAILED", "ERROR", "EXPIRED", or last known status.
     */
    suspend fun waitForVerifierSuccess(
        config: BackendConfig,
        sessionId: String,
        client: HttpClient,
        timeoutMs: Long = 30_000,
    ): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastStatus = "UNKNOWN"

        while (System.currentTimeMillis() < deadline) {
            // Get fresh token for each poll
            val token = getAdminToken(config, client)

            val response = client.get(
                "${config.apiBaseUrl}/v1/${config.tenantPath}.${config.verifier}.$sessionId/verifier2-service-api/verification-session/info"
            ) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    if (!config.apiHostHeader.isNullOrBlank()) {
                        append(HttpHeaders.Host, config.apiHostHeader)
                    }
                    if (config.apiBaseUrl.startsWith("https://")) {
                        append("ngrok-skip-browser-warning", "true")
                    }
                }
            }

            val responseJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
            lastStatus = responseJson["session"]?.jsonObject?.get("status")?.jsonPrimitive?.content ?: "UNKNOWN"

            when (lastStatus.uppercase()) {
                "SUCCESSFUL" -> return "SUCCESSFUL"
                "FAILED", "ERROR", "EXPIRED" -> return lastStatus
            }

            kotlinx.coroutines.delay(2_000)
        }

        return lastStatus
    }
}
