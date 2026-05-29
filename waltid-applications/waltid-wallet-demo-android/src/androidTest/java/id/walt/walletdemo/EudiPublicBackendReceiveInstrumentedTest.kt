package id.walt.walletdemo

import android.content.Intent
import android.net.ParseException
import android.net.Uri
import android.text.Html
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EudiPublicBackendReceiveInstrumentedTest {

    private data class GeneratedOffer(
        val offerUrl: String,
    )

    private data class VerifierTransaction(
        val transactionId: String,
        val authorizationRequestUri: String,
    )

    private data class HttpTextResponse(
        val finalUrl: String,
        val body: String,
    )

    companion object {
        private const val DEFAULT_CREDENTIAL_ID = "eu.europa.ec.eudi.pid_vc_sd_jwt"
        private const val ENTRYPOINT = "https://issuer.eudiw.dev/credential_offer"
        private const val BACKEND_GENERATE = "https://backend.issuer.eudiw.dev/credential_offer"
        private const val BACKEND_AUTHORIZE = "https://backend.issuer.eudiw.dev/form_authorize_generate"
    }

    @Test
    fun receiveAndPresentAgainstEudiPublicBackends() {
        val args = InstrumentationRegistry.getArguments()
        val requestedCredentialId = args.getString("e2e_credential_id") ?: DEFAULT_CREDENTIAL_ID
        val offerUrlArg = args.getString("e2e_offer_url")
            ?: args.getString("e2e_offer_url_b64")?.let(::decodeBase64)
        val generatedOffer = if (offerUrlArg == null) generatePreAuthorizedOffer(requestedCredentialId) else null
        val offerUrl = offerUrlArg ?: generatedOffer!!.offerUrl

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK) }
            ?: error("Cannot resolve launch intent for ${context.packageName}")
        context.startActivity(launchIntent)

        assertTrue(
            "Wallet did not become ready",
            waitForStatus(
                device = device,
                timeoutMs = 120_000,
                matcher = { it == "Wallet ready" },
                failurePrefixes = listOf("Bootstrap failed")
            )
        )

        val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(offerUrl)).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(deepLinkIntent)
        assertTrue(
            "Offer URL did not appear in UI after deep link",
            waitForOfferUrlInUi(device, timeoutMs = 30_000)
        )

        // Prefer clickable button closest to bottom to avoid selecting the section header text.
        val receiveButton = waitForClickableButton(device, "Receive", timeoutMs = 30_000)
        assertNotNull("Receive button not found", receiveButton)
        val bounds = receiveButton!!.visibleBounds
        device.click(bounds.centerX(), bounds.centerY())

        val receiveSuccess = waitForStatus(
            device = device,
            timeoutMs = 220_000,
            matcher = { it.startsWith("Received") },
            failurePrefixes = listOf("Receive failed", "Bootstrap failed", "Present failed")
        )

        assertTrue("Receive did not complete successfully. Latest status: ${latestStatus(device)}", receiveSuccess)
        assertTrue("No credentials were shown in UI", device.findObject(By.text("No credentials")) == null)

        val credentialId = extractCredentialIdFromOfferUrl(offerUrl)
        val verifierTx = createVerifierTransaction(credentialId)

        val presentationDeepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(verifierTx.authorizationRequestUri)).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(presentationDeepLinkIntent)

        // Some builds begin presentation immediately after the request URL is set.
        val startedWithoutTap = waitForStatus(
            device = device,
            timeoutMs = 5_000,
            matcher = {
                it.startsWith("Presenting credential") ||
                    it.startsWith("Presentation sent") ||
                    it.startsWith("Presentation finished")
            },
            failurePrefixes = listOf("Present failed", "Receive failed", "Bootstrap failed")
        )
        if (!startedWithoutTap) {
            val presentButton = waitForClickableButton(device, "Present", timeoutMs = 30_000)
            assertNotNull("Present button not found", presentButton)
            val presentBounds = presentButton!!.visibleBounds
            device.click(presentBounds.centerX(), presentBounds.centerY())
        }

        Thread.sleep(8_000)
        val statusAfterPresent = latestStatus(device)
        assertTrue(
            "Presentation failed in app. Latest status: $statusAfterPresent",
            !statusAfterPresent.startsWith("Present failed") &&
                !statusAfterPresent.startsWith("Receive failed") &&
                !statusAfterPresent.startsWith("Bootstrap failed")
        )
        assertTrue(
            "Wallet app is no longer in foreground after presentation flow",
            device.currentPackageName == context.packageName
        )

        val verifierEvent = waitForVerifierResult(verifierTx.transactionId, timeoutMs = 220_000)
        assertNotNull("Verifier did not report successful wallet response event", verifierEvent)
    }

    private fun waitForClickableButton(device: UiDevice, text: String, timeoutMs: Long): UiObject2? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val candidates = device.findObjects(By.text(text))
            val preferredCandidate = candidates.maxByOrNull { it.visibleBounds.bottom }
            if (preferredCandidate != null) {
                val clickable = preferredCandidate.clickableAncestorOrSelf()
                if (clickable != null) {
                    return clickable
                }
                if (text != "Present") {
                    return preferredCandidate
                }
            }
            if (text == "Present") {
                device.swipe(540, 2000, 540, 900, 24)
            }
            Thread.sleep(500)
        }
        return null
    }

    private fun waitForOfferUrlInUi(device: UiDevice, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val offerNode = device.findObject(By.textStartsWith("openid-credential-offer://"))
            if (offerNode != null) return true
            Thread.sleep(500)
        }
        return false
    }

    private fun UiObject2.clickableAncestorOrSelf(): UiObject2? {
        var node: UiObject2? = this
        while (node != null) {
            if (node.isClickable) return node
            node = node.parent
        }
        return null
    }

    private fun latestStatus(device: UiDevice): String {
        val prefixes = listOf(
            "Wallet ready",
            "Starting wallet",
            "Bootstrapping wallet",
            "Receiving credential",
            "Received",
            "Receive failed",
            "Bootstrap failed",
            "Presenting credential",
            "Presentation sent",
            "Presentation finished",
            "Present failed",
        )
        for (prefix in prefixes) {
            val obj = device.findObject(By.textStartsWith(prefix))
            if (obj != null) return obj.text
        }
        return "UNKNOWN"
    }

    private fun waitForStatus(
        device: UiDevice,
        timeoutMs: Long,
        matcher: (String) -> Boolean,
        failurePrefixes: List<String>
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val status = latestStatus(device)
            if (matcher(status)) return true
            if (failurePrefixes.any { status.startsWith(it) }) return false
            Thread.sleep(500)
        }
        return false
    }

    private fun decodeBase64(value: String): String =
        String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)

    private fun extractCredentialIdFromOfferUrl(offerUrl: String): String {
        val parsed = Uri.parse(offerUrl)
        val offerRaw = parsed.getQueryParameter("credential_offer")
            ?: return "eu.europa.ec.eudi.pid_vc_sd_jwt"
        return runCatching {
            val offerJson = JSONObject(offerRaw)
            val ids = offerJson.optJSONArray("credential_configuration_ids")
            ids?.optString(0)?.takeIf { it.isNotBlank() } ?: "eu.europa.ec.eudi.pid_vc_sd_jwt"
        }.getOrDefault("eu.europa.ec.eudi.pid_vc_sd_jwt")
    }

    private fun createVerifierTransaction(credentialId: String): VerifierTransaction {
        val dcql = buildDcqlQuery(credentialId)
        val payload = JSONObject()
            .put("dcql_query", dcql)
            .put("nonce", UUID.randomUUID().toString())
            .put("request_uri_method", "post")
            .put("profile", "openid4vp")
            .put("authorization_request_uri", "openid4vp://")

        val response = httpJson(
            url = "https://verifier-backend.eudiw.dev/ui/presentations/v2",
            method = "POST",
            body = payload.toString()
        )
        val txId = response.optString("transaction_id")
        val authRequest = response.optString("authorization_request_uri")
        require(txId.isNotBlank()) { "Verifier response missing transaction_id: $response" }
        require(authRequest.isNotBlank()) { "Verifier response missing authorization_request_uri: $response" }
        return VerifierTransaction(transactionId = txId, authorizationRequestUri = authRequest)
    }

    private fun buildDcqlQuery(credentialId: String): JSONObject {
        val normalized = credentialId.lowercase(Locale.ROOT)
        return if (normalized.contains("sd_jwt")) {
            JSONObject().put(
                "credentials",
                JSONArray().put(
                    JSONObject()
                        .put("id", "query_0")
                        .put("format", "dc+sd-jwt")
                        .put(
                            "meta",
                            JSONObject().put(
                                "vct_values",
                                JSONArray()
                                    .put("urn:eudi:pid:1")
                                    .put("eu.europa.ec.eudi.pid.1")
                            )
                        )
                )
            )
        } else {
            val docType = if (normalized.contains("mdl")) "org.iso.18013.5.1.mDL" else "eu.europa.ec.eudi.pid.1"
            JSONObject().put(
                "credentials",
                JSONArray().put(
                    JSONObject()
                        .put("id", "query_0")
                        .put("format", "mso_mdoc")
                        .put("meta", JSONObject().put("doctype_value", docType))
                )
            )
        }
    }

    private fun waitForVerifierResult(transactionId: String, timeoutMs: Long): JSONObject? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val eventsResponse = httpJson("https://verifier-backend.eudiw.dev/ui/presentations/$transactionId/events")
            val events = eventsResponse.optJSONArray("events") ?: JSONArray()

            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i) ?: continue
                val eventName = event.optString("event")
                val cause = event.optString("cause")
                val joined = "$eventName | $cause".lowercase(Locale.ROOT)
                if (
                    joined.contains("failed") ||
                    joined.contains("error") ||
                    joined.contains("invalid") ||
                    joined.contains("timed out")
                ) {
                    throw AssertionError("Verifier failure event: $event")
                }
            }

            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i) ?: continue
                val eventName = event.optString("event").lowercase(Locale.ROOT)
                if (
                    eventName.contains("wallet response posted") ||
                    eventName.contains("successful") ||
                    eventName.contains("verified")
                ) {
                    return event
                }
            }
            Thread.sleep(2_000)
        }
        return null
    }

    private fun httpJson(url: String, method: String = "GET", body: String? = null): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val content = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        require(status in 200..299) { "HTTP $status from $url: $content" }
        return if (content.isBlank()) JSONObject() else JSONObject(content)
    }

    private fun generatePreAuthorizedOffer(credentialId: String): GeneratedOffer {
        val flow = EudiOfferFlow()
        return flow.generate(credentialId)
    }

    private class EudiOfferFlow {
        private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)

        fun generate(credentialId: String): GeneratedOffer {
            val entry = getPage(ENTRYPOINT)
            val redirectAction = resolveUrl(entry.finalUrl, extractFormAction(entry.body, "redirect_form"))
            val redirectPayload = extractPayload(entry.body)
            postForm(redirectAction, mapOf("payload" to redirectPayload))

            val preauthRedirect = postForm(
                BACKEND_GENERATE,
                mapOf(
                    credentialId to credentialId,
                    "Authorization Code Grant" to "pre_auth_code",
                    "credential_offer_URI" to "openid-credential-offer://",
                    "proceed" to "Submit",
                )
            )

            val displayFormAction = resolveUrl(
                preauthRedirect.finalUrl,
                extractFormAction(preauthRedirect.body, "redirect_form")
            )
            val displayFormPayload = extractPayload(preauthRedirect.body)
            val displayFormPage = postForm(displayFormAction, mapOf("payload" to displayFormPayload))

            val selectCountryAction = resolveUrl(
                displayFormPage.finalUrl,
                extractFormAction(displayFormPage.body, "selectCountryForm")
            )
            val authorizeRedirect = postForm(
                selectCountryAction,
                mapOf(
                    "birthdate" to "1990-01-01",
                    "family_name" to "Tester",
                    "given_name" to "Alice",
                    "nationalities[0][country_code]" to "DE",
                    "place_of_birth[0][country]" to "DE",
                    "place_of_birth[0][region]" to "Berlin",
                    "place_of_birth[0][locality]" to "Berlin",
                    "proceed" to "Confirm",
                )
            )

            val displayAuthAction = resolveUrl(
                authorizeRedirect.finalUrl,
                extractFormAction(authorizeRedirect.body, "redirect_form")
            )
            val displayAuthPayload = extractPayload(authorizeRedirect.body)
            val authorizationPage = postForm(displayAuthAction, mapOf("payload" to displayAuthPayload))

            val userId = extractUserId(authorizationPage.body)
            val offerRedirect = postForm(
                BACKEND_AUTHORIZE,
                mapOf("user_id" to userId, "proceed" to "Authorize")
            )

            val finalPayload = JSONObject(extractPayload(offerRedirect.body))
            val txCodeValue = finalPayload.opt("tx_code")?.toString()
                ?: error("final payload missing tx_code")
            val urlData = finalPayload.optString("url_data")
            require(urlData.isNotBlank()) { "final payload missing url_data" }

            val offerUri = Uri.parse(urlData)
            val credentialOfferRaw = offerUri.getQueryParameter("credential_offer")
                ?: error("credential_offer query parameter missing from url_data")
            val offerJson = JSONObject(credentialOfferRaw)
            val preAuthorized = offerJson
                .optJSONObject("grants")
                ?.optJSONObject("urn:ietf:params:oauth:grant-type:pre-authorized_code")
                ?: error("pre-authorized grant missing in generated offer")

            val txCode = preAuthorized.optJSONObject("tx_code") ?: JSONObject().also {
                preAuthorized.put("tx_code", it)
            }
            txCode.put("value", txCodeValue)

            val scheme = offerUri.scheme ?: "openid-credential-offer"
            val encodedOffer = Uri.encode(offerJson.toString())
            val injectedOfferUrl = "$scheme://credential_offer?credential_offer=$encodedOffer"

            return GeneratedOffer(
                offerUrl = injectedOfferUrl
            )
        }

        private fun getPage(url: String): HttpTextResponse = httpText(url, method = "GET")

        private fun postForm(url: String, fields: Map<String, String>): HttpTextResponse {
            val body = encodeForm(fields)
            return httpText(
                url = url,
                method = "POST",
                body = body,
                contentType = "application/x-www-form-urlencoded"
            )
        }

        private fun httpText(
            url: String,
            method: String,
            body: String? = null,
            contentType: String? = null,
        ): HttpTextResponse {
            val uri = URI(url)
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 30_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "*/*")
                buildCookieHeader(uri)?.let { setRequestProperty("Cookie", it) }
                if (body != null) {
                    doOutput = true
                    if (contentType != null) {
                        setRequestProperty("Content-Type", contentType)
                    }
                    outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
                }
            }
            val status = conn.responseCode
            storeCookies(uri, conn)
            val stream = if (status in 200..399) conn.inputStream else conn.errorStream
            val content = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            require(status in 200..399) { "HTTP $status from $url: $content" }
            return HttpTextResponse(
                finalUrl = conn.url.toString(),
                body = content,
            )
        }

        private fun buildCookieHeader(uri: URI): String? {
            val cookies = cookieManager.cookieStore.get(uri)
            if (cookies.isEmpty()) return null
            return cookies.joinToString("; ") { "${it.name}=${it.value}" }
        }

        private fun storeCookies(uri: URI, conn: HttpURLConnection) {
            val headers = conn.headerFields
                .filterKeys { it != null }
                .mapValues { it.value ?: emptyList() }
            if (headers.isNotEmpty()) {
                cookieManager.put(uri, headers)
            }
        }

        private fun resolveUrl(baseUrl: String, action: String): String = URL(URL(baseUrl), action).toString()

        private fun extractPayload(page: String): String {
            val pattern = Regex("name=\"payload\"\\s+value='(.*?)'\\s*>", setOf(RegexOption.DOT_MATCHES_ALL))
            val match = pattern.find(page) ?: error("hidden payload input not found")
            return htmlUnescape(match.groupValues[1])
        }

        private fun extractFormAction(page: String, formId: String): String {
            val pattern = Regex(
                "<form\\s+id=\"${Regex.escape(formId)}\"[^>]*action=\"([^\"]+)\"",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            )
            val match = pattern.find(page) ?: error("form action not found for id=$formId")
            return htmlUnescape(match.groupValues[1])
        }

        private fun extractUserId(page: String): String {
            val patterns = listOf(
                Regex("name=\"user_id\"\\s+value=\"([^\"]+)\""),
                Regex("value=\"([a-f0-9\\-]{36})\"\\s+name=\"user_id\"")
            )
            for (pattern in patterns) {
                val match = pattern.find(page)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
            error("user_id not found on authorization page")
        }

        private fun htmlUnescape(value: String): String {
            return try {
                Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
            } catch (_: ParseException) {
                value
            }
        }

        private fun encodeForm(fields: Map<String, String>): String {
            return fields.entries.joinToString("&") { (k, v) ->
                "${urlEncode(k)}=${urlEncode(v)}"
            }
        }

        private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }
}
