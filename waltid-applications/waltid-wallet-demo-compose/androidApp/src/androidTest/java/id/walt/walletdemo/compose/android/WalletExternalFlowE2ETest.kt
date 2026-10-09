package id.walt.walletdemo.compose.android

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.recreateActivity
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.sendDeepLink
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.unlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletExternalFlowE2ETest {
    @Test
    fun coldSheetUnlockRecreationAndCloseReturnToWallet() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        launchAndUnlock(context, device)
        val offer = DemoTestBackend.createOffer(DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }, inlineOffer = true)
        context.startActivity(Intent(context, WalletExternalFlowTestCallerActivity::class.java)
            .putExtra("offer", offer.offerUrl)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val launch = device.wait(Until.findObject(By.text("Open wallet request")), UI_ELEMENT_TIMEOUT)
        assertNotNull("Caller did not open", launch)
        launch.click()
        unlock(device, initializeSigningIdentity = false)
        assertNotNull("External offer did not prepare after unlock", waitForResource(device, "wallet.offerReview", UI_ELEMENT_TIMEOUT))
        assertFalse(device.hasObject(By.res("wallet.offerInput")))
        recreateActivity(MainActivity::class.java, device)
        assertNotNull("Review was lost on recreation", waitForResource(device, "wallet.offerReview", UI_ELEMENT_TIMEOUT))
        assertTrue(device.takeScreenshot(File(context.cacheDir, "external-receive-sheet.png")))
        clickByTag(device, "wallet.external.close")
        assertNotNull("Closing did not keep the wallet open", waitForResource(device, "wallet.scanButton", UI_ELEMENT_TIMEOUT))
        assertFalse("The caller must not show through the wallet", device.hasObject(By.text("External test caller")))
        assertFalse(device.hasObject(By.res("wallet.external.flow")))
    }

    @Test
    fun unavailableCallbackDoesNotOfferAnUnsafeRetry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        launchAndUnlock(context, device)
        sendDeepLink(context, "openid://callback?code=orphan&state=lost")
        assertNotNull(waitForResource(device, "wallet.external.unavailable", UI_ELEMENT_TIMEOUT))
        assertFalse(device.hasObject(By.res("wallet.external.retry")))
        assertFalse(device.hasObject(By.res("wallet.offerAcceptButton")))
        clickByTag(device, "wallet.external.close")
        assertNotNull(waitForResource(device, "wallet.scanButton", UI_ELEMENT_TIMEOUT))
    }

    @Test
    fun warmExternalIssuanceKeepsReceiptUntilDoneAndReturnsToWallet() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        launchAndUnlock(context, device)
        val offer = DemoTestBackend.createOffer(DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }, inlineOffer = true)
        sendDeepLink(context, offer.offerUrl)
        assertNotNull("Warm external offer did not open", waitForResource(device, "wallet.offerReview", UI_ELEMENT_TIMEOUT))
        clickByTag(device, "wallet.offerAcceptButton")
        DemoTestBackend.waitForIssuerIssuanceSuccess(offer.offerId)
        assertNotNull("The issued result must remain in the sheet", waitForResource(device, "issuance-done", UI_ELEMENT_TIMEOUT))
        assertNotNull(waitForResource(device, "wallet.external.flow", UI_ELEMENT_TIMEOUT))
        clickByTag(device, "issuance-done")
        assertNotNull("Done should restore the existing wallet", waitForResource(device, "wallet.scanButton", UI_ELEMENT_TIMEOUT))
    }
}
