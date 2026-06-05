package id.walt.wallet2.client.test

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Generates credential offers from the public EUDI test backend.
 * Ports the form-submission flow from EudiPublicBackendReceiveInstrumentedTest.
 */
object EudiTestBackend {

    private const val ENTRYPOINT = "https://issuer.eudiw.dev/credential_offer"
    private const val BACKEND_GENERATE = "https://backend.issuer.eudiw.dev/credential_offer"
    private const val BACKEND_AUTHORIZE = "https://backend.issuer.eudiw.dev/form_authorize_generate"
    private const val VERIFIER_BACKEND = "https://verifier-backend.eudiw.dev"

    private val client by lazy {
        HttpClient {
            install(HttpCookies)
            install(HttpRedirect) { checkHttpMethod = false }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 30_000
            }
            followRedirects = true
        }
    }

    data class GeneratedOffer(val offerUrl: String, val txCode: String?)

    data class VerifierTransaction(val transactionId: String, val authorizationRequestUri: String)

    suspend fun generateOffer(credentialId: String = "eu.europa.ec.eudi.pid_jwt_vc_json"): GeneratedOffer {
        // Step 1: Get entry page, extract redirect form
        val entryResponse = client.get(ENTRYPOINT)
        val entryBody = entryResponse.bodyAsText()
        val entryUrl = entryResponse.request.url.toString()

        val redirectAction = resolveUrl(entryUrl, extractFormAction(entryBody, "redirect_form"))
        val redirectPayload = extractPayload(entryBody)
        client.submitForm(redirectAction, parameters { append("payload", redirectPayload) })

        // Step 2: Generate pre-authorized offer
        val preauthResponse = client.submitForm(BACKEND_GENERATE, parameters {
            append(credentialId, credentialId)
            append("Authorization Code Grant", "pre_auth_code")
            append("credential_offer_URI", "openid-credential-offer://")
            append("proceed", "Submit")
        })
        val preauthBody = preauthResponse.bodyAsText()
        val preauthUrl = preauthResponse.request.url.toString()

        // Step 3: Follow redirect form to display page
        val displayAction = resolveUrl(preauthUrl, extractFormAction(preauthBody, "redirect_form"))
        val displayPayload = extractPayload(preauthBody)
        val displayResponse = client.submitForm(displayAction, parameters { append("payload", displayPayload) })
        val displayBody = displayResponse.bodyAsText()
        val displayUrl = displayResponse.request.url.toString()

        // Step 4: Submit country/user data form
        val countryAction = resolveUrl(displayUrl, extractFormAction(displayBody, "selectCountryForm"))
        val authResponse = client.submitForm(countryAction, parameters {
            append("birthdate", "1990-01-01")
            append("family_name", "Tester")
            append("given_name", "Alice")
            append("nationalities[0][country_code]", "DE")
            append("place_of_birth[0][country]", "DE")
            append("place_of_birth[0][region]", "Berlin")
            append("place_of_birth[0][locality]", "Berlin")
            append("proceed", "Confirm")
        })
        val authBody = authResponse.bodyAsText()
        val authUrl = authResponse.request.url.toString()

        // Step 5: Follow authorization redirect form
        val authFormAction = resolveUrl(authUrl, extractFormAction(authBody, "redirect_form"))
        val authFormPayload = extractPayload(authBody)
        val authPageResponse = client.submitForm(authFormAction, parameters { append("payload", authFormPayload) })
        val authPageBody = authPageResponse.bodyAsText()

        // Step 6: Extract user_id and authorize
        val userId = extractUserId(authPageBody)
        val offerResponse = client.submitForm(BACKEND_AUTHORIZE, parameters {
            append("user_id", userId)
            append("proceed", "Authorize")
        })
        val offerBody = offerResponse.bodyAsText()

        // Step 7: Extract final payload with tx_code and url_data
        val finalPayload = Json.parseToJsonElement(extractPayload(offerBody)).jsonObject
        val txCodeValue = finalPayload["tx_code"]?.jsonPrimitive?.content
        val urlData = finalPayload["url_data"]?.jsonPrimitive?.content
            ?: error("final payload missing url_data")

        // Step 8: Parse credential_offer, inject tx_code
        val credentialOfferParam = Url(urlData).parameters["credential_offer"]
            ?: error("credential_offer query parameter missing from url_data")
        val offerJson = Json.parseToJsonElement(credentialOfferParam).jsonObject.toMutableMap()

        if (txCodeValue != null) {
            val grants = offerJson["grants"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
            val preAuth = grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"]
                ?.jsonObject?.toMutableMap() ?: mutableMapOf()
            val txCode = preAuth["tx_code"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
            txCode["value"] = JsonPrimitive(txCodeValue)
            preAuth["tx_code"] = JsonObject(txCode)
            grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"] = JsonObject(preAuth)
            offerJson["grants"] = JsonObject(grants)
        }

        val encodedOffer = JsonObject(offerJson).toString().encodeURLParameter()
        val injectedOfferUrl = "openid-credential-offer://credential_offer?credential_offer=$encodedOffer"

        return GeneratedOffer(offerUrl = injectedOfferUrl, txCode = txCodeValue)
    }

    suspend fun createVerifierTransaction(credentialId: String = "eu.europa.ec.eudi.pid_jwt_vc_json"): VerifierTransaction {
        val dcqlQuery = buildDcqlQuery(credentialId)
        val payload = buildJsonObject {
            put("dcql_query", dcqlQuery)
            @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
            put("nonce", JsonPrimitive(kotlin.uuid.Uuid.random().toString()))
            put("request_uri_method", JsonPrimitive("post"))
            put("profile", JsonPrimitive("openid4vp"))
            put("authorization_request_uri", JsonPrimitive("openid4vp://"))
        }

        val response = client.post("$VERIFIER_BACKEND/ui/presentations/v2") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val transactionId = responseJson["transaction_id"]?.jsonPrimitive?.content
            ?: error("No transaction_id in verifier response: $responseJson")
        val authRequestUri = responseJson["authorization_request_uri"]?.jsonPrimitive?.content
            ?: error("No authorization_request_uri in verifier response: $responseJson")

        return VerifierTransaction(transactionId, authRequestUri)
    }

    suspend fun waitForVerifierSuccess(transactionId: String, timeoutMs: Long = 90_000) {
        val mark = TimeSource.Monotonic.markNow()
        while (true) {
            if (mark.elapsedNow() > timeoutMs.milliseconds) {
                error("Verifier did not confirm presentation within ${timeoutMs}ms")
            }

            val response = client.get("$VERIFIER_BACKEND/ui/presentations/$transactionId/events")
            val body = response.bodyAsText()
            if (body.isNotBlank()) {
                val events = Json.parseToJsonElement(body).jsonArray
                for (event in events) {
                    val text = event.toString().lowercase()
                    if (text.contains("failed") || text.contains("error") || text.contains("invalid")) {
                        error("Verifier reported failure: $event")
                    }
                    if (text.contains("wallet response posted") || text.contains("successful") || text.contains("verified")) {
                        return
                    }
                }
            }

            kotlinx.coroutines.delay(2000)
        }
    }

    private fun buildDcqlQuery(credentialId: String): JsonElement {
        val format: String
        val meta: JsonObject

        when {
            credentialId.contains("sd_jwt") || credentialId.contains("jwt_vc") -> {
                format = "dc+sd-jwt"
                meta = buildJsonObject {
                    putJsonArray("vct_values") {
                        add("urn:eu.europa.ec.eudi:pid:1")
                        add("eu.europa.ec.eudi.pid.1")
                    }
                }
            }
            credentialId.contains("mdl") -> {
                format = "mso_mdoc"
                meta = buildJsonObject {
                    put("doctype_value", JsonPrimitive("org.iso.18013.5.1.mDL"))
                }
            }
            else -> {
                format = "mso_mdoc"
                meta = buildJsonObject {
                    put("doctype_value", JsonPrimitive("eu.europa.ec.eudi.pid.1"))
                }
            }
        }

        return buildJsonObject {
            putJsonArray("credentials") {
                addJsonObject {
                    put("id", JsonPrimitive("identity"))
                    put("format", JsonPrimitive(format))
                    put("meta", meta)
                    putJsonArray("claims") {
                        addJsonObject { putJsonArray("path") { add("given_name") } }
                        addJsonObject { putJsonArray("path") { add("family_name") } }
                    }
                }
            }
        }
    }

    private fun extractFormAction(html: String, formId: String): String {
        val regex = Regex("""<form\s+[^>]*id="$formId"[^>]*action="([^"]+)"""")
        val altRegex = Regex("""<form\s+[^>]*action="([^"]+)"[^>]*id="$formId"""")
        val match = regex.find(html) ?: altRegex.find(html)
            ?: error("Form '$formId' not found in HTML")
        return htmlUnescape(match.groupValues[1])
    }

    private fun extractPayload(html: String): String {
        val regex = Regex("""name="payload"\s+value='(.*?)'\s*>""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(html) ?: error("Payload not found in HTML")
        return htmlUnescape(match.groupValues[1])
    }

    private fun extractUserId(html: String): String {
        val regex = Regex("""name="user_id"\s+value="([^"]+)"""")
        val match = regex.find(html)
        if (match != null) return match.groupValues[1]

        val uuidRegex = Regex("""value="([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"""")
        val uuidMatch = uuidRegex.find(html) ?: error("user_id not found in HTML")
        return uuidMatch.groupValues[1]
    }

    private fun resolveUrl(baseUrl: String, relative: String): String {
        if (relative.startsWith("http")) return relative
        val base = Url(baseUrl)
        return URLBuilder(base).apply {
            encodedPath = relative
        }.buildString()
    }

    private fun htmlUnescape(text: String): String =
        text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
}
