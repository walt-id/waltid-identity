package id.walt.walletdemo.compose.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import id.walt.mobile.test.backend.EnterpriseMobileFixtureClient
import id.walt.mobile.test.backend.EnterpriseMobilePlatform
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertResourceTextEquals
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertResourceVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.credentialCardTags
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.latestStatus
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.relaunchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.sendDeepLink
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real issuer and wallet acceptance; requires the coordinated Enterprise mobile fixture. */
@RunWith(AndroidJUnit4::class)
class BatchIssuanceE2ETest {
    @Test
    fun explicitCopiesSurviveActivityRelaunch() = runBlocking {
        val fixtureUrl = requireNotNull(InstrumentationRegistry.getArguments()
            .getString("enterprise_fixture_base_url")?.takeIf { it.isNotBlank() }) {
            "Run enterpriseAndroidMobileIntegrationTest or supply enterprise_fixture_base_url"
        }
        val fixture = EnterpriseMobileFixtureClient(fixtureUrl)
        val scenario = fixture.scenarios().first { it.id == "enterprise-mdl" }
        val offer = fixture.createOffer(scenario, EnterpriseMobilePlatform.ANDROID)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        launchAndUnlock(context, device)
        val previousIds = device.credentialCardTags()

        sendDeepLink(context, offer.offerUrl)
        assertNotNull("Offer review missing: ${latestStatus(device)}",
            waitForResource(device, "wallet.offerAcceptButton", 60_000))
        val configurationId = "org.iso.18013.5.1.mDL"
        assertResourceVisibleAfterScrolling(device, "issuance-copies-$configurationId", "Copy selection missing", 10_000)
        assertResourceTextEquals(device, "issuance-copies-$configurationId", "Copies: 1", 10_000,
            "Advertised batch support must not request extra copies")
        clickByTag(device, "issuance-more-$configurationId")
        assertResourceTextEquals(device, "issuance-copies-$configurationId", "Copies: 2", 10_000,
            "Explicit copy selection was not applied")
        screenshot(device, "batch-review")
        clickByTag(device, "wallet.offerAcceptButton")
        assertNotNull("Batch receipt missing: ${latestStatus(device)}",
            waitForResource(device, "issuance-done", 90_000))
        clickByTag(device, "issuance-done")
        val receivedIds = device.credentialCardTags() - previousIds
        assertEquals("Expected two distinct stored credential cards", 2, receivedIds.size)
        screenshot(device, "batch-stored")

        relaunchAndUnlock(context, device)
        assertTrue("Stored credentials missing after Activity relaunch", device.credentialCardTags().containsAll(receivedIds))
        screenshot(device, "batch-relaunched")
    }

    private fun screenshot(device: UiDevice, name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "batch-evidence")
        check(directory.isDirectory || directory.mkdirs())
        assertTrue("Screenshot capture failed", device.takeScreenshot(File(directory, "$name.png")))
    }
}
