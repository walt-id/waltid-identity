package id.walt.walletdemo.compose.android

import android.content.Intent
import android.util.Log
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.sendDeepLink
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * OS-mediated Digital Credentials sharing E2E.
 *
 * This requires an emulator image with Google Play services. The regular AOSP device-test lane
 * intentionally skips it; the dedicated Google APIs lane runs it.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalDigitalCredentialApi::class)
class DigitalCredentialSharingE2ETest {
    @Test
    fun receivesMdlAndSharesItThroughAndroidCredentialManager() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        assumeTrue(
            "Digital Credentials E2E requires an Android emulator with Google Play services",
            hasGooglePlayServices(context),
        )

        val offer = DemoTestBackend.createOffer(
            DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" },
        )
        launchAndUnlock(context, device)
        sendDeepLink(context, offer.offerUrl)
        clickByTag(device, "wallet.receiveButton")
        assertTrue(
            "Offer preview did not appear",
            waitForStatus(
                device = device,
                timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
                matcher = { it.startsWith("Review credential offer") },
                failurePrefixes = listOf("Receive failed", "Bootstrap failed"),
            ),
        )
        clickByTag(device, "wallet.offerAcceptButton")
        assertTrue(
            "mDL was not received",
            waitForStatus(
                device = device,
                timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
                matcher = { it.startsWith("Received") },
                failurePrefixes = listOf("Receive failed", "Bootstrap failed"),
            ),
        )

        DigitalCredentialTestVerifier.reset()
        context.startActivity(
            Intent(context, DigitalCredentialTestVerifierActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        val candidate = device.wait(Until.findObject(By.text("org.iso.18013.5.1.mDL")), UI_ELEMENT_TIMEOUT)
        if (candidate == null) captureFailureDiagnostics(instrumentation, device, "candidate-not-found")
        assertNotNull("Credential Manager did not surface the mDL candidate", candidate)
        val continueButton = device.wait(Until.findObject(By.text("Agree and continue")), UI_ELEMENT_TIMEOUT)
            ?: device.wait(Until.findObject(By.text("Continue")), UI_ELEMENT_TIMEOUT)
        if (continueButton == null) captureFailureDiagnostics(instrumentation, device, "selector-consent-not-found")
        assertNotNull("Credential Manager did not offer consent", continueButton)
        continueButton!!.click()

        val shareButton = device.wait(Until.findObject(By.text("SHARE")), UI_ELEMENT_TIMEOUT)
        if (shareButton == null) captureFailureDiagnostics(instrumentation, device, "provider-consent-not-found")
        assertNotNull("Wallet provider consent did not open", shareButton)
        shareButton!!.click()

        val response = withTimeout(CREDENTIAL_OPERATION_TIMEOUT) {
            DigitalCredentialTestVerifier.await().getOrThrow()
        }
        val credential = response.credential as? DigitalCredential
        assertNotNull("Native verifier did not receive a digital credential", credential)
        val responseJson = Json.parseToJsonElement(credential!!.credentialJson).jsonObject
        assertEquals("openid4vp-v1-unsigned", responseJson["protocol"]?.jsonPrimitive?.content)
        assertTrue(responseJson["data"] is kotlinx.serialization.json.JsonObject)
    }

    private fun hasGooglePlayServices(context: android.content.Context): Boolean =
        runCatching { context.packageManager.getPackageInfo("com.google.android.gms", 0) }.isSuccess

    private fun captureFailureDiagnostics(
        instrumentation: android.app.Instrumentation,
        device: UiDevice,
        label: String,
    ) {
        val outputDirectory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File)
            ?: instrumentation.targetContext.getExternalFilesDir("dc-api-e2e")
            ?: return
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) return

        runCatching { device.dumpWindowHierarchy(File(outputDirectory, "$label.xml")) }
            .onFailure { Log.w(TAG, "Could not capture DC API UI hierarchy", it) }
        runCatching { device.takeScreenshot(File(outputDirectory, "$label.png")) }
            .onFailure { Log.w(TAG, "Could not capture DC API screenshot", it) }
    }

    private companion object {
        const val TAG = "DigitalCredentialE2E"
    }
}
