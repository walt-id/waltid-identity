package id.walt.itb

import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Route
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import kotlin.test.*

/** Offline DOM contract tests. All browser requests are intercepted; no tenant credentials are used. */
@Tag("itb-portal")
class ItbPortalBridgeBrowserTest {
    private val catalogue = ItbCatalogue.initialWalletCases()
    private val suite = catalogue.suites.single { it.id == "cs02v1" }
    private val case = suite.cases.single { it.id == "tc_vp_001" }
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

    private suspend fun withPage(
        systemName: String = catalogue.systemName,
        interactionDelayMillis: Int = 30,
        downloadDelayMillis: Int = 0,
        failStart: Boolean = false,
        startNeverEnabled: Boolean = false,
        block: suspend (Page) -> Unit,
    ) {
        Playwright.create().use { playwright ->
            playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true)).use { browser ->
                browser.newContext().use { context ->
                    var sessionNumber = 0
                    context.route("**/*") { route ->
                        if (route.request().url() == statementsUrl.substringBefore('#')) {
                            val session = "00000000-0000-0000-0000-${(++sessionNumber).toString().padStart(12, '0')}"
                            route.fulfill(Route.FulfillOptions().setContentType("text/html")
                                .setBody(fixture(session, systemName, interactionDelayMillis, downloadDelayMillis, failStart, startNeverEnabled)))
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
        startNeverEnabled: Boolean,
    ) = """
        <button>$systemName</button><button onclick="showTests()">${suite.statement}</button>
        <script>
        window.started = false;
        window.startCount = 0;
        window.interactive = false;
        function showTests() {
            document.body.innerHTML = `
                <button id="button-executionType">Actions...</button>
                <button id="button-executionType" onclick="showModes()">Sequential background execution</button>
                <div class="testSuite"><div>${suite.name}</div>
                  <div class="mainLine"><div>${case.name}</div><button ngbtooltip="Run" onclick="prepare('selected')">Run</button></div>
                </div>
                <div class="testSuite"><div>Other suite</div>
                  <div class="mainLine"><div>${case.name}</div><button ngbtooltip="Run" onclick="prepare('wrong')">Run</button></div>
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
                dialog.innerHTML = `<app-any-content-view><div> VP request QR code </div><button>Download</button></app-any-content-view>
                  <app-any-content-view><div> VP request </div><button ngbtooltip="Download" onclick="download()">Download</button></app-any-content-view>
                  <button onclick="this.parentElement.remove()">Close</button>`;
                document.body.append(dialog);
            }, $interactionDelayMillis);
        }
        function download() {
            setTimeout(() => {
                const a = document.createElement('a');
                a.href = URL.createObjectURL(new Blob(['openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Frequest']));
                a.download = 'interaction.txt';
                a.click();
            }, $downloadDelayMillis);
        }
        </script>
    """.trimIndent()
}
