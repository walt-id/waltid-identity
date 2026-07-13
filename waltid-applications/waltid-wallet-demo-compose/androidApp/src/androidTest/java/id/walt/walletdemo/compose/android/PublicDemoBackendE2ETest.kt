package id.walt.walletdemo.compose.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.POST_PRESENT_DELAY
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.QUICK_STATUS_CHECK_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.VERIFIER_POLLING_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.latestStatus
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.sendDeepLink
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicDemoBackendE2ETest {

    @Test
    fun receiveAndPresentAgainstPublicDemoIssuer2Verifier2() = runBlocking {
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "eudi-pid-mdoc" }
        val offer = DemoTestBackend.createOffer(scenario)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        launchAndUnlock(context, device)
        clickByTag(device, "wallet.openReceiveButton")
        grantCameraPermissionIfPrompted(device)
        sendDeepLink(context, offer.offerUrl)
        assertTrue(
            "Could not find 'Enter link' toggle to switch to manual entry mode",
            device.wait(Until.hasObject(By.text("Enter link")), UI_ELEMENT_TIMEOUT)
        )
        device.findObject(By.text("Enter link")).click()
        device.wait(Until.findObject(By.res("wallet.offerInput").text(offer.offerUrl)), UI_ELEMENT_TIMEOUT)

        assertTrue(
            "Offer URL did not appear in UI after deep link",
            waitForResource(device, "wallet.offerInput", UI_ELEMENT_TIMEOUT)?.text == offer.offerUrl
        )

        clickByTag(device, "wallet.receiveButton")
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

        if (device.wait(Until.hasObject(By.res("wallet.offerInput")), QUICK_STATUS_CHECK_TIMEOUT)) {
            device.findObject(By.desc("Back")).click()
            assertTrue(
                "Could not return to main screen after unexpected Receive redirect",
                device.wait(Until.hasObject(By.res("wallet.presentationInput")), UI_ELEMENT_TIMEOUT)
            )
        }


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
            clickByTag(device, "wallet.presentButton")
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

        DemoTestBackend.waitForVerifierSuccess(session.sessionId, timeoutMs = VERIFIER_POLLING_TIMEOUT)
    }


    private fun grantCameraPermissionIfPrompted(device: UiDevice) {
        val allowButton = device.wait(
            Until.findObject(
                By.res("com.android.permissioncontroller:id/permission_allow_foreground_only_button")
            ),
            3000
        ) ?: device.wait(
            Until.findObject(By.text("While using the app")),
            1000
        ) ?: device.wait(
            Until.findObject(By.text("Allow")),
            1000
        )
        allowButton?.click()
    }

}
