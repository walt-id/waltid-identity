package id.walt.itb

import com.microsoft.playwright.Page
import com.microsoft.playwright.Locator
import com.microsoft.playwright.options.AriaRole
import io.ktor.http.Url
import java.util.regex.Pattern
import java.nio.file.Files

/** Interactive GITB 1.29.5 execution. Browser storage, traces and screenshots are never exported. */
class ItbPortalBridge(
    private val page: Page,
    private val statementsUrl: String,
    private val systemName: String,
) : ItbInteractionBridge {
    override suspend fun prepare(suite: ItbCatalogue.Suite, case: ItbCatalogue.Case): ItbSession {
        // A fresh document discards old dialogs and pending session-list updates while retaining login cookies.
        page.navigate("about:blank")
        page.navigate(statementsUrl)
        val system = page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(systemName))
        check(system.innerText().trim() == systemName) { "The portal selected a different ITB system" }
        page.getByText(suite.statement, Page.GetByTextOptions().setExact(true)).click()
        page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(
            Pattern.compile("(?:Interactive|Parallel background|Sequential background) execution$"),
        )).click()
        page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Interactive execution").setExact(true)).click()
        val suiteRow = page.locator(".testSuite").filter(Locator.FilterOptions().setHas(
            page.getByText(suite.name, Page.GetByTextOptions().setExact(true)),
        ))
        suiteRow.locator(".mainLine").filter(Locator.FilterOptions().setHas(
            page.getByText(case.name, Page.GetByTextOptions().setExact(true)),
        )).locator("button[ngbtooltip=Run]").click()
        page.waitForCondition { startButton().isEnabled }
        return ItbSession(suite.id, case.id, sessionId())
    }

    private fun startButton(): Locator = page.getByRole(
        AriaRole.BUTTON, Page.GetByRoleOptions().setName(Pattern.compile("Start$")),
    )

    private fun sessionId(): String = page.locator(".session-table-title-value .value").innerText().trim()

    override suspend fun read(session: ItbSession): ItbWalletInteraction {
        check(sessionId() == session.session) { "The portal is not showing the owned ITB session" }
        // Interactive execution keeps DC API instructions pending; REST background starts skip those steps.
        startButton().click()
        val dialog = page.locator("ngb-modal-window:not([aria-hidden=true])")
        dialog.getByText(Pattern.compile(
            "^\\s*(VCI request|VP request|(?:TS12 payment )?Digital Credentials API presentation request)\\s*$",
        )).waitFor()
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
