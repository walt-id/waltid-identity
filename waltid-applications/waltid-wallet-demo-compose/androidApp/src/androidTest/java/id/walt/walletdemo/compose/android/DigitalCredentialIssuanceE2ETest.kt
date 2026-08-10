package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.credentials.CreateDigitalCredentialResponse
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
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * OS-mediated Digital Credentials create E2E for OpenID4VCI pre-authorized offers.
 *
 * Requires Google Play services, so only the dedicated Play Store lane should run it.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalDigitalCredentialApi::class)
class DigitalCredentialIssuanceE2ETest {

    @Test
    fun acceptsPreAuthorizedOfferThroughCreateCredential() = runBlocking {
        val fixture = start() ?: return@runBlocking
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        val offer = DemoTestBackend.createOffer(scenario)
        val offerJson = requireNotNull(Uri.parse(offer.offerUrl).getQueryParameter("credential_offer")) {
            "Demo offer URL did not carry an inline credential_offer"
        }

        DigitalCredentialTestIssuer.reset(
            requestJson = """
                {"requests":[{"protocol":"openid4vci-v1","data":$offerJson}]}
            """.trimIndent(),
        )
        fixture.device.wait(Until.gone(By.pkg(CREDENTIAL_SELECTOR_PACKAGE).depth(0)), UI_ELEMENT_TIMEOUT)
        fixture.context.startActivity(
            Intent(fixture.context, DigitalCredentialTestIssuerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        val candidate = fixture.device.wait(
            Until.findObject(By.textContains("walt.id")),
            UI_ELEMENT_TIMEOUT,
        ) ?: fixture.device.wait(
            Until.findObject(By.textContains("Wallet")),
            UI_ELEMENT_TIMEOUT,
        )
        assertNotNull("Credential Manager did not surface the wallet create option", candidate)
        candidate.click()

        val continueButton = fixture.device.wait(Until.findObject(By.res("continue_button")), UI_ELEMENT_TIMEOUT)
            ?: fixture.device.wait(Until.findObject(By.text("Continue")), UI_ELEMENT_TIMEOUT)
        continueButton?.click()

        assertNotNull(
            "Wallet create offer review did not open",
            waitForResource(fixture.device, "wallet.offerReview", UI_ELEMENT_TIMEOUT),
        )
        clickByTag(fixture.device, "wallet.offerAcceptButton")

        val response = withTimeout(CREDENTIAL_OPERATION_TIMEOUT) {
            DigitalCredentialTestIssuer.await().getOrThrow()
        }
        assertIsCreateAck(response)
    }

    private fun assertIsCreateAck(response: CreateDigitalCredentialResponse) {
        val body = Json.parseToJsonElement(response.responseJson).jsonObject
        assertEquals("openid4vci-v1", body["protocol"]?.jsonPrimitive?.content)
        assertEquals("{}", body["data"]?.toString())
    }

    private class Fixture(val context: Context, val device: UiDevice)

    private fun start(): Fixture? {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(
            "Digital Credentials E2E requires an Android emulator with Google Play services",
            hasGooglePlayServices(context),
        )
        val fixture = Fixture(context, UiDevice.getInstance(instrumentation))
        launchAndUnlock(fixture.context, fixture.device)
        return fixture
    }

    private fun hasGooglePlayServices(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo("com.google.android.gms", 0)
            true
        }.getOrDefault(false)

    private companion object {
        private const val CREDENTIAL_SELECTOR_PACKAGE = "com.google.android.gms"
    }
}
