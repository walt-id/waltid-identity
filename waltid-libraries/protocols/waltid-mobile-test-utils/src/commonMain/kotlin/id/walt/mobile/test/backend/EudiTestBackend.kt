package id.walt.mobile.test.backend

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/**
 * Generates credential offers from the public EUDI test backend.
 */
object EudiTestBackend {

    private const val ENTRYPOINT = "https://issuer.eudiw.dev/credential_offer"
    private const val BACKEND_GENERATE = "https://backend.issuer.eudiw.dev/credential_offer"
    private const val BACKEND_AUTHORIZE = "https://backend.issuer.eudiw.dev/form_authorize_generate"
    private const val VERIFIER_BACKEND = "https://verifier-backend.eudiw.dev"

    private val client by lazy {
        HttpClient {
            expectSuccess = false
            install(HttpCookies)
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 30_000
            }
        }
    }

    data class GeneratedOffer(val offerUrl: String, val txCode: String?)

    data class VerifierTransaction(val transactionId: String, val authorizationRequestUri: String)

    fun extractCredentialIdFromOfferUrl(offerUrl: String): String {
        val offerJson = Url(offerUrl).parameters["credential_offer"]
            ?.let { Json.parseToJsonElement(it).jsonObject }
            ?: return "eu.europa.ec.eudi.pid_vc_sd_jwt"

        return offerJson["credential_configuration_ids"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: "eu.europa.ec.eudi.pid_vc_sd_jwt"
    }

    suspend fun generateOffer(credentialId: String = "eu.europa.ec.eudi.pid_vc_sd_jwt"): GeneratedOffer {
        // Step 1: Get entry page, then follow the redirect chain explicitly.
        val entryResponse = getPage(ENTRYPOINT)
        val entryBody = entryResponse.bodyAsText()
        val entryUrl = entryResponse.call.request.url.toString()

        val redirectAction = resolveUrl(entryUrl, extractFormAction(entryBody, "redirect_form"))
        val redirectPayload = extractPayload(entryBody)
        postForm(redirectAction, mapOf("payload" to redirectPayload))

        // Step 2: Generate pre-authorized offer
        val preauthResponse = postForm(
            BACKEND_GENERATE,
            mapOf(
                credentialId to credentialId,
                "Authorization Code Grant" to "pre_auth_code",
                "credential_offer_URI" to "openid-credential-offer://",
                "proceed" to "Submit",
            ),
        )
        val preauthBody = preauthResponse.bodyAsText()
        val preauthUrl = preauthResponse.call.request.url.toString()

        // Step 3: Follow redirect form to display page
        val displayAction = resolveUrl(preauthUrl, extractFormAction(preauthBody, "redirect_form"))
        val displayPayload = extractPayload(preauthBody)
        val displayResponse = postForm(displayAction, mapOf("payload" to displayPayload))
        val displayBody = displayResponse.bodyAsText()
        val displayUrl = displayResponse.call.request.url.toString()

        // Step 4: Submit country/user data form
        val countryAction = resolveUrl(displayUrl, extractFormAction(displayBody, "selectCountryForm"))
        val authResponse = postForm(
            countryAction,
            mapOf(
                "birthdate" to "1990-01-01",
                "family_name" to "Tester",
                "given_name" to "Alice",
                "nationalities[0][country_code]" to "DE",
                "place_of_birth[0][country]" to "DE",
                "place_of_birth[0][region]" to "Berlin",
                "place_of_birth[0][locality]" to "Berlin",
                "proceed" to "Confirm",
            ),
        )
        val authBody = authResponse.bodyAsText()
        val authUrl = authResponse.call.request.url.toString()

        // Step 5: Follow authorization redirect form
        val authFormAction = resolveUrl(authUrl, extractFormAction(authBody, "redirect_form"))
        val authFormPayload = extractPayload(authBody)
        val authPageResponse = postForm(authFormAction, mapOf("payload" to authFormPayload))
        val authPageBody = authPageResponse.bodyAsText()

        // Step 6: Extract user_id and authorize
        val userId = extractUserId(authPageBody)
        val offerResponse = postForm(
            BACKEND_AUTHORIZE,
            mapOf(
                "user_id" to userId,
                "proceed" to "Authorize",
            ),
        )
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

    suspend fun createVerifierTransaction(credentialId: String = "eu.europa.ec.eudi.pid_vc_sd_jwt"): VerifierTransaction {
        val dcqlQuery = buildDcqlQuery(credentialId)
        val payload = buildJsonObject {
            put("dcql_query", dcqlQuery)
            put("nonce", JsonPrimitive(Uuid.random().toString()))
            // verifier-backend.eudiw.dev currently supports JAR retrieval via the default GET
            // method, but returns HTTP 400 when request_uri_method=post is requested.
            put("profile", JsonPrimitive("openid4vp"))
            put("authorization_request_uri", JsonPrimitive("openid4vp://"))
        }

        val response = requestText(
            url = "$VERIFIER_BACKEND/ui/presentations/v2",
            method = HttpMethod.Post,
            body = payload.toString(),
            contentType = ContentType.Application.Json,
        )
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
                val events = when (val responseElement = Json.parseToJsonElement(body)) {
                    is JsonArray -> responseElement
                    is JsonObject -> when (val eventsField = responseElement["events"]) {
                        is JsonArray -> eventsField
                        null -> error("Verifier events response did not contain an events field: $body")
                        else -> error("Verifier events field was not an array: $body")
                    }
                    else -> error("Unexpected verifier events response shape: $body")
                }
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

            delay(2000.milliseconds)
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
                        add("urn:eudi:pid:1")
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
                }
            }
        }
    }

    private suspend fun getPage(url: String): HttpResponse = requestText(url = url)

    private suspend fun postForm(url: String, fields: Map<String, String>): HttpResponse {
        val body = fields.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
        return requestText(
            url = url,
            method = HttpMethod.Post,
            body = body,
            contentType = ContentType.Application.FormUrlEncoded,
        )
    }

    private suspend fun requestText(
        url: String,
        method: HttpMethod = HttpMethod.Get,
        body: String? = null,
        contentType: ContentType? = null,
    ): HttpResponse {
        var currentUrl = url
        var currentMethod = method
        var currentBody = body

        repeat(10) {
            val response = client.request(currentUrl) {
                this.method = currentMethod
                accept(ContentType.Any)
                contentType?.let { contentType(it) }
                if (currentBody != null) {
                    setBody(currentBody)
                }
            }

            val status = response.status
            if (status.value in 300..399) {
                val location = response.headers[HttpHeaders.Location]
                    ?: error("Redirect from $currentUrl did not include a Location header")
                currentUrl = resolveUrl(currentUrl, location)
                val nextMethod = if (status == HttpStatusCode.TemporaryRedirect || status == HttpStatusCode.PermanentRedirect) {
                    currentMethod
                } else {
                    HttpMethod.Get
                }
                currentMethod = nextMethod
                currentBody = if (nextMethod == HttpMethod.Get) null else currentBody
                return@repeat
            }

            if (status.value !in 200..299) {
                val errorBody = response.bodyAsText()
                error("HTTP ${status.value} from $currentUrl: $errorBody")
            }

            return response
        }

        error("Too many redirects while requesting $url")
    }

    private fun extractFormAction(html: String, formId: String): String {
        val formRegex = Regex("""<form\s[^>]*id="$formId"[^>]*>""")
        val formTag = formRegex.find(html)?.value
            ?: error("Form '$formId' not found in HTML")
        val actionRegex = Regex("""action="([^"]+)"""")
        val actionMatch = actionRegex.find(formTag)
            ?: error("No action attribute in form '$formId'")
        return htmlUnescape(actionMatch.groups[1]?.value ?: error("Action group not found"))
    }

    private fun extractPayload(html: String): String {
        val regex = Regex("""(?s)name="payload"\s+value='(.*?)'\s*>""")
        val match = regex.find(html) ?: error("Payload not found in HTML")
        return htmlUnescape(match.groups[1]?.value ?: error("Payload group not found"))
    }

    private fun extractUserId(html: String): String {
        val regex = Regex("""name="user_id"\s+value="([^"]+)"""")
        val match = regex.find(html)
        if (match != null) return match.groups[1]?.value ?: error("user_id group not found")

        val uuidRegex = Regex("""value="([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"""")
        val uuidMatch = uuidRegex.find(html) ?: error("user_id not found in HTML")
        return uuidMatch.groups[1]?.value ?: error("UUID group not found")
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

    private fun urlEncode(value: String): String = value.encodeURLParameter()
}
