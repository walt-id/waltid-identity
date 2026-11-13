package id.walt.walletdemo.compose.android

import android.graphics.Rect
import android.os.Process as AndroidProcess
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertTextVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.latestStatus
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.relaunchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.sendDeepLink
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResourceAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.regex.Pattern
import kotlin.math.roundToInt

/** Explicitly selected real issuance/status journey; never a persisted display-state override. */
@RunWith(AndroidJUnit4::class)
class CredentialStatusBackendE2ETest {
    /** Run against a fresh, test-owned app installation; no persisted display state is injected. */
    @Test
    fun credentialStatusLifecycle() = runBlocking {
        val control = InstrumentationRegistry.getArguments().getString("walletStatusControlUrl")
        assumeTrue("Run explicitly with the isolated controlled status issuer", control != null)
        val backend = ControlledStatusBackend(requireNotNull(control))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val cards = By.res(Pattern.compile("wallet\\.credentialCard\\..*"))
        fun cardIDs() = device.findObjects(cards).map { it.resourceName }.toSet()
        fun home() {
            val actions = listOf("wallet.scanButton", "wallet-detail-close", "wallet.detailsBack", "wallet.flowBack",
                "wallet.external.close", "wallet.sheet.close", "wallet.settingsBack", "issuance-done", "wallet.presentationDone")
            val nextAction = By.res(Pattern.compile(actions.joinToString("|") { Pattern.quote(it) }))
            repeat(6) {
                if (!device.wait(Until.hasObject(nextAction), 20_000)) {
                    val hierarchy = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }
                    throw AssertionError("No action returns to the collection: $hierarchy")
                }
                if (device.hasObject(By.res("wallet.scanButton"))) return
                val close = actions.drop(1).firstOrNull { device.hasObject(By.res(it)) }
                if (close != null) clickByTag(device, close)
            }
            assertNotNull(waitForResource(device, "wallet.scanButton", 20_000))
        }
        fun capture(name: String) {
            assertTrue("Transient feedback must clear before evidence capture",
                device.wait(Until.gone(By.text(Pattern.compile("(?s)Status changed:.*"))), 15_000))
            device.waitForIdle()
            WalletGalleryEvidence.capture(device, "android-status-$name")
        }
        fun issue(url: String): String {
            home()
            val before = cardIDs()
            WalletGalleryEvidence.request("issuance", url)
            sendDeepLink(context, url)
            assertNotNull(waitForResource(device, "wallet.offerAcceptButton", 90_000))
            clickByTag(device, "wallet.offerAcceptButton")
            assertNotNull("Normal issuance did not finish: ${latestStatus(device)}",
                waitForResource(device, "issuance-done", 90_000))
            clickByTag(device, "issuance-done")
            assertNotNull(waitForResource(device, "wallet.scanButton", 20_000))
            val added = cardIDs() - before
            assertEquals("Each offer must add exactly one real stored card", 1, added.size)
            return added.single()
        }
        fun openCard(id: String, headline: String) {
            home()
            tapStoredCard(device, id)
            assertTrue("The selected credential must have $headline",
                device.wait(Until.hasObject(By.text(headline)), 30_000))
        }
        fun refresh(headline: String) {
            val action = requireNotNull(device.findObject(By.res(Pattern.compile("credential-status-check-.*"))))
            val tag = action.resourceName
            assertTrue(device.wait(Until.hasObject(By.res(tag).enabled(true)), 30_000))
            clickByTag(device, tag)
            assertTrue("Refresh must render $headline", device.wait(Until.hasObject(By.text(headline)), 30_000))
            assertTrue(device.wait(Until.hasObject(By.res(tag).enabled(true)), 30_000))
        }
        fun submitLabel(): String? {
            assertNotNull(waitForResource(device, "wallet.presentationSubmitButton", 90_000))
            assertTrue(device.wait(Until.hasObject(By.res("wallet.presentationSubmitButton").enabled(true)), 30_000))
            val submit = requireNotNull(device.findObject(By.res("wallet.presentationSubmitButton")))
            return submit.text?.takeIf { it.isNotBlank() }
                ?: submit.contentDescription?.takeIf { it.isNotBlank() }
                ?: submit.findObject(By.text(Pattern.compile("Share|Confirm payment")))?.text
        }
        fun awaitSubmission(rejected: Boolean = false) {
            assertTrue("The wallet must finish its intentional submission", waitForStatus(device, 90_000,
                matcher = { it.startsWith("Presentation sent") || it.startsWith("Presentation finished") ||
                    rejected && it.startsWith("Present failed") },
                failurePrefixes = listOfNotNull("Receive failed", "Bootstrap failed", "Present failed".takeUnless { rejected })))
        }
        fun toggleCredential(card: String) {
            val id = card.removePrefix("wallet.credentialCard.")
            val choice = By.res(Pattern.compile("wallet\\.presentationCredentialToggle\\..*${Pattern.quote(id)}.*"))
            val tag = requireNotNull(device.wait(Until.findObject(choice), 15_000)).resourceName
            assertNotNull(waitForResourceAfterScrolling(device, tag, 10_000))
            clickByTag(device, tag)
        }

