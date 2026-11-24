package id.walt.itb

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Request
import com.microsoft.playwright.Response
import com.microsoft.playwright.TimeoutError
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.ElementState
import com.microsoft.playwright.options.WaitUntilState
import io.ktor.http.Url
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.util.function.Consumer
import java.util.regex.Pattern

/** The owned session started, but the portal displayed its generic execution error. */
internal class ItbPortalExecutionError : IllegalStateException("The ITB portal could not start the wallet interaction")

/** A fixed reason suitable for reports that must never include the downloaded interaction. */
internal class ItbPortalInputError(val reason: Reason) : IllegalArgumentException(reason.name) {
    enum class Reason {
        INTERACTION_DOWNLOAD_SIZE,
        FIELD_COUNT,
        FIELD_VALUE_INVALID,
        WALLET_LINK_INVALID,
        TRANSACTION_CODE_INVALID,
    }
}

/** A bounded portal step timed out; the step name is safe to include in sanitized reports. */
internal class ItbPortalStepTimeout(val step: Step) : IllegalStateException("The ITB portal timed out at $step") {
    enum class Step {
        LOGIN_NAVIGATION, LOGIN_FORM, LOGIN_SUBMIT, LOGIN_HOME,
        NAVIGATION, SYSTEM, STATEMENT, ACTOR, EXECUTION_MODE, CASE, TEST_DEFINITION,
        SESSION, START, INTERACTION, DIALOG, CONFIRMATION, DOWNLOAD_CONTROL, DOWNLOAD_EVENT,
    }
}

internal class ItbPortalDefinitionFailure(val status: Int) :
    IllegalStateException("ITB test definition returned HTTP $status")

internal enum class PortalInput(val label: String, val usesDcApi: Boolean = false) {
    OFFER("VCI request"),
    PRESENTATION("VP request"),
    DC_ISSUANCE("Prepare offer and invoke wallet for PID issuance", usesDcApi = true),
    DC_PRESENTATION("Digital Credentials API presentation request", usesDcApi = true),
    DC_PAYMENT("TS12 payment Digital Credentials API presentation request", usesDcApi = true);

    companion object {
        fun forCase(suite: String, case: String): PortalInput = when (suite) {
            "cs01v1", "cs10-test-suite" -> OFFER
            "cs02v1" -> PRESENTATION
            "onespan_com" -> when (case) {
                "onespan_vci_01", "onespan_vci_03" -> OFFER
                else -> error("Unknown OneSpan wallet case")
            }
            "rec" -> when (case) {
                "recweb_issue_01", "recweb_issue_02" -> OFFER
                else -> error("Unknown Reconi wallet case")
            }
            "cts07" -> when (case) {
                "tc15" -> DC_PRESENTATION
                "tc16" -> DC_ISSUANCE
                else -> error("Unknown CS-07 wallet case")
            }
            "ts12" -> when {
                case.startsWith("ts12_issue_") -> OFFER
                case.startsWith("ts12_pay_dc_api_") -> DC_PAYMENT
                case.startsWith("ts12_pay_") -> PRESENTATION
                else -> error("Unknown TS-12 wallet case")
            }
            else -> error("Unknown ITB wallet suite")
        }
    }
}

