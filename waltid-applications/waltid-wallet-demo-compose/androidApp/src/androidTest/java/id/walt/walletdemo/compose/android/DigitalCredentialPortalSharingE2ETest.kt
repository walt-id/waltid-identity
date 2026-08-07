package id.walt.walletdemo.compose.android

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.sendDeepLink
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Browser-mediated Digital Credentials sharing E2E driven by the walt.id demo portal.
 *
 * Where [DigitalCredentialBrowserSharingE2ETest] uses a purpose-built single-page harness, this
 * drives the actual portal a demo audience is shown: the same Nuxt app, the same
 * `useVerifierSession.createDcApiSession` code path, the same Swagger-sourced example payloads. The
 * wallet-side assertion is identical - `mso_mdoc/device-auth` only passes if the wallet hashed the
 * canonical web origin Chrome asserted into the mdoc session transcript and the verifier hashed the
 * same string - but the caller is production UI rather than a fixture, so it also covers the portal
 * wiring itself.
 *
 * Two portal properties shape how this is written:
 * - The portal picks the DC API code path from the *example title* (`isDcApiExample` tests for
 *   `dc_api`), not from the payload. Pasting a DC API payload under a QR example silently posts it
 *   as a cross-device session and never calls `navigator.credentials.get()`, which would look like a
 *   wallet failure. So the example must be selected, not just the payload written.
 * - The portal ships no `id` or `data-testid` attributes, so nothing here can use [By.res] for page
 *   content. Selection is by visible text, and the payload field is found by the text it contains.
 *
 * ## Why this is @Ignore'd
 *
 * The flow itself works. Driven manually, and on automated runs that got far enough, the portal
 * reaches `Completed successfully` and the verifier reports `status: SUCCESSFUL` with
 * `mso_mdoc/device-auth` passing - the wallet asserted the canonical web origin exactly as intended.
 * What is not reliable is *driving* the portal on a phone-sized screen, and the cause is the
 * portal's responsive layout rather than anything in the wallet:
 *
 * - Below `MOBILE_BREAKPOINT_PX = 768` the portal forces simple mode and renders the Simple/Advanced
 *   toggle under `v-if="!isMobile"`, so the payload editor this test needs is *unreachable* at phone
 *   width. Hence [enableDesktopSite] - a browser-level workaround for a page-level limitation.
 * - In the resulting desktop layout the payload textarea sits directly above the submit button, so
 *   the soft keyboard opened by [UiObject2.setText] covers the button. Dismissing it is timing
 *   dependent: the button stays in the accessibility tree reporting `clickable=true` while covered,
 *   so a swallowed click is indistinguishable from a delivered one and the run fails later, at the
 *   trust prompt, with no signal about the real cause. This is what makes the test flaky.
 *
 * The fix belongs in the portal, not here: make the Advanced editor reachable on mobile (or give the
 * payload field and submit button stable `data-testid`s and keep them from overlapping the IME).
 * Once phone-width layout works, [enableDesktopSite], [dismissKeyboard] and the scroll-and-poll
 * helpers all become unnecessary and this test reduces to roughly the shape of
 * [DigitalCredentialBrowserSharingE2ETest]. Kept rather than deleted because the portal is what
 * demos are given on, so it is worth re-enabling as soon as that lands.
 *
 * Origin coverage does not depend on this test: [DigitalCredentialSharingE2ETest] covers the native
 * `android:apk-key-hash:` flavour and [DigitalCredentialBrowserSharingE2ETest] covers the canonical
 * web origin against a harness page that is stable to automate.
 */
@RunWith(AndroidJUnit4::class)
@Ignore(
    "Flaky against portal2's mobile layout, not a wallet issue - see the class KDoc. Re-enable once " +
        "the portal's Advanced editor is usable at phone width.",
)
class DigitalCredentialPortalSharingE2ETest {

    @Test
    fun receivesMdlAndSharesItThroughTheDemoPortal() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        assumeTrue(
            "Digital Credentials E2E requires an Android emulator with Google Play services",
            hasPackage(context, "com.google.android.gms"),
        )
        assumeTrue(
            "Portal DC API E2E requires Chrome, which must also be in privileged_apps.json",
            hasPackage(context, CHROME_PACKAGE),
        )

        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        val offer = DemoTestBackend.createOffer(scenario)
        launchAndUnlock(context, device)
        sendDeepLink(context, offer.offerUrl)
        clickByTag(device, "wallet.receiveButton")
        assertTrue(
            "Offer preview did not appear",
            waitForStatus(
                device = device,
                timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
                matcher = { it.startsWith("Review credential offer") },
                failurePrefixes = listOf("Receive failed", "Bootstrap failed"),
            ),
        )
        clickByTag(device, "wallet.offerAcceptButton")
        assertTrue(
            "mDL was not received",
            waitForStatus(
                device = device,
                timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
                matcher = { it.startsWith("Received") },
                failurePrefixes = listOf("Receive failed", "Bootstrap failed"),
            ),
        )

