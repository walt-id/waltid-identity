package id.walt.openid4vp.conformance.testplans.runner

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import id.walt.openid4vp.conformance.testplans.httpdata.TestRunResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

data class IssuerBrowserAutomationConfig(
    val enabled: Boolean,
    val username: String,
    val password: String,
    val timeoutSeconds: Long,
) {
    companion object {
        private const val PROPERTY_ENABLED = "openid4vci.conformance.browser-automation"
        private const val PROPERTY_USERNAME = "openid4vci.conformance.auth-username"
        private const val PROPERTY_PASSWORD = "openid4vci.conformance.auth-password"
        private const val PROPERTY_TIMEOUT_SECONDS = "openid4vci.conformance.auth-timeout-seconds"

        private const val ENV_ENABLED = "OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION"
        private const val ENV_USERNAME = "OPENID4VCI_CONFORMANCE_AUTH_USERNAME"
        private const val ENV_PASSWORD = "OPENID4VCI_CONFORMANCE_AUTH_PASSWORD"
        private const val ENV_TIMEOUT_SECONDS = "OPENID4VCI_CONFORMANCE_AUTH_TIMEOUT_SECONDS"

        private const val DEFAULT_USERNAME = "jane@walt.id"
        private const val DEFAULT_PASSWORD = "jane"
        private const val DEFAULT_TIMEOUT_SECONDS = 90L

        fun fromEnvironment(): IssuerBrowserAutomationConfig = IssuerBrowserAutomationConfig(
            enabled = bool(PROPERTY_ENABLED, ENV_ENABLED),
            username = value(PROPERTY_USERNAME, ENV_USERNAME) ?: DEFAULT_USERNAME,
            password = value(PROPERTY_PASSWORD, ENV_PASSWORD) ?: DEFAULT_PASSWORD,
            timeoutSeconds = value(PROPERTY_TIMEOUT_SECONDS, ENV_TIMEOUT_SECONDS)?.toLongOrNull()?.coerceAtLeast(1)
                ?: DEFAULT_TIMEOUT_SECONDS,
        )

        private fun bool(property: String, env: String): Boolean =
            value(property, env)
                ?.let { it.equals("true", ignoreCase = true) || it == "1" || it.equals("yes", ignoreCase = true) }
                ?: false

        private fun value(property: String, env: String): String? =
            System.getProperty(property)?.trim()?.takeIf { it.isNotBlank() }
                ?: System.getenv(env)?.trim()?.takeIf { it.isNotBlank() }
    }
}

data class IssuerBrowserInteraction(
    val url: String,
    val method: String,
)

fun TestRunResult.browserInteractionsForAutomation(): List<IssuerBrowserInteraction> {
    val pending = pendingBrowserInteractions()
    return pending.ifEmpty {
        browser.visitedUrlsWithMethod.mapNotNull { element ->
            element?.browserUrlWithMethod()
        } + browser.visited.mapNotNull { element ->
            element?.browserUrl()
        }
    }
}

fun TestRunResult.pendingBrowserInteractions(): List<IssuerBrowserInteraction> {
    val withMethod = browser.urlsWithMethod.mapNotNull { element ->
        element?.browserUrlWithMethod()
    }

    if (withMethod.isNotEmpty()) {
        return withMethod
    }

    return browser.urls.mapNotNull { element ->
        element?.browserUrl()
    }
}

private fun JsonElement.browserUrlWithMethod(): IssuerBrowserInteraction? {
    val obj = this as? JsonObject ?: return browserUrl()
    val url = obj.stringValue("url")
        ?: obj.stringValue("originalUrl")
        ?: obj.stringValue("fullUrl")
        ?: return null
    val method = obj.stringValue("method") ?: "GET"
    return IssuerBrowserInteraction(url = url, method = method.uppercase())
}

private fun JsonElement.browserUrl(): IssuerBrowserInteraction? =
    runCatching {
        jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }?.let {
            IssuerBrowserInteraction(url = it, method = "GET")
        }
    }.getOrNull()

private fun JsonObject.stringValue(name: String): String? =
    get(name)?.let { element ->
        runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
    }?.takeIf { it.isNotBlank() }

