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
            assertEquals("openid4vp", interaction.url.protocol.name)
            assertEquals("https://verifier.example/request", interaction.url.parameters["request_uri"])
            assertEquals(true, page.evaluate("window.interactive"))
            assertEquals("selected", page.evaluate("window.selectedSuite"))
            bridge.complete()
            assertEquals(0, page.locator("ngb-modal-window").count())
        }
    }

    @Test
    fun readsTheCompleteInlineInteractionWithoutADownloadControl() = runBlocking<Unit> {
        val url = "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example%2Fshort"
        withPage(inlineInteraction = url, interactionLabel = "VCI request") { page ->
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            val session = bridge.prepare(suite, case)
            val interaction = assertIs<ItbWalletInteraction.Offer>(bridge.read(session))
            assertEquals("https://issuer.example/short", interaction.url.parameters["credential_offer_uri"])
            assertEquals(0, page.evaluate("window.textDownloadCount"))
            assertEquals(0, page.evaluate("window.qrDownloadCount"))
            assertEquals(0, page.evaluate("window.copyCount"))
            assertEquals(1, page.evaluate("window.startCount"))
        }
    }

    @Test
    fun readsThePresentationQrWhenDerivedTextIsANumericBarcode() = runBlocking<Unit> {
        withPage(inlineInteraction = "12345678") { page ->
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            val interaction = assertIs<ItbWalletInteraction.Presentation>(bridge.read(bridge.prepare(suite, case)))
            assertEquals("openid4vp", interaction.url.protocol.name)
            assertEquals("https://verifier.example/request", interaction.url.parameters["request_uri"])
            assertEquals(1, page.evaluate("window.qrDownloadCount"))
            assertEquals(0, page.evaluate("window.textDownloadCount"))
            assertEquals(0, page.evaluate("window.copyCount"))
            assertEquals(1, page.evaluate("window.startCount"))
        }
    }

    @Test
    fun neverFallsBackToTextWhenThePresentationQrIsInvalid() = runBlocking<Unit> {
        withPage(
            inlineInteraction = "openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Fshort",
            presentationQr = byteArrayOf(1, 2, 3),
        ) { page ->
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            assertFailsWith<IllegalArgumentException> { bridge.read(bridge.prepare(suite, case)) }
            assertEquals(1, page.evaluate("window.qrDownloadCount"))
            assertEquals(0, page.evaluate("window.textDownloadCount"))
            assertEquals(1, page.evaluate("window.startCount"))
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
    fun waitsForTheSamePreviewDownloadBeyondTheOrdinaryDomTimeout() = runBlocking<Unit> {
        withPage(downloadDelayMillis = 200) { page ->
            val bridge = ItbPortalBridge(page, statementsUrl, catalogue.systemName)
            val session = bridge.prepare(suite, case)
            page.setDefaultTimeout(100.0)
            assertIs<ItbWalletInteraction.Presentation>(bridge.read(session))
            assertEquals(1, page.evaluate("window.startCount"))
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
    fun loginFailureReportsTheSafeStepWithoutRetryingOrLeakingInputs() {
        for (showForm in listOf(false, true)) {
            withLoginPage(showForm = showForm, acceptLogin = false) { page ->
                page.setDefaultTimeout(500.0)
                val failure = assertFailsWith<ItbPortalStepTimeout> {
                    ItbPortalLogin.login(page, "https://itb.example", "private-user", "private-password", 500.0)
                }
                assertEquals(if (showForm) ItbPortalStepTimeout.Step.LOGIN_HOME else ItbPortalStepTimeout.Step.LOGIN_FORM, failure.step)
                assertEquals(if (showForm) 1 else 0, page.evaluate("window.submits"))
                assertFalse(failure.toString().contains("private"))
            }
        }
    }

    private fun withLoginPage(showForm: Boolean = true, acceptLogin: Boolean = true, block: (Page) -> Unit) {
        Playwright.create().use { playwright ->
            playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true)).use { browser ->
                browser.newContext().use { context ->
                    context.route("**/*") { route ->
                        if (route.request().url() == "https://itb.example/app") {
                            route.fulfill(Route.FulfillOptions().setContentType("text/html").setBody("""
                                <img src="/slow-image">
                                <script>
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

    private suspend fun withPage(
        systemName: String = catalogue.systemName,
        applicationDelayMillis: Int = 0,
        inlineInteraction: String? = null,
        interactionLabel: String = "VP request",
        presentationQr: ByteArray = ItbQrFixtures.qr("openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Frequest"),
        interactionDelayMillis: Int = 30,
        downloadDelayMillis: Int = 0,
        failStart: Boolean = false,
        startNeverEnabled: Boolean = false,
        actorVisible: Boolean = false,
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
                                interactionLabel, Base64.getEncoder().encodeToString(presentationQr),
                            )
                            route.fulfill(Route.FulfillOptions().setContentType("text/html").setBody(html))
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
        interactionLabel: String, qrBase64: String,
    ) = """
        <script>
        setTimeout(() => {
            const system = document.createElement('button');
            system.textContent = '$systemName'; document.body.prepend(system);
        }, $applicationDelayMillis);
        </script>
        <app-conformance-statement-item-display>
          <button onclick="selectStatement()">${suite.statement}</button><div id="actors"></div>
        </app-conformance-statement-item-display>
        <app-conformance-statement-item-display><div>Other statement</div><button>User</button></app-conformance-statement-item-display>
        <script>
        window.started = false;
        window.startCount = 0;
        window.copyCount = 0;
        window.qrDownloadCount = 0;
        window.textDownloadCount = 0;
        window.interactive = false;
        function selectStatement() {
            if ($actorVisible) document.querySelector('#actors').innerHTML = '<button onclick="showTests()">User</button>';
            else showTests();
        }
        function showTests() {
            document.body.innerHTML = `
                <button id="button-executionType">Actions...</button>
                <button id="button-executionType" onclick="showModes()">Sequential background execution</button>
                <div class="testSuite"><div>${suite.name}</div>
                  <div class="mainLine"><div>${deployedCaseName.replace("[Wallet SUT]", "[Verifier SUT]")}</div><button ngbtooltip="Run" onclick="prepare('wrong-actor')">Run</button></div>
                  <div class="mainLine"><div>$deployedCaseName</div><button ngbtooltip="Run" onclick="prepare('selected')">Run</button></div>
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
            window.selectedSuite = selected;
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
                dialog.innerHTML = `<app-any-content-view><div> $interactionLabel QR code </div><button ngbtooltip="Download" onclick="download(true)">Download</button></app-any-content-view>
                  <app-any-content-view><div> $interactionLabel </div>${
                      if (inlineInteraction == null) """<button ngbtooltip="Download" onclick="download(false)">Download</button>"""
                      else """<div class="content-value">$inlineInteraction </div><button ngbtooltip="Copy" onclick="window.copyCount++">Copy</button>"""
                  }</app-any-content-view>
                  <button onclick="this.parentElement.remove()">Close</button>`;
                document.body.append(dialog);
            }, $interactionDelayMillis);
        }
        function download(qr) {
            if (qr) window.qrDownloadCount++; else window.textDownloadCount++;
            setTimeout(() => {
                const a = document.createElement('a');
                a.href = qr ? 'data:image/png;base64,$qrBase64' : URL.createObjectURL(new Blob(['openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Frequest']));
                a.download = qr ? 'interaction.png' : 'interaction.txt';
                a.click();
            }, $downloadDelayMillis);
        }
        </script>
    """.trimIndent()
}