        openPortal(context, device)
        enableDesktopSite(device)
        selectTab(device, "Advanced", confirmedBy = "Payload (editable JSON)")
        selectTab(device, "Verify", confirmedBy = "Verification Session Options")
        selectDcApiExample(device)

        // The example ships expectedOrigins: ["https://portal2.demo.walt.id"], but this test runs
        // against the test deployment, so the verifier would hash a different origin than Chrome
        // asserts and device auth would fail. The portal forwards the payload verbatim, so
        // overwriting the field is enough - applySecurityOverridesToJson() does not touch origins.
        val payloadField = requirePayloadField(device)
        payloadField.setText(createPayload(expectedOrigin = PORTAL_ORIGIN))
        // setText leaves the field focused with the IME up, and its floating toolbar overlaps the
        // submit button - the click would be swallowed by the input method instead.
        dismissKeyboard(device)

        val submit = device.wait(Until.findObject(By.text(SUBMIT_LABEL)), UI_ELEMENT_TIMEOUT)
        assertNotNull("Portal has no '$SUBMIT_LABEL' button", submit)
        submit!!.click()

        acceptSiteTrustPrompt(device)
        driveCredentialManagerPicker(device)

        // The portal posts the wallet response itself and then polls /info, so its own banner is the
        // verifier's verdict relayed back through production code. Assert on it first: if the portal
        // mishandles a correct response, that is a demo-blocking bug and this is where it shows.
        val completed = awaitInResultLog(device, By.textContains(SUCCESS_BANNER))
        assertNotNull("Portal did not report success. Page text:\n${pageText(device)}", completed)

        // Then confirm against the verifier directly, so a portal that mislabels its own outcome
        // cannot turn this green.
        val info = portalSessionInfo(readSessionId(device))
        assertEquals(
            "Verifier did not accept the presentation: $info",
            "SUCCESSFUL",
            info["status"]?.jsonPrimitive?.content,
        )

