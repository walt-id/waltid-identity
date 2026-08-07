package id.walt.walletdemo.compose.android

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
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
import kotlinx.serialization.json.JsonPrimitive
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

/**
 * Browser-mediated Digital Credentials sharing E2E.
 *
 * Complements [DigitalCredentialSharingE2ETest], which drives a native verifier Activity. That
 * covers only one of the two origin flavours Credential Manager can assert: a native caller has no
 * populated origin, so [id.walt.wallet2.mobile.AndroidDigitalCredentialProvider] falls through to
 * `android:apk-key-hash:...` and never exercises the privileged-browser branch. Here Chrome is the
 * caller, so `getOrigin(privilegedAppsJson)` returns a populated web origin, the allowlist check in
 * `privileged_apps.json` has to pass, and `canonicalWebOrigin()` normalizes it. Both flavours feed
 * the same mdoc session transcript hash; this is the web one.
 *
 * Consequences of using the already-hosted page rather than a local harness:
 * - The session is created by the page against a *deployed* verifier, so this asserts wallet-side
 *   behaviour only. Verifier changes on this branch are not covered here.
 * - Chrome must be allowlisted in `privileged_apps.json` and past its first-run screens.
 *
 * ## Why this is @Ignore'd
 *
 * It cannot pass today, and the reason is not in the wallet. The whole browser flow works: Chrome
 * calls Credential Manager, the wallet asserts the web origin, and the page posts the response. The
 * verifier then rejects it with
 * `Failed VP policies: mdl/mso_mdoc/issuer_auth`, because `issuer2.demo.walt.id` signs its mDLs with
 * an X.509 **v1** document-signer certificate carrying no `keyUsage` and no EKU `1.0.18013.5.1.2` -
 * see `DeployedIssuerDocumentSignerTest`, which pins that certificate.
 *
 * `verifier2.portal.test.waltid.cloud` enforces the ISO 18013-5 document-signer profile;
 * `verifier2.demo.walt.id` `0.23.0`, which [DigitalCredentialSharingE2ETest] uses, does not.
 * `DcApiDeploymentComparisonE2ETest` isolates that by holding the wallet, issuer and credential
 * fixed and varying only the deployment. So this test is blocked on the issuer reissuing a
 * conformant document signer, not on any wallet change - and it should not be made to pass by
 * narrowing `vp_policies` to exclude `issuer_auth`, which is what an earlier version of it did.
 */
@Ignore(
    "Blocked on issuer2.demo.walt.id issuing an X.509 v1 document signer without the ISO 18013-5 " +
        "usage extensions, which this deployment correctly rejects - see the class KDoc.",
)
@RunWith(AndroidJUnit4::class)
class DigitalCredentialBrowserSharingE2ETest {

    @Test
    fun receivesMdlAndSharesItThroughChromeDigitalCredentialsApi() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        assumeTrue(
            "Digital Credentials E2E requires an Android emulator with Google Play services",
            hasPackage(context, "com.google.android.gms"),
        )
        assumeTrue(
            "Browser DC API E2E requires Chrome, which must also be in privileged_apps.json",
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

        openTestPage(context, device)

        // The page's own payload - sourced from the verifier's OpenAPI examples - is used as-is
        // except for one field. Its expectedOrigins names https://portal2.demo.walt.id, which is not
        // where this page is served from, so the wallet and the verifier would hash different
        // origins into the mdoc session transcript and device auth would fail.
        //
        // Only that field is rewritten, deliberately. An earlier version of this test replaced the
        // whole payload with a hand-written one, which meant the test no longer exercised the
        // request the deployment actually publishes and could not notice it changing.
        val payloadField = device.wait(Until.findObject(By.res("payload-input")), UI_ELEMENT_TIMEOUT)
        assertNotNull("DC API test page has no payload field", payloadField)
        payloadField!!.text = withExpectedOrigin(payloadField.text, PAGE_ORIGIN)

        scrollToControls(device)
        val callButton = device.wait(Until.findObject(By.res("call-dc-api")), UI_ELEMENT_TIMEOUT)
        assertNotNull("DC API test page has no call button", callButton)
        callButton!!.click()

        acceptSiteTrustPrompt(device)
        driveCredentialManagerPicker(device)

        // The page posts the response itself and then polls, so its own status is the verifier's
        // verdict relayed back. Assert on that first for a readable failure.
        scrollToControls(device)
        val completed = device.wait(
            Until.findObject(By.res("status").text("Completed successfully.")),
            CREDENTIAL_OPERATION_TIMEOUT,
        )
        val log = device.findObject(By.res("log-field"))?.text.orEmpty()
        assertNotNull("DC API page did not report success. Log:\n$log", completed)

        // Then confirm against the verifier directly, so a page that mislabels its own outcome
        // cannot turn this green.
        val sessionId = SESSION_ID_PATTERN.find(log)?.groupValues?.get(1)
            ?: error("Could not read the session id from the page log:\n$log")
        val info = portalSessionInfo(sessionId)
        assertEquals("Verifier did not accept the presentation: $info", "SUCCESSFUL", info["status"]?.jsonPrimitive?.content)

        val policyResults = info["policy_results"]
            ?: error("Session info has no policy_results: $info")
        // device-auth is the transcript check: it only passes if the wallet hashed the canonical web
        // origin Chrome asserted and the verifier hashed the same string.
        val executed = policyResults.executedPolicyIds()
        listOf("mso_mdoc/device-auth", "mso_mdoc/issuer_auth").forEach { policyId ->
            assertTrue("$policyId did not run. Executed: $executed", executed.contains(policyId))
        }
        assertTrue(
            "Failed policies: ${policyResults.failedPolicies()}",
            policyResults.failedPolicies().isEmpty(),
        )
    }

