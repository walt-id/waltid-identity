package id.walt.walletdemo.compose.e2e

import android.os.Build
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Condition
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.VERIFIER_POLLING_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.WALLET_READY_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.foregroundWindowSnapshot
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.latestStatus
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.sendDeepLink
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class MobileWalletRestartTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val appId = requireNotNull(InstrumentationRegistry.getArguments().getString("targetAppId"))

    @Test
    fun presentsSavedCredentialAfterProcessRestart() = runBlocking {
        val context = instrumentation.context.createPackageContext(appId, 0)
        val runnerPid = Process.myPid()
        // Each invocation owns a fresh wallet; the restart below preserves its data.
        assertEquals("Success", device.executeShellCommand("pm clear $appId").trim())
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "eudi-pid-mdoc" }
        val offer = DemoTestBackend.createOffer(scenario)
        launchAndUnlock(context, device)
        sendDeepLink(context, offer.offerUrl)
        assertReview("wallet.offerAcceptButton")
        clickByTag(device, "wallet.offerAcceptButton")
        assertReview("issuance-done")
        clickByTag(device, "issuance-done")
        val savedCredentialIds = credentialIds()
        val savedIdentity = signingIdentity()

        val walletPid = device.executeShellCommand("pidof $appId").trim()
        assertTrue("Wallet process must be running", walletPid.isNotEmpty())
        assertNotEquals("Test runner must be separate from the wallet", runnerPid.toString(), walletPid)
        device.executeShellCommand("am force-stop $appId")
        assertEquals("Wallet process must terminate", "", device.executeShellCommand("pidof $appId").trim())
        assertEquals("Test runner must survive the restart", runnerPid, Process.myPid())
        launchAndUnlock(context, device, initializeSigningIdentity = false)
        expectStatus("Wallet ready", WALLET_READY_TIMEOUT)
        assertNotEquals(walletPid, device.executeShellCommand("pidof $appId").trim())
        assertFalse("Restart must reuse the saved identity", device.hasObject(By.res("wallet.keySetupContinue")))
        assertEquals(savedCredentialIds, credentialIds())

        val session = DemoTestBackend.createVerifierSession(
            scenario, signedRequest = true, clientId = DemoTestBackend.PUBLIC_DEMO_DID_VERIFIER_CLIENT_ID,
        )
        sendDeepLink(context, session.authorizationRequestUri)
        assertReview("wallet.presentationSubmitButton")
        clickByTag(device, "wallet.presentationSubmitButton")
        expectStatus("Presentation")
        DemoTestBackend.waitForVerifierSuccess(session.sessionId, timeoutMs = VERIFIER_POLLING_TIMEOUT)
        assertEquals(savedCredentialIds, credentialIds())
        assertEquals(savedIdentity, signingIdentity())
    }

    private fun assertReview(action: String) {
        assertTrue("Expected $action in the automatically opened review",
            waitForResource(device, action, CREDENTIAL_OPERATION_TIMEOUT) != null)
    }

    private fun expectStatus(prefix: String, timeoutMs: Long = CREDENTIAL_OPERATION_TIMEOUT) {
        val matched = waitForStatus(
            device, timeoutMs, { it.startsWith(prefix) },
            listOf("Receive failed", "Preview failed", "Present failed", "Bootstrap failed"),
        )
        assertTrue("Expected $prefix, got ${latestStatus(device)}", matched)
    }

    private fun credentialIds(): Set<String> {
        if (device.hasObject(By.res("wallet.external.close"))) clickByTag(device, "wallet.external.close")
        assertReview("wallet.scanButton")
        val cards = By.res(Pattern.compile("wallet\\.credentialCard\\..*"))
        val ids = device.wait(Condition<UiDevice, Set<String>?> { currentDevice ->
            try {
                // A cached tree can omit cards composed after the new process becomes ready.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    instrumentation.uiAutomation.clearCache()
                }
                currentDevice.findObjects(cards).map { it.resourceName }.toSet().takeIf { it.isNotEmpty() }
            } catch (_: StaleObjectException) {
                // Reacquire every card if Compose replaces a node while reading the snapshot.
                null
            }
        }, UI_ELEMENT_TIMEOUT)
        assertTrue(
            "Saved credential must remain visible" +
                if (ids == null) "\n${foregroundWindowSnapshot(device)}" else "",
            ids != null,
        )
        return requireNotNull(ids).also {
            assertEquals("The fresh wallet must contain the one issued credential", 1, it.size)
        }
    }

    private fun signingIdentity(): Pair<String, String> {
        clickByTag(device, "wallet.settingsButton")
        clickByTag(device, "wallet.settingsTechnicalDetails")
        val did = requireNotNull(waitForResource(device, "wallet.settingsDid", UI_ELEMENT_TIMEOUT)?.text)
        val keyId = requireNotNull(waitForResource(device, "wallet.settingsKeyId", UI_ELEMENT_TIMEOUT)?.text)
        assertTrue("Wallet DID must be available", did.startsWith("did:"))
        assertTrue("Wallet key ID must be available", keyId.isNotBlank() && keyId != "Unavailable")
        clickByTag(device, "wallet.settingsBack")
        assertReview("wallet.settingsSigningKey")
        clickByTag(device, "wallet.settingsBack")
        assertReview("wallet.scanButton")
        return did to keyId
    }
}