fun TestRunResult.browserInteractionSummary(): String =
    "urlsWithMethod=${browser.urlsWithMethod.map { it.shortJson() }}, " +
        "urls=${browser.urls.map { it.shortJson() }}, " +
        "visitedUrlsWithMethod=${browser.visitedUrlsWithMethod.map { it.shortJson() }}, " +
        "visited=${browser.visited.map { it.shortJson() }}"

private fun JsonElement?.shortJson(): String =
    this?.toString()?.let { if (it.length > 500) it.take(500) + "..." else it } ?: "null"

class IssuerConformanceBrowserAutomation(
    private val config: IssuerBrowserAutomationConfig,
    private val conformanceHost: String,
    private val conformancePort: Int,
) {
    fun complete(interaction: IssuerBrowserInteraction) {
        val browser = KeycloakConformanceBrowser.open()
        try {
            val page = browser.page
            page.setDefaultTimeout(Duration.ofSeconds(30).toMillis().toDouble())
            page.setDefaultNavigationTimeout(Duration.ofSeconds(30).toMillis().toDouble())

            println("Opening conformance browser interaction via ${interaction.method}: ${interaction.url}")
            openInteraction(page, interaction)

            val timeoutAt = System.currentTimeMillis() + Duration.ofSeconds(config.timeoutSeconds).toMillis()
            var loginSubmitted = false
            var lastSeenUrl = page.url().orEmpty()
            var nextProgressLogAt = 0L

            while (System.currentTimeMillis() < timeoutAt) {
                val now = System.currentTimeMillis()
                val currentUrl = page.url().orEmpty()
                if (currentUrl.isNotBlank()) {
                    lastSeenUrl = currentUrl
                }

                if (now >= nextProgressLogAt) {
                    println("Browser automation current URL: ${currentUrl.ifBlank { "<blank>" }}")
                    nextProgressLogAt = now + 5_000
                }

                if (currentUrl.isConformanceSuiteUrl()) {
                    if (!currentUrl.isConformanceSuiteCallbackUrl()) {
                        println("Browser returned to conformance suite: $currentUrl")
                        return
                    }

                    if (browser.hasElement("#submission_complete")) {
                        println("Conformance callback submission completed: $currentUrl")
                        return
                    }
                }

                if (currentUrl.contains("/openid4vci/callback")) {
                    currentUrl.callbackErrorDescription()?.let {
                        error("Issuer authorization callback returned error: $it (url=$currentUrl)")
                    }
                }

                val pageError = browser.firstVisibleText(".alert-error, .kc-feedback-text, #input-error, .pf-c-alert__title, .pf-v5-c-alert__title")
                    .orEmpty()
                if (pageError.isNotBlank()) {
                    error("Keycloak page error: $pageError (url=$currentUrl)")
                }

                val submitButton = browser.firstVisibleEnabled("#kc-login")
                    ?: browser.firstVisibleEnabled("button[type='submit']")
                    ?: browser.firstVisibleEnabled("input[type='submit']")

                if (!loginSubmitted) {
                    val usernameInput = browser.firstVisible("#username, input[name='username']")
                    val passwordInput = browser.firstVisible("#password, input[name='password']")

                    if (usernameInput != null && passwordInput != null && submitButton != null) {
                        usernameInput.fill(config.username)
                        passwordInput.fill(config.password)
                        submitButton.click()
                        loginSubmitted = true
                    } else if (submitButton != null) {
                        submitButton.click()
                        loginSubmitted = true
                    }
                } else if (submitButton != null && currentUrl.needsContinuationClick()) {
                    submitButton.click()
                }

                page.waitForTimeout(250.0)
            }

            val pageSnippet = runCatching { page.content().take(1000) }.getOrNull() ?: "<page source unavailable>"
            error(
                "Timeout waiting for browser login to return to conformance suite. " +
                    "Last URL: $lastSeenUrl ; page snippet: $pageSnippet"
            )
        } finally {
            browser.close()
        }
    }

    private fun openInteraction(page: Page, interaction: IssuerBrowserInteraction) {
        when (interaction.method.uppercase()) {
            "POST" -> page.setContent(buildPostRedirectForm(interaction.url))
            else -> page.navigate(interaction.url)
        }
    }

    private fun buildPostRedirectForm(url: String): String {
        val uri = URI.create(url)
        val action = buildString {
            append(uri.scheme)
            append("://")
            append(uri.rawAuthority)
            append(uri.rawPath.orEmpty())
        }
        val inputs = parseFormParams(uri.rawQuery)
            .joinToString("\n") { (name, value) ->
                """<input type="hidden" name="${name.htmlEscape()}" value="${value.htmlEscape()}">"""
            }

        return """
            <!doctype html>
            <html>
              <body>
                <form method="post" action="${action.htmlEscape()}">
                  $inputs
                  <button type="submit">Continue</button>
                </form>
                <script>document.forms[0].submit();</script>
              </body>
            </html>
        """.trimIndent()
    }

    private fun String.isConformanceSuiteUrl(): Boolean {
        val uri = runCatching { URI.create(this) }.getOrNull() ?: return false
        return uri.host == conformanceHost && effectivePort(uri) == conformancePort
    }

    private fun String.isConformanceSuiteCallbackUrl(): Boolean =
        runCatching { URI.create(this).path.endsWith("/callback") }.getOrDefault(false)

    private fun String.callbackErrorDescription(): String? {
        if (!contains("error=")) {
            return null
        }

        val uri = runCatching { URI.create(this) }.getOrNull() ?: return null
        val params = parseFormParams(uri.rawQuery)
        val error = params.firstOrNull { it.first == "error" }?.second ?: return null
        val description = params.firstOrNull { it.first == "error_description" }?.second
        return if (description.isNullOrBlank()) error else "$error: $description"
    }

    private fun String.needsContinuationClick(): Boolean =
        contains("login-actions/consent") ||
            contains("login-actions/required-action") ||
            contains("login-actions/action-token")

    private fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        uri.scheme.equals("http", ignoreCase = true) -> 80
        else -> -1
    }
}

