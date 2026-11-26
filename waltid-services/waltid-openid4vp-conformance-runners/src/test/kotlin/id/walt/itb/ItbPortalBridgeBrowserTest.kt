package id.walt.itb

import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Route
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import java.util.Base64
import kotlin.test.*

/** Offline DOM contract tests. All browser requests are intercepted; no tenant credentials are used. */
@Tag("itb-portal")
class ItbPortalBridgeBrowserTest {
    private val catalogue = ItbCatalogue.walletCases()
    private val suite = catalogue.suites.single { it.id == "cs02v1" }
    private val case = suite.cases.single { it.id == "tc_vp_001" }
    // Observed portal label; keep independent from the catalogue under test.
    private val deployedCaseName = "VP-001: CS-02 hash presentation with default encrypted response mode [Wallet SUT]"
    private val presentationUrl = "openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Frequest&state=Ab%2Bc%252F" + "1234567890".repeat(4)
    private val statementsUrl = "https://itb.example/app#/organisation/conformance/20"

    @Test
    fun selectsTheExactSuiteAndInteractiveModeBeforeReadingTheOwnedSession() = runBlocking<Unit> {
        withPage { page ->
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            val session = bridge.prepare(suite, case)
            assertEquals(suite.id, session.testSuite)
            assertEquals(case.id, session.testCase)
            assertEquals(false, page.evaluate("window.started"))
            val interaction = assertIs<ItbWalletInteraction.Presentation>(bridge.read(session))
            val delivered = assertIs<ItbWalletInteraction.Presentation>(ItbDeviceWire.decode(ItbDeviceWire.encode(interaction)))
            assertEquals(presentationUrl, delivered.url.toString())
            assertEquals(true, page.evaluate("window.interactive"))
            assertEquals("selected", page.evaluate("window.selectedSuite"))
            bridge.complete()
            assertEquals(0, page.locator("ngb-modal-window").count())
        }
    }

    @Test
    fun readsTheIssuanceLinkAndItsPin() = runBlocking<Unit> {
        val offerUrl = "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example%2Foffer"
        withPage(attachment = offerUrl.encodeToByteArray(), attachmentLabel = "VCI request", transactionCode = "012345") { page ->
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            val session = bridge.prepare(suite.copy(id = "cs01v1"), case.copy(id = "tc_vci_007"))
            val delivered = ItbDeviceWire.decode(ItbDeviceWire.encode(bridge.read(session)))
            val offer = assertIs<ItbWalletInteraction.Offer>(delivered)
            assertEquals(offerUrl, offer.url.toString())
            assertEquals("012345", offer.transactionCode)
            assertEquals(1, page.evaluate("window.attachmentDownloads"))
        }
    }

    @Test
    fun scopesTheReferenceSuiteFilterAndReadsItsOffer() = runBlocking<Unit> {
        val reference = catalogue.suites.single { it.id == "rec" }
        val referenceCase = reference.cases.single { it.id == "recweb_issue_01" }
        val offerUrl = "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example%2Foffer"
        withPage(
            referenceSuite = true,
            attachment = offerUrl.encodeToByteArray(),
            attachmentLabel = "VCI request",
        ) { page ->
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            val session = bridge.prepare(reference, referenceCase)
            assertEquals(true, page.evaluate("window.suiteFiltered"))
            val offer = assertIs<ItbWalletInteraction.Offer>(bridge.read(session))
            assertEquals(offerUrl, offer.url.toString())
            assertNull(offer.transactionCode)
            assertEquals(1, page.evaluate("window.attachmentDownloads"))
        }
    }

    @Test
    fun ambiguousOfferRowsFailBeforeEitherDownload() = runBlocking<Unit> {
        withPage(attachmentLabel = "VCI request", otherLabel = "VCI request") { page ->
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            val session = bridge.prepare(suite.copy(id = "cs10-test-suite"), case.copy(id = "rv_iss_01"))
            val error = assertFailsWith<ItbPortalInputError> { bridge.read(session) }
            assertEquals(ItbPortalInputError.Reason.FIELD_COUNT, error.reason)
            assertEquals(0, page.evaluate("window.attachmentDownloads + window.otherDownloads"))
        }
    }