        backend.setOutage(false)
        try {
            launchAndUnlock(context, device)
            assertTrue("Use isolated empty app data for the lifecycle driver", cardIDs().isEmpty())
            val pid = DemoTestBackend.scenarios.first { it.id == "eudi-pid-sdjwt" }
            val pidCard = issue(DemoTestBackend.createOffer(pid).offerUrl)
            val eventOffer = backend.offer("event_pass")
            val eventCard = issue(eventOffer.url)
            val event = backend.reference(eventOffer)
            val accessOffer = backend.offer("staff_access")
            val accessCard = issue(accessOffer.url)
            val access = backend.reference(accessOffer)
            assertEquals(3, cardIDs().size)
            capture("multiple-real-credentials")
            val saved = cardIDs()
            // This exercises a fresh Activity; a process restart requires a separate instrumentation invocation.
            relaunchAndUnlock(context, device)
            assertEquals(saved, cardIDs())
            openCard(pidCard, "Status not provided")
            capture("public-demo-no-reference")
            openCard(eventCard, "Not suspended")
            backend.setStatus(event, 1)
            refresh("Suspended")
            capture("authenticated-suspended")
            backend.setStatus(event, 0)
            refresh("Not suspended")
            capture("suspension-cleared")
            openCard(accessCard, "Not revoked")
            capture("authenticated-clear")
            backend.setStatus(access, 1)
            refresh("Revoked")
            expandStatusDetails(device, accessCard)
            val checkedTag = "credential-status-last-checked-${accessCard.removePrefix("wallet.credentialCard.")}"
            val checked = requireNotNull(waitForResourceAfterScrolling(device, checkedTag, 10_000))
            val originalCheck = checked.text
            assertFalse(originalCheck.isNullOrBlank())
            capture("authenticated-revoked-expanded")
            backend.setOutage(true)
            // The refresh remains above the expanded facts.
            device.swipe(device.displayWidth / 2, device.displayHeight / 3,
                device.displayWidth / 2, device.displayHeight * 3 / 4, 30)
            refresh("Last known: revoked")
            assertEquals(originalCheck, requireNotNull(waitForResourceAfterScrolling(device, checkedTag, 10_000)).text)
            assertTrue("Signed freshness must expire without changing the device clock",
                device.wait(Until.hasObject(By.text("This result is out of date.")), 45_000))
            assertTextVisibleAfterScrolling(device, listOf("This result is out of date."),
                "The stale evidence note must remain reachable")
            assertEquals(originalCheck, requireNotNull(waitForResourceAfterScrolling(device, checkedTag, 10_000)).text)
            capture("outage-retained-evidence")
            backend.setOutage(false)
            home()
            capture("home-adverse")

            val session = DemoTestBackend.createCredentialStatusVerifierSession()
            WalletGalleryEvidence.request("presentation", session.authorizationRequestUri)
            sendDeepLink(context, session.authorizationRequestUri)
            assertEquals("Share", submitLabel())
            assertTextVisibleAfterScrolling(device, listOf("Status not provided"), "The quiet PID must stay separate")
            assertTrue(device.hasObject(By.text(Pattern.compile("(?:Last known: )?revoked", Pattern.CASE_INSENSITIVE))))
            capture("mixed-sharing")
            clickByTag(device, "wallet.presentationSubmitButton")
            assertTrue(device.wait(Until.hasObject(By.text("Share with status issues?")), 10_000))
            assertTrue(device.hasObject(By.text(Pattern.compile("(?s).*Workplace access.*"))))
            capture("sharing-confirmation")
            clickByTag(device, "credential-status-back")
            assertNotNull(waitForResource(device, "wallet.presentationSubmitButton", 10_000))
            clickByTag(device, "wallet.presentationSubmitButton")
            assertNotNull(waitForResource(device, "credential-status-confirm", 10_000))
            clickByTag(device, "credential-status-confirm")
            awaitSubmission()
            DemoTestBackend.waitForVerifierSuccess(session.sessionId, timeoutMs = 30_000)
            capture("sharing-result")
            home()

            val alternativeOffer = backend.offer("staff_access_alternative")
            val alternativeCard = issue(alternativeOffer.url)
            backend.reference(alternativeOffer)
            val clearSession = DemoTestBackend.createStatusEnforcementVerifierSession()
            WalletGalleryEvidence.request("presentation", clearSession.authorizationRequestUri)
            sendDeepLink(context, clearSession.authorizationRequestUri)
            assertEquals("Share", submitLabel())
            toggleCredential(accessCard)
            toggleCredential(alternativeCard)
            capture("unselected-adverse-alternative")
            clickByTag(device, "wallet.presentationSubmitButton")
            assertFalse("An unselected adverse alternative must not trigger a confirmation",
                device.wait(Until.hasObject(By.text("Share with status issues?")), 2_000))
            awaitSubmission()
            DemoTestBackend.verifyCredentialStatusPolicy(clearSession.sessionId, clear = true)
            capture("clear-verifier-policy-result")
            home()

            val revokedSession = DemoTestBackend.createStatusEnforcementVerifierSession()
            WalletGalleryEvidence.request("presentation", revokedSession.authorizationRequestUri)
            sendDeepLink(context, revokedSession.authorizationRequestUri)
            assertEquals("Share", submitLabel())
            clickByTag(device, "wallet.presentationSubmitButton")
            assertNotNull(waitForResource(device, "credential-status-confirm", 10_000))
            capture("revoked-verifier-policy-confirmation")
            clickByTag(device, "credential-status-confirm")
            awaitSubmission(rejected = true)
            DemoTestBackend.verifyCredentialStatusPolicy(revokedSession.sessionId, clear = false)
            capture("revoked-verifier-policy-result")
            home()

            // Exercise the actual preference and batch before payment, then deliberately
            // review expired evidence in manual mode without automatic warning invalidation.
            clickByTag(device, "wallet.settingsButton")
            capture("root-settings")
            clickByTag(device, "settings-credential-status")
            val automatic = requireNotNull(waitForResource(device, "settings-credential-status-automatic", 10_000))
            assertTrue(automatic.isChecked)
            clickByTag(device, automatic.resourceName)
            assertTrue(device.wait(Until.hasObject(By.res(automatic.resourceName).checked(false)), 5_000))
            capture("settings-manual")
            clickByTag(device, automatic.resourceName)
            assertTrue(device.wait(Until.hasObject(By.res(automatic.resourceName).checked(true)), 5_000))
            clickByTag(device, "settings-credential-status-check-all")
            assertTrue(device.wait(Until.hasObject(By.text("Processed")), 30_000))
            assertTrue(device.wait(Until.hasObject(By.res("settings-credential-status-check-all").enabled(true)), 30_000))
            capture("settings-completed")
            clickByTag(device, automatic.resourceName)
            assertTrue(device.wait(Until.hasObject(By.res(automatic.resourceName).checked(false)), 5_000))
            home()

            val paymentCard = issue(DemoTestBackend.createOffer(DemoTestBackend.credentialStatusPaymentScenario).offerUrl)
            val payment = DemoTestBackend.createCredentialStatusPaymentVerifierSession()
            WalletGalleryEvidence.request("presentation", payment.authorizationRequestUri)
            sendDeepLink(context, payment.authorizationRequestUri)
            assertEquals("Confirm payment", submitLabel())
            for (term in listOf("Review payment", "42.00", "EUR", "ACME Corp")) {
                assertTextVisibleAfterScrolling(device, listOf(term), "The real payment must retain $term")
            }
            capture("payment-main-terms")
            assertTextVisibleAfterScrolling(device, listOf("Status not provided"), "The payment must include its credential rows")
            assertTextVisibleAfterScrolling(device, listOf("Revoked", "Last known: revoked"),
                "The selected affected credential must remain reachable below the quiet payment rows")
            assertTrue("Payment evidence must deliberately review the expired result in manual mode",
                device.wait(Until.hasObject(By.text("Last known: revoked")), 45_000))
            capture("payment-selected-credentials")
            fun showPaymentWarning() {
                clickByTag(device, "wallet.presentationSubmitButton")
                val changed = "The review changed. Check the updated information before continuing."
                assertTrue(device.wait(Until.hasObject(By.text(Pattern.compile(
                    "Credential status warning|${Pattern.quote(changed)}"))), 10_000))
                if (!device.hasObject(By.text("Credential status warning"))) {
                    // A real freshness change invalidates the old attempt without submitting.
                    assertTrue(device.hasObject(By.text(changed)))
                    assertTrue(device.hasObject(By.text("Last known: revoked")))
                    assertFalse(device.hasObject(By.res("wallet.presentationDone")))
                    clickByTag(device, "wallet.presentationSubmitButton")
                }
                assertTrue(device.wait(Until.hasObject(By.text("Credential status warning")), 10_000))
            }
            showPaymentWarning()
            val confirm = requireNotNull(waitForResource(device, "credential-status-confirm", 10_000))
            assertTrue(confirm.text == "Confirm payment" || confirm.findObject(By.text("Confirm payment")) != null)
            assertFalse(device.hasObject(By.text("Share anyway")))
            capture("payment-confirmation")
            clickByTag(device, "credential-status-back")
            assertEquals("Confirm payment", submitLabel())
            showPaymentWarning()
            assertNotNull(waitForResource(device, "credential-status-confirm", 10_000))
            clickByTag(device, "credential-status-confirm")
            awaitSubmission()
            DemoTestBackend.waitForVerifierSuccess(payment.sessionId, timeoutMs = 30_000)
            val paymentInfo = DemoTestBackend.verifierSessionInfo(payment.sessionId)
            assertEquals(setOf("pid", "payment_card", "workplace"),
                paymentInfo["policy_results"]?.jsonObject?.get("vp_policies")?.jsonObject?.keys)
            capture("payment-result")
            home()

            // Persist only test expectations. A separate invocation must kill/relaunch the process;
            // no SDK data or status is injected, and automatic checks stay off during that read.
            tapStoredCard(device, accessCard)
            assertTrue(device.wait(Until.hasObject(By.text(Pattern.compile("(?:Last known: )?revoked", Pattern.CASE_INSENSITIVE))), 30_000))
            expandStatusDetails(device, accessCard)
            val retainedTime = requireNotNull(waitForResourceAfterScrolling(device, checkedTag, 10_000)).text
            assertFalse(retainedTime.isNullOrBlank())
            File(context.cacheDir, "credential-status-process-retention.json").writeText(JSONObject()
                .put("cards", JSONArray((saved + alternativeCard + paymentCard).toList()))
                .put("accessCard", accessCard).put("checked", retainedTime).put("pid", AndroidProcess.myPid()).toString())
        } finally {
            backend.setOutage(false)
        }
    }

    /** Explicit second invocation after force-stop: proves process identity and real durable state. */
    @Test
    fun credentialStatusAfterProcessRestart() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Run explicitly after the lifecycle driver's process has stopped",
            arguments.getString("walletStatusRetentionVerification") == "true")
        val backend = ControlledStatusBackend(requireNotNull(arguments.getString("walletStatusControlUrl")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val expected = JSONObject(File(context.cacheDir, "credential-status-process-retention.json").readText())
        assertTrue("Activity recreation is insufficient", expected.getInt("pid") != AndroidProcess.myPid())
        backend.setOutage(true)
        try {
            launchAndUnlock(context, device)
            val stored = device.findObjects(By.res(Pattern.compile("wallet\\.credentialCard\\..*"))).map { it.resourceName }.toSet()
            val expectedCards = expected.getJSONArray("cards").let { values ->
                (0 until values.length()).map { values.getString(it) }.toSet()
            }
            assertEquals(5, expectedCards.size)
            assertEquals(expectedCards, stored)
            val access = expected.getString("accessCard")
            tapStoredCard(device, access)
            assertTrue(device.wait(Until.hasObject(By.text(Pattern.compile("(?:Last known: )?revoked", Pattern.CASE_INSENSITIVE))), 30_000))
            val checkedTag = "credential-status-last-checked-${access.removePrefix("wallet.credentialCard.")}"
            expandStatusDetails(device, access)
            val checked = waitForResourceAfterScrolling(device, checkedTag, 10_000)
            if (checked == null) {
                WalletGalleryEvidence.capture(device, "android-status-process-retention-diagnostic")
                val direct = device.findObject(By.res(checkedTag))
                val hierarchy = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }
                throw AssertionError("Original evidence time missing: direct=${direct?.text}, bounds=${direct?.visibleBounds}. $hierarchy")
            }
            assertEquals(expected.getString("checked"), requireNotNull(checked).text)
            WalletGalleryEvidence.capture(device, "android-status-process-retained-evidence")
            println("WALLET_STATUS_PROCESS_RETENTION=passed:5:original-check-time-preserved")
        } finally {
            backend.setOutage(false)
        }
    }

    private fun expandStatusDetails(device: UiDevice, card: String) {
        val tag = "credential-status-details-${card.removePrefix("wallet.credentialCard.")}"
        val disclosure = requireNotNull(waitForResourceAfterScrolling(device, tag, 10_000)) {
            "The selected credential's status disclosure must become visible: $tag"
        }
        val state = AccessibilityNodeInfoCompat.wrap(disclosure.accessibilityNodeInfo).stateDescription?.toString()
        assertTrue("The disclosure must expose its accessible state: $state", state in setOf("Expanded", "Collapsed"))
        if (state == "Collapsed") clickByTag(device, tag)
    }

    private fun tapStoredCard(device: UiDevice, id: String) {
        val frame = requireNotNull(device.findObject(By.res(id))).visibleBounds
        val lowerEdge = device.findObjects(By.res(Pattern.compile("wallet\\.credentialCard\\..*")))
            .filter { it.resourceName != id }.map { it.visibleBounds }
            .filter { it.top > frame.top && Rect.intersects(it, frame) }.minOfOrNull { it.top } ?: frame.bottom
        assertTrue("The selected card needs an exposed band", lowerEdge > frame.top)
        device.click(frame.centerX(), (frame.top + lowerEdge) / 2)
    }

    @Test
    fun controlledIssuanceAndRevocation() {
        val control = InstrumentationRegistry.getArguments().getString("walletStatusControlUrl")
        assumeTrue("Run explicitly with the isolated controlled status issuer", control != null)
        val backend = ControlledStatusBackend(requireNotNull(control))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        launchAndUnlock(context, device)
        val cards = By.res(Pattern.compile("wallet\\.credentialCard\\..*"))
        val previous = device.findObjects(cards).map { it.resourceName }.toSet()
        val offer = backend.offer("staff_access")
        WalletGalleryEvidence.request("issuance", offer.url)
        sendDeepLink(context, offer.url)
        assertNotNull("Normal offer review did not appear", waitForResource(device, "wallet.offerAcceptButton", 90_000))
        clickByTag(device, "wallet.offerAcceptButton")
        assertNotNull("Normal issuance did not finish", waitForResource(device, "issuance-done", 90_000))
        clickByTag(device, "issuance-done")
        assertNotNull(waitForResource(device, "wallet.scanButton", 20_000))
        val added = device.findObjects(cards).filter { it.resourceName !in previous }
        assertEquals("Issuance must add exactly one real stored card", 1, added.size)
        val reference = backend.reference(offer)
        clickByTag(device, added.single().resourceName)
        assertTrue("An authenticated clear result must appear", device.wait(Until.hasObject(By.text("Not revoked")), 30_000))
        val refresh = requireNotNull(device.findObject(By.res(Pattern.compile("credential-status-check-.*"))))
        val disclosure = requireNotNull(device.findObject(By.res(Pattern.compile("credential-status-details-.*"))))
        val minimum = (48 * context.resources.displayMetrics.density).roundToInt()
        for (controlNode in listOf(refresh, disclosure)) {
            assertTrue("Independent controls need 48-dp targets", controlNode.visibleBounds.width() >= minimum)
            assertTrue("Independent controls need 48-dp targets", controlNode.visibleBounds.height() >= minimum)
        }
        assertFalse(Rect.intersects(refresh.visibleBounds, disclosure.visibleBounds))
        WalletGalleryEvidence.capture(device, "android-status-authenticated-clear")
        backend.setStatus(reference, 1)
        clickByTag(device, refresh.resourceName)
        assertTrue("A real changed list must reach the UI", device.wait(Until.hasObject(By.text("Revoked")), 30_000))
        WalletGalleryEvidence.capture(device, "android-status-authenticated-revoked")
    }
}

