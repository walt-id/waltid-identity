package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.httpdata.TestRunResult
import java.time.Duration

data class WalletBrowserAutomationConfig(
    val enabled: Boolean,
    val timeoutSeconds: Long,
) {
    companion object {
        fun fromEnvironment(): WalletBrowserAutomationConfig = WalletBrowserAutomationConfig(
            enabled = value(
                "openid4vci.wallet-conformance.browser-automation",
                "OPENID4VCI_WALLET_CONFORMANCE_BROWSER_AUTOMATION",
            )
                ?.let { it.equals("true", true) || it == "1" || it.equals("yes", true) }
                ?: true,
            timeoutSeconds = value(
                "openid4vci.wallet-conformance.browser-timeout-seconds",
                "OPENID4VCI_WALLET_CONFORMANCE_BROWSER_TIMEOUT_SECONDS",
            )
                ?.toLongOrNull()
                ?.coerceAtLeast(1)
                ?: 90L,
        )

        private fun value(property: String, environment: String): String? =
            System.getProperty(property)?.trim()?.takeIf { it.isNotEmpty() }
                ?: System.getenv(environment)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/** Drives only the front-channel URLs emitted by the conformance suite. */
class WalletConformanceBrowserAutomation(
    private val config: WalletBrowserAutomationConfig,
    private val adapterPublicUrl: String,
    private val conformanceHost: String,
    private val conformancePort: Int,
) {
    fun complete(interaction: BrowserInteraction) {
        val browser = ConformanceBrowser.open()
        try {
            val page = browser.page
            page.setDefaultTimeout(30_000.0)
            page.setDefaultNavigationTimeout(30_000.0)
            println("Opening wallet browser interaction via ${interaction.method}: ${interaction.url}")
            openBrowserInteraction(page, interaction)

            val deadline = System.currentTimeMillis() + Duration.ofSeconds(config.timeoutSeconds).toMillis()