    @Test
    fun readsTheInlinePaymentLinkWithoutCopyingOrDownloadingItsQr() = runBlocking<Unit> {
        val inlineUrl = "openid4vp://?request_uri=https%3A%2F%2Fitb.example%2Fr&state=Ab%2Bc%252F"
        withPage(inlineInteraction = inlineUrl, attachmentLabel = "VP request QR code") { page ->
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            val session = bridge.prepare(suite.copy(id = "ts12"), case.copy(id = "ts12_pay_02"))
            val interaction = assertIs<ItbWalletInteraction.Presentation>(bridge.read(session))
            assertEquals(inlineUrl, interaction.url.toString())
            assertEquals(0, page.evaluate("window.attachmentDownloads + window.otherDownloads + window.copyCount"))
        }
    }

    @Test
    fun rejectsMalformedLinksAndWrongSchemesWithoutTryingAnotherField() = runBlocking<Unit> {
        for (invalid in listOf("openid4vp://?state=%zz", "openid-credential-offer://?credential_offer_uri=example", "12345678")) {
            withPage(inlineInteraction = invalid, attachmentLabel = "VP request QR code") { page ->
                val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
                val failure = assertFailsWith<ItbPortalInputError> { bridge.read(bridge.prepare(suite, case)) }
                assertEquals(ItbPortalInputError.Reason.WALLET_LINK_INVALID, failure.reason)
                assertEquals(0, page.evaluate("window.attachmentDownloads + window.otherDownloads"))
            }
        }
    }

    @Test
    fun readsReconiCodeFromItsOwnUniqueField() = runBlocking<Unit> {
        val offer = "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example%2Foffer"
        for ((value, otherLabel) in listOf("001234" to "QR code", null to "QR code", "001234" to "Transaction Code")) {
            withPage(attachment = offer.encodeToByteArray(), attachmentLabel = "VCI request",
                inlineInteraction = value, interactionLabel = "Transaction Code", otherLabel = otherLabel,
                transactionCode = "888888") { page ->
                val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
                val session = bridge.prepare(suite.copy(id = "rec"), case.copy(id = "recweb_issue_02"))
                if (value != null && otherLabel != "Transaction Code") {
                    assertEquals(value, assertIs<ItbWalletInteraction.Offer>(bridge.read(session)).transactionCode)
                } else {
                    assertEquals(ItbPortalInputError.Reason.TRANSACTION_CODE_INVALID,
                        assertFailsWith<ItbPortalInputError> { bridge.read(session) }.reason)
                }
            }
        }
    }

    @Test
    fun missingPresentationFieldNeverConsumesTheQr() = runBlocking<Unit> {
        withPage(attachmentLabel = "VP request QR code") { page ->
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            val failure = assertFailsWith<ItbPortalInputError> { bridge.read(bridge.prepare(suite, case)) }
            assertEquals(ItbPortalInputError.Reason.FIELD_COUNT, failure.reason)
            assertEquals(0, page.evaluate("window.attachmentDownloads + window.otherDownloads"))
        }
    }

    @Test
    fun selectsTheVisibleActorWithinItsConfiguredStatement() = runBlocking<Unit> {
        val actor = catalogue.suites.single { it.id == "cts07" }.actor
        assertEquals("User", actor)
        withPage(actorVisible = true) { page ->
            ItbPortalBridge(page, statementsUrl, catalogue.systemName).prepare(suite.copy(actor = actor), case)
            assertEquals("selected", page.evaluate("window.selectedSuite"))
            assertEquals(false, page.evaluate("window.started"))
        }
    }

    @Test
    fun freshDocumentWaitsForApplicationReadinessBeyondTheOrdinaryDomTimeout() = runBlocking<Unit> {
        withPage(applicationDelayMillis = 200) { page ->
            page.setDefaultTimeout(100.0)
            val session = ItbPortalBridge(page, statementsUrl, catalogue.systemName).prepare(suite, case)
            assertEquals(case.id, session.testCase)
            assertEquals(false, page.evaluate("window.started"))
        }
    }

    @Test
    fun identifiesAStaleCaseLabelWithoutStartingASession() = runBlocking<Unit> {
        withPage { page ->
            page.setDefaultTimeout(1_000.0)
            val failure = assertFailsWith<ItbPortalStepTimeout> {
                ItbPortalBridge(page, statementsUrl, catalogue.systemName)
                    .prepare(suite, case.copy(name = "VP-001: obsolete case label"))
            }
            assertEquals(ItbPortalStepTimeout.Step.CASE, failure.step)
            assertNull(page.evaluate("window.selectedSuite"))
            assertEquals(0, page.evaluate("window.startCount"))
        }
    }

