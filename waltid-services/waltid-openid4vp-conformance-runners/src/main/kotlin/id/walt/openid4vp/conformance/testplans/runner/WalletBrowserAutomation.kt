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
internal class WalletConformanceBrowserAutomation(
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
            var actionClicked = false
            while (System.currentTimeMillis() < deadline) {
                val currentUrl = page.url().orEmpty()
                if (currentUrl.startsWith(adapterPublicUrl) && currentUrl.contains("/callback")) return
                if (currentUrl.isConformanceCallback()) return

                if (!actionClicked) {
                    val submit = page.locator("button[type='submit'], input[type='submit']")
                    if (submit.count() > 0 && submit.first().isVisible) {
                        submit.first().click()
                        actionClicked = true
                    }
                }

                if (currentUrl.startsWith(adapterPublicUrl) && page.content().contains("Credential issuance completed")) {
                    return
                }
                if (currentUrl.startsWith(adapterPublicUrl) && page.content().contains("Wallet failed")) {
                    error("Wallet adapter failed to complete the interaction: ${page.content().take(1_000)}")
                }
                page.waitForTimeout(250.0)
            }

            error("Timed out waiting for the wallet browser interaction to complete; last URL=${page.url()}")
        } finally {
            browser.close()
        }
    }

    private fun String.isConformanceCallback(): Boolean =
        startsWith("https://$conformanceHost:$conformancePort/") && contains("/callback")
}

internal fun TestRunResult.walletBrowserInteractionsForAutomation(): List<BrowserInteraction> = pendingBrowserInteractions()
