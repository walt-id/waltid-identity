package id.walt.walletdemo.compose.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.VERIFIER_POLLING_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertClaimValueVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertResourceVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertTextContainingVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertTextVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.foregroundWindowSnapshot
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.latestStatus
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.scrollUp
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.setTextByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicDemoBackendE2ETest {

    @Test
    fun authorizationCodeOfferReviewAgainstPublicDemoIssuer2() = runBlocking {
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "eudi-pid-mdoc" }
        val offer = DemoTestBackend.createOffer(
            scenario = scenario,
            authorizationMethod = DemoTestBackend.OfferAuthorizationMethod.Authorized,
        )
        WalletGalleryCapture.recordRequest("issuance-authorization-code", offer.offerUrl)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        launchAndUnlock(context, device)
        clickByTag(device, "wallet.scanEmptyAction")
        setTextByTag(device, "wallet.scanInput", offer.offerUrl)
        clickByTag(device, "wallet.scanSubmit")
        val previewVisible = device.wait(
            Until.hasObject(By.res("wallet.offerReview")),
            CREDENTIAL_OPERATION_TIMEOUT,
        )
        assertTrue(
            "Authorization-code offer preview did not appear.\n${foregroundWindowSnapshot(device)}",
            previewVisible,
        )
        assertResourceVisibleAfterScrolling(
            device,
            "wallet.reviewIslandToggle.required_action",
            "Issuer sign-in action missing",
        )
        WalletGalleryCapture.capture(device, "wallet-issuance-authorization-code-review")
    }

    @Test
    fun transactionCodePromptRejectsWrongCodeAndRetriesAgainstPublicDemoIssuer2() = runBlocking {
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "eudi-pid-mdoc" }
        val offer = DemoTestBackend.createOffer(scenario, withGeneratedTransactionCode = true)
        WalletGalleryCapture.recordRequest("issuance-transaction-code", offer.offerUrl)
        val transactionCode = requireNotNull(offer.txCode) {
            "Public demo issuer2 did not return a transaction code"
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        launchAndUnlock(context, device)
        submitThroughUnifiedScan(device, "wallet.scanEmptyAction", offer.offerUrl)
        val previewVisible = device.wait(
            Until.hasObject(By.res("wallet.offerReview")),
            CREDENTIAL_OPERATION_TIMEOUT,
        )
        assertTrue(
            "Offer preview did not appear.\n${foregroundWindowSnapshot(device)}",
            previewVisible,
        )
        WalletGalleryCapture.capture(device, "wallet-issuance-transaction-code-review")

        setTextByTag(device, "wallet.txCodeInput", incorrectCodeFor(transactionCode))
        clickByTag(device, "wallet.offerAcceptButton")
        assertTrue(
            "Incorrect transaction code was not reported as rejected.\n" +
                foregroundWindowSnapshot(device),
            waitForReceiveFailureAfterScrolling(device),
        )
        assertTextContainingVisibleAfterScrolling(
            device = device,
            substring = "Receive failed",
            message = "Incorrect transaction code was not reported as rejected",
        )

        // The reviewed offer remains active so the corrected code can be retried directly.
        setTextByTag(device, "wallet.txCodeInput", transactionCode)
        clickByTag(device, "wallet.offerAcceptButton")
        assertTrue(
            "Receive did not succeed after correcting the transaction code. Latest status: ${latestStatus(device)}",
            waitForStatus(
                device = device,
                timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
                matcher = { it.startsWith("Received") },
                failurePrefixes = listOf("Receive failed", "Bootstrap failed"),
            ),
        )
    }

    @Test
    fun receiveAndPresentAgainstPublicDemoIssuer2Verifier2() = runBlocking {
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "eudi-pid-mdoc" }
        val offer = DemoTestBackend.createOffer(scenario)
        WalletGalleryCapture.recordRequest("issuance", offer.offerUrl)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        launchAndUnlock(context, device)

        submitThroughUnifiedScan(device, "wallet.scanEmptyAction", offer.offerUrl)
        val offerPreviewReady = device.wait(
            Until.hasObject(By.res("wallet.offerReview")),
            CREDENTIAL_OPERATION_TIMEOUT,
        )
        assertTrue(
            "Offer preview did not appear.\n${foregroundWindowSnapshot(device)}",
            offerPreviewReady,
        )
        WalletGalleryCapture.capture(device, "wallet-issuance-${scenario.id}-review")
        clickByTag(device, "wallet.offerAcceptButton")
        val receiveSuccess = waitForStatus(
            device = device,
            timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
            matcher = { it.startsWith("Received") },
            failurePrefixes = listOf("Receive failed", "Bootstrap failed", "Present failed")
        )
        assertTrue("Receive did not complete successfully. Latest status: ${latestStatus(device)}", receiveSuccess)
        assertTrue("No credentials were shown in UI", device.findObject(By.text("No credentials")) == null)

        val session = DemoTestBackend.createVerifierSession(scenario)
        WalletGalleryCapture.recordRequest("presentation", session.authorizationRequestUri)
        submitThroughUnifiedScan(device, "wallet.scanAction", session.authorizationRequestUri)
        val previewReady = device.wait(
            Until.hasObject(By.res("wallet.presentationReview")),
            CREDENTIAL_OPERATION_TIMEOUT,
        )
        assertTrue(
            "Presentation preview did not load.\n${foregroundWindowSnapshot(device)}",
            previewReady,
        )
        WalletGalleryCapture.capture(device, "wallet-presentation-${scenario.id}-review")

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
    }

    @Test
    fun transactionDataPreviewAgainstPublicDemoIssuer2Verifier2() = runBlocking {
        val scenario = DemoTestBackend.transactionDataPresentationScenario
        val offer = DemoTestBackend.createOffer(scenario)
        WalletGalleryCapture.recordRequest("issuance", offer.offerUrl)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        launchAndUnlock(context, device)

        submitThroughUnifiedScan(device, "wallet.scanEmptyAction", offer.offerUrl)
        val offerPreviewReady2 = device.wait(
            Until.hasObject(By.res("wallet.offerReview")),
            CREDENTIAL_OPERATION_TIMEOUT,
        )
        assertTrue(
            "Offer preview did not appear.\n${foregroundWindowSnapshot(device)}",
            offerPreviewReady2,
        )
        clickByTag(device, "wallet.offerAcceptButton")
        val receiveSuccess = waitForStatus(
            device = device,
            timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
            matcher = { it.startsWith("Received") },
            failurePrefixes = listOf("Receive failed", "Bootstrap failed", "Present failed")
        )
        assertTrue("Receive did not complete successfully. Latest status: ${latestStatus(device)}", receiveSuccess)

        val session = DemoTestBackend.createTransactionDataVerifierSession(scenario)
        WalletGalleryCapture.recordRequest("presentation-transaction-data", session.authorizationRequestUri)
        submitThroughUnifiedScan(device, "wallet.scanAction", session.authorizationRequestUri)
        val previewReady = device.wait(
            Until.hasObject(By.res("wallet.presentationReview")),
            CREDENTIAL_OPERATION_TIMEOUT,
        )
        assertTrue(
            "Transaction-data preview did not load.\n${foregroundWindowSnapshot(device)}",
            previewReady,
        )

        WalletGalleryCapture.capture(device, "wallet-presentation-transaction-data-review")

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

    private fun submitThroughUnifiedScan(device: UiDevice, actionTag: String, value: String) {
        clickByTag(device, actionTag)
        setTextByTag(device, "wallet.scanInput", value)
        clickByTag(device, "wallet.scanSubmit")
    }

    private fun waitForReceiveFailureAfterScrolling(device: UiDevice): Boolean {
        val deadline = System.currentTimeMillis() + CREDENTIAL_OPERATION_TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            device.scrollUp()
            if (latestStatus(device).startsWith("Receive failed")) return true
            Thread.sleep(400)
        }
        return false
    }

    private fun incorrectCodeFor(code: String): String {
        require(code.isNotEmpty()) { "Transaction code must not be empty" }
        val replacement = if (code.last() == '0') '1' else '0'
        return code.dropLast(1) + replacement
    }
}
