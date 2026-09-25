@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.walletdemo.compose.android

import android.util.Base64
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertTextContainingVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.logic.createAndroidDemoSharingSettingsStore
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewTestTags
import java.security.MessageDigest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

internal enum class ScaPaymentAction { Approve, ReviewCancellation, NativeBackCancellation, DeniedAuthentication, MissingTranslation }

/** Shared real-app payment review, transport and proof assertions for simulated and native authentication. */
@OptIn(ExperimentalDigitalCredentialApi::class)
internal abstract class ScaPaymentE2E : DigitalCredentialSharingE2E() {
    protected suspend fun exerciseScaPayment(action: ScaPaymentAction) {
        val fixture = fixture()
        createAndroidDemoSharingSettingsStore(fixture.context).setShowDcApiPresentationPreview(false)
        val scenario = DemoTestBackend.scaPaymentSdJwtScenario
        val session = DemoTestBackend.createDcApiVerifierSession(
            credentialQueries = listOf(scenario.verifierCredentialQuery),
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
            signedRequest = true,
            transactionData = listOf(DemoTestBackend.scaPaymentTransactionData("sca_payment").let { original ->
                if (action != ScaPaymentAction.DeniedAuthentication) original else JsonObject(original + ("payload" to
                    JsonObject(original.getValue("payload").jsonObject + ("transaction_id" to JsonPrimitive("sca-app-e2e-denied")))))
            }),
        )
        val wire = Json.parseToJsonElement(session.requestJson).jsonObject.getValue("requests").jsonArray.single().jsonObject
        assertEquals("openid4vp-v1-signed", wire.getValue("protocol").jsonPrimitive.content)
        val requestObject = wire.getValue("data").jsonObject.getValue("request").jsonPrimitive.content
        val requestClaims = Json.parseToJsonElement(Base64.decode(requestObject.split('.')[1], Base64.URL_SAFE).decodeToString()).jsonObject
        val encodedEntry = requestClaims.getValue("transaction_data").jsonArray.single().jsonPrimitive.content
        val request = fixture.startCredentialRequest(session.requestJson)
        fixture.enterProviderReview(request, scenario.credentialConfigurationId)
        if (action == ScaPaymentAction.MissingTranslation) {
            val blocked = fixture.device.wait(Until.findObject(By.res("payment-consent-blocked")), UI_ELEMENT_TIMEOUT)
            assertNotNull("Missing translation did not produce a visible consent error", blocked)
            assertEquals("Required payment instructions are unavailable in your preferred languages.", blocked!!.text)
            val submit = fixture.device.wait(Until.findObject(By.res(WALLET_SHARE_BUTTON_TAG)), UI_ELEMENT_TIMEOUT)
            assertNotNull("Submission control is unavailable", submit)
            assertFalse("Missing translation left submission enabled", submit!!.isEnabled)
            assertFalse("Blocked review released a credential response", request.isComplete)
            assertFalse("Blocked review opened native authorization", fixture.device.hasObject(
                By.pkg("com.android.systemui").text("Authorize wallet signing")))
            clickByTag(fixture.device, WalletDemoSharingReviewTestTags.CancelButton)
            assertTrue("Blocked review returned a credential", fixture.awaitCancellationOutcome(request).isFailure)
            assertTrue("Blocked review was accepted by verifier", DemoTestBackend.verifierSessionInfo(session.sessionId)["status"]?.jsonPrimitive?.content != "SUCCESSFUL")
            println("SCA_APP_E2E missingTranslationVisible=true submissionDisabled=true responseReleased=false")
            return
        }
        listOf(DemoTestBackend.SCA_PAYMENT_PAYEE_NAME, "merchant-001", "EUR", SCA_AMOUNT_TEXT).forEach { value ->
            assertTextContainingVisibleAfterScrolling(fixture.device, value, "Payment review is missing '$value'")
        }
        if (action == ScaPaymentAction.NativeBackCancellation) {
            println("SCA_OPERATOR: leave the final biometric prompt untouched; the test presses Back")
        }
        if (action == ScaPaymentAction.ReviewCancellation) {
            clickByTag(fixture.device, WalletDemoSharingReviewTestTags.CancelButton)
        } else clickByTag(fixture.device, WALLET_SHARE_BUTTON_TAG)
        if (action == ScaPaymentAction.NativeBackCancellation) {
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
        val result = withTimeout(180_000L) { request.await() }
        if (action in setOf(ScaPaymentAction.NativeBackCancellation, ScaPaymentAction.ReviewCancellation, ScaPaymentAction.DeniedAuthentication)) {
            assertTrue("Cancellation or denied authentication released a credential response", result.isFailure)
            assertTrue("Cancelled request was accepted by verifier", DemoTestBackend.verifierSessionInfo(session.sessionId)["status"]?.jsonPrimitive?.content != "SUCCESSFUL")
            println("SCA_APP_E2E action=$action responseReleased=false")
            return
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
        println("SCA_APP_E2E action=$action mandatoryReview=true exactEncodedHash=true verifier=SUCCESSFUL")
    }
}
