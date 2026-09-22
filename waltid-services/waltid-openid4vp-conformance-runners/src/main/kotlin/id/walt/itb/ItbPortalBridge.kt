package id.walt.itb

import com.microsoft.playwright.Page
import com.microsoft.playwright.Locator
import com.microsoft.playwright.options.AriaRole
import io.ktor.http.Url
import io.ktor.http.parseQueryString
import java.util.regex.Pattern
import java.nio.file.Files

/** DOM-only bridge for the deployed GITB 1.29.5 portal. No browser storage, traces or screenshots are exported. */
class ItbPortalBridge(
    private val page: Page,
    private val sessionsUrl: String,
) : ItbInteractionBridge {
    override suspend fun read(session: ItbRestClient.CreatedSession): ItbWalletInteraction {
        // A fresh page also dismisses any interaction belonging to a preceding failed session.
        if (page.url() == sessionsUrl) page.reload() else page.navigate(sessionsUrl)
        val filter = page.locator("input[name=filterValue]")
        if (!filter.isVisible) page.getByText("Filters", Page.GetByTextOptions().setExact(true)).click()
        filter.click()
        filter.fill(session.session)
        // Expanding before the filtered response renders can click a loading row or lose the expansion.
        val response = page.waitForResponse({ response ->
            Url(response.url()).encodedPath.endsWith("/api/reports/active") &&
                parseQueryString(response.request().postData().orEmpty())["session_id"] == session.session
        }) { filter.press("Enter") }
        check(response.ok() && response.finished() == null) { "ITB session search failed" }
        val active = page.locator("#active-tests")
        active.locator("tr[table-row-directive]").click()
        active.getByText(session.session, Locator.GetByTextOptions().setExact(true)).waitFor()
        active.getByRole(AriaRole.BUTTON, Locator.GetByRoleOptions().setName(Pattern.compile("View pending interaction"))).click()
        val dialog = page.locator("ngb-modal-window:not([aria-hidden=true])")
        dialog.getByText(Pattern.compile("VCI request|VP request|Digital Credentials API presentation request")).waitFor()
        val text = dialog.innerText()
        return when {
            text.contains("VCI request") -> {
                val pin = Regex("\\bPIN\\s+([0-9]+)\\b").find(text)?.groupValues?.get(1)
                require(session.testCase != "tc_vci_007" || pin != null) { "The ITB interaction did not supply its transaction code" }
                ItbWalletInteraction.Offer(Url(preview("VCI request")), pin)
            }
            text.contains("VP request") -> ItbWalletInteraction.Presentation(Url(preview("VP request")))
            text.contains("TS12 payment Digital Credentials API presentation request") -> parseDigitalCredentials(
                preview("TS12 payment Digital Credentials API presentation request"),
            )
            text.contains("Digital Credentials API presentation request") -> parseDigitalCredentials(
                preview("Digital Credentials API presentation request"),
            )
            else -> error("The deployed case has an unrecognised wallet interaction")
        }
    }

    private fun preview(label: String): String {
        val dialog = page.locator("ngb-modal-window:not([aria-hidden=true])")
        val row = dialog.locator("app-any-content-view").filter(
            Locator.FilterOptions().setHas(page.getByText(label, Page.GetByTextOptions().setExact(true))),
        )
        // CodeMirror virtualizes long scripts, so rendered lines can omit payment inputs.
        val download = page.waitForDownload { row.locator("button[ngbtooltip=Download]").click() }
        return try {
            val path = download.path()
            require(Files.size(path) <= 1024 * 1024) { "The ITB interaction exceeds the size limit" }
            Files.readString(path).trim()
        } finally {
            download.delete()
        }
    }

    override suspend fun complete() {
        page.locator("ngb-modal-window:not([aria-hidden=true])")
            .getByRole(AriaRole.BUTTON, Locator.GetByRoleOptions().setName(Pattern.compile("Close"))).click()
    }

    companion object {
        /** Read only the deployed script's declarative inputs; never execute supplied JavaScript in the wallet. */
        internal fun parseDigitalCredentials(script: String): ItbWalletInteraction.DigitalCredentials {
            fun string(name: String): String {
                val matches = Regex("\\b(?:const|let|var)\\s+${Regex.escape(name)}\\s*=\\s*[\"']([^\"'\\r\\n]+)[\"']\\s*;")
                    .findAll(script).toList()
                require(matches.size == 1) { "The deployed DC API interaction has no unique $name" }
                return matches.single().groupValues[1]
            }
            val payment = if (Regex("\\b(?:const|let|var)\\s+attestationType\\s*=").containsMatchIn(script)) {
                fun literal(name: String): String {
                    val matches = Regex("\\b${Regex.escape(name)}\\s*:\\s*[\"']([^\"'\\r\\n]+)[\"']")
                        .findAll(script).toList()
                    require(matches.size == 1) { "The deployed payment script has no unique $name" }
                    return matches.single().groupValues[1]
                }
                ItbWalletInteraction.Payment(
                    string("attestationType"), literal("merchant"), literal("payee_id"),
                    literal("currency"), literal("amount"), literal("transaction_id"),
                )
            } else null
            return ItbWalletInteraction.DigitalCredentials(
                Url(string(if (payment == null) "presentationRequestEndpoint" else "requestEndpoint")),
                string(if (payment == null) "validationSessionId" else "sessionId"),
                string("profile"), string("dcApiProtocol"), payment,
            )
        }
    }
}
