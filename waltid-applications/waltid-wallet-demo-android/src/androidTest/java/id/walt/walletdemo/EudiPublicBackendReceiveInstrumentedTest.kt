package id.walt.walletdemo

import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import id.walt.mobile.test.backend.EudiTestBackend
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EudiPublicBackendReceiveInstrumentedTest {

    companion object {
        private const val DEFAULT_CREDENTIAL_ID = "eu.europa.ec.eudi.pid_vc_sd_jwt"

        private const val WALLET_READY_TIMEOUT = 60_000L          // 1 min - wallet bootstrap
        private const val UI_ELEMENT_TIMEOUT = 30_000L            // 30 sec - buttons, UI elements
        private const val CREDENTIAL_OPERATION_TIMEOUT = 90_000L  // 1.5 min - receive/present
        private const val VERIFIER_POLLING_TIMEOUT = 30_000L      // 30 sec - backend verification
        private const val QUICK_STATUS_CHECK_TIMEOUT = 5_000L     // 5 sec - instant checks
        private const val POST_PRESENT_DELAY = 5_000L             // 5 sec - stabilization wait
    }

    @Test
    fun receiveAndPresentAgainstEudiPublicBackends() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val requestedCredentialId = args.getString("e2e_credential_id") ?: DEFAULT_CREDENTIAL_ID
        val offerUrlArg = args.getString("e2e_offer_url")
            ?: args.getString("e2e_offer_url_b64")?.let(::decodeBase64)
        val generatedOffer = if (offerUrlArg == null) EudiTestBackend.generateOffer(requestedCredentialId) else null
        val offerUrl = offerUrlArg ?: generatedOffer!!.offerUrl

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) }
            ?: error("Cannot resolve launch intent for ${context.packageName}")
        context.startActivity(launchIntent)

        assertTrue(
            "Wallet did not become ready",
            waitForStatus(
                device = device,
                timeoutMs = WALLET_READY_TIMEOUT,
                matcher = { it == "Wallet ready" },
                failurePrefixes = listOf("Bootstrap failed")
            )
        )

        val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(offerUrl)).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(deepLinkIntent)
        assertTrue(
            "Offer URL did not appear in UI after deep link",
            waitForOfferUrlInUi(device, timeoutMs = UI_ELEMENT_TIMEOUT)
        )

        // Prefer clickable button closest to bottom to avoid selecting the section header text.
        val receiveButton = waitForClickableButton(device, "Receive", timeoutMs = UI_ELEMENT_TIMEOUT)
        assertNotNull("Receive button not found", receiveButton)
        val bounds = receiveButton!!.visibleBounds
        device.click(bounds.centerX(), bounds.centerY())

        val receiveSuccess = waitForStatus(
            device = device,
            timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
            matcher = { it.startsWith("Received") },
            failurePrefixes = listOf("Receive failed", "Bootstrap failed", "Present failed")
        )

        assertTrue("Receive did not complete successfully. Latest status: ${latestStatus(device)}", receiveSuccess)
        assertTrue("No credentials were shown in UI", device.findObject(By.text("No credentials")) == null)

        val credentialId = EudiTestBackend.extractCredentialIdFromOfferUrl(offerUrl)
        val verifierTx = EudiTestBackend.createVerifierTransaction(credentialId)

        val presentationDeepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(verifierTx.authorizationRequestUri)).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(presentationDeepLinkIntent)

        // Some builds begin presentation immediately after the request URL is set.
        val startedWithoutTap = waitForStatus(
            device = device,
            timeoutMs = QUICK_STATUS_CHECK_TIMEOUT,
            matcher = {
                it.startsWith("Presenting credential") ||
                    it.startsWith("Presentation sent") ||
                    it.startsWith("Presentation finished")
            },
            failurePrefixes = listOf("Present failed", "Receive failed", "Bootstrap failed")
        )
        if (!startedWithoutTap) {
            val presentButton = waitForClickableButton(device, "Present", timeoutMs = UI_ELEMENT_TIMEOUT)
            assertNotNull("Present button not found", presentButton)
            val presentBounds = presentButton!!.visibleBounds
            device.click(presentBounds.centerX(), presentBounds.centerY())
        }

        Thread.sleep(POST_PRESENT_DELAY)
        val statusAfterPresent = latestStatus(device)
        assertTrue(
            "Presentation failed in app. Latest status: $statusAfterPresent",
            !statusAfterPresent.startsWith("Present failed") &&
                !statusAfterPresent.startsWith("Receive failed") &&
                !statusAfterPresent.startsWith("Bootstrap failed")
        )
        assertTrue(
            "Wallet app is no longer in foreground after presentation flow",
            device.currentPackageName == context.packageName
        )

        EudiTestBackend.waitForVerifierSuccess(verifierTx.transactionId, timeoutMs = VERIFIER_POLLING_TIMEOUT)
    }

    private fun waitForClickableButton(device: UiDevice, text: String, timeoutMs: Long): UiObject2? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val candidates = device.findObjects(By.text(text))
            val preferredCandidate = candidates.maxByOrNull { it.visibleBounds.bottom }
            if (preferredCandidate != null) {
                val clickable = preferredCandidate.clickableAncestorOrSelf()
                if (clickable != null) {
                    return clickable
                }
                if (text != "Present") {
                    return preferredCandidate
                }
            }
            if (text == "Present") {
                device.swipe(540, 2000, 540, 900, 24)
            }
            Thread.sleep(500)
        }
        return null
    }

    private fun waitForOfferUrlInUi(device: UiDevice, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val offerNode = device.findObject(By.textStartsWith("openid-credential-offer://"))
            if (offerNode != null) return true
            Thread.sleep(500)
        }
        return false
    }

    private fun UiObject2.clickableAncestorOrSelf(): UiObject2? {
        var node: UiObject2? = this
        while (node != null) {
            if (node.isClickable) return node
            node = node.parent
        }
        return null
    }

    private fun latestStatus(device: UiDevice): String {
        val prefixes = listOf(
            "Wallet ready",
            "Starting wallet",
            "Bootstrapping wallet",
            "Receiving credential",
            "Received",
            "Receive failed",
            "Bootstrap failed",
            "Presenting credential",
            "Presentation sent",
            "Presentation finished",
            "Present failed",
        )
        for (prefix in prefixes) {
            val obj = device.findObject(By.textStartsWith(prefix))
            if (obj != null) return obj.text
        }
        return "UNKNOWN"
    }

    private fun waitForStatus(
        device: UiDevice,
        timeoutMs: Long,
        matcher: (String) -> Boolean,
        failurePrefixes: List<String>
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val status = latestStatus(device)
            if (matcher(status)) return true
            if (failurePrefixes.any { status.startsWith(it) }) return false
            Thread.sleep(500)
        }
        return false
    }

    private fun decodeBase64(value: String): String =
        String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
}
