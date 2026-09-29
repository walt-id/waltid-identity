package id.walt.walletdemo.compose.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import id.walt.mobile.test.backend.EnterpriseMobileFixtureClient
import id.walt.mobile.test.backend.EnterpriseMobilePlatform
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertResourceTextEquals
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.latestStatus
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.relaunchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.sendDeepLink
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/** Real issuer and wallet acceptance; requires the coordinated Enterprise mobile fixture. */
@RunWith(AndroidJUnit4::class)
class BatchIssuanceE2ETest {
    @Test
    fun explicitCopiesSurviveActivityRelaunch() = runBlocking {
        val fixtureUrl = InstrumentationRegistry.getArguments().getString("enterpriseMobileFixtureBaseUrl")
        assumeNotNull(fixtureUrl)
        val fixture = EnterpriseMobileFixtureClient(requireNotNull(fixtureUrl))
        val scenario = fixture.scenarios().first { it.id == "enterprise-mdl" }
        val offer = fixture.createOffer(scenario, EnterpriseMobilePlatform.ANDROID)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        launchAndUnlock(context, device)
        val previousIds = credentialIds(device)

        sendDeepLink(context, offer.offerUrl)
        clickByTag(device, "wallet.receiveButton")
        assertTrue("Offer review missing: ${latestStatus(device)}", waitForStatus(
            device, 60_000, { it == "Review credential offer" }, listOf("Receive failed"),
        ))
        val configurationId = "org.iso.18013.5.1.mDL"
        assertResourceTextEquals(device, "issuance-copies-$configurationId", "Copies: 1", 10_000,
            "Advertised batch support must not request extra copies")
        clickByTag(device, "issuance-more-$configurationId")
        assertResourceTextEquals(device, "issuance-copies-$configurationId", "Copies: 2", 10_000,
            "Explicit copy selection was not applied")
        screenshot(device, "batch-review")
        clickByTag(device, "wallet.offerAcceptButton")
        assertTrue("Batch receive did not finish: ${latestStatus(device)}", waitForStatus(
            device, 90_000, { it.startsWith("Received 2") }, listOf("Receive failed"),
        ))
        val receivedIds = credentialIds(device) - previousIds
        assertEquals("Expected two distinct stored credential cards", 2, receivedIds.size)
        screenshot(device, "batch-stored")

        relaunchAndUnlock(context, device)
        assertTrue("Stored credentials missing after Activity relaunch", credentialIds(device).containsAll(receivedIds))
        screenshot(device, "batch-relaunched")
    }

    private fun credentialIds(device: UiDevice): Set<String> =
        device.findObjects(By.res(Pattern.compile("wallet\\.credentialCard\\..+"))).map { it.resourceName }.toSet()

    private fun screenshot(device: UiDevice, name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "batch-evidence")
        check(directory.isDirectory || directory.mkdirs())
        assertTrue("Screenshot capture failed", device.takeScreenshot(File(directory, "$name.png")))
    }
}