/** Interactive GITB 1.29.5 execution. Browser storage, traces and screenshots are never exported. */
class ItbPortalBridge(
    private val page: Page,
    private val statementsUrl: String,
    private val systemName: String,
) : ItbInteractionBridge {
    private val definitionUrl = Regex(
        "${Regex.escape(statementsUrl.substringBefore('#').removeSuffix("/app"))}/api/tests/[0-9]+/definitionByStatement",
    )

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
        val runButton = step(ItbPortalStepTimeout.Step.CASE) {
            if (suite.statement == "Reference Implementations") {
                val menu = page.locator("button:visible").filter(
                    Locator.FilterOptions().setHasText(Pattern.compile("^\\s*Show test suite")),
                )
                menu.click()
                menu.locator("xpath=..").locator(".filterForm")
                    .getByText(suite.name, Locator.GetByTextOptions().setExact(true)).click()
            }
            val suiteRow = page.locator(".testSuite").filter(Locator.FilterOptions().setHas(
                page.getByText(suite.name, Page.GetByTextOptions().setExact(true)),
            ))
            suiteRow.locator(".mainLine").filter(Locator.FilterOptions().setHas(
                page.getByText(case.name, Page.GetByTextOptions().setExact(true)),
            )).locator("button[ngbtooltip=Run]")
        }
        return ItbSession(suite.id, case.id, prepareSession(runButton))
    }

    private fun prepareSession(runButton: Locator): String {
        // The portal reads the test definition before creating an interactive session.
        // Observe this read without delaying or repeating Run after an uncertain start.
        var definition: Request? = null
        var definitionFailure: ItbPortalDefinitionFailure? = null
        val onRequest = Consumer<Request> { request ->
            if (request.method() == "GET" && definitionUrl.matches(request.url().substringBefore('?'))) {
                definition = request
            }
        }
        val onResponse = Consumer<Response> { response ->
            if (response.request() == definition) {
                definition = null
                if (response.status() >= 400) {
                    definitionFailure = ItbPortalDefinitionFailure(response.status())
                }
            }
        }
        page.onRequest(onRequest)
        page.onResponse(onResponse)
        try {
            step(ItbPortalStepTimeout.Step.CASE) { runButton.click() }
            try {
                page.waitForCondition {
                    definitionFailure != null ||
                        (page.locator(".session-table-title-value .value").count() == 1 && sessionId().isNotBlank())
                }
            } catch (_: TimeoutError) {
                val step = if (definition == null) ItbPortalStepTimeout.Step.SESSION
                    else ItbPortalStepTimeout.Step.TEST_DEFINITION
                throw ItbPortalStepTimeout(step)
            }
            definitionFailure?.let { throw it }
            return sessionId()
        } finally {
            page.offRequest(onRequest)
            page.offResponse(onResponse)
        }
    }

    private fun startButton(): Locator = page.getByRole(
        AriaRole.BUTTON, Page.GetByRoleOptions().setName(Pattern.compile("Start$")),
    )

    private fun sessionId(): String = page.locator(".session-table-title-value .value").innerText().trim()
    private fun dialog(): Locator = page.locator("ngb-modal-window:not([aria-hidden=true])")

    private inline fun <T> step(name: ItbPortalStepTimeout.Step, action: () -> T): T = try {
        action()
    } catch (_: TimeoutError) {
        throw ItbPortalStepTimeout(name)
    }

    override suspend fun read(session: ItbSession): ItbWalletInteraction {
        verifySession(session)
        // Interactive execution keeps DC API instructions pending; REST background starts skip those steps.
        step(ItbPortalStepTimeout.Step.START) {
            page.waitForCondition { startButton().isEnabled }
            startButton().click()
        }
        return readNext(session)
    }

    private fun verifySession(session: ItbSession) {
        check(step(ItbPortalStepTimeout.Step.SESSION, ::sessionId) == session.session) {
            "The portal is not showing the owned ITB session"
        }
    }

    override suspend fun readNext(session: ItbSession): ItbWalletInteraction {
        val dialog = dialog()
        val input = PortalInput.forCase(session.testSuite, session.testCase)
        val fields = dialog.locator("app-any-content-view .content-name")
        val portalError = page.getByText("Unexpected Error", Page.GetByTextOptions().setExact(true))
        // Some reference-service starts take longer than the ordinary 15-second DOM timeout.
        // Wait for this owned session's interaction, without clicking Start or creating a session again.
        step(ItbPortalStepTimeout.Step.INTERACTION) {
            page.waitForCondition(
                { fields.count() > 0 || portalError.count() > 0 },
                Page.WaitForConditionOptions().setTimeout(45_000.0),
            )
        }
        verifySession(session)
        val field = fieldNamed(dialog, input.label)
        val count = field.count()
        if (count == 0 && portalError.count() > 0) throw ItbPortalExecutionError()
        if (count != 1) throw ItbPortalInputError(ItbPortalInputError.Reason.FIELD_COUNT)
        val row = field.locator("xpath=ancestor::app-any-content-view[1]")
        val content = if (input.usesDcApi) downloadText(row) else readText(row)
        return when (input) {
            PortalInput.OFFER -> ItbWalletInteraction.Offer(
                walletLink(content, "openid-credential-offer"), transactionCode(dialog, session),
            )
            PortalInput.PRESENTATION ->
                ItbWalletInteraction.Presentation(walletLink(content, "openid4vp", "eu-eaap", "haip-vp"))
            PortalInput.DC_ISSUANCE -> parseDigitalIssuance(content)
            PortalInput.DC_PRESENTATION, PortalInput.DC_PAYMENT ->
                parseDigitalCredentials(content, isPayment = input == PortalInput.DC_PAYMENT)
        }
    }

    private fun fieldNamed(dialog: Locator, label: String): Locator =
        dialog.getByText(label, Locator.GetByTextOptions().setExact(true))
            .and(dialog.locator("app-any-content-view .content-name"))

    private fun transactionCode(dialog: Locator, session: ItbSession): String? {
        val code = when ("${session.testSuite}/${session.testCase}") {
            "cs01v1/tc_vci_007" -> {
                val text = step(ItbPortalStepTimeout.Step.DIALOG) { dialog.innerText() }
                Regex("""\bPIN\s+([0-9]+)\b""").findAll(text).singleOrNull()?.groupValues?.get(1)
            }
            "rec/recweb_issue_02" -> {
                val field = fieldNamed(dialog, "Transaction Code")
                if (field.count() != 1) throw ItbPortalInputError(ItbPortalInputError.Reason.TRANSACTION_CODE_INVALID)
                readText(field.locator("xpath=ancestor::app-any-content-view[1]")).takeIf { it.matches(Regex("[0-9]{3,12}")) }
            }
            else -> return null
        }
        return code ?: throw ItbPortalInputError(ItbPortalInputError.Reason.TRANSACTION_CODE_INVALID)
    }

    private fun walletLink(content: String, vararg schemes: String): URI = try {
        URI(content).takeIf { uri -> schemes.any { it.equals(uri.scheme, ignoreCase = true) } }
            ?: throw ItbPortalInputError(ItbPortalInputError.Reason.WALLET_LINK_INVALID)
    } catch (_: URISyntaxException) {
        throw ItbPortalInputError(ItbPortalInputError.Reason.WALLET_LINK_INVALID)
    }

    private fun readText(row: Locator): String {
        val control = row.locator("button[ngbtooltip=Copy], button[ngbtooltip=Download]")
        step(ItbPortalStepTimeout.Step.DOWNLOAD_CONTROL) {
            control.first().waitFor(Locator.WaitForOptions().setTimeout(45_000.0))
        }
        if (control.count() != 1) throw ItbPortalInputError(ItbPortalInputError.Reason.FIELD_COUNT)
        if (control.getAttribute("ngbtooltip") == "Download") return downloadText(row)
        // GITB renders short fields in full beside Copy. Reading the row preserves the clipboard.
        return row.locator(".content-value").innerText().trim().takeIf { it.length in 1..100 }
            ?: throw ItbPortalInputError(ItbPortalInputError.Reason.FIELD_VALUE_INVALID)
    }

    private fun downloadText(row: Locator): String {
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
            if (Files.size(path) !in 1..1024 * 1024) {
                throw ItbPortalInputError(ItbPortalInputError.Reason.INTERACTION_DOWNLOAD_SIZE)
            }
            Files.readAllBytes(path).decodeToString(throwOnInvalidSequence = true).trim()
        } catch (_: CharacterCodingException) {
            throw ItbPortalInputError(ItbPortalInputError.Reason.FIELD_VALUE_INVALID)
        } finally {
            download.delete()
        }
    }

    override suspend fun complete() = step(ItbPortalStepTimeout.Step.DIALOG) { close(dialog()) }

    override suspend fun confirmRevocation(session: ItbSession) {
        val label = when ("${session.testSuite}/${session.testCase}") {
            "cs10-test-suite/rv_iss_01" -> "Confirm both credentials are shown as valid."
            "cs10-test-suite/rv_iss_02" -> "Confirm the wallet reports the credential as revoked."
            else -> error("This ITB case has no revocation confirmation")
        }
        val confirmation = dialog().filter(Locator.FilterOptions().setHas(
            page.getByText(label, Page.GetByTextOptions().setExact(true)),
        ))
        step(ItbPortalStepTimeout.Step.CONFIRMATION) {
            confirmation.waitFor(Locator.WaitForOptions().setTimeout(45_000.0))
            verifySession(session)
            close(confirmation)
        }
    }

    private fun close(dialog: Locator) {
        val previous = dialog.elementHandle()
        try {
            dialog.getByRole(AriaRole.BUTTON, Locator.GetByRoleOptions().setName(Pattern.compile("Close"))).click()
            // The following interaction may reuse the same labels.
            previous.waitForElementState(ElementState.HIDDEN)
        } finally {
            previous.dispose()
        }
    }

    companion object {
        internal const val APPLICATION_READY_TIMEOUT_MILLIS = 60_000.0

        internal fun parseDigitalIssuance(script: String): ItbWalletInteraction.DigitalIssuance =
            ItbWalletInteraction.DigitalIssuance(
                Url(string(script, "offerEndpoint")), string(script, "scenario"),
                string(script, "credentialType"), string(script, "protocol"),
            ).also {
                require(it.scenario == "pid-authorization-code" && it.credentialType == "urn:eu.europa.ec.eudi:pid:1" &&
                    it.protocol == "openid4vci-v1") { "Unexpected CS-07 DC API issuance profile" }
            }

        private fun string(script: String, name: String): String {
            val matches = Regex("\\b(?:const|let|var)\\s+${Regex.escape(name)}\\s*=\\s*[\"']([^\"'\\r\\n]+)[\"']\\s*;")
                .findAll(script).toList()
            require(matches.size == 1) { "The deployed DC API interaction has no unique $name" }
            return matches.single().groupValues[1]
        }

        /** Read only the deployed script's declarative inputs; never execute supplied JavaScript in the wallet. */
        internal fun parseDigitalCredentials(script: String, isPayment: Boolean): ItbWalletInteraction.DigitalCredentials {
            val payment = if (isPayment) {
                fun literal(name: String): String {
                    val matches = Regex("\\b${Regex.escape(name)}\\s*:\\s*[\"']([^\"'\\r\\n]+)[\"']")
                        .findAll(script).toList()
                    require(matches.size == 1) { "The deployed payment script has no unique $name" }
                    return matches.single().groupValues[1]
                }
                ItbWalletInteraction.Payment(
                    string(script, "attestationType"), literal("merchant"), literal("payee_id"),
                    literal("currency"), literal("amount"), literal("transaction_id"),
                )
            } else null
            return ItbWalletInteraction.DigitalCredentials(
                Url(string(script, if (payment == null) "presentationRequestEndpoint" else "requestEndpoint")),
                string(script, if (payment == null) "validationSessionId" else "sessionId"),
                string(script, "profile"), string(script, "dcApiProtocol"), payment,
            )
        }
    }
}