private class KeycloakConformanceBrowser private constructor(
    private val playwright: Playwright,
    private val browser: Browser,
    private val context: BrowserContext,
    val page: Page,
) : AutoCloseable {

    fun firstVisible(selector: String): Locator? {
        val locator = page.locator(selector)
        return (0 until locator.count())
            .asSequence()
            .map { locator.nth(it) }
            .firstOrNull { candidate -> runCatching { candidate.isVisible }.getOrDefault(false) }
    }

    fun firstVisibleEnabled(selector: String): Locator? =
        firstVisible(selector)?.takeIf { candidate -> runCatching { candidate.isEnabled }.getOrDefault(false) }

    fun firstVisibleText(selector: String): String? =
        firstVisible(selector)
            ?.let { candidate -> runCatching { candidate.textContent()?.trim() }.getOrNull() }
            ?.takeIf { it.isNotBlank() }

    fun hasElement(selector: String): Boolean =
        runCatching { page.locator(selector).count() > 0 }.getOrDefault(false)

    override fun close() {
        runCatching { context.close() }
        runCatching { browser.close() }
        playwright.close()
    }

    companion object {
        private const val PROPERTY_PLAYWRIGHT_BROWSER = "playwright.browser"
        private const val PROPERTY_PLAYWRIGHT_BROWSER_BIN = "playwright.browser-bin"
        private const val PROPERTY_PLAYWRIGHT_HEADLESS = "playwright.headless"

        private const val ENV_PLAYWRIGHT_BROWSER = "PLAYWRIGHT_BROWSER"
        private const val ENV_PLAYWRIGHT_BROWSER_BIN = "PLAYWRIGHT_BROWSER_BIN"
        private const val ENV_PLAYWRIGHT_HEADLESS = "PLAYWRIGHT_HEADLESS"

        fun open(): KeycloakConformanceBrowser {
            val browserName = PlaywrightBrowserName.fromEnv()
            val playwright = Playwright.create()
            return try {
                val browser = browserName.type(playwright).launch(
                    BrowserType.LaunchOptions()
                        .setHeadless(isHeadlessEnabled())
                        .setArgs(browserName.launchArgs())
                        .also { options ->
                            resolveBrowserBinaryPath(browserName)?.let { options.setExecutablePath(Path.of(it)) }
                        }
                )
                val context = browser.newContext(
                    Browser.NewContextOptions()
                        .setIgnoreHTTPSErrors(true)
                        .setViewportSize(1280, 800)
                        .setExtraHTTPHeaders(mapOf("ngrok-skip-browser-warning" to "true"))
                )
                KeycloakConformanceBrowser(
                    playwright = playwright,
                    browser = browser,
                    context = context,
                    page = context.newPage(),
                )
            } catch (ex: Throwable) {
                playwright.close()
                if (ex.isMissingPlaywrightBrowser()) {
                    throw IllegalStateException(
                        "Playwright browser is not installed. Run " +
                            "./gradlew :waltid-services:waltid-openid4vp-conformance-runners:installPlaywrightBrowsers first.",
                        ex
                    )
                }
                throw ex
            }
        }

        private fun isHeadlessEnabled(): Boolean =
            when (value(PROPERTY_PLAYWRIGHT_HEADLESS, ENV_PLAYWRIGHT_HEADLESS)?.lowercase()) {
                "true" -> true
                "false" -> false
                else -> true
            }

        private fun resolveBrowserBinaryPath(browser: PlaywrightBrowserName): String? = sequenceOf(
            value(PROPERTY_PLAYWRIGHT_BROWSER_BIN, ENV_PLAYWRIGHT_BROWSER_BIN),
            when (browser) {
                PlaywrightBrowserName.CHROMIUM -> value("playwright.chromium-bin", "CHROMIUM_BIN")
                    ?: value("playwright.chrome-bin", "CHROME_BIN")
                    ?: value("playwright.google-chrome-bin", "GOOGLE_CHROME_BIN")

                PlaywrightBrowserName.FIREFOX -> value("playwright.firefox-bin", "FIREFOX_BIN")
                PlaywrightBrowserName.WEBKIT -> value("playwright.webkit-bin", "WEBKIT_BIN")
            }
        )
            .filterNotNull()
            .firstOrNull { candidate ->
                runCatching {
                    val path = Path.of(candidate)
                    Files.isRegularFile(path) && Files.isExecutable(path)
                }.getOrDefault(false)
            }

        fun configuredBrowserName(): String? =
            value(PROPERTY_PLAYWRIGHT_BROWSER, ENV_PLAYWRIGHT_BROWSER)

        private fun value(property: String, env: String): String? =
            System.getProperty(property)?.trim()?.takeIf { it.isNotBlank() }
                ?: System.getenv(env)?.trim()?.takeIf { it.isNotBlank() }

        private fun Throwable.isMissingPlaywrightBrowser(): Boolean =
            generateSequence(this) { it.cause }.any { cause ->
                val message = cause.message.orEmpty()
                message.contains("Executable doesn't exist") ||
                    message.contains("playwright install", ignoreCase = true)
            }
    }
}

