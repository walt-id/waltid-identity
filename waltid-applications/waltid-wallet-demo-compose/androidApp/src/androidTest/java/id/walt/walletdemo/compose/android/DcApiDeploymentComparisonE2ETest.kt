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
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/**
 * DEMONSTRATIVE - remove before merging. This test proves a **verifier deployment** defect, not
 * wallet behaviour, and it depends on a deployed build that will be replaced.
 *
 * ## What it isolates
 *
 * [DigitalCredentialSharingE2ETest] passes the verifier's full default mdoc policy set - including
 * `mso_mdoc/issuer_auth` - against `verifier2.demo.walt.id`. The portal's DC API page, driving the
 * *same* wallet with the *same* `issuer2.demo.walt.id` mDL, gets:
 *
 * ```
 * "error": "invalid_request",
 * "error_description": "Presentation validation failed. Failed VP policies: my_mdl/mso_mdoc/issuer_auth"
 * ```
 *
 * Two things differ between those runs - the caller (Chrome vs native) and the verifier deployment -
 * so neither run alone identifies the cause. This test fixes the caller as native and varies only
 * the deployment, which is the missing cell:
 *
 * | caller | verifier | `issuer_auth` |
 * |---|---|---|
 * | native | `verifier2.demo.walt.id` `0.23.0` | passes ([DigitalCredentialSharingE2ETest]) |
 * | Chrome | `verifier2.portal.test.waltid.cloud` | **fails** (portal DC API page) |
 * | native | `verifier2.portal.test.waltid.cloud` | this test |
 *
 * ## Result, and which side is wrong
 *
 * Recorded outcome: `DEMO` succeeds with all six default mdoc policies passing, `PORTAL` rejects the
 * post with HTTP 400 `Failed VP policies: mdl/mso_mdoc/issuer_auth`. The caller is therefore
 * exonerated - the same wallet output is accepted by one build and rejected by the other.
 *
 * The rejecting build is the correct one. The chain the issuer signs with is logged below, and
 * `DeployedIssuerDocumentSignerTest` pins it: it is an X.509 **version 1** certificate with no
 * extensions, so it carries neither `keyUsage:digitalSignature` nor EKU `1.0.18013.5.1.2`, both of
 * which ISO 18013-5 requires of a document signer.
 * `IssuerAuthentication.validateDocumentSignerCertificateChain` enforces exactly that, so
 * `verifier2.demo.walt.id` `0.23.0` is passing a certificate it should reject - it predates that
 * enforcement. **`DigitalCredentialSharingE2ETest`'s `issuer_auth` assertion is consequently weaker
 * than it reads**: it passes because that deployment is lenient, not because the issuer is
 * conformant. The fix belongs to the issuer: reissue the demo document signer as v3 with the ISO
 * usage extensions.
 *
 * Deliberately asserts nothing about which deployment succeeds. Its value is the recorded verdict on
 * both, so it does not encode today's deployment state as an expectation.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalDigitalCredentialApi::class)
class DcApiDeploymentComparisonE2ETest {

    @Test
    fun reportsIssuerAuthOutcomeOnBothDeployments() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        assumeTrue(
            "Digital Credentials E2E requires an Android emulator with Google Play services",
            runCatching { context.packageManager.getPackageInfo("com.google.android.gms", 0) }.isSuccess,
        )

        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        val offer = DemoTestBackend.createOffer(scenario)
        launchAndUnlock(context, device)
        sendDeepLink(context, offer.offerUrl)
        clickByTag(device, "wallet.receiveButton")
        assertEquals(
            "Offer preview did not appear", true,
            waitForStatus(
                device = device,
                timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
                matcher = { it.startsWith("Review credential offer") },
                failurePrefixes = listOf("Receive failed", "Bootstrap failed"),
            ),
        )
        clickByTag(device, "wallet.offerAcceptButton")
        assertEquals(
            "mDL was not received", true,
            waitForStatus(
                device = device,
                timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
                matcher = { it.startsWith("Received") },
                failurePrefixes = listOf("Receive failed", "Bootstrap failed"),
            ),
        )

        // One credential, presented twice. Re-issuing per deployment would reintroduce the issuer as
        // a variable, which is exactly what this test removes.
        val report = DemoTestBackend.DcApiDeployment.entries.map { deployment ->
            deployment to shareOnce(context, device, scenario, deployment)
        }

        report.forEach { (deployment, info) ->
            val status = info["status"]?.jsonPrimitive?.content
            android.util.Log.i(
                "DcApiDeploymentCompare",
                "${deployment.name} (${deployment.verifierBaseUrl}): status=$status " +
                    "executed=${info["policy_results"]?.executedPolicyIds()} " +
                    "failed=${info["policy_results"]?.failedPolicies()}",
            )
            // The chain the issuer actually signed with. Logging it is the point: the two builds are
            // handed identical bytes, so whichever DS profile rule the newer one enforces has to be
            // visible in this certificate.
            info["policy_results"]?.firstValueOf("certificate_chain")?.let { chain ->
                android.util.Log.i("DcApiDeploymentCompare", "${deployment.name} certificate_chain=$chain")
            }
        }
        // Fails only if a deployment could not be exercised at all - a missing data point is the one
        // outcome that makes the comparison meaningless.
        report.forEach { (deployment, info) ->
            assertNotNull("${deployment.name} returned no status: $info", info["status"])
        }
    }

    private suspend fun shareOnce(
        context: android.content.Context,
        device: UiDevice,
        scenario: DemoTestBackend.CredentialScenario,
        deployment: DemoTestBackend.DcApiDeployment,
    ): JsonObject {
        val session = DemoTestBackend.createDcApiVerifierSession(
            scenario = scenario,
            expectedOrigins = listOf(nativeAppOrigin(context)),
            deployment = deployment,
        )
        DigitalCredentialTestVerifier.reset(session.requestJson)
        context.startActivity(
            Intent(context, DigitalCredentialTestVerifierActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        assertNotNull(
            "${deployment.name}: Credential Manager did not surface the mDL candidate",
            device.wait(Until.findObject(By.text("org.iso.18013.5.1.mDL")), UI_ELEMENT_TIMEOUT),
        )
        val continueButton = device.wait(Until.findObject(By.res("continue_button")), UI_ELEMENT_TIMEOUT)
            ?: device.wait(Until.findObject(By.text("Continue")), UI_ELEMENT_TIMEOUT)
        assertNotNull("${deployment.name}: Credential Manager did not offer consent", continueButton)
        continueButton!!.click()

        val shareButton = device.wait(Until.findObject(By.res("android:id/button1")), UI_ELEMENT_TIMEOUT)
            ?: device.wait(Until.findObject(By.text("Share")), UI_ELEMENT_TIMEOUT)
            ?: device.wait(Until.findObject(By.text("SHARE")), UI_ELEMENT_TIMEOUT)
        assertNotNull("${deployment.name}: wallet provider consent did not open", shareButton)
        shareButton!!.click()

        val response = withTimeout(CREDENTIAL_OPERATION_TIMEOUT) {
            DigitalCredentialTestVerifier.await().getOrThrow()
        }
        val credential = response.credential as DigitalCredential

        // The verifier rejects a failing presentation with HTTP 400, so a failure surfaces here and
        // not in /info. Capture it as a result instead of letting it abort the comparison.
        val submission = runCatching {
            DemoTestBackend.submitDcApiResponse(session.sessionId, credential.credentialJson, deployment)
        }
        submission.exceptionOrNull()?.let { cause ->
            android.util.Log.w("DcApiDeploymentCompare", "${deployment.name} rejected the response: ${cause.message}")
        }
        return DemoTestBackend.verifierSessionInfo(session.sessionId, deployment)
    }

    /** Mirrors `AndroidDigitalCredentialProvider.nativeAppOrigin`, which is internal to the wallet. */
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

                is JsonArray -> element.forEachIndexed { index, value -> walk(value, "$path[$index]") }
                else -> Unit
            }
        }
        walk(this@failedPolicies, "")
    }

    /** First value stored under [key] anywhere in a policy-result tree. */
    private fun JsonElement.firstValueOf(key: String): JsonElement? {
        when (this) {
            is JsonObject -> {
                this[key]?.let { return it }
                values.forEach { child -> child.firstValueOf(key)?.let { return it } }
            }

            is JsonArray -> forEach { child -> child.firstValueOf(key)?.let { return it } }
            else -> Unit
        }
        return null
    }

    private fun JsonElement.executedPolicyIds(): Set<String> = buildSet {
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    element["policy_executed"]?.jsonObject?.get("id")?.jsonPrimitive?.content?.let { add(it) }
                    element.values.forEach(::walk)
                }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this@executedPolicyIds)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
