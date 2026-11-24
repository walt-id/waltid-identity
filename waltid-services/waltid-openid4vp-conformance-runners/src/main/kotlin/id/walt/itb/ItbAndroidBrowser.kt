package id.walt.itb

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Route
import com.microsoft.playwright.TimeoutError
import com.microsoft.playwright.impl.TargetClosedError
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Delivers the reference counterpart's inputs through real Chrome Digital Credentials APIs. */
internal class ItbAndroidBrowser(
    private val chrome: Browser,
    private val client: HttpClient,
    private val origin: Url,
    private val serial: String,
) {
    suspend fun present(
        interaction: ItbWalletInteraction.DigitalCredentials,
        executeOnDevice: suspend (ItbWalletInteraction) -> ItbWalletResult,
    ): ItbWalletResult {
        val endpoint = sameOrigin(interaction.endpoint)
        val descriptor = jsonPost(endpoint, interaction.descriptorRequest())
        check(descriptor.text("sessionId") == interaction.validationSession) { "DC API session mismatch" }
        checkUnexpired(descriptor)
        val request = descriptor.getValue("request").jsonObject
        check(request.text("protocol") == interaction.protocol) { "DC API protocol mismatch" }
        val responseEndpoint = sameOrigin(Url(descriptor.text("responseEndpoint")))
        return withTab("get", JsonArray(listOf(request)), interaction.protocol) { page ->
            val result = useProvider(page, interaction, executeOnDevice)
            awaitBrowserResult(page)
            val response = Json.parseToJsonElement(page.evaluate("() => window.itbResponse") as String).jsonObject
            jsonPost(responseEndpoint, response, verifierResponse = true)
            result
        }
    }

    suspend fun issue(
        interaction: ItbWalletInteraction.DigitalIssuance,
        executeOnDevice: suspend (ItbWalletInteraction) -> ItbWalletResult,
    ): ItbWalletResult {
        val endpoint = sameOrigin(interaction.endpoint)
        val descriptor = jsonPost(endpoint, buildJsonObject {
            put("scenario", interaction.scenario)
            put("credentialType", interaction.credentialType)
        })
        checkUnexpired(descriptor)
        val statusEndpoint = issuanceStatusEndpoint(endpoint, descriptor.text("sessionId"), descriptor.text("statusEndpoint"))
        val capability = descriptor.text("statusToken")
        require(capability.isNotBlank() && capability.length <= 8192)
        val requests = descriptor.getValue("digital").jsonObject.getValue("requests").jsonArray
        val request = requests.single().jsonObject
        check(request.text("protocol") == interaction.protocol && request["data"] is JsonObject) {
            "Unexpected DC API issuance request"
        }
        return withTab("create", requests, interaction.protocol) { page ->
            val result = useProvider(page, interaction, executeOnDevice)
            awaitBrowserResult(page)
            withTimeout(30_000) {
                while (true) {
                    val response = client.get(statusEndpoint) {
                        bearerAuth(capability)
                        header(HttpHeaders.CacheControl, "no-cache")
                        accept(ContentType.Application.Json)
                    }
                    check(response.status.isSuccess()) { "Issuer status HTTP failure" }
                    val status = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    when (status.text("status")) {
                        "issued" -> break
                        "failed" -> throw ItbWalletRejection("dc_issuer_reported_failure")
                        else -> delay(500)
                    }
                }
            }
            result
        }
    }

    private fun awaitBrowserResult(page: Page) {
        try {
            page.waitForFunction("() => window.itbError || window.itbResponse", null,
                Page.WaitForFunctionOptions().setTimeout(15_000.0))
        } catch (_: TimeoutError) {
            throw ItbWalletRejection("dc_browser_response_missing")
        }
        requireBrowserSuccess(page)
    }

    private suspend fun useProvider(
        page: Page,
        interaction: ItbWalletInteraction,
        execute: suspend (ItbWalletInteraction) -> ItbWalletResult,
    ): ItbWalletResult {
        page.bringToFront()
        check(page.evaluate("() => !document.hidden") == true) { "The test browser is in the background" }
        val invoke = page.locator("#invoke")
        check(invoke.isVisible && invoke.isEnabled) { "The Digital Credentials API trigger is unavailable" }
        try {
            // A headless Android emulator can paint the button without delivering animation frames.
            invoke.click(Locator.ClickOptions().setForce(true).setTimeout(10_000.0))
        } catch (_: TimeoutError) {
            throw ItbWalletRejection("dc_browser_trigger_timeout")
        }
        // A timeout before dispatch leaves the device channel available for the next case.
        return try {
            execute(interaction)
        } catch (error: ItbWalletRejection) {
            if (!error.code.startsWith("demo_payment_") && !page.isClosed && chrome.isConnected) {
                requireBrowserSuccess(page)
            }
            throw error
        }
    }

    private fun requireBrowserSuccess(page: Page) {
        val error = page.evaluate("() => window.itbError") as? String ?: return
        val code = when (error) {
            "invalid_response" -> "dc_browser_invalid_response"
            "NotAllowedError" -> "dc_browser_not_allowed"
            "NotSupportedError" -> "dc_browser_not_supported"
            else -> "dc_browser_request_failed"
        }
        throw ItbWalletRejection(code)
    }

    private suspend fun <T> withTab(
        operation: String,
        requests: JsonArray,
        protocol: String,
        run: suspend (Page) -> T,
    ): T {
        activateChrome()
        val page = chrome.contexts().single().newPage()
        val address = "${origin.toString().trimEnd('/')}/itb/__wallet-test/${UUID.randomUUID()}"
        try {
            page.route(address) { route -> route.fulfill(Route.FulfillOptions()
                .setContentType("text/html").setBody("<!doctype html><title>WE BUILD Wallet Test</title><button id=invoke>Open wallet</button>")) }
            page.navigate(address)
            check(page.url() == address)
            val allowsProtocol = page.evaluate(
                "p => typeof DigitalCredential === 'function' && DigitalCredential.userAgentAllowsProtocol(p)", protocol,
            )
            if (allowsProtocol != true) throw ItbWalletRejection("dc_browser_protocol_unavailable")
            page.evaluate(
                """
                ({operation, requests, protocol}) => {
                    document.querySelector('#invoke').addEventListener('click', async () => {
                        try {
                            const credential = await navigator.credentials[operation]({digital: {requests: JSON.parse(requests)}});
                            if (credential?.protocol !== protocol || !credential.data ||
                                typeof credential.data !== 'object' || Array.isArray(credential.data) || credential.data.error) {
                                window.itbError = 'invalid_response';
                                return;
                            }
                            window.itbResponse = JSON.stringify({protocol: credential.protocol, data: credential.data});
                        } catch (error) {
                            window.itbError = error.name;
                        }
                    }, {once: true});
                }
                """.trimIndent(),
                mapOf("operation" to operation, "requests" to requests.toString(), "protocol" to protocol),
            )
            return run(page)
        } finally {
            try {
                if (!page.isClosed && chrome.isConnected) page.close()
            } catch (_: TargetClosedError) {
                // Chrome already closed the test tab during the handoff.
            }
        }
    }

    private fun activateChrome() {
        val process = ProcessBuilder(
            "adb", "-s", serial, "shell", "am", "start", "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LAUNCHER", "-p", "com.android.chrome",
        ).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).apply {
            environment().keys.removeAll { it.startsWith("ITB_") }
        }.start()
        if (!process.waitFor(8, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw ItbWalletRejection("dc_browser_activation_timeout")
        }
        if (process.exitValue() != 0) throw ItbWalletRejection("dc_browser_activation_failed")
    }

    private suspend fun jsonPost(endpoint: Url, payload: JsonObject, verifierResponse: Boolean = false): JsonObject {
        val response = client.post(endpoint) {
            header(HttpHeaders.Origin, origin.toString().trimEnd('/'))
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            if (verifierResponse) {
                val payload = try { Json.parseToJsonElement(body) } catch (_: IllegalArgumentException) { null }
                throw ItbVerifierRejection(payload, response.status)
            }
            throw ItbWalletRejection("dc_counterpart_http_${response.status.value}")
        }
        return if (verifierResponse) JsonObject(emptyMap()) else Json.parseToJsonElement(body).jsonObject
    }

    private fun sameOrigin(endpoint: Url): Url = endpoint.also {
        require(it.protocol == origin.protocol && it.host == origin.host && it.port == origin.port &&
            it.user == null && it.password == null && it.fragment.isEmpty()) { "Unexpected DC API endpoint origin" }
    }

    private fun checkUnexpired(descriptor: JsonObject) {
        check(ItbWalletDriver.descriptorExpiry(descriptor.getValue("expiresAt")).isAfter(Instant.now())) {
            "DC API descriptor expired"
        }
    }

    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content

    companion object {
        /** Resolve issuer-relative routes under the issuer deployment prefix, then pin to this session. */
        internal fun issuanceStatusEndpoint(offer: Url, session: String, advertised: String): Url {
            val suffix = "vci/dc-api/offer"
            require(offer.encodedPath.endsWith("/$suffix") && offer.parameters.isEmpty())
            require(session.matches(Regex("[A-Za-z0-9_-]{1,128}")))
            val base = URI(offer.toString().removeSuffix(suffix))
            val expected = base.resolve("vci/dc-api/session/$session")
            val actual = base.resolve(if (advertised.startsWith("/vci/")) advertised.drop(1) else advertised)
            require(actual == expected) { "Issuer advertised a status endpoint outside the prepared issuance session" }
            return Url(expected.toString())
        }
    }
}
