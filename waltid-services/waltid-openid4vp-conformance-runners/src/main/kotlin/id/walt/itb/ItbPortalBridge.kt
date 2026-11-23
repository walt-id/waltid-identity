package id.walt.itb

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.TimeoutError
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.ElementState
import com.microsoft.playwright.options.WaitUntilState
import io.ktor.http.Url
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.util.regex.Pattern

/** The owned session started, but the portal displayed its generic execution error. */
internal class ItbPortalExecutionError : IllegalStateException("The ITB portal could not start the wallet interaction")

/** A fixed reason suitable for reports that must never include the downloaded interaction. */
internal class ItbPortalInputError(val reason: Reason) : IllegalArgumentException(reason.name) {
    enum class Reason {
        INTERACTION_DOWNLOAD_SIZE,
        WALLET_QR_IMAGE_FORMAT,
        WALLET_QR_IMAGE_DIMENSIONS,
        WALLET_QR_IMAGE_READ,
        WALLET_QR_UNREADABLE,
        WALLET_QR_INVALID_URL,
        ATTACHMENT_COUNT,
        ISSUANCE_OFFER_INVALID_URL,
        TRANSACTION_CODE_MISSING,
    }
}

/** A bounded portal step timed out; the step name is safe to include in sanitized reports. */
internal class ItbPortalStepTimeout(val step: Step) : IllegalStateException("The ITB portal timed out at $step") {
    enum class Step {
        LOGIN_NAVIGATION, LOGIN_FORM, LOGIN_SUBMIT, LOGIN_HOME,
        NAVIGATION, SYSTEM, STATEMENT, ACTOR, EXECUTION_MODE, CASE,
        SESSION, START, INTERACTION, DIALOG, CONFIRMATION, DOWNLOAD_CONTROL, DOWNLOAD_EVENT,
    }
}

internal enum class PortalAttachment(vararg names: String, val usesDcApi: Boolean = false) {
    OFFER("Credential QR code", "VCI request QR code", "Credential Offer QR code", "QR code"),
    PRESENTATION("Credential QR code"),
    PAYMENT("VP request QR code"),
    DC_ISSUANCE("Prepare offer and invoke wallet for PID issuance", usesDcApi = true),
    DC_PRESENTATION("Digital Credentials API presentation request", usesDcApi = true),
    DC_PAYMENT("TS12 payment Digital Credentials API presentation request", usesDcApi = true);

    val label: Pattern = Pattern.compile(
        names.joinToString("|", "^\\s*(?:", ")\\s*$"),
        Pattern.CASE_INSENSITIVE,
    )

    companion object {
        fun forCase(suite: String, case: String): PortalAttachment = when (suite) {
            "cs01v1", "cs10-test-suite" -> OFFER
            "cs02v1" -> PRESENTATION
            "onespan_com" -> when (case) {
                "onespan_vci_01", "onespan_vci_03" -> OFFER
                else -> error("Unknown OneSpan wallet case")
            }
            "rec" -> when (case) {
                "recweb_issue_01" -> OFFER
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
                case.startsWith("ts12_pay_") -> PAYMENT
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
        val attachment = PortalAttachment.forCase(session.testSuite, session.testCase)
        val field = dialog.locator("app-any-content-view .content-name").filter(
            Locator.FilterOptions().setHasText(attachment.label),
        )
        val portalError = page.getByText("Unexpected Error", Page.GetByTextOptions().setExact(true))
        // Some reference-service starts take longer than the ordinary 15-second DOM timeout.
        // Wait for this owned session's interaction, without clicking Start or creating a session again.
        step(ItbPortalStepTimeout.Step.INTERACTION) {
            page.waitForCondition(
                { field.count() > 0 || portalError.count() > 0 },
                Page.WaitForConditionOptions().setTimeout(45_000.0),
            )
        }
        verifySession(session)
        val count = field.count()
        if (count == 0 && portalError.count() > 0) throw ItbPortalExecutionError()
        if (count != 1) throw ItbPortalInputError(ItbPortalInputError.Reason.ATTACHMENT_COUNT)
        val content = download(field.locator("xpath=ancestor::app-any-content-view[1]"))
        return when (attachment) {
            PortalAttachment.OFFER -> {
                val url = walletLink(content)
                if (!url.scheme.equals("openid-credential-offer", ignoreCase = true)) {
                    throw ItbPortalInputError(ItbPortalInputError.Reason.ISSUANCE_OFFER_INVALID_URL)
                }
                val text = step(ItbPortalStepTimeout.Step.DIALOG) { dialog.innerText() }
                val pin = Regex("""\bPIN\s+([0-9]+)\b""").find(text)?.groupValues?.get(1)
                if (session.testSuite == "cs01v1" && session.testCase == "tc_vci_007" && pin == null) {
                    throw ItbPortalInputError(ItbPortalInputError.Reason.TRANSACTION_CODE_MISSING)
                }
                ItbWalletInteraction.Offer(url, pin)
            }
            PortalAttachment.PRESENTATION, PortalAttachment.PAYMENT ->
                ItbWalletInteraction.Presentation(walletLink(content))
            PortalAttachment.DC_ISSUANCE -> parseDigitalIssuance(content.decodeToString(throwOnInvalidSequence = true))
            PortalAttachment.DC_PRESENTATION, PortalAttachment.DC_PAYMENT ->
                parseDigitalCredentials(
                    content.decodeToString(throwOnInvalidSequence = true),
                    isPayment = attachment == PortalAttachment.DC_PAYMENT,
                )
        }
    }

    private fun walletLink(content: ByteArray): URI = try {
        URI(ItbWalletQr.decode(content)).takeIf { it.isAbsolute }
            ?: throw ItbPortalInputError(ItbPortalInputError.Reason.WALLET_QR_INVALID_URL)
    } catch (_: URISyntaxException) {
        throw ItbPortalInputError(ItbPortalInputError.Reason.WALLET_QR_INVALID_URL)
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
            if (Files.size(path) !in 1..1024 * 1024) {
                throw ItbPortalInputError(ItbPortalInputError.Reason.INTERACTION_DOWNLOAD_SIZE)
            }
            Files.readAllBytes(path)
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
