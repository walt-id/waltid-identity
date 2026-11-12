package id.walt.itb

import com.microsoft.playwright.Page
import com.microsoft.playwright.Locator
import com.microsoft.playwright.TimeoutError
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.WaitUntilState
import io.ktor.http.Url
import java.nio.ByteBuffer
import java.util.regex.Pattern
import java.nio.file.Files

/** The owned session started, but the portal displayed its generic execution error. */
internal class ItbPortalExecutionError : IllegalStateException("The ITB portal could not start the wallet interaction")

/** A bounded portal step timed out; the step name is safe to include in sanitized reports. */
internal class ItbPortalStepTimeout(val step: Step) : IllegalStateException("The ITB portal timed out at $step") {
    enum class Step {
        LOGIN_NAVIGATION, LOGIN_FORM, LOGIN_SUBMIT, LOGIN_HOME,
        NAVIGATION, SYSTEM, STATEMENT, ACTOR, EXECUTION_MODE, CASE,
        SESSION, START, INTERACTION, DIALOG, DOWNLOAD_CONTROL, DOWNLOAD_EVENT,
    }
}

/** Interactive GITB 1.29.5 execution. Browser storage, traces and screenshots are never exported. */
class ItbPortalBridge(
    private val page: Page,
    private val statementsUrl: String,
    private val systemName: String,
) : ItbInteractionBridge {
    override suspend fun prepare(suite: ItbCatalogue.Suite, case: ItbCatalogue.Case): ItbSession {
        // A fresh document discards old dialogs and pending session-list updates while retaining login cookies.
        step(ItbPortalStepTimeout.Step.NAVIGATION) {
            page.navigate("about:blank")
            page.navigate(statementsUrl, Page.NavigateOptions().setWaitUntil(WaitUntilState.COMMIT))
        }
        step(ItbPortalStepTimeout.Step.SYSTEM) {
            val system = page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(systemName))
            // Each fresh document must bootstrap the application before ordinary DOM steps can begin.
            check(system.innerText(Locator.InnerTextOptions().setTimeout(APPLICATION_READY_TIMEOUT_MILLIS)).trim() == systemName) {
                "The portal selected a different ITB system"
            }
        }
        step(ItbPortalStepTimeout.Step.STATEMENT) {
            page.getByText(suite.statement, Page.GetByTextOptions().setExact(true)).click()
        }
        suite.actor?.let { actor ->
            step(ItbPortalStepTimeout.Step.ACTOR) {
                page.getByText(suite.statement, Page.GetByTextOptions().setExact(true))
                    .locator("xpath=ancestor::app-conformance-statement-item-display[1]")
                    .getByText(actor, Locator.GetByTextOptions().setExact(true)).click()
            }
        }
        step(ItbPortalStepTimeout.Step.EXECUTION_MODE) {
            page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(
                Pattern.compile("(?:Interactive|Parallel background|Sequential background) execution$"),
            )).click()
            page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Interactive execution").setExact(true)).click()
        }
        step(ItbPortalStepTimeout.Step.CASE) {
            val suiteRow = page.locator(".testSuite").filter(Locator.FilterOptions().setHas(
                page.getByText(suite.name, Page.GetByTextOptions().setExact(true)),
            ))
            suiteRow.locator(".mainLine").filter(Locator.FilterOptions().setHas(
                page.getByText(case.name, Page.GetByTextOptions().setExact(true)),
            )).locator("button[ngbtooltip=Run]").click()
        }
        val id = step(ItbPortalStepTimeout.Step.SESSION) {
            page.waitForCondition { page.locator(".session-table-title-value .value").count() == 1 && sessionId().isNotBlank() }
            sessionId()
        }
        return ItbSession(suite.id, case.id, id)
    }

    private fun startButton(): Locator = page.getByRole(
        AriaRole.BUTTON, Page.GetByRoleOptions().setName(Pattern.compile("Start$")),
    )

    private fun sessionId(): String = page.locator(".session-table-title-value .value").innerText().trim()

    private inline fun <T> step(name: ItbPortalStepTimeout.Step, action: () -> T): T = try {
        action()
    } catch (_: TimeoutError) {
        throw ItbPortalStepTimeout(name)
    }

    override suspend fun read(session: ItbSession): ItbWalletInteraction {
        check(step(ItbPortalStepTimeout.Step.SESSION, ::sessionId) == session.session) {
            "The portal is not showing the owned ITB session"
        }
        // Interactive execution keeps DC API instructions pending; REST background starts skip those steps.
        step(ItbPortalStepTimeout.Step.START) {
            page.waitForCondition { startButton().isEnabled }
            startButton().click()
        }
        val dialog = page.locator("ngb-modal-window:not([aria-hidden=true])")
        val interaction = dialog.getByText(Pattern.compile(
            "^\\s*(VCI request|VP request|(?:TS12 payment )?Digital Credentials API presentation request)\\s*$",
        ))
        val portalError = page.getByText("Unexpected Error", Page.GetByTextOptions().setExact(true))
        // Some reference-service starts take longer than the ordinary 15-second DOM timeout.
        // Wait for this owned session's interaction, without clicking Start or creating a session again.
        step(ItbPortalStepTimeout.Step.INTERACTION) {
            page.waitForCondition(
                { interaction.count() > 0 || portalError.count() > 0 },
                Page.WaitForConditionOptions().setTimeout(45_000.0),
            )
        }
        if (portalError.count() > 0 && interaction.count() == 0) throw ItbPortalExecutionError()
        val text = step(ItbPortalStepTimeout.Step.DIALOG) { dialog.innerText() }
        return when {
            text.contains("VCI request") -> {
                val pin = Regex("\\bPIN\\s+([0-9]+)\\b").find(text)?.groupValues?.get(1)
                require(session.testCase != "tc_vci_007" || pin != null) { "The ITB interaction did not supply its transaction code" }
                ItbWalletInteraction.Offer(Url(preview("VCI request")), pin)
            }
            // Consume the wallet-facing QR directly; ITB's derived text can be a false EAN/UPC decode.
            text.contains("VP request") -> ItbWalletInteraction.Presentation(
                Url(ItbPresentationQr.decode(download(row("Credential QR code")))),
            )
            text.contains("TS12 payment Digital Credentials API presentation request") -> parseDigitalCredentials(
                preview("TS12 payment Digital Credentials API presentation request"),
            )
            text.contains("Digital Credentials API presentation request") -> parseDigitalCredentials(
                preview("Digital Credentials API presentation request"),
            )
            else -> error("The deployed case has an unrecognised wallet interaction")
        }
    }

    private fun row(label: String): Locator {
        val dialog = page.locator("ngb-modal-window:not([aria-hidden=true])")
        return dialog.locator("app-any-content-view").filter(
            Locator.FilterOptions().setHas(page.getByText(label, Page.GetByTextOptions().setExact(true))),
        )
    }

    private fun preview(label: String): String {
        val row = row(label)
        val control = row.locator("button[ngbtooltip=Download], button[ngbtooltip=Copy]")
        step(ItbPortalStepTimeout.Step.DOWNLOAD_CONTROL) {
            control.waitFor(Locator.WaitForOptions().setTimeout(45_000.0))
        }
        // GITB renders plain values up to 100 characters inline, with Copy instead of Download.
        // Read that exact row's complete text without touching the clipboard or evaluating scripts.
        if (control.getAttribute("ngbtooltip") == "Copy") {
            return row.locator(".content-value").innerText().trim().also {
                require(it.length in 1..100) { "The ITB inline interaction has an unexpected size" }
            }
        }
        return Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(download(row))).toString().trim()
    }

    private fun download(row: Locator): ByteArray {
        // CodeMirror virtualizes long scripts, so rendered lines can omit payment inputs.
        val download = step(ItbPortalStepTimeout.Step.DOWNLOAD_EVENT) {
            // The portal may prepare this one file after its interaction dialog appears.
            // Wait for the same click's download; never click again after an uncertain result.
            page.waitForDownload(Page.WaitForDownloadOptions().setTimeout(45_000.0)) {
                step(ItbPortalStepTimeout.Step.DOWNLOAD_CONTROL) {
                    row.locator("button[ngbtooltip=Download]").click(Locator.ClickOptions().setTimeout(45_000.0))
                }
            }
        }
        return try {
            val path = download.path()
            require(Files.size(path) <= 1024 * 1024) { "The ITB interaction exceeds the size limit" }
            Files.readAllBytes(path)
        } finally {
            download.delete()
        }
    }

    override suspend fun complete() {
        page.locator("ngb-modal-window:not([aria-hidden=true])")
            .getByRole(AriaRole.BUTTON, Locator.GetByRoleOptions().setName(Pattern.compile("Close"))).click()
    }

    companion object {
        internal const val APPLICATION_READY_TIMEOUT_MILLIS = 60_000.0

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
