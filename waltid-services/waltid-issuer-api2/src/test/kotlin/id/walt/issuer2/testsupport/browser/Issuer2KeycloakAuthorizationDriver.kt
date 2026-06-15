package id.walt.issuer2.testsupport.browser

import id.walt.commons.config.ConfigManager
import id.walt.issuer2.config.AuthenticationServiceConfig
import io.ktor.http.Url
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals

class Issuer2KeycloakAuthorizationDriver(
    private val keycloak: Issuer2KeycloakTestConfig = Issuer2KeycloakTestConfig.fromLoadedConfig(),
) {
    fun loginAndGetAuthorizationCode(
        authorizeUrl: String,
        redirectUri: String,
        expectedState: String?,
    ): String {
        KeycloakAuthorizationBrowser.open().use { browser ->
            val page = browser.page
            val expectedRedirect = Url(redirectUri)
            val capturedRedirectUrl = AtomicReference<String?>()

            page.onRequestFailed { request ->
                val failedUrl = request.url()
                val failedParsed = runCatching { Url(failedUrl) }.getOrNull()
                if (failedParsed != null &&
                    failedParsed.protocol == expectedRedirect.protocol &&
                    failedParsed.host == expectedRedirect.host &&
                    failedParsed.encodedPath == expectedRedirect.encodedPath
                ) {
                    capturedRedirectUrl.set(failedUrl)
                }
            }
            page.route("$redirectUri**") { route ->
                capturedRedirectUrl.set(route.request().url())
                route.fulfill(
                    com.microsoft.playwright.Route.FulfillOptions()
                        .setStatus(200)
                        .setContentType("text/html")
                        .setBody("<html><body>Authorization redirect captured</body></html>")
                )
            }

            page.navigate(authorizeUrl)
            val keycloakBaseUrl = Url(keycloak.baseUrl)
            val timeoutAt = System.currentTimeMillis() + Duration.ofSeconds(90).toMillis()
            var loginSubmitted = false
            var lastSeenUrl = page.url().orEmpty()

            while (System.currentTimeMillis() < timeoutAt) {
                val currentUrl = capturedRedirectUrl.get() ?: page.url().orEmpty()
                if (currentUrl.isNotBlank()) {
                    lastSeenUrl = currentUrl
                }
                val parsed = runCatching { Url(currentUrl) }.getOrNull()
                val isExpectedRedirectTarget = parsed != null &&
                        parsed.protocol == expectedRedirect.protocol &&
                        parsed.host == expectedRedirect.host &&
                        parsed.encodedPath == expectedRedirect.encodedPath

                if (isExpectedRedirectTarget) {
                    parsed.parameters["error"]?.let { errorCode ->
                        error(
                            "Authorization response returned error '$errorCode' with description: " +
                                    (parsed.parameters["error_description"] ?: "<none>")
                        )
                    }
                    expectedState?.let {
                        assertEquals(it, parsed.parameters["state"], "Returned state does not match request state")
                    }
                    return parsed.parameters["code"]
                        ?: error("Missing authorization code in redirect URL: $currentUrl")
                }

                if (parsed != null &&
                    parsed.protocol == keycloakBaseUrl.protocol &&
                    parsed.host == keycloakBaseUrl.host &&
                    parsed.encodedPath.startsWith(keycloakBaseUrl.encodedPath)
                ) {
                    val pageError = browser.firstVisibleText(".alert-error, .kc-feedback-text, #input-error").orEmpty()
                    if (pageError.isNotBlank()) {
                        error("Keycloak page error: $pageError (url=$currentUrl)")
                    }

                    val loginButton = browser.firstVisibleEnabled("#kc-login")
                        ?: browser.firstVisibleEnabled("button[type='submit']")

                    if (!loginSubmitted) {
                        val usernameInput = browser.firstVisible("#username")
                        val passwordInput = browser.firstVisible("#password")
                        if (usernameInput != null && passwordInput != null && loginButton != null) {
                            usernameInput.fill(keycloak.username)
                            passwordInput.fill(keycloak.password)
                            loginButton.click()
                            loginSubmitted = true
                        } else if (loginButton != null) {
                            loginButton.click()
                            loginSubmitted = true
                        }
                    } else if ((currentUrl.contains("login-actions/consent") ||
                                currentUrl.contains("login-actions/required-action")) &&
                        loginButton != null
                    ) {
                        loginButton.click()
                    }
                }

                page.waitForTimeout(250.0)
            }

            val pageSnippet = runCatching { page.content().take(1000) }
                .getOrNull()
                ?: "<page source unavailable>"
            error(
                "Timeout waiting for authorization redirect to wallet redirect URI. " +
                        "Last URL: $lastSeenUrl ; page snippet: $pageSnippet"
            )
        }
    }
}

data class Issuer2KeycloakTestConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    companion object {
        private const val ENV_KEYCLOAK_URL = "KEYCLOAK_URL"
        private const val ENV_KEYCLOAK_USERNAME = "KEYCLOAK_USERNAME"
        private const val ENV_KEYCLOAK_PASSWORD = "KEYCLOAK_PASSWORD"
        private const val DEFAULT_KEYCLOAK_USERNAME = "jane@walt.id"
        private const val DEFAULT_KEYCLOAK_PASSWORD = "jane"

        fun fromLoadedConfig(): Issuer2KeycloakTestConfig {
            val authConfig = ConfigManager.getConfig<AuthenticationServiceConfig>()
            return Issuer2KeycloakTestConfig(
                baseUrl = System.getenv(ENV_KEYCLOAK_URL)?.takeIf { it.isNotBlank() }
                    ?: authConfig.authorizeUrl.substringBefore("/protocol/openid-connect/auth"),
                username = System.getenv(ENV_KEYCLOAK_USERNAME)?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_KEYCLOAK_USERNAME,
                password = System.getenv(ENV_KEYCLOAK_PASSWORD)?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_KEYCLOAK_PASSWORD,
            )
        }
    }
}