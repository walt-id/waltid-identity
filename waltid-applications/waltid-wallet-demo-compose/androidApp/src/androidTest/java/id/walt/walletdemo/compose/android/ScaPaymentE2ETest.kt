@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.walletdemo.compose.android

import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.identity.SigningIdentityState
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.KeyProtectionLevel
import id.walt.walletdemo.compose.logic.createAndroidDemoMobileWallet
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

import id.walt.mobile.test.PhysicalDeviceTest

/** Real app setup and issuance, then native SCA authorization through Credential Manager. */
@PhysicalDeviceTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalDigitalCredentialApi::class)
internal class ScaPaymentE2ETest : ScaPaymentE2E() {
    override val wallet: MobileWallet get() = provisionedWallet
    override val issuedCredentialIds: Set<String> get() = provisionedCredentialIds
    @Test
    fun sharesScaSdJwtWithNativeAuthorization() = runBlocking {
        exerciseScaPayment(when (operatorRoute) {
            "approve" -> ScaPaymentAction.Approve
            "cancel" -> ScaPaymentAction.NativeBackCancellation
            else -> error("Select the explicit physical-device SCA lane")
        })
    }

    companion object {
        private lateinit var provisionedWallet: MobileWallet
        private lateinit var provisionedCredentialIds: Set<String>
        private lateinit var operatorRoute: String

        @JvmStatic
        @BeforeClass
        fun provisionThroughApp(): Unit = runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            check(context.packageName == "id.walt.wallet.compose.test") { "Use a fresh isolated preview installation" }
            operatorRoute = InstrumentationRegistry.getArguments().getString("wallet.sca").orEmpty()
            check(operatorRoute in setOf("approve", "cancel")) { "Select the physical lane with -e wallet.sca approve|cancel" }
            val offer = DemoTestBackend.createOffer(DemoTestBackend.scaPaymentSdJwtScenario)
            val created = createAndroidDemoMobileWallet(context, demoWalletConfig())
            provisionedWallet = created.wallet
            check(provisionedWallet.credentials().isEmpty() && provisionedWallet.signingIdentity.state() == SigningIdentityState.Absent) {
                "Preview app already contains wallet material; use a fresh installation"
            }
            val device = UiDevice.getInstance(instrumentation)
            WalletComposeE2EHelper.launchAndCreateScaIdentity(context, device)
            WalletComposeE2EHelper.receiveThroughApp(device, offer.offerUrl)
            provisionedWallet = createAndroidDemoMobileWallet(context, demoWalletConfig()).wallet
            val active = provisionedWallet.signingIdentity.state() as? SigningIdentityState.Active
                ?: error("The app did not create a signing identity")
            assertEquals(KeyProtectionLevel.HARDWARE, active.identity.keyFacts.protection)
            assertEquals(KeyUseAuthorizationPolicy.BiometricCurrentSet, active.identity.authorization)
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
