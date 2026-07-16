package id.walt.walletdemo.compose.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.VERIFIER_POLLING_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertClaimValueVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertResourceTextEquals
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertTextVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.latestStatus
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.sendDeepLink
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.setTextByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResourceEnabled
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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
        assertResourceTextEquals(
            device = device,
            tag = "wallet.offerInput",
            expected = offer.offerUrl,
            timeoutMs = UI_ELEMENT_TIMEOUT,
            message = "Offer URL did not appear in UI after deep link",
        )

        clickByTag(device, "wallet.receiveButton")
        assertTrue(
            "Transaction-code input did not appear in offer review. Latest status: ${latestStatus(device)}",
            waitForResource(device, "wallet.txCodeInput", CREDENTIAL_OPERATION_TIMEOUT) != null,
        )

        setTextByTag(device, "wallet.txCodeInput", incorrectCodeFor(transactionCode))
        assertTrue(
            "Accept button did not enable after entering a transaction code",
            waitForResourceEnabled(device, "wallet.offerAcceptButton", UI_ELEMENT_TIMEOUT),
        )
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

        // After failure, offer review is dismissed — resolve the offer again
        clickByTag(device, "wallet.receiveButton")
        assertTrue(
            "Transaction-code input did not reappear for retry. Latest status: ${latestStatus(device)}",
            waitForResource(device, "wallet.txCodeInput", CREDENTIAL_OPERATION_TIMEOUT) != null,
        )
        setTextByTag(device, "wallet.txCodeInput", transactionCode)
        assertTrue(
            "Accept button did not re-enable after correcting the transaction code",
            waitForResourceEnabled(device, "wallet.offerAcceptButton", UI_ELEMENT_TIMEOUT),
        )
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

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        launchAndUnlock(context, device)

        sendDeepLink(context, offer.offerUrl)
        assertResourceTextEquals(
            device = device,
            tag = "wallet.offerInput",
            expected = offer.offerUrl,
            timeoutMs = UI_ELEMENT_TIMEOUT,
            message = "Offer URL did not appear in UI after deep link",
        )

        clickByTag(device, "wallet.receiveButton")
        val offerPreviewReady = waitForStatus(
            device = device,
            timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
            matcher = { it.startsWith("Review credential offer") },
            failurePrefixes = listOf("Receive failed", "Bootstrap failed", "Present failed")
        )
        assertTrue("Offer preview did not appear. Latest status: ${latestStatus(device)}", offerPreviewReady)
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
        sendDeepLink(context, session.authorizationRequestUri)
        assertResourceTextEquals(
            device = device,
            tag = "wallet.presentationInput",
            expected = session.authorizationRequestUri,
            timeoutMs = UI_ELEMENT_TIMEOUT,
            message = "Presentation request URL did not appear in UI after deep link",
        )

        clickByTag(device, "wallet.presentButton")
        val previewReady = waitForStatus(
            device = device,
            timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
            matcher = { it == "Review presentation request" },
            failurePrefixes = listOf("Preview failed", "Present failed", "Receive failed", "Bootstrap failed")
        )
        assertTrue("Presentation preview did not load. Latest status: ${latestStatus(device)}", previewReady)

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

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        launchAndUnlock(context, device)

        sendDeepLink(context, offer.offerUrl)
        assertResourceTextEquals(
            device = device,
            tag = "wallet.offerInput",
            expected = offer.offerUrl,
            timeoutMs = UI_ELEMENT_TIMEOUT,
            message = "Offer URL did not appear in UI after deep link",
        )

        clickByTag(device, "wallet.receiveButton")
        val offerPreviewReady2 = waitForStatus(
            device = device,
            timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
            matcher = { it.startsWith("Review credential offer") },
            failurePrefixes = listOf("Receive failed", "Bootstrap failed", "Present failed")
        )
        assertTrue("Offer preview did not appear. Latest status: ${latestStatus(device)}", offerPreviewReady2)
        clickByTag(device, "wallet.offerAcceptButton")
        val receiveSuccess = waitForStatus(
            device = device,
            timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
            matcher = { it.startsWith("Received") },
            failurePrefixes = listOf("Receive failed", "Bootstrap failed", "Present failed")
        )
        assertTrue("Receive did not complete successfully. Latest status: ${latestStatus(device)}", receiveSuccess)

        val session = DemoTestBackend.createTransactionDataVerifierSession(scenario)
        sendDeepLink(context, session.authorizationRequestUri)
        assertResourceTextEquals(
            device = device,
            tag = "wallet.presentationInput",
            expected = session.authorizationRequestUri,
            timeoutMs = UI_ELEMENT_TIMEOUT,
            message = "Presentation request URL did not appear in UI after deep link",
        )

        clickByTag(device, "wallet.presentButton")
        val previewReady = waitForStatus(
            device = device,
            timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
            matcher = { it == "Review presentation request" },
            failurePrefixes = listOf("Preview failed", "Present failed", "Receive failed", "Bootstrap failed")
        )
        assertTrue("Transaction-data preview did not load. Latest status: ${latestStatus(device)}", previewReady)

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
            path = "transactionData[0].details.payee",
            label = "Payee",
            expectedValues = listOf("ACME Corp"),
            message = "Payment payee missing",
        )
    }

    private fun incorrectCodeFor(code: String): String {
        require(code.isNotEmpty()) { "Transaction code must not be empty" }
        val replacement = if (code.last() == '0') '1' else '0'
        return code.dropLast(1) + replacement
    }
}