    @Test
    fun distinguishesAPendingTestDefinitionFromWaitingForASessionId() = runBlocking<Unit> {
        for ((definition, expectedStep) in listOf(
            DefinitionResponse.PENDING to ItbPortalStepTimeout.Step.TEST_DEFINITION,
            DefinitionResponse.OK to ItbPortalStepTimeout.Step.SESSION,
        )) {
            withPage(definition = definition) { page ->
                page.setDefaultTimeout(1_000.0)
                val failure = assertFailsWith<ItbPortalStepTimeout> {
                    ItbPortalBridge(page, statementsUrl, catalogue.systemName).prepare(suite, case)
                }
                assertEquals(expectedStep, failure.step)
                assertEquals(1, page.evaluate("window.runCount"))
            }
        }
    }

    @Test
    fun reportsTheDefinitionHttpStatusWithoutItsBodyOrSubmittingAgain() = runBlocking<Unit> {
        withPage(definition = DefinitionResponse.SERVER_ERROR) { page ->
            val failure = assertFailsWith<ItbPortalDefinitionFailure> {
                ItbPortalBridge(page, statementsUrl, catalogue.systemName).prepare(suite, case)
            }
            assertEquals(500, failure.status)
            assertFalse(failure.toString().contains("private portal details"))
            assertEquals(1, page.evaluate("window.runCount"))
        }
    }

    @Test
    fun newSessionDiscardsThePreviousDialogAndCannotStartUsingItsIdentity() = runBlocking<Unit> {
        withPage { page ->
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            val first = bridge.prepare(suite, case)
            bridge.read(first) // Simulate a wallet failure that leaves the dialog open.
            val second = bridge.prepare(suite, case)
            assertNotEquals(first.session, second.session)
            assertEquals(0, page.locator("ngb-modal-window").count())
            assertFailsWith<IllegalStateException> { bridge.read(first) }
            assertEquals(false, page.evaluate("window.started"))
            assertIs<ItbWalletInteraction.Presentation>(bridge.read(second))
        }
    }

    @Test
    fun refusesAnotherSystemEvenWhenItsNameContainsTheConfiguredName() = runBlocking<Unit> {
        withPage(systemName = "${catalogue.systemName} staging") { page ->
            assertFailsWith<IllegalStateException> {
                ItbPortalBridge(page, statementsUrl, catalogue.systemName).prepare(suite, case)
            }
            assertNull(page.evaluate("window.selectedSuite"))
        }
    }

    @Test
    fun waitsForTheOwnedInteractionBeyondTheOrdinaryDomTimeout() = runBlocking<Unit> {
        withPage(interactionDelayMillis = 200) { page ->
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            val session = bridge.prepare(suite, case)
            page.setDefaultTimeout(100.0)
            assertIs<ItbWalletInteraction.Presentation>(bridge.read(session))
        }
    }

    @Test
    fun waitsForTheSameDcAttachmentDownloadBeyondTheOrdinaryDomTimeout() = runBlocking<Unit> {
        withPage(
            downloadDelayMillis = 200,
            attachmentLabel = "Prepare offer and invoke wallet for PID issuance",
            attachment = ItbDcScriptFixtures.issuance.encodeToByteArray(),
            inlineInteraction = "VCI request",
        ) { page ->
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            val session = bridge.prepare(suite.copy(id = "cts07"), case.copy(id = "tc16"))
            page.setDefaultTimeout(100.0)
            val issue = assertIs<ItbWalletInteraction.DigitalIssuance>(bridge.read(session))
            assertEquals("/rfc-issuer/vci/dc-api/offer", issue.endpoint.encodedPath)
            assertEquals(1, page.evaluate("window.attachmentDownloads"))
            assertEquals(0, page.evaluate("window.otherDownloads"))
            assertEquals(1, page.evaluate("window.startCount"))
        }
    }

