package id.walt.walletdemo.compose.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletCredentialOffer
import id.walt.wallet2.mobile.MobileWalletIssuanceRequest
import id.walt.walletdemo.compose.android.Portal2DigitalCredentialE2EHelper.Labels
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.logic.createAndroidDemoMobileWallet
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Browser-origin presentation coverage through portal2's phone-sized Simple mode. */
@RunWith(AndroidJUnit4::class)
class DigitalCredentialPortalPresentationE2ETest {

    private lateinit var fixture: Fixture

    @Before
    fun provisionMdlAndOpenPortal() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val capabilities = Portal2DigitalCredentialE2EHelper.requirePresentationCapabilities(context)

        val wallet = createAndroidDemoMobileWallet(context, demoWalletConfig()).wallet
        wallet.bootstrap()
        wallet.credentials().forEach { credential ->
            check(wallet.deleteCredential(credential.id)) {
                "Failed to delete stale credential ${credential.id}"
            }
        }
        val registrationWithoutCredentials = wallet.refreshDigitalCredentialRegistration()
        assertTrue(
            "Credential Manager registration was unavailable after clearing the wallet: " +
                registrationWithoutCredentials.reason,
            registrationWithoutCredentials.available,
        )

        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        issueFromDemoIssuer(wallet, scenario)
        val registration = wallet.refreshDigitalCredentialRegistration()
        assertTrue(
            "Credential Manager registration was unavailable after mDL provisioning: ${registration.reason}",
            registration.available,
        )
        assertEquals("Expected one registered mDL", 1, registration.registeredEntryCount)

        launchAndUnlock(context, device)
        Portal2DigitalCredentialE2EHelper.openPortal(context, device, capabilities)
        Portal2DigitalCredentialE2EHelper.selectPortalTab(
            device,
            Labels.VERIFY_TAB,
            Labels.VERIFY_HEADING,
        )
        Portal2DigitalCredentialE2EHelper.clickPortalControl(device, Labels.MDL_VERIFY_OPTION)
        fixture = Fixture(device, capabilities)
    }

    @Test
    fun presentsMdlThroughPortalOpenId4VpDcApi() = runBlocking {
        presentAndAssert(
            actionLabel = Labels.VERIFY_OPENID4VP,
            requiredPolicyIds = MDOC_REQUIRED_POLICIES,
        )
    }

    @Test
    fun presentsMdlThroughPortalIso180137DcApi() = runBlocking {
        presentAndAssert(
            actionLabel = Labels.VERIFY_ANNEX_C,
            requiredPolicyIds = MDOC_REQUIRED_POLICIES,
        )
    }

    private suspend fun presentAndAssert(actionLabel: String, requiredPolicyIds: Set<String>) {
        Portal2DigitalCredentialE2EHelper.requirePortalCapability(
            fixture.device,
            actionLabel,
            Labels.PRESENTATION_UNAVAILABLE,
            fixture.capabilities,
            "Chrome 141+ must expose window.DigitalCredential and navigator.credentials.get on HTTPS.",
        )
        Portal2DigitalCredentialE2EHelper.clickPortalControl(fixture.device, actionLabel)
        Portal2DigitalCredentialE2EHelper.acceptChromeTrustPromptIfPresent(fixture.device)
        Portal2DigitalCredentialE2EHelper.drivePresentationPicker(fixture.device)

        val sessionId = Portal2DigitalCredentialE2EHelper.awaitPortalCompletion(fixture.device)
        val info = Portal2DigitalCredentialE2EHelper.verifierSessionInfo(sessionId)
        assertEquals(
            "Verifier did not accept the portal presentation: $info",
            "SUCCESSFUL",
            info["status"]?.jsonPrimitive?.content,
        )
        val policyResults = info["policy_results"]
            ?: error("Verifier session has no policy_results: $info")
        val executed = Portal2DigitalCredentialE2EHelper.executedPolicyIds(policyResults)
        requiredPolicyIds.forEach { policyId ->
            assertTrue("$policyId did not run. Executed: $executed", policyId in executed)
        }
        val failed = Portal2DigitalCredentialE2EHelper.failedPolicies(policyResults)
        assertTrue("Verifier policies failed: $failed", failed.isEmpty())
    }

    private suspend fun issueFromDemoIssuer(
        wallet: MobileWallet,
        scenario: DemoTestBackend.CredentialScenario,
    ) {
        val offer = DemoTestBackend.createOffer(scenario)
        val session = wallet.startIssuance(
            MobileWalletIssuanceRequest(offer = MobileWalletCredentialOffer.Uri(offer.offerUrl)),
        )
        when (val outcome = wallet.continuePreAuthorizedIssuance(session.id, offer.txCode)) {
            is WalletIssuanceOutcome.Stored -> assertEquals(1, outcome.credentialIds.size)
            is WalletIssuanceOutcome.Deferred -> error(
                "Live issuer deferred mDL provisioning: " +
                    "stored=${outcome.storedCredentialIds}, deferred=${outcome.credentials}",
            )
            is WalletIssuanceOutcome.Failed -> error(
                "Live issuer failed mDL provisioning: ${outcome.error.code}: ${outcome.error.message}",
            )
            is WalletIssuanceOutcome.Cancelled -> error(
                "Live issuer cancelled mDL provisioning for ${outcome.sessionId}",
            )
        }
    }

    private data class Fixture(
        val device: UiDevice,
        val capabilities: Portal2DigitalCredentialE2EHelper.DeviceCapabilities,
    )

    private companion object {
        val MDOC_REQUIRED_POLICIES = setOf("mso_mdoc/device-auth", "mso_mdoc/issuer_auth")
    }
}
