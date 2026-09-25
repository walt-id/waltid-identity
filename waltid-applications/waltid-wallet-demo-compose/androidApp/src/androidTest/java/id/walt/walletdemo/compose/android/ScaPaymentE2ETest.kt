@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.walletdemo.compose.android

import android.util.Base64
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.identity.SigningIdentityState
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.KeyProtectionLevel
import id.walt.walletdemo.compose.logic.createAndroidDemoMobileWallet
import id.walt.walletdemo.compose.logic.createAndroidDemoSharingSettingsStore
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertTextContainingVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

import id.walt.mobile.test.PhysicalDeviceTest

/** Real app setup and issuance, then native SCA authorization through Credential Manager. */
@PhysicalDeviceTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalDigitalCredentialApi::class)
internal class ScaPaymentE2ETest : DigitalCredentialSharingE2E() {
    override val wallet: MobileWallet get() = provisionedWallet
    override val issuedCredentialIds: Set<String> get() = provisionedCredentialIds
    /** Select this physical class explicitly with -e wallet.sca approve|cancel. */
    @Test
    fun sharesScaSdJwtWithNativeAuthorization() = runBlocking {
        check(operatorRoute in setOf("approve", "cancel")) { "Select the explicit physical-device SCA lane" }
        val fixture = fixture()
        createAndroidDemoSharingSettingsStore(fixture.context).setShowDcApiPresentationPreview(false)
        val scenario = DemoTestBackend.scaPaymentSdJwtScenario
        val session = DemoTestBackend.createDcApiVerifierSession(
            credentialQueries = listOf(scenario.verifierCredentialQuery),
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
            signedRequest = true,
            transactionData = listOf(DemoTestBackend.scaPaymentTransactionData("sca_payment")),
        )
        val wire = Json.parseToJsonElement(session.requestJson).jsonObject.getValue("requests").jsonArray.single().jsonObject
        assertEquals("openid4vp-v1-signed", wire.getValue("protocol").jsonPrimitive.content)
        val requestObject = wire.getValue("data").jsonObject.getValue("request").jsonPrimitive.content
        val requestClaims = Json.parseToJsonElement(Base64.decode(requestObject.split('.')[1], Base64.URL_SAFE).decodeToString()).jsonObject
        val encodedEntry = requestClaims.getValue("transaction_data").jsonArray.single().jsonPrimitive.content
        val request = fixture.startCredentialRequest(session.requestJson)
        fixture.enterProviderReview(request, scenario.credentialConfigurationId)
        listOf(DemoTestBackend.SCA_PAYMENT_PAYEE_NAME, "merchant-001", "EUR", SCA_AMOUNT_TEXT).forEach { value ->
            assertTextContainingVisibleAfterScrolling(fixture.device, value, "Payment review is missing '$value'")
        }
        println(if (operatorRoute == "cancel") "SCA_OPERATOR: leave the final biometric prompt untouched; the test presses Back"
            else "SCA_OPERATOR: approve the next biometric prompt after payment review")
        clickByTag(fixture.device, WALLET_SHARE_BUTTON_TAG)
        if (operatorRoute == "cancel") {
            val prompt = fixture.device.wait(
                Until.findObject(By.pkg("com.android.systemui").text("Authorize wallet signing")),
                UI_ELEMENT_TIMEOUT,
            )
            assertNotNull("Native authorization prompt never appeared; an unrelated failure is not cancellation evidence", prompt)
            assertFalse("Request completed before native cancellation", request.isComplete)
            // System Back dismisses BiometricPrompt without depending on OEM button text/casing.
            assertTrue("Could not dismiss the native authorization prompt", fixture.device.pressBack())
            println("SCA_DEVICE_E2E nativePromptObserved=true nativeBackPressed=true")
        }
        val result = withTimeout(OPERATOR_TIMEOUT) { request.await() }
        if (operatorRoute == "cancel") {
            assertTrue("Cancelling native authorization released a credential response", result.isFailure)
            assertTrue("Cancelled request was accepted by verifier", DemoTestBackend.verifierSessionInfo(session.sessionId)["status"]?.jsonPrimitive?.content != "SUCCESSFUL")
            println("SCA_DEVICE_E2E nativeCancelled=true responseReleased=false")
            return@runBlocking
        }
        val credential = result.getOrThrow().credential as DigitalCredential
        val response = Json.parseToJsonElement(credential.credentialJson).jsonObject
        val presentation = response.getValue("data").jsonObject.getValue("vp_token").jsonObject
            .getValue("sca_payment").jsonArray.single().jsonPrimitive.content
        val claims = keyBindingJwtClaims(presentation)
        val expectedHash = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(encodedEntry.encodeToByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        assertEquals(listOf(expectedHash), claims.getValue("transaction_data_hashes").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("sha-256", claims["transaction_data_hashes_alg"]?.jsonPrimitive?.content)
        assertEquals("origin:${nativeAppOrigin(fixture.context)}", claims["aud"]?.jsonPrimitive?.content)
        assertEquals(requestClaims["nonce"], claims["nonce"])
        assertEquals("dc_api", claims["response_mode"]?.jsonPrimitive?.content)
        assertEquals(4, java.util.UUID.fromString(claims.getValue("jti").jsonPrimitive.content).version())
        assertEquals(Json.parseToJsonElement("""[{"possession":"other"},{"inherence":"other"}]"""), claims["amr"])
        assertVerifierAccepted(session.sessionId, credential.credentialJson, "sca_payment",
            SD_JWT_REQUIRED_POLICIES + "dc+sd-jwt/transaction-data-hash-check")
        println("SCA_DEVICE_E2E nativeApproved=true mandatoryReview=true exactEncodedHash=true verifier=SUCCESSFUL")
    }

    companion object {
        private lateinit var provisionedWallet: MobileWallet
        private lateinit var provisionedCredentialIds: Set<String>
        private lateinit var operatorRoute: String
        private const val OPERATOR_TIMEOUT = 180_000L

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
