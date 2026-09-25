@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.walletdemo.compose.android

import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.identity.SigningIdentityState
import id.walt.walletdemo.compose.logic.createAndroidDemoMobileWallet
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/** Unattended real-app E2E. Authentication alone is simulated in the isolated test build. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalDigitalCredentialApi::class)
internal class ScaPaymentAppE2ETest : ScaPaymentE2E() {
    override val wallet: MobileWallet get() = provisionedWallet
    override val issuedCredentialIds: Set<String> get() = provisionedCredentialIds
    @Test fun approvesPayment() = exercise(ScaPaymentAction.Approve)
    @Test fun cancelsReviewWithoutReleasingProof() = exercise(ScaPaymentAction.ReviewCancellation)
    @Test fun deniedAuthenticationReleasesNoProof() = exercise(ScaPaymentAction.DeniedAuthentication)

    private fun exercise(action: ScaPaymentAction) = runBlocking { exerciseScaPayment(action) }

    companion object {
        private lateinit var provisionedWallet: MobileWallet
        private lateinit var provisionedCredentialIds: Set<String>

        @JvmStatic
        @BeforeClass
        fun provisionThroughApp(): Unit = runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            check(context.packageName == "id.walt.wallet.compose.sca.e2e") { "Run the isolated SCA app E2E build" }
            val offer = DemoTestBackend.createOffer(DemoTestBackend.scaPaymentSdJwtScenario)
            val created = createAndroidDemoMobileWallet(context, demoWalletConfig())
            provisionedWallet = created.wallet
            check(provisionedWallet.credentials().isEmpty() && provisionedWallet.signingIdentity.state() == SigningIdentityState.Absent) {
                "Preview app already contains wallet material; use a fresh installation"
            }
            val device = UiDevice.getInstance(instrumentation)
            WalletComposeE2EHelper.launchExpectingSetupAndUnlock(context, device)
            WalletComposeE2EHelper.receiveThroughApp(device, offer.offerUrl, "disabled")
            provisionedWallet = createAndroidDemoMobileWallet(context, demoWalletConfig()).wallet
            val active = provisionedWallet.signingIdentity.state() as? SigningIdentityState.Active
                ?: error("The app did not create a signing identity")
            assertEquals(KeyUseAuthorizationPolicy.None, active.identity.authorization)
            val credential = provisionedWallet.credentials().single()
            provisionedCredentialIds = setOf(credential.id)
            val claims = Json.parseToJsonElement(credential.credentialDataJson).jsonObject
            val boundJwk = claims.getValue("cnf").jsonObject.getValue("jwk").jsonObject
            val identityJwk = Json.parseToJsonElement(active.identity.publicJwk).jsonObject
            listOf("kty", "crv", "x", "y").forEach { assertEquals(identityJwk[it], boundJwk[it]) }
            // Reopen through the factory used by independently launched Credential Manager activities.
            provisionedWallet = createAndroidDemoMobileWallet(context, demoWalletConfig()).wallet
            assertEquals(active, provisionedWallet.signingIdentity.state())
            assertEquals(provisionedCredentialIds, provisionedWallet.credentials().map { it.id }.toSet())
            val registration = provisionedWallet.refreshDigitalCredentialRegistration()
            check(registration.available && registration.registeredEntryCount == 1) { "Credential registration failed: $registration" }
            device.pressHome()
        }
    }
}
