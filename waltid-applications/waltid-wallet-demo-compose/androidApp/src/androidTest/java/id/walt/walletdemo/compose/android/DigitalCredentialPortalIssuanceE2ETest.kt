package id.walt.walletdemo.compose.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.walletdemo.compose.android.Portal2DigitalCredentialE2EHelper.Labels
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Pre-authorized mDL issuance through portal2 and Chrome's Digital Credentials create API. */
@RunWith(AndroidJUnit4::class)
class DigitalCredentialPortalIssuanceE2ETest {

    @Test
    fun issuesPreAuthorizedMdlThroughPortalDigitalCredentialsApi() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val capabilities = Portal2DigitalCredentialE2EHelper.requireIssuanceCapabilities(context)
        val cardsBefore = Portal2DigitalCredentialE2EHelper.snapshotCredentialCards(context, device)

        Portal2DigitalCredentialE2EHelper.openPortal(context, device, capabilities)
        Portal2DigitalCredentialE2EHelper.selectPortalTab(
            device,
            Labels.ISSUE_TAB,
            Labels.ISSUE_HEADING,
        )
        Portal2DigitalCredentialE2EHelper.clickPortalControl(device, Labels.MDL_ISSUE_OPTION)
        // PRE_AUTHORIZED is the portal's default; selecting it explicitly makes the intended profile
        // visible in the test and protects against a persisted/hydrated UI state changing it.
        Portal2DigitalCredentialE2EHelper.clickPortalControl(device, Labels.PRE_AUTHORIZED)
        Portal2DigitalCredentialE2EHelper.clickPortalControl(device, Labels.DC_API_DELIVERY)
        Portal2DigitalCredentialE2EHelper.requirePortalCapability(
            device,
            Labels.CREATE_OFFER,
            Labels.ISSUANCE_UNAVAILABLE,
            capabilities,
            "Enable chrome://flags/#web-identity-digital-credentials-creation, relaunch Chrome, " +
                "and require DigitalCredential.userAgentAllowsProtocol(\"openid4vci-v1\") plus " +
                "navigator.credentials.create.",
        )
        Portal2DigitalCredentialE2EHelper.clickPortalControl(device, Labels.CREATE_OFFER)
        Portal2DigitalCredentialE2EHelper.acceptChromeTrustPromptIfPresent(device)
        Portal2DigitalCredentialE2EHelper.driveIssuancePicker(device)

        val issuerSessionId = Portal2DigitalCredentialE2EHelper.awaitPortalCompletion(device)
        DemoTestBackend.waitForIssuerIssuanceSuccess(issuerSessionId)
        Portal2DigitalCredentialE2EHelper.assertNewMdlCredentialStored(
            context,
            device,
            cardsBefore,
        )
    }
}
