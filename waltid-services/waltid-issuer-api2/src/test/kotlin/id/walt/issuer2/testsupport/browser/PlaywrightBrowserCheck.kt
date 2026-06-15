package id.walt.issuer2.testsupport.browser

object PlaywrightBrowserCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        KeycloakAuthorizationBrowser.open().use { browser ->
            val page = browser.page
            page.setContent("<!doctype html><title>playwright-check</title><main>ready</main>")
            check(page.title() == "playwright-check") {
                "Playwright browser check failed: unexpected page title '${page.title()}'"
            }
            println("Playwright browser check passed.")
        }
    }
}