    @Test
    fun deliversDcPresentationScriptsFromTheRequestedCase() = runBlocking<Unit> {
        suspend fun checkScript(
            suiteId: String, caseId: String, label: String, script: String,
            verify: (ItbWalletInteraction.DigitalCredentials) -> Unit,
        ) {
            withPage(attachmentLabel = label, attachment = script.encodeToByteArray(), otherLabel = "Credential QR code") { page ->
                val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
                val session = bridge.prepare(suite.copy(id = suiteId), case.copy(id = caseId))
                verify(assertIs<ItbWalletInteraction.DigitalCredentials>(bridge.read(session)))
                assertEquals(1, page.evaluate("window.attachmentDownloads"))
                assertEquals(0, page.evaluate("window.otherDownloads"))
            }
        }
        checkScript("cts07", "tc15", "Digital Credentials API presentation request", ItbDcScriptFixtures.presentation) {
            assertEquals("00000000-0000-0000-0000-000000000001", it.validationSession)
        }
        checkScript("ts12", "ts12_pay_dc_api_01", "TS12 payment Digital Credentials API presentation request", ItbDcScriptFixtures.payment) {
            assertEquals("tx-dc-api-iban-001", it.payment?.transactionId)
        }
    }

    @Test
    fun acknowledgesOnlyTheMatchingRevocationConfirmation() = runBlocking<Unit> {
        withPage { page ->
            val session = ItbSession("cs10-test-suite", "rv_iss_02", "00000000-0000-0000-0000-000000000001")
            page.setContent("""
                <div class="session-table-title-value"><div class="value">${session.session}</div></div>
                <ngb-modal-window id="other">
                  <div>Confirm both credentials are shown as valid.</div>
                  <button onclick="this.parentElement.remove()">Close</button>
                </ngb-modal-window>
                <ngb-modal-window id="expected">
                  <div>Confirm the wallet reports the credential as revoked.</div>
                  <button onclick="this.parentElement.remove()">Close</button>
                </ngb-modal-window>
            """.trimIndent())
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            assertFailsWith<IllegalStateException> {
                bridge.confirmRevocation(session.copy(session = "00000000-0000-0000-0000-000000000002"))
            }
            assertEquals(1, page.locator("#expected").count())
            bridge.confirmRevocation(session)
            assertEquals(0, page.locator("#expected").count())
            assertEquals(1, page.locator("#other").count())
        }
    }

    @Test
    fun reportsPortalExecutionErrorWithoutRetryingTheStart() = runBlocking<Unit> {
        withPage(failStart = true) { page ->
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            val session = bridge.prepare(suite, case)
            assertFailsWith<ItbPortalExecutionError> { bridge.read(session) }
            assertEquals(1, page.evaluate("window.startCount"))
        }
    }

    @Test
    fun identifiesAStartControlTimeoutWithoutStartingAnotherSession() = runBlocking<Unit> {
        withPage(startNeverEnabled = true) { page ->
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            val session = bridge.prepare(suite, case)
            assertEquals(false, page.evaluate("window.started"))
            page.setDefaultTimeout(100.0)
            val failure = assertFailsWith<ItbPortalStepTimeout> { bridge.read(session) }
            assertEquals(ItbPortalStepTimeout.Step.START, failure.step)
            assertEquals(0, page.evaluate("window.startCount"))
            assertEquals(session.session, page.locator(".session-table-title-value .value").innerText().trim())
        }
    }

    @Test
    fun loginWaitsForTheApplicationWithoutWaitingForUnrelatedResources() {
        withLoginPage { page ->
            page.setDefaultTimeout(100.0)
            ItbPortalLogin.login(page, "https://itb.example", "test-user", "test-password", 3_000.0)
            assertEquals("https://itb.example/app#/home", page.url())
            assertEquals(1, page.evaluate("window.submits"))
            assertEquals(false, page.evaluate("window.loaded"))
        }
    }

    @Test
    fun loginReloadsAnUnrenderedFormBeforeSubmittingOnce() {
        withLoginPage(formOnLoad = 2) { page ->
            page.setDefaultTimeout(500.0)
            ItbPortalLogin.login(page, "https://itb.example", "test-user", "test-password", 1_000.0)
            assertEquals("https://itb.example/app#/home", page.url())
            assertEquals(2, page.evaluate("window.appLoads"))
            assertEquals(1, page.evaluate("window.submits"))
        }
    }

    @Test
    fun loginFailureReportsTheSafeStepWithoutResubmittingOrLeakingInputs() {
        for (showForm in listOf(false, true)) {
            withLoginPage(formOnLoad = if (showForm) 1 else Int.MAX_VALUE, acceptLogin = false) { page ->
                page.setDefaultTimeout(500.0)
                val failure = assertFailsWith<ItbPortalStepTimeout> {
                    ItbPortalLogin.login(page, "https://itb.example", "private-user", "private-password", 1_000.0)
                }
                assertEquals(if (showForm) ItbPortalStepTimeout.Step.LOGIN_HOME else ItbPortalStepTimeout.Step.LOGIN_FORM, failure.step)
                assertEquals(if (showForm) 1 else 2, page.evaluate("window.appLoads"))
                assertEquals(if (showForm) 1 else 0, page.evaluate("window.submits"))
                assertFalse(failure.toString().contains("private"))
            }
        }
    }