        val policyResults = info["policy_results"]
            ?: error("Session info has no policy_results: $info")
        assertTrue(
            "mso_mdoc/device-auth did not run: $policyResults",
            policyResults.executedPolicyIds().contains("mso_mdoc/device-auth"),
        )
        assertTrue(
            "Failed policies: ${policyResults.failedPolicies()}",
            policyResults.failedPolicies().isEmpty(),
        )
    }

    private fun openPortal(context: android.content.Context, device: UiDevice) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(PORTAL_URL))
                .setPackage(CHROME_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val loaded = device.wait(Until.findObject(By.text("Demo Portal")), CREDENTIAL_OPERATION_TIMEOUT)
        assertNotNull(
            "Portal did not load. Chrome may be showing a first-run screen.",
            loaded,
        )
    }

    /**
     * The portal's phone layout is unusable to a test. Below `MOBILE_BREAKPOINT_PX = 768` the portal
     * forces simple mode and hides the Simple/Advanced toggle behind `v-if="!isMobile"`, so the
     * payload editor is unreachable by design; on top of that the collapsed editor grid leaves most
     * controls reporting zero-height bounds, and taps meant for the Verify tab land on the
     * overlapping issuer panel. Requesting the desktop site lays the page out at full width and
     * every control becomes hittable. This is a workaround for a portal layout limitation, not
     * something the wallet needs - and it is what makes the keyboard overlap in [dismissKeyboard]
     * possible, so fixing the portal removes both.
     *
     * Chrome exposes the toggle's state in the menu item's content description ("Turn on ..." vs
     * "Turn off ..."), which is what makes this idempotent - the setting persists per site, so a
     * blind tap would switch it back off on a second run.
     */
    private fun enableDesktopSite(device: UiDevice) {
        val menuButton = device.wait(
            Until.findObject(By.res("$CHROME_PACKAGE:id/menu_button")),
            UI_ELEMENT_TIMEOUT,
        )
        assertNotNull("Chrome has no overflow menu button", menuButton)
        menuButton!!.click()

        val turnOn = device.wait(
            Until.findObject(By.desc(DESKTOP_SITE_OFF_DESC)),
            UI_ELEMENT_TIMEOUT,
        )
        if (turnOn == null) {
            // Already on from an earlier run - close the menu and carry on.
            device.pressBack()
        } else {
            turnOn.click()
        }
        // Toggling reloads the page; toggling off does not, hence waiting on content either way.
        assertNotNull(
            "Portal did not come back after requesting the desktop site",
            device.wait(Until.findObject(By.text("Demo Portal")), CREDENTIAL_OPERATION_TIMEOUT),
        )
    }

    /**
     * Clicks a tab and waits for content only that tab shows, retrying if nothing changes.
     *
     * The retry is not defensiveness: the portal is server-rendered, so its tab buttons are in the
     * accessibility tree - and clickable - before Vue has hydrated, and a click landing in that
     * window is swallowed silently. Waiting on [confirmedBy] rather than on the button's own state
     * is also required, because the portal's tabs report `selected=false` whether active or not.
     */
    private fun selectTab(device: UiDevice, label: String, confirmedBy: String) {
        repeat(MAX_TAB_ATTEMPTS) {
            val tab = device.wait(Until.findObject(By.text(label).clickable(true)), UI_ELEMENT_TIMEOUT)
            assertNotNull("Portal has no '$label' tab", tab)
            tab!!.click()
            if (device.wait(Until.findObject(By.textContains(confirmedBy)), UI_ELEMENT_TIMEOUT) != null) return
        }
        error("Clicking '$label' never revealed '$confirmedBy'; the portal may not have hydrated")
    }

    /**
     * Chooses an unsigned mdoc DC API example. The title match is the functional part: the portal
     * routes to `createDcApiSession` only when the selected example's title contains `dc_api`.
     *
     * The dropdown is a native spinner list of ~38 examples, so the wanted entry starts off-screen
     * and has to be scrolled to - Chrome publishes accessibility nodes only for visible rows.
     */
    private fun selectDcApiExample(device: UiDevice) {
        val exampleSelect = device.wait(
            Until.findObject(By.textStartsWith("[openid4vp-").clickable(true)),
            UI_ELEMENT_TIMEOUT,
        )
        assertNotNull("Portal has no verification example dropdown", exampleSelect)
        exampleSelect!!.click()

        val wanted = By.textStartsWith(DC_API_EXAMPLE_PREFIX)
        val option = device.wait(Until.findObject(wanted), UI_ELEMENT_TIMEOUT)
            ?: scrollListTo(device, wanted)
        assertNotNull(
            "No '$DC_API_EXAMPLE_PREFIX...' example is offered. The deployed verifier may not " +
                "advertise DC API examples, or the portal may still be filtering them out.",
            option,
        )
        option!!.click()

        // The payload only becomes the DC API one once the selection has applied; waiting on it
        // keeps the origin overwrite below from racing the reactive update.
        assertNotNull(
            "Selecting the DC API example did not load its payload",
            device.wait(Until.findObject(payloadSelector()), UI_ELEMENT_TIMEOUT),
        )
    }

    private fun scrollListTo(device: UiDevice, target: BySelector): UiObject2? {
        val list = device.wait(Until.findObject(By.scrollable(true)), UI_ELEMENT_TIMEOUT) ?: return null
        repeat(MAX_LIST_SCROLLS) {
            if (!list.scroll(Direction.DOWN, 1f)) return device.findObject(target)
            device.findObject(target)?.let { return it }
        }
        return device.findObject(target)
    }

    /**
     * The payload textarea carries no resource-id, so it is identified by the discriminator its
     * content must contain. That doubles as an assertion: if this finds nothing, the portal loaded
     * something other than a DC API payload and the run would otherwise fail later and less clearly.
     */
    private fun requirePayloadField(device: UiDevice): UiObject2 {
        val field = device.wait(Until.findObject(payloadSelector()), UI_ELEMENT_TIMEOUT)
        assertNotNull("Portal payload field does not hold a $FLOW_TYPE payload", field)
        return field!!
    }

    private fun payloadSelector(): BySelector = By.clazz("android.widget.EditText").textContains(FLOW_TYPE)

    /**
     * Closes the soft keyboard opened by `setText`.
     *
     * The IME cannot be detected by looking for the submit button: that node stays in the tree and
     * reports itself clickable while covered, so the click is delivered to the input method and
     * silently lost. Detect the IME by its own window instead, and stop as soon as it is gone so a
     * surplus back press cannot navigate the page away.
     */
    private fun dismissKeyboard(device: UiDevice) {
        repeat(MAX_KEYBOARD_DISMISS_ATTEMPTS) {
            if (device.findObject(By.pkg(Pattern.compile(".*inputmethod.*"))) == null) return
            device.pressBack()
            device.waitForIdle(UI_ELEMENT_TIMEOUT)
        }
        assertNotNull(
            "Payload field disappeared while closing the keyboard",
            device.findObject(payloadSelector()),
        )
    }

    /**
     * Waits for something in the portal's result log, scrolling down to it between polls.
     *
     * `Result Log` sits below the submit button and off the bottom of the screen, and Chrome
     * publishes accessibility nodes only for content in the viewport - so a plain wait would never
     * see it however long it ran.
     *
     * Scroll vertically only. A left-to-right swipe is Chrome's back-navigation gesture, so a
     * horizontal poll would walk the SPA backwards and discard the very session it is waiting on.
     */
    private fun awaitInResultLog(device: UiDevice, selector: BySelector): UiObject2? {
        val deadline = SystemClock.uptimeMillis() + CREDENTIAL_OPERATION_TIMEOUT
        while (SystemClock.uptimeMillis() < deadline) {
            device.findObject(selector)?.let { return it }
            device.swipe(
                device.displayWidth / 2, device.displayHeight * 3 / 4,
                device.displayWidth / 2, device.displayHeight / 4,
                12,
            )
            device.waitForIdle(PANEL_SETTLE_TIMEOUT)
        }
        return null
    }

    /**
     * Reads the session id out of the portal's result card, scrolling back up to find it.
     *
     * The card ("Session `<id>` is running through the browser Digital Credentials API") sits *above*
     * the result log, so waiting for the success banner leaves it scrolled off the top - and Chrome
     * publishes accessibility nodes only for content in the viewport.
     */
    private fun readSessionId(device: UiDevice): String {
        repeat(MAX_LIST_SCROLLS) {
            SESSION_ID_PATTERN.find(pageText(device))?.let { return it.value }
            device.swipe(
                device.displayWidth / 2, device.displayHeight / 4,
                device.displayWidth / 2, device.displayHeight * 3 / 4,
                12,
            )
            device.waitForIdle(PANEL_SETTLE_TIMEOUT)
        }
        error("Could not read a session id from the portal:\n${pageText(device)}")
    }

    /** Every visible string on the page, for reading the session id and for failure messages. */
    private fun pageText(device: UiDevice): String =
        device.findObjects(By.pkg(CHROME_PACKAGE))
            .mapNotNull { runCatching { it.text }.getOrNull() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .ifEmpty { "(no text found on the page)" }

    /**
     * Chrome gates `navigator.credentials.get()` behind its own "Do you trust this site with your
     * data?" dialog before it will call Credential Manager. A native caller has no equivalent, so
     * this step exists only on the browser path.
     */
    private fun acceptSiteTrustPrompt(device: UiDevice) {
        val trustPrompt = device.wait(
            Until.findObject(By.res("$CHROME_PACKAGE:id/positive_button")),
            UI_ELEMENT_TIMEOUT,
        )
        assertNotNull("Chrome did not ask whether to trust the site", trustPrompt)
        trustPrompt!!.click()
    }

    private fun driveCredentialManagerPicker(device: UiDevice) {
        val candidate = device.wait(
            Until.findObject(By.text("org.iso.18013.5.1.mDL")),
            UI_ELEMENT_TIMEOUT,
        )
        assertNotNull("Credential Manager did not surface the mDL candidate", candidate)
        val continueButton = device.wait(
            Until.findObject(By.res("continue_button")),
            UI_ELEMENT_TIMEOUT,
        ) ?: device.wait(Until.findObject(By.text("Continue")), UI_ELEMENT_TIMEOUT)
        assertNotNull("Credential Manager did not offer consent", continueButton)
        continueButton!!.click()

        val shareButton = device.wait(
            Until.findObject(By.res("android:id/button1")),
            UI_ELEMENT_TIMEOUT,
        ) ?: device.wait(Until.findObject(By.text("Share")), UI_ELEMENT_TIMEOUT)
            ?: device.wait(Until.findObject(By.text("SHARE")), UI_ELEMENT_TIMEOUT)
        assertNotNull("Wallet provider consent did not open", shareButton)
        shareButton!!.click()
    }

    /**
     * `vp_policies` is narrowed rather than left at the example's default. The default set includes
     * `mso_mdoc/issuer_auth`, which since crypto2 (`d10b40986`) enforces ISO 18013-5 document-signer
     * certificate usage via `validateDocumentSigningCertificateUsage`: `keyUsage:digitalSignature`
     * plus the mDL DS EKU `1.0.18013.5.1.2`. No profile on the demo or portal issuers has a DS
     * certificate carrying that EKU, so `issuer_auth` cannot pass against any credential this test
     * can obtain - a deployment gap unrelated to DC API. What this test is for is browser origin
     * handling through the portal, and `mso_mdoc/device-auth` is the policy that proves it.
     * [DigitalCredentialSharingE2ETest] keeps the full default policy set, so issuer authentication
     * stays covered there.
     */
    private fun createPayload(expectedOrigin: String): String =
        """
        {"flow_type":"$FLOW_TYPE","core_flow":{"dcql_query":{"credentials":[{"id":"my_mdl",
        "format":"mso_mdoc","meta":{"doctype_value":"org.iso.18013.5.1.mDL"},"claims":[
        {"path":["org.iso.18013.5.1","family_name"]},{"path":["org.iso.18013.5.1","given_name"]}]}]},
        "signed_request":false,"encrypted_response":false,
        "policies":{"vp_policies":[{"policy":"mso_mdoc/device-auth"},
        {"policy":"mso_mdoc/device_key_auth"},{"policy":"mso_mdoc/issuer_signed_integrity"},
        {"policy":"mso_mdoc/mso"}]}},
        "expectedOrigins":["$expectedOrigin"]}
        """.trimIndent().replace("\n", "")

    private suspend fun portalSessionInfo(sessionId: String): JsonObject {
        HttpClient().use { client ->
            val response = client.get("$PORTAL_VERIFIER_BASE/verification-session/$sessionId/info") {
                accept(ContentType.Application.Json)
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error("HTTP ${response.status.value} from portal verifier2 session info for $sessionId: $body")
            }
            return json.parseToJsonElement(body).jsonObject
        }
    }

    /** Collects every `success == false` leaf so a failure names the policy, not just `false`. */
    private fun JsonElement.failedPolicies(): List<String> = buildList {
        fun walk(element: JsonElement, path: String) {
            when (element) {
                is JsonObject -> {
                    val id = element["policy_executed"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                    if (id != null && element["success"]?.jsonPrimitive?.booleanOrNull == false) {
                        add("$path/$id: ${element["errors"]}")
                    }
                    element.forEach { (key, value) -> walk(value, "$path/$key") }
                }

                is JsonArray -> element.forEachIndexed { index, value -> walk(value, "$path[$index]") }
                else -> Unit
            }
        }
        walk(this@failedPolicies, "")
    }

    private fun JsonElement.executedPolicyIds(): Set<String> = buildSet {
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    element["policy_executed"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                        ?.let { add(it) }
                    element.values.forEach(::walk)
                }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this@executedPolicyIds)
    }

    private fun hasPackage(context: android.content.Context, packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    private companion object {
        const val CHROME_PACKAGE = "com.android.chrome"
        const val PORTAL_URL = "https://portal2.test.waltid.cloud/"
        const val PORTAL_ORIGIN = "https://portal2.test.waltid.cloud"
        const val PORTAL_VERIFIER_BASE = "https://verifier2.portal.test.waltid.cloud"

        /** This deployment spells the discriminator `dc_api_openid4vp`, not the branch's `dc_api`. */
        const val FLOW_TYPE = "dc_api_openid4vp"
        const val DC_API_EXAMPLE_PREFIX = "[openid4vp-dc_api][iso mdl] unsigned"
        const val SUBMIT_LABEL = "Create Verification Session"
        const val SUCCESS_BANNER = "Completed successfully"
        const val DESKTOP_SITE_OFF_DESC = "Turn on Request desktop site"
        const val MAX_LIST_SCROLLS = 12
        const val MAX_TAB_ATTEMPTS = 4
        const val MAX_KEYBOARD_DISMISS_ATTEMPTS = 3
        const val PANEL_SETTLE_TIMEOUT = 2_000L

        val SESSION_ID_PATTERN = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
        val json = Json { ignoreUnknownKeys = true }
    }
}