private enum class PlaywrightBrowserName {
    CHROMIUM,
    FIREFOX,
    WEBKIT;

    fun type(playwright: Playwright): BrowserType = when (this) {
        CHROMIUM -> playwright.chromium()
        FIREFOX -> playwright.firefox()
        WEBKIT -> playwright.webkit()
    }

    fun launchArgs(): List<String> = when (this) {
        CHROMIUM -> listOf(
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--disable-gpu",
            "--remote-debugging-port=0",
        )

        FIREFOX,
        WEBKIT -> emptyList()
    }

    companion object {
        fun fromEnv(): PlaywrightBrowserName = when (
            (KeycloakConformanceBrowser.configuredBrowserName() ?: "chromium").lowercase()
        ) {
            "chromium", "chrome" -> CHROMIUM
            "firefox" -> FIREFOX
            "webkit", "safari" -> WEBKIT
            else -> error("Unsupported PLAYWRIGHT_BROWSER value. Expected one of: chromium, firefox, webkit")
        }
    }
}

private fun parseFormParams(rawQuery: String?): List<Pair<String, String>> =
    rawQuery
        ?.takeIf { it.isNotBlank() }
        ?.split("&")
        ?.filter { it.isNotBlank() }
        ?.map { part ->
            val separator = part.indexOf('=')
            if (separator == -1) {
                decodeFormValue(part) to ""
            } else {
                decodeFormValue(part.substring(0, separator)) to decodeFormValue(part.substring(separator + 1))
            }
        }
        ?: emptyList()

private fun decodeFormValue(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)

private fun String.htmlEscape(): String = buildString {
    this@htmlEscape.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}