    private fun openTestPage(context: android.content.Context, device: UiDevice) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(PAGE_URL))
                .setPackage(CHROME_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        // Wait on the payload field rather than #status: Chrome exposes accessibility nodes only for
        // content in the viewport, and #status sits below the fold on a phone. Examples are fetched
        // from the verifier's OpenAPI document, so a populated payload is also a network wait.
        val loaded = device.wait(
            Until.findObject(By.res("payload-input")),
            CREDENTIAL_OPERATION_TIMEOUT,
        )
        assertNotNull(
            "DC API test page did not load. Chrome may be showing a first-run screen.",
            loaded,
        )
        val ready = device.wait(
            Until.findObject(By.res("example-select").textContains("dc_api")),
            CREDENTIAL_OPERATION_TIMEOUT,
        )
        assertNotNull("DC API test page did not load its Swagger examples", ready)
    }

    /**
     * Brings below-the-fold elements into the viewport. Chrome only publishes accessibility nodes
     * for visible content, so `#status`, `#call-dc-api` and `#log-field` do not exist in the tree
     * until scrolled to - matching them without this simply times out.
     */
    private fun scrollToControls(device: UiDevice) {
        repeat(6) {
            if (device.findObject(By.res("call-dc-api")) != null) return
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 12)
            device.waitForIdle(SCROLL_SETTLE_TIMEOUT)
        }
    }

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
     * Replaces `expectedOrigins` in the page's payload, leaving everything else untouched.
     *
     * Rewriting rather than regenerating matters: the payload's shape is the deployment's, not ours
     * (this deployment spells the flow `dc_api_openid4vp` with `core_flow`, where the verifier on
     * this branch serializes `dc_api` with `core`), and it carries no `vp_policies`, so the verifier
     * applies its full default mdoc set - including `mso_mdoc/issuer_auth`. Keeping that default is
     * the point: narrowing the policy list is what previously hid the non-conformant document-signer
     * certificate this deployment rejects (see the class KDoc).
     */
    private fun withExpectedOrigin(payload: String, expectedOrigin: String): String {
        val parsed = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrElse {
            error("DC API test page payload is not JSON, cannot retarget its origin:\n$payload")
        }
        require(parsed.containsKey("expectedOrigins")) {
            "DC API test page payload has no expectedOrigins to retarget:\n$payload"
        }
        return JsonObject(
            parsed + ("expectedOrigins" to JsonArray(listOf(JsonPrimitive(expectedOrigin)))),
        ).toString()
    }

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
        const val SCROLL_SETTLE_TIMEOUT = 3_000L
        const val PAGE_URL = "https://digital-credentials.walt.id/"
        const val PAGE_ORIGIN = "https://digital-credentials.walt.id"

        /** The page's own hardcoded "open-source" preset; it offers no way to point it elsewhere. */
        const val PORTAL_VERIFIER_BASE = "https://verifier2.portal.test.waltid.cloud"

        val SESSION_ID_PATTERN = Regex("""Session created: ([0-9a-fA-F-]{36})""")
        val json = Json { ignoreUnknownKeys = true }
    }
}
