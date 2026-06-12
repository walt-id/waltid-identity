package id.walt.issuer2.testsupport.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import java.nio.file.Files
import java.nio.file.Path

class KeycloakAuthorizationBrowser private constructor(
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

    override fun close() {
        runCatching { context.close() }
        runCatching { browser.close() }
        playwright.close()
    }

    companion object {
        private const val ENV_PLAYWRIGHT_BROWSER = "PLAYWRIGHT_BROWSER"
        private const val ENV_PLAYWRIGHT_BROWSER_BIN = "PLAYWRIGHT_BROWSER_BIN"
        private const val ENV_PLAYWRIGHT_HEADLESS = "PLAYWRIGHT_HEADLESS"

        fun open(): KeycloakAuthorizationBrowser {
            val browserName = PlaywrightBrowser.fromEnv()
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
                )
                KeycloakAuthorizationBrowser(
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
                            "./gradlew :waltid-services:waltid-issuer-api2:installPlaywrightBrowsers first.",
                        ex
                    )
                }
                throw ex
            }
        }

        private fun isHeadlessEnabled(): Boolean =
            when (env(ENV_PLAYWRIGHT_HEADLESS)?.lowercase()) {
                "true" -> true
                "false" -> false
                else -> true
            }

        private fun resolveBrowserBinaryPath(browser: PlaywrightBrowser): String? = sequenceOf(
            env(ENV_PLAYWRIGHT_BROWSER_BIN),
            when (browser) {
                PlaywrightBrowser.CHROMIUM -> env("CHROMIUM_BIN")
                    ?: env("CHROME_BIN")
                    ?: env("GOOGLE_CHROME_BIN")

                PlaywrightBrowser.FIREFOX -> env("FIREFOX_BIN")
                PlaywrightBrowser.WEBKIT -> env("WEBKIT_BIN")
            }
        )
            .filterNotNull()
            .firstOrNull { candidate ->
                runCatching {
                    val path = Path.of(candidate)
                    Files.isRegularFile(path) && Files.isExecutable(path)
                }.getOrDefault(false)
            }

        private fun env(name: String): String? =
            System.getenv(name)?.trim()?.takeIf { it.isNotBlank() }

        private fun Throwable.isMissingPlaywrightBrowser(): Boolean =
            generateSequence(this) { it.cause }.any { cause ->
                val message = cause.message.orEmpty()
                message.contains("Executable doesn't exist") ||
                    message.contains("playwright install", ignoreCase = true)
            }

        private enum class PlaywrightBrowser {
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
                fun fromEnv(): PlaywrightBrowser = when (
                    (env(ENV_PLAYWRIGHT_BROWSER) ?: "chromium").lowercase()
                ) {
                    "chromium", "chrome" -> CHROMIUM
                    "firefox" -> FIREFOX
                    "webkit", "safari" -> WEBKIT
                    else -> error(
                        "Unsupported $ENV_PLAYWRIGHT_BROWSER value. Expected one of: chromium, firefox, webkit"
                    )
                }
            }
        }
    }
}
