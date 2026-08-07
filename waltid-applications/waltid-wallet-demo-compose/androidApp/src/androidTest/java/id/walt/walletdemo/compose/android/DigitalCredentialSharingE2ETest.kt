package id.walt.walletdemo.compose.android

import android.content.Intent
import android.content.pm.PackageManager
import android.util.Base64
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/**
 * OS-mediated Digital Credentials sharing E2E against the public demo verifier.
 *
 * The whole chain is real: issuer2 issues an mDL into the wallet, verifier2 creates an Annex D
 * (DC API) session, Android Credential Manager mediates the picker, and verifier2 verifies the
 * resulting response with its actual mdoc policies. Nothing here is stubbed, so a wrong session
 * transcript or a re-encoded issuer signature fails the test instead of passing it.
 *
 * This requires an emulator image with Google Play services. The regular AOSP device-test lane
 * intentionally skips it; the dedicated Play Store lane runs it.
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

        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        val offer = DemoTestBackend.createOffer(scenario)
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

        // The verifier hashes expectedOrigins.first() into the mdoc session transcript and the
        // wallet hashes what Credential Manager asserts for this (native, non-browser) caller. The
        // debug signing key differs per machine and per CI runner, so derive it at runtime rather
        // than pinning a fingerprint.
        val session = DemoTestBackend.createDcApiVerifierSession(
            scenario = scenario,
            expectedOrigins = listOf(nativeAppOrigin(context)),
        )

        DigitalCredentialTestVerifier.reset(session.requestJson)
        context.startActivity(
            Intent(context, DigitalCredentialTestVerifierActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        val candidate = device.wait(Until.findObject(By.text("org.iso.18013.5.1.mDL")), UI_ELEMENT_TIMEOUT)
        assertNotNull("Credential Manager did not surface the mDL candidate", candidate)
        val continueButton = device.wait(
            Until.findObject(By.res("continue_button")),
            UI_ELEMENT_TIMEOUT,
        ) ?: device.wait(Until.findObject(By.text("Continue")), UI_ELEMENT_TIMEOUT)
        assertNotNull("Credential Manager did not offer consent", continueButton)
        continueButton!!.click()

        val shareButton = device.wait(
            Until.findObject(By.res("android:id/button1")),
            UI_ELEMENT_TIMEOUT,
        ) ?: device.wait(Until.findObject(By.text("Share")), UI_ELEMENT_TIMEOUT)
            ?: device.wait(Until.findObject(By.text("SHARE")), UI_ELEMENT_TIMEOUT)
        assertNotNull("Wallet provider consent did not open", shareButton)
        shareButton!!.click()

        val response = withTimeout(CREDENTIAL_OPERATION_TIMEOUT) {
            DigitalCredentialTestVerifier.await().getOrThrow()
        }
        val credential = response.credential as? DigitalCredential
        assertNotNull("Native verifier did not receive a digital credential", credential)
        val responseJson = Json.parseToJsonElement(credential!!.credentialJson).jsonObject
        assertEquals("openid4vp-v1-unsigned", responseJson["protocol"]?.jsonPrimitive?.content)
        assertTrue("DC API response carries no data object", responseJson["data"] is JsonObject)

        // Unlike direct_post, response_mode=dc_api sends nothing from the wallet to the verifier:
        // the response comes back through the OS to whoever called getCredential, and that caller
        // delivers it. This test is that caller, so it posts - which is also what makes the
        // verifier run its real mdoc policies over the wallet's output. Verification is inline, so
        // the session is already terminal when this returns and there is nothing to poll for.
        DemoTestBackend.submitDcApiResponse(session.sessionId, credential.credentialJson)

        val info = DemoTestBackend.verifierSessionInfo(session.sessionId)
        assertEquals("SUCCESSFUL", info["status"]?.jsonPrimitive?.content)
        assertNotNull(
            "Verifier did not report the presented mDL: $info",
            info["presented_credentials"]?.jsonObject?.get("mdl"),
        )
        // The session is created without a vp_policies override, so the verifier applies its full
        // default mdoc set. Assert the two that would otherwise fail silently by not running:
        //
        // - device-auth is the DC API session transcript check. It passes only if the wallet hashed
        //   the origin Credential Manager asserted and the verifier hashed expectedOrigins.first(),
        //   over the same nonce - the one control that is specific to this flow.
        // - issuer_auth is asserted because it is the policy most likely to be quietly dropped,
        //   which would silently reduce this to a device-auth-only check.
        //
        // Note what issuer_auth passing here does *not* establish. It proves the wallet relayed the
        // issuer signature unaltered - a re-encoded COSE_Sign1 would fail it - but not that the
        // document signer meets the ISO 18013-5 profile. verifier2.demo.walt.id 0.23.0 predates that
        // enforcement, and the certificate issuer2.demo.walt.id actually signs with is X.509 v1 with
        // no extensions, so it has neither keyUsage:digitalSignature nor EKU 1.0.18013.5.1.2. A
        // verifier that does enforce the profile rejects this same presentation; see
        // DcApiDeploymentComparisonE2ETest and DeployedIssuerDocumentSignerTest.
        val policyResults = info["policy_results"]
            ?: error("Session info has no policy_results: $info")
        val executed = policyResults.executedPolicyIds()
        listOf("mso_mdoc/device-auth", "mso_mdoc/issuer_auth").forEach { policyId ->
            assertTrue("$policyId did not run. Executed: $executed", executed.contains(policyId))
        }
        assertTrue(
            "Failed policies: ${policyResults.failedPolicies()}",
            policyResults.failedPolicies().isEmpty(),
        )
    }

    /**
     * `android:apk-key-hash:<base64url-sha256(signing cert)>` - the origin Credential Manager
     * asserts for a native caller. Mirrors `AndroidDigitalCredentialProvider.nativeAppOrigin`,
     * which is internal to the wallet-mobile module.
     */
    private fun nativeAppOrigin(context: android.content.Context): String {
        val signatures = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo
            ?.signingCertificateHistory
            ?: error("Wallet package has no signing certificate")
        val digest = MessageDigest.getInstance("SHA-256").digest(signatures.first().toByteArray())
        val hash = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "android:apk-key-hash:$hash"
    }

    /** Collects every `success == false` leaf so a failure names the policy, not just `false`. */
    private fun JsonElement.failedPolicies(): List<String> = buildList {
        fun walk(element: JsonElement, path: String) {
            when (element) {
                is JsonObject -> {
                    val id = element["policy_executed"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                    if (id != null && element["success"]?.jsonPrimitive?.booleanOrNull == false) {
                        add("$path/$id: ${element["errors"]}")
                    }
                    element.forEach { (key, value) -> walk(value, "$path/$key") }
                }

                is JsonArray ->
                    element.forEachIndexed { index, value -> walk(value, "$path[$index]") }

                else -> Unit
            }
        }
        walk(this@failedPolicies, "")
    }

    private fun JsonElement.executedPolicyIds(): Set<String> = buildSet {
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    element["policy_executed"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                        ?.let { add(it) }
                    element.values.forEach(::walk)
                }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this@executedPolicyIds)
    }

    private fun hasGooglePlayServices(context: android.content.Context): Boolean =
        runCatching { context.packageManager.getPackageInfo("com.google.android.gms", 0) }.isSuccess

}