    private fun withLoginPage(formOnLoad: Int = 1, acceptLogin: Boolean = true, block: (Page) -> Unit) {
        Playwright.create().use { playwright ->
            playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true)).use { browser ->
                browser.newContext().use { context ->
                    var appLoads = 0
                    context.route("**/*") { route ->
                        if (route.request().url() == "https://itb.example/app") {
                            appLoads++
                            val showForm = appLoads >= formOnLoad
                            route.fulfill(Route.FulfillOptions().setContentType("text/html").setBody("""
                                <img src="/slow-image">
                                <script>
                                window.appLoads = $appLoads;
                                window.submits = 0; window.loaded = false;
                                window.addEventListener('load', () => window.loaded = true);
                                if ($showForm) setTimeout(() => {
                                    const form = document.createElement('div');
                                    form.innerHTML = `<label>Username:<input></label><label>Password:<input></label>
                                        <button onclick="window.submits++; if ($acceptLogin) location.hash='/home'">Log in</button>`;
                                    document.body.append(form);
                                }, 200);
                                </script>
                            """.trimIndent()))
                        }
                        // Deliberately leave the unrelated image pending. Context teardown closes it.
                    }
                    block(context.newPage())
                }
            }
        }
    }

    private enum class DefinitionResponse(val status: Int?) { PENDING(null), OK(200), SERVER_ERROR(500) }

    private suspend fun withPage(
        systemName: String = catalogue.systemName,
        applicationDelayMillis: Int = 0,
        inlineInteraction: String? = null,
        interactionLabel: String = "VP request",
        attachment: ByteArray = presentationUrl.encodeToByteArray(),
        attachmentLabel: String = "VP request",
        otherLabel: String = "Credential QR code",
        transactionCode: String? = null,
        referenceSuite: Boolean = false,
        interactionDelayMillis: Int = 30,
        downloadDelayMillis: Int = 0,
        failStart: Boolean = false,
        startNeverEnabled: Boolean = false,
        actorVisible: Boolean = false,
        definition: DefinitionResponse? = null,
        block: suspend (Page) -> Unit,
    ) {
        Playwright.create().use { playwright ->
            playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true)).use { browser ->
                browser.newContext().use { context ->
                    var sessionNumber = 0
                    context.route("**/*") { route ->
                        if (route.request().url() == statementsUrl.substringBefore('#')) {
                            val session = "00000000-0000-0000-0000-${(++sessionNumber).toString().padStart(12, '0')}"
                            val html = fixture(
                                session, systemName, interactionDelayMillis, downloadDelayMillis, failStart,
                                startNeverEnabled, actorVisible, applicationDelayMillis, inlineInteraction,
                                interactionLabel, attachmentLabel, otherLabel, Base64.getEncoder().encodeToString(attachment),
                                transactionCode, referenceSuite, definition != null,
                            )
                            route.fulfill(Route.FulfillOptions().setContentType("text/html").setBody(html))
                        } else if (route.request().url() == "https://itb.example/api/tests/42/definitionByStatement?system=18") {
                            definition?.status?.let { status ->
                                route.fulfill(Route.FulfillOptions().setStatus(status).setBody("private portal details"))
                            }
                        } else route.abort()
                    }
                    val page = context.newPage()
                    page.setDefaultTimeout(3_000.0)
                    block(page)
                }
            }
        }
    }

    private fun fixture(
        session: String, systemName: String, interactionDelayMillis: Int, downloadDelayMillis: Int, failStart: Boolean,
        startNeverEnabled: Boolean, actorVisible: Boolean, applicationDelayMillis: Int, inlineInteraction: String?,
        interactionLabel: String, attachmentLabel: String, otherLabel: String, attachmentBase64: String,
        transactionCode: String?, referenceSuite: Boolean,
        requestDefinition: Boolean,
    ): String {
        val statement = if (referenceSuite) "Reference Implementations" else suite.statement
        val suiteName = if (referenceSuite) "reconi.nl Reference Implementation Test Suite" else suite.name
        val caseName = if (referenceSuite) "Test case 01: RECWEB hello_world issuance - authorization code flow"
            else deployedCaseName
        val suiteFilter = if (referenceSuite) """
            <div>
              <button onclick="this.nextElementSibling.hidden=false">Show test suite: All</button>
              <div class="filterForm" hidden>
                <button onclick="window.suiteFiltered=true; this.parentElement.hidden=true">$suiteName</button>
              </div>
            </div>
        """ else ""
        return """
        <script>
        setTimeout(() => {
            const system = document.createElement('button');
            system.textContent = '$systemName'; document.body.prepend(system);
        }, $applicationDelayMillis);
        </script>
        <app-conformance-statement-item-display>
          <button onclick="selectStatement()">$statement</button><div id="actors"></div>
        </app-conformance-statement-item-display>
        <app-conformance-statement-item-display><div>Other statement</div><button>User</button></app-conformance-statement-item-display>
        <script>
        window.started = false;
        window.startCount = 0;
        window.copyCount = 0;
        window.attachmentDownloads = 0;
        window.otherDownloads = 0;
        window.suiteFiltered = false;
        window.interactive = false;
        window.runCount = 0;
        function selectStatement() {
            if ($actorVisible) document.querySelector('#actors').innerHTML = '<button onclick="showTests()">User</button>';
            else showTests();
        }
        function showTests() {
            document.body.innerHTML = `
                <button id="button-executionType">Actions...</button>
                <button id="button-executionType" onclick="showModes()">Sequential background execution</button>
                $suiteFilter
                <div class="testSuite"><div>$suiteName</div>
                  <div class="mainLine"><div>${deployedCaseName.replace("[Wallet SUT]", "[Verifier SUT]")}</div><button ngbtooltip="Run" onclick="prepare('wrong-actor')">Run</button></div>
                  <div class="mainLine"><div>$caseName</div><button ngbtooltip="Run" onclick="prepare('selected')">Run</button></div>
                </div>
                <div class="testSuite"><div>Other suite</div>
                  <div class="mainLine"><div>$deployedCaseName</div><button ngbtooltip="Run" onclick="prepare('wrong')">Run</button></div>
                </div>`;
        }
        function showModes() {
            const option = document.createElement('button');
            option.textContent = 'Interactive execution';
            option.onclick = () => { window.interactive = true; option.remove(); };
            document.body.append(option);
        }
        function prepare(selected) {
            window.runCount++;
            window.selectedSuite = selected;
            if ($requestDefinition) {
                fetch('/api/tests/42/definitionByStatement?system=18');
                return;
            }
            document.body.innerHTML = `<div class="session-table-title-value"><div class="value">$session</div></div>
              <button id="start" disabled onclick="start()">Start</button>`;
            if (!$startNeverEnabled) setTimeout(() => document.querySelector('#start').disabled = false, 30);
        }
        function start() {
            window.started = true;
            window.startCount++;
            document.querySelector('#start').disabled = true;
            setTimeout(() => {
                if ($failStart) {
                    const error = document.createElement('div');
                    error.textContent = 'Unexpected Error';
                    document.body.append(error);
                    return;
                }
                const dialog = document.createElement('ngb-modal-window');
                dialog.innerHTML = `<app-any-content-view><div class="content-name"> $otherLabel </div><button ngbtooltip="Download" onclick="download(false)">Download</button></app-any-content-view>
                  <app-any-content-view><div class="content-name"> $attachmentLabel </div><button ngbtooltip="Download" onclick="download(true)">Download</button></app-any-content-view>
                  ${transactionCode?.let { "<p>Use PIN $it</p>" }.orEmpty()}
                  ${inlineInteraction?.let { """<app-any-content-view><div class="content-name">$interactionLabel</div>
                    <div class="content-value">$it</div><button ngbtooltip="Copy" onclick="window.copyCount++">Copy</button>
                  </app-any-content-view>""" }.orEmpty()}
                  <button onclick="this.parentElement.remove()">Close</button>`;
                document.body.append(dialog);
            }, $interactionDelayMillis);
        }
        function download(expected) {
            if (expected) window.attachmentDownloads++; else window.otherDownloads++;
            setTimeout(() => {
                const a = document.createElement('a');
                a.href = expected ? 'data:application/octet-stream;base64,$attachmentBase64' : 'data:text/plain,wrong-row';
                a.download = 'interaction.bin';
                a.click();
            }, $downloadDelayMillis);
        }
        </script>
        """.trimIndent()
    }
}
