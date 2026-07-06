package id.walt.walletdemo.compose.android

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import id.walt.mobile.test.backend.EudiTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.POST_PRESENT_DELAY
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.QUICK_STATUS_CHECK_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.VERIFIER_POLLING_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.latestStatus
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.sendDeepLink
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EudiPublicBackendE2ETest {

    @Ignore("EUDI backend certificate expired 2026-07-04 - https://github.com/eu-digital-identity-wallet/eudi-srv-web-issuing-eudiw-py/issues/168")
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

        launchAndUnlock(context, device)

        sendDeepLink(context, offerUrl)
        assertTrue(
            "Offer URL did not appear in UI after deep link",
            waitForResource(device, "wallet.offerInput", UI_ELEMENT_TIMEOUT)?.text == offerUrl
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

        val credentialId = EudiTestBackend.extractCredentialIdFromOfferUrl(offerUrl)
        val verifierTx = EudiTestBackend.createVerifierTransaction(credentialId)

        sendDeepLink(context, verifierTx.authorizationRequestUri)

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

        EudiTestBackend.waitForVerifierSuccess(verifierTx.transactionId, timeoutMs = VERIFIER_POLLING_TIMEOUT)
    }

    private fun decodeBase64(value: String): String =
        String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)

    private companion object {
        const val DEFAULT_CREDENTIAL_ID = "eu.europa.ec.eudi.pid_vc_sd_jwt"
    }
}