/** Test-only loopback controls; signing, list authentication and state interpretation stay in the SDK. */
private class ControlledStatusBackend(raw: String) {
    private val base = URI(raw).also {
        require(it.scheme == "http" && it.host in setOf("127.0.0.1", "localhost", "[::1]", "::1") &&
            it.userInfo == null && it.query == null && it.fragment == null && it.path in setOf("", "/"))
    }
    data class Offer(val url: String, val configuration: String, val previousIDs: Set<String>)
    data class Reference(val id: String, val configuration: String, val purpose: String, val index: Int)

    fun offer(configuration: String): Offer {
        val previous = issued().map { it.id }.toSet()
        val value = request("offers", JSONObject().put("configuration", configuration))
        return Offer(value.getString("credentialOffer"), configuration, previous)
    }

    fun reference(offer: Offer): Reference = issued().single {
        it.configuration == offer.configuration && it.id !in offer.previousIDs
    }

    fun setStatus(reference: Reference, value: Int) {
        require(value in 0..1)
        request("status", JSONObject().put("purpose", reference.purpose).put("index", reference.index).put("value", value))
    }

    fun setOutage(enabled: Boolean) {
        request("outage", JSONObject().put("enabled", enabled))
    }

    private fun issued(): List<Reference> {
        val values = request("state").getJSONArray("issued")
        return List(values.length()) {
            val value = values.getJSONObject(it)
            Reference(value.getString("id"), value.getString("configuration"), value.getString("purpose"), value.getInt("index"))
        }
    }

    private fun request(path: String, body: JSONObject? = null): JSONObject {
        val connection = base.resolve("/$path").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            check(connection.responseCode == 200) { "Controlled issuer request failed: ${connection.responseCode}" }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            check(text.length <= 65_536) { "Controlled issuer response exceeds the fixture bound" }
            return JSONObject(text)
        } finally { connection.disconnect() }
    }
}
