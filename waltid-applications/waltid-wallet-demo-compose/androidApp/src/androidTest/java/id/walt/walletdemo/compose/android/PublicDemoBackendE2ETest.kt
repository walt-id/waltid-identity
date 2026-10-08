package id.walt.walletdemo.compose.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.activeWindow
import androidx.test.uiautomator.waitForStable
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.VERIFIER_POLLING_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertClaimValueVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertTextVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.latestStatus
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.sendDeepLink
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.setTextByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class PublicDemoBackendE2ETest {

    @Test
    fun transactionCodePromptRejectsWrongCodeAndRetriesAgainstPublicDemoIssuer2() = runBlocking {
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "eudi-pid-mdoc" }
        val offer = DemoTestBackend.createOffer(scenario, withGeneratedTransactionCode = true)
        val transactionCode = requireNotNull(offer.txCode) {
            "Public demo issuer2 did not return a transaction code"
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        launchAndUnlock(context, device)
        sendDeepLink(context, offer.offerUrl)
        assertReview(device, "wallet.offerAcceptButton")

        setTextByTag(device, "wallet.txCodeInput", incorrectCodeFor(transactionCode))
        clickByTag(device, "wallet.offerAcceptButton")
        assertTrue(
            "Incorrect transaction code was not rejected. Latest status: ${latestStatus(device)}",
            waitForStatus(
                device = device,
                timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
                matcher = { it.startsWith("Receive failed") },
                failurePrefixes = emptyList(),
            ),
        )

        // The reviewed offer remains active so the corrected code can be retried directly.
        setTextByTag(device, "wallet.txCodeInput", transactionCode)
        clickByTag(device, "wallet.offerAcceptButton")
        assertReview(device, "issuance-done")
    }

    @Test
    fun receiveAndPresentAgainstPublicDemoIssuer2Verifier2() = runBlocking {
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "eudi-pid-mdoc" }
        val offer = DemoTestBackend.createOffer(scenario)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        launchAndUnlock(context, device)

        sendDeepLink(context, offer.offerUrl)
        assertReview(device, "wallet.offerAcceptButton")
        val offeredInformation = requireNotNull(device.findObject(By.res(Pattern.compile("issuance-details-.*"))))
        clickByTag(device, offeredInformation.resourceName)
        assertReview(device, "issuance-credential-details")
        assertEquals("One information header must own the offer details", 1,
            device.findObjects(By.text("Credential information")).size)
        assertFalse("Offer actions must not remain under details", device.hasObject(By.res("wallet.offerAcceptButton")))
        captureHost(device, "offer-information")
        clickByTag(device, "wallet-detail-back")
        assertReview(device, "wallet.offerAcceptButton")
        clickByTag(device, "wallet.offerAcceptButton")
        assertReview(device, "issuance-done")
        assertTrue("No credentials were shown in UI", device.findObject(By.text("No credentials")) == null)

        clickByTag(device, "issuance-done")
        assertHome(device)
        val session = DemoTestBackend.createVerifierSession(scenario)
        sendDeepLink(context, session.authorizationRequestUri)
        assertReview(device, "wallet.presentationSubmitButton")
        assertTextVisibleAfterScrolling(device, listOf("Information to share"), "Shared information section missing")
        captureHost(device, "requested-information")
        val information = requireNotNull(device.findObject(By.res(Pattern.compile("wallet\\.presentationClaimsToggle\\..*"))))
        clickByTag(device, information.resourceName)
        assertReview(device, "wallet.presentationClaimsDialog")
        assertEquals(1, device.findObjects(By.text("Credential information")).size)
        assertFalse("Share actions must not remain under details", device.hasObject(By.res("wallet.presentationSubmitButton")))
        captureHost(device, "all-information")
        clickByTag(device, "wallet-detail-back")
        assertReview(device, "wallet.presentationSubmitButton")

        clickByTag(device, "wallet.presentationSubmitButton")
        val presentSuccess = waitForStatus(
            device = device,
            timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
            matcher = { it.startsWith("Presentation sent") || it.startsWith("Presentation finished") },
            failurePrefixes = listOf("Present failed", "Receive failed", "Bootstrap failed")
        )
        assertTrue("Presentation did not complete in app. Latest status: ${latestStatus(device)}", presentSuccess)

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

        DemoTestBackend.waitForVerifierSuccess(session.sessionId, timeoutMs = VERIFIER_POLLING_TIMEOUT)
        assertReview(device, "wallet.presentationDone")
        assertFalse(device.hasObject(By.res("wallet.presentationInput")))
        captureHost(device, "sharing-result")
        clickByTag(device, "wallet.presentationDone")
        assertHome(device)
    }

    @Test
    fun transactionDataPreviewAgainstPublicDemoIssuer2Verifier2() = runBlocking {
        val scenario = DemoTestBackend.transactionDataPresentationScenario
        val offer = DemoTestBackend.createOffer(scenario)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        launchAndUnlock(context, device)

        sendDeepLink(context, offer.offerUrl)
        assertReview(device, "wallet.offerAcceptButton")
        clickByTag(device, "wallet.offerAcceptButton")
        assertReview(device, "issuance-done")

        clickByTag(device, "issuance-done")
        val session = DemoTestBackend.createTransactionDataVerifierSession(scenario)
        sendDeepLink(context, session.authorizationRequestUri)
        assertReview(device, "wallet.presentationSubmitButton")

        val stableWindow = device.activeWindow().waitForStable(stableTimeoutMs = UI_ELEMENT_TIMEOUT)
        assertTrue("Transaction-data review did not stabilize before capture", !stableWindow.isTimeout)
        stableWindow.screenshot?.recycle()
        val screenshot = File("/sdcard/Download/wal1077-compose-android-transaction-data.png")
        if (device.takeScreenshot(screenshot)) {
            println("WAL1077_SCREENSHOT=${screenshot.absolutePath}")
        } else {
            println("WAL1077_SCREENSHOT_CAPTURE_FAILED=${screenshot.absolutePath}")
        }

        assertTextVisibleAfterScrolling(
            device,
            listOf("PAYMENT AUTHORIZATION", "Payment Authorization"),
            "Payment profile title missing",
        )
        assertClaimValueVisibleAfterScrolling(
            device = device,
            path = "transactionData[0].details.amount",
            label = "Amount",
            expectedValues = listOf("42.00"),
            message = "Payment amount missing",
        )
        assertClaimValueVisibleAfterScrolling(
            device = device,
            path = "transactionData[0].details.currency",
            label = "Currency",
            expectedValues = listOf("EUR"),
            message = "Payment currency missing",
        )
        assertClaimValueVisibleAfterScrolling(
            device = device,
            path = "transactionData[0].details.merchant_name",
            label = "Merchant name",
            expectedValues = listOf("ACME Corp"),
            message = "Payment merchant name missing",
        )
    }

    private fun assertReview(device: UiDevice, tag: String) {
        assertNotNull("Wallet surface $tag did not appear. Latest status: ${latestStatus(device)}",
            waitForResource(device, tag, CREDENTIAL_OPERATION_TIMEOUT))
    }

    private fun assertHome(device: UiDevice) {
        assertReview(device, "wallet.scanButton")
        assertFalse("Completion must return Home, not the legacy request form",
            device.hasObject(By.res("wallet.presentationInput")))
        assertFalse(device.hasObject(By.res("wallet.presentationSubmitButton")))
    }

    private fun captureHost(device: UiDevice, name: String) {
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val directory = File(output, "wallet-refinement-host").apply { mkdirs() }
        assertTrue("Could not capture $name", device.takeScreenshot(File(directory, "$name.png")))
    }

    private fun incorrectCodeFor(code: String): String {
        require(code.isNotEmpty()) { "Transaction code must not be empty" }
        val replacement = if (code.last() == '0') '1' else '0'
        return code.dropLast(1) + replacement
    }
}
