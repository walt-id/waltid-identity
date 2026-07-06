package id.walt.walletdemo.compose.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import id.walt.mobile.test.backend.LocalEnterpriseTestBackend
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
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalEnterpriseBackendE2ETest {
    private val httpClient = HttpClient(Android)

    @Test
    fun receiveAndPresentAgainstLocalEnterpriseBackend() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val config = backendConfig(args.getString("e2e_host_alias_domain"))

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        val token = LocalEnterpriseTestBackend.getAdminToken(config, httpClient)
        val offerUrl = LocalEnterpriseTestBackend.createPreAuthorizedOffer(config, token, httpClient)

        launchAndUnlock(context, device)
        sendDeepLink(context, offerUrl)
        assertTrue(
            "Offer URL did not appear in UI after deep link",
            waitForResource(device, "wallet.offerInput", UI_ELEMENT_TIMEOUT)?.text == offerUrl
        )

        clickByTag(device, "wallet.receiveButton")
        assertReceivedCredential(device)

        val verifierSession = LocalEnterpriseTestBackend.createVerifierSession(config, token, httpClient)
        sendDeepLink(context, verifierSession.bootstrapAuthorizationRequestUrl)
        presentCredentialIfNeeded(device)
        assertPresentationDidNotFail(device)
        assertTrue("Wallet app is no longer in foreground", device.currentPackageName == context.packageName)

        val verifierStatus = LocalEnterpriseTestBackend.waitForVerifierSuccess(
            config,
            verifierSession.sessionId,
            httpClient,
            timeoutMs = VERIFIER_POLLING_TIMEOUT
        )
        assertTrue("Verifier session was not SUCCESSFUL. Last status: $verifierStatus", verifierStatus == "SUCCESSFUL")
    }

    @Test
    fun credentialsPersistAcrossAppRestart() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val config = backendConfig(args.getString("e2e_host_alias_domain"))

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        val token = LocalEnterpriseTestBackend.getAdminToken(config, httpClient)
        val offerUrl = LocalEnterpriseTestBackend.createPreAuthorizedOffer(config, token, httpClient)

        launchAndUnlock(context, device)
        sendDeepLink(context, offerUrl)
        assertTrue("Offer URL did not appear", waitForResource(device, "wallet.offerInput", UI_ELEMENT_TIMEOUT)?.text == offerUrl)
        clickByTag(device, "wallet.receiveButton")
        assertReceivedCredential(device)

        device.pressHome()
        Thread.sleep(1_000)
        device.executeShellCommand("am kill ${context.packageName}")
        Thread.sleep(2_000)

        launchAndUnlock(context, device)
        assertTrue(
            "Credentials not displayed after restart - persistence failed",
            device.findObject(By.text("No credentials")) == null
        )

        val verifierSession = LocalEnterpriseTestBackend.createVerifierSession(config, token, httpClient)
        sendDeepLink(context, verifierSession.bootstrapAuthorizationRequestUrl)
        presentCredentialIfNeeded(device)
        assertPresentationDidNotFail(device)

        val verifierStatus = LocalEnterpriseTestBackend.waitForVerifierSuccess(
            config,
            verifierSession.sessionId,
            httpClient,
            timeoutMs = VERIFIER_POLLING_TIMEOUT
        )
        assertTrue("Verifier session was not SUCCESSFUL after restart. Status: $verifierStatus", verifierStatus == "SUCCESSFUL")
    }

    private fun backendConfig(ngrokDomainArg: String?): LocalEnterpriseTestBackend.BackendConfig {
        val args = InstrumentationRegistry.getArguments()
        val ngrokDomain = ngrokDomainArg
            ?: error("Missing required instrumentation arg: e2e_host_alias_domain")

        return LocalEnterpriseTestBackend.BackendConfig(
            apiBaseUrl = args.getString("e2e_api_base_url") ?: "https://$ngrokDomain",
            ngrokBaseUrl = "https://$ngrokDomain",
            apiHostHeader = args.getString("e2e_api_host_header"),
            adminEmail = args.getString("e2e_admin_email") ?: DEFAULT_ADMIN_EMAIL,
            adminPassword = args.getString("e2e_admin_password") ?: DEFAULT_ADMIN_PASSWORD,
            org = args.getString("e2e_org") ?: DEFAULT_ORG,
            tenant = args.getString("e2e_tenant") ?: DEFAULT_TENANT,
            issuerProfile = args.getString("e2e_issuer_profile") ?: DEFAULT_ISSUER_PROFILE,
            verifier = args.getString("e2e_verifier") ?: DEFAULT_VERIFIER,
        )
    }

    private fun assertReceivedCredential(device: UiDevice) {
        val receiveSuccess = waitForStatus(
            device = device,
            timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
            matcher = { it.startsWith("Received") },
            failurePrefixes = listOf("Receive failed", "Bootstrap failed", "Present failed")
        )
        assertTrue("Receive did not complete. Latest status: ${latestStatus(device)}", receiveSuccess)
        assertTrue("No credentials were shown in UI", device.findObject(By.text("No credentials")) == null)
    }

    private fun presentCredentialIfNeeded(device: UiDevice) {
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
    }

    private fun assertPresentationDidNotFail(device: UiDevice) {
        Thread.sleep(POST_PRESENT_DELAY)
        val statusAfterPresent = latestStatus(device)
        assertTrue(
            "Presentation failed in app. Latest status: $statusAfterPresent",
            !statusAfterPresent.startsWith("Present failed") &&
                !statusAfterPresent.startsWith("Receive failed") &&
                !statusAfterPresent.startsWith("Bootstrap failed")
        )
    }

    private companion object {
        const val DEFAULT_ADMIN_EMAIL = "admin@walt.id"
        const val DEFAULT_ADMIN_PASSWORD = "admin123456"
        const val DEFAULT_ORG = "waltid"
        const val DEFAULT_TENANT = "waltid-tenant01"
        const val DEFAULT_ISSUER_PROFILE = "issuer2.mdl-profile"
        const val DEFAULT_VERIFIER = "verifier2"
    }
}
