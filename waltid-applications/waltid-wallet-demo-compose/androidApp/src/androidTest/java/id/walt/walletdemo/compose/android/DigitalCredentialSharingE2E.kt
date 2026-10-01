@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Base64
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.GetCredentialResponse
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.wallet2.mobile.MobileWallet
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.foregroundWindowSnapshot
import id.walt.walletdemo.compose.logic.createAndroidDemoSharingSettingsStore
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewTestTags
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before

/** Shared Credential Manager transport, diagnostics and proof assertions for device E2Es. */
@OptIn(ExperimentalDigitalCredentialApi::class)
internal abstract class DigitalCredentialSharingE2E {
    protected abstract val wallet: MobileWallet
    protected abstract val issuedCredentialIds: Set<String>

    private val activeRequests = mutableListOf<DigitalCredentialRequestHandle>()

    @Before
    fun prepareTest() {
        val fixture = fixture()
        assertCredentialManagerIdle(fixture)
        createAndroidDemoSharingSettingsStore(fixture.context).setShowDcApiPresentationPreview(true)
        activeRequests.clear()
        runBlocking { assertSharedCredentialStateUnchanged() }
    }

    @After
    fun cleanupTest() {
        val fixture = fixture()
        activeRequests.forEach { request -> runCatching { request.abandon() } }
        runBlocking {
            activeRequests.forEach { request ->
                withTimeout(CLEANUP_TIMEOUT) { request.awaitSettled() }
            }
        }
        activeRequests.clear()
        settleCredentialManagerInteraction(fixture)
        createAndroidDemoSharingSettingsStore(fixture.context).setShowDcApiPresentationPreview(true)
        assertEquals(
            "Verifier request registry was not empty after test cleanup",
            0,
            DigitalCredentialTestVerifier.activeRequestCount(),
        )
    }

    /** A device fixture; the wallet itself is provisioned once for the whole test class. */
    protected class Fixture(val context: Context, val device: UiDevice)

    protected fun fixture(): Fixture {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return Fixture(instrumentation.targetContext, UiDevice.getInstance(instrumentation))
    }

    protected fun Fixture.startCredentialRequest(requestJson: String): DigitalCredentialRequestHandle {
        assertCredentialManagerIdle(this)
        val request = DigitalCredentialTestVerifier.prepare()
        activeRequests += request
        context.startActivity(
            Intent(context, DigitalCredentialTestVerifierActivity::class.java)
                .putExtra(EXTRA_REQUEST_ID, request.id)
                .putExtra(EXTRA_REQUEST_JSON, requestJson)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return request
    }

    /** Runs one request handle through Credential Manager and returns the wallet's response. */
    protected suspend fun Fixture.share(
        request: DigitalCredentialRequestHandle,
        candidateText: String,
        onCredentialManagerPrompt: (UiDevice) -> Unit = {},
        beforeShare: (UiDevice) -> Unit = {},
    ): DigitalCredential {
        enterProviderReview(request, candidateText, onCredentialManagerPrompt)
        beforeShare(device)
        clickByTag(device, WALLET_SHARE_BUTTON_TAG)

        val response = withTimeout(CREDENTIAL_OPERATION_TIMEOUT) {
            request.await().getOrThrow()
        }
        return requireNotNull(response.credential as? DigitalCredential) {
            "Caller did not receive a digital credential: ${response.credential}"
        }
    }

    /** Selects a Credential Manager candidate and requires completion without a wallet review. */
    protected suspend fun Fixture.shareWithoutWalletReview(
        request: DigitalCredentialRequestHandle,
        candidateText: String,
    ): DigitalCredential {
        val response = completeWithoutWalletReview(request, candidateText).getOrThrow()
        return requireNotNull(response.credential as? DigitalCredential) {
            "Caller did not receive a digital credential: ${response.credential}"
        }
    }

    /** Also returns submission failures so native authorization can be checked without an app review. */
    protected suspend fun Fixture.completeWithoutWalletReview(
        request: DigitalCredentialRequestHandle,
        candidateText: String,
        onCredentialManagerPrompt: (UiDevice) -> Unit = {},
    ): Result<GetCredentialResponse> {
        val deadline = System.currentTimeMillis() + CREDENTIAL_OPERATION_TIMEOUT
        var candidateSelected = false

        while (!request.isComplete && System.currentTimeMillis() < deadline) {
            assertFalse("Wallet review appeared while preview was disabled", walletReviewVisible())
            if (!candidateSelected && device.findCredentialManagerText(candidateText) != null) {
                onCredentialManagerPrompt(device)
                assertTrue(
                    "Could not click the '$candidateText' candidate",
                    device.clickCredentialManagerCandidate(candidateText),
                )
                candidateSelected = true
            } else if (device.findEnabledCredentialManagerContinue() != null) {
                assertTrue(
                    "Could not click Credential Manager's Continue button",
                    device.clickCredentialManagerNode(::isCredentialManagerContinue),
                )
            }
            delay(CLEANUP_POLL_MILLIS)
        }

        if (!request.isComplete) {
            fail(
                "Credential Manager did not complete without a wallet review.\n" +
                    pickerDiagnostic(request, candidateText, candidateSelected),
            )
        }
        assertFalse("Wallet review appeared while preview was disabled", walletReviewVisible())

        return request.await()
    }

    /** Candidate selection, optional confirmation and provider transition share one deadline. */
    protected fun Fixture.enterProviderReview(
        request: DigitalCredentialRequestHandle,
        candidateText: String,
        onCredentialManagerPrompt: (UiDevice) -> Unit = {},
    ) {
        val deadline = System.currentTimeMillis() + UI_ELEMENT_TIMEOUT
        var promptAsserted = false
        var candidateSelected = false

        while (System.currentTimeMillis() < deadline) {
            if (walletReviewVisible()) return
            if (request.isComplete) {
                fail(
                    "Credential Manager completed the request before wallet provider review opened: " +
                        request.completedResultDescription(),
                )
            }

            if (!candidateSelected && device.findCredentialManagerText(candidateText) != null) {
                if (!promptAsserted) {
                    onCredentialManagerPrompt(device)
                    promptAsserted = true
                }
                assertTrue(
                    "Could not click the '$candidateText' candidate",
                    device.clickCredentialManagerCandidate(candidateText),
                )
                candidateSelected = true
                continue
            }

            if (device.findEnabledCredentialManagerContinue() != null) {
                assertTrue(
                    "Could not click Credential Manager's Continue button",
                    device.clickCredentialManagerNode(::isCredentialManagerContinue),
                )
                continue
            }

            Thread.sleep(CLEANUP_POLL_MILLIS)
        }

        fail(
            "Credential Manager did not open wallet provider review.\n" +
                pickerDiagnostic(request, candidateText, candidateSelected),
        )
    }

    protected suspend fun Fixture.awaitCancellationOutcome(
        request: DigitalCredentialRequestHandle,
        candidateText: String,
    ): Result<GetCredentialResponse> {
        val completed = withTimeoutOrNull(CANCELLATION_TRANSITION_TIMEOUT) {
            while (!request.isComplete) {
                if (!walletReviewVisible() && device.credentialManagerWindowVisible()) {
                    fail(
                        "Credential Manager selector reappeared after provider Cancel without " +
                            "resolving the caller request.\n" +
                            pickerDiagnostic(request, candidateText, candidateSelected = true),
                    )
                }
                delay(CLEANUP_POLL_MILLIS)
            }
            true
        }
        if (completed != true) {
            fail(
                "Provider Cancel did not resolve the caller request within " +
                    "$CANCELLATION_TRANSITION_TIMEOUT ms.\n" +
                    pickerDiagnostic(request, candidateText, candidateSelected = true),
            )
        }
        return request.await()
    }

    /**
     * Drives an unsupported request until Credential Manager shows its empty state. A candidate is a
     * failure, and the request is only allowed to complete after the GMS-scoped Close action.
     */
    protected fun Fixture.awaitUnsupportedRequestEmptyState(
        request: DigitalCredentialRequestHandle,
        protocol: String,
        candidateText: String,
    ) {
        val deadline = System.currentTimeMillis() + UI_ELEMENT_TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            if (device.findCredentialManagerText(candidateText) != null) {
                fail(
                    "Credential Manager surfaced a '$candidateText' candidate for $protocol.\n" +
                        pickerDiagnostic(request, candidateText, candidateSelected = false),
                )
            }
            if (request.isComplete) {
                fail(
                    "$protocol completed before Credential Manager's empty state was dismissed: " +
                        request.completedResultDescription(),
                )
            }
            if (device.findCredentialManagerClose() != null) {
                assertTrue(
                    "Could not dismiss Credential Manager's empty state for $protocol",
                    device.clickCredentialManagerNode(::isCredentialManagerClose),
                )
                return
            }
            Thread.sleep(CLEANUP_POLL_MILLIS)
        }

        fail(
            "Credential Manager did not show its empty state for $protocol.\n" +
                pickerDiagnostic(request, candidateText, candidateSelected = false),
        )
    }

    private suspend fun assertSharedCredentialStateUnchanged() {
        val storedIds = wallet.credentials().map { it.id }.toSet()
        assertEquals(
            "Shared credential state was modified by a previous test",
            issuedCredentialIds,
            storedIds,
        )
    }

    private fun assertCredentialManagerIdle(fixture: Fixture) {
        val deadline = System.currentTimeMillis() + CLEANUP_TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            if (!fixture.walletReviewVisible() &&
                !fixture.device.credentialManagerWindowVisible() &&
                DigitalCredentialTestVerifier.activeRequestCount() == 0
            ) {
                return
            }
            Thread.sleep(CLEANUP_POLL_MILLIS)
        }
        fail("Credential Manager was not idle before the test started.\n${interactionDiagnostic(fixture)}")
    }

    private fun settleCredentialManagerInteraction(fixture: Fixture) {
        val deadline = System.currentTimeMillis() + CLEANUP_TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            val walletReviewVisible = fixture.walletReviewVisible()
            val selectorVisible = fixture.device.credentialManagerWindowVisible()
            if (!walletReviewVisible &&
                !selectorVisible &&
                DigitalCredentialTestVerifier.activeRequestCount() == 0
            ) {
                return
            }
            if (walletReviewVisible || selectorVisible) fixture.device.pressBack()
            Thread.sleep(CLEANUP_POLL_MILLIS)
        }
        fail("Credential Manager did not settle after test cleanup.\n${interactionDiagnostic(fixture)}")
    }

    private fun interactionDiagnostic(fixture: Fixture): String = """
        activeRequestCount=${DigitalCredentialTestVerifier.activeRequestCount()}
        currentPackage=${fixture.device.currentPackageName}
        selectorVisible=${fixture.device.credentialManagerWindowVisible()}
        walletReviewVisible=${fixture.walletReviewVisible()}
        foreground=${foregroundWindowSnapshot(fixture.device)}
    """.trimIndent()

    private fun DigitalCredentialRequestHandle.completedResultDescription(): String {
        val result = completedResult()
            ?: return "request is complete but its result was unavailable"
        result.exceptionOrNull()?.let { exception ->
            return "${exception::class.java.name}: ${exception.message}"
        }
        return "unexpected credential response: ${result.getOrNull()}"
    }

    protected fun Fixture.walletReviewVisible(): Boolean =
        device.findObject(By.res(WALLET_SHARING_REVIEW_TAG)) != null

    private fun UiDevice.credentialManagerWindowVisible(): Boolean =
        currentPackageName == CREDENTIAL_SELECTOR_PACKAGE ||
            findObjects(By.pkg(CREDENTIAL_SELECTOR_PACKAGE)).isNotEmpty()

    protected fun UiDevice.findCredentialManagerText(text: String): UiObject2? =
        credentialManagerNodes().firstOrNull { node ->
            runCatching { node.text?.contains(text) == true }.getOrDefault(false)
        }

    private fun UiDevice.findCredentialManagerClose(): UiObject2? =
        credentialManagerNodes().firstOrNull(::isCredentialManagerClose)

    private fun isCredentialManagerClose(node: UiObject2): Boolean =
        runCatching { node.isEnabled && node.text == CREDENTIAL_SELECTOR_CLOSE_LABEL }.getOrDefault(false)

    private fun UiDevice.clickCredentialManagerCandidate(candidateText: String): Boolean =
        clickCredentialManagerNode { node ->
            runCatching { node.text?.contains(candidateText) == true }.getOrDefault(false)
        }

    private fun UiDevice.clickCredentialManagerNode(matcher: (UiObject2) -> Boolean): Boolean {
        repeat(CANDIDATE_CLICK_ATTEMPTS) {
            val clicked = runCatching {
                val node = credentialManagerNodes().firstOrNull(matcher) ?: return@runCatching false
                (node.clickableAncestorOrSelf() ?: node).click()
                true
            }
            clicked.getOrNull()?.let { if (it) return true }
            if (clicked.exceptionOrNull() !is StaleObjectException) return false
        }
        return false
    }

    protected fun UiDevice.findEnabledCredentialManagerContinue(): UiObject2? =
        credentialManagerNodes().firstOrNull { node ->
            runCatching { node.isEnabled && isCredentialManagerContinue(node) }.getOrDefault(false)
        }

    private fun UiDevice.findCredentialManagerContinue(): UiObject2? =
        credentialManagerNodes().firstOrNull(::isCredentialManagerContinue)

    private fun isCredentialManagerContinue(node: UiObject2): Boolean =
        runCatching {
            node.text?.contains("continue", ignoreCase = true) == true ||
                node.resourceName?.substringAfterLast(':') == "continue_button"
        }.getOrDefault(false)

    private fun UiDevice.credentialManagerNodes(): List<UiObject2> =
        findObjects(By.pkg(CREDENTIAL_SELECTOR_PACKAGE)).flatMap { it.flatten() }

    private fun UiObject2.flatten(): List<UiObject2> =
        listOf(this) + runCatching { children.flatMap { it.flatten() } }.getOrDefault(emptyList())

    protected fun pickerDiagnostic(
        request: DigitalCredentialRequestHandle,
        candidateText: String,
        candidateSelected: Boolean,
    ): String {
        val fixture = fixture()
        val nodes = fixture.device.credentialManagerNodes()
            .take(MAX_ACCESSIBILITY_NODES)
            .mapNotNull { node ->
                runCatching {
                    "package=$CREDENTIAL_SELECTOR_PACKAGE " +
                        "resource=${node.resourceName.orEmpty()} " +
                        "text=${node.text.orEmpty()} " +
                        "class=${node.className} " +
                        "enabled=${node.isEnabled} clickable=${node.isClickable} " +
                        "bounds=${node.visibleBounds.toShortString()}"
                }.getOrNull()
            }
            .joinToString("\n")
        return """
            requestComplete=${request.isComplete}
            currentPackage=${fixture.device.currentPackageName}
            selectorVisible=${fixture.device.credentialManagerWindowVisible()}
            expectedCandidateVisible=${fixture.device.findCredentialManagerText(candidateText) != null}
            expectedCandidateSelected=$candidateSelected
            continueVisible=${fixture.device.findCredentialManagerContinue() != null}
            closeVisible=${fixture.device.findCredentialManagerClose() != null}
            walletReviewVisible=${fixture.walletReviewVisible()}
            foreground=${foregroundWindowSnapshot(fixture.device)}
            accessibilityNodes:
            ${nodes.ifBlank { "<none>" }}
        """.trimIndent()
    }

    /**
     * Posts the wallet's response to verifier2 and asserts it verified.
     *
     * Unlike direct_post, `response_mode=dc_api` sends nothing from the wallet to the verifier: the
     * response returns through the OS to whoever called `getCredential`, and this test is that caller.
     * Verification is inline, so the session is already terminal when this returns.
     */
    protected suspend fun assertVerifierAccepted(
        sessionId: String,
        responseJson: String,
        presentedCredentialId: String,
        requiredPolicyIds: List<String>,
    ): JsonObject {
        DemoTestBackend.submitDcApiResponse(sessionId, responseJson)
        val info = DemoTestBackend.verifierSessionInfo(sessionId)
        assertEquals("SUCCESSFUL", info["status"]?.jsonPrimitive?.content)
        assertNotNull(
            "Verifier did not report the presented credential '$presentedCredentialId': $info",
            info["presented_credentials"]?.jsonObject?.get(presentedCredentialId),
        )
        // A skipped policy leaves the session SUCCESSFUL, so "no failures" alone would pass on a
        // verifier that checked nothing; [requiredPolicyIds] must therefore be asserted as executed.
        val policyResults = info["policy_results"] ?: error("Session info has no policy_results: $info")
        val executed = policyResults.executedPolicyIds()
        requiredPolicyIds.forEach { policyId ->
            assertTrue("$policyId did not run. Executed: $executed", executed.contains(policyId))
        }
        assertTrue(
            "Failed policies: ${policyResults.failedPolicies()}",
            policyResults.failedPolicies().isEmpty(),
        )
        return info
    }

    /** The claims of an SD-JWT VC presentation's key binding JWT, which is its final `~` segment. */
    protected fun keyBindingJwtClaims(presentation: String): JsonObject {
        val keyBindingJwt = presentation.substringAfterLast('~')
        require(keyBindingJwt.isNotBlank()) { "SD-JWT presentation carries no key binding JWT" }
        val payload = keyBindingJwt.split('.').getOrNull(1)
            ?: error("SD-JWT key binding JWT is not a compact JWS: $keyBindingJwt")
        return Json.parseToJsonElement(
            Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING).decodeToString(),
        ).jsonObject
    }

    /**
     * `android:apk-key-hash:<base64url-sha256(signing cert)>`, the origin Credential Manager asserts for
     * a native caller. Mirrors `AndroidDigitalCredentialProvider.nativeAppOrigin`, which is internal to
     * the wallet-mobile module.
     */
    protected fun nativeAppOrigin(context: Context): String {
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

    private fun UiObject2.clickableAncestorOrSelf(): UiObject2? {
        var node: UiObject2? = this
        while (node != null) {
            if (node.isClickable) return node
            node = node.parent
        }
        return null
    }

    protected companion object {
        /** How the amount reads once rendered, on the prompt and on the review alike. */
        const val SCA_AMOUNT_TEXT = "11.56"

        /** Owns `CredentialSelectorActivity`, i.e. the picker window these tests drive. */
        const val CREDENTIAL_SELECTOR_PACKAGE = "com.google.android.gms"

        /** Dismisses Credential Manager's own "Your info wasn't found" state, which has no candidates. */
        private const val CREDENTIAL_SELECTOR_CLOSE_LABEL = "Close"
        private const val CANDIDATE_CLICK_ATTEMPTS = 3
        private const val CLEANUP_TIMEOUT = 10_000L
        private const val CANCELLATION_TRANSITION_TIMEOUT = 10_000L
        private const val CLEANUP_POLL_MILLIS = 200L
        private const val MAX_ACCESSIBILITY_NODES = 80

        /**
         * Compose test tags of the wallet's shared review, exported as Android resource IDs. The same
         * tags the in-app OpenID4VP review is driven by; the provider surface is not a separate UI.
         */
        val WALLET_SHARING_REVIEW_TAG = WalletDemoSharingReviewTestTags.Review
        val WALLET_SHARE_BUTTON_TAG = WalletDemoSharingReviewTestTags.ShareButton

        /**
         * The SD-JWT counterpart: `kb-jwt_signature` is holder binding and `sd_hash-check` ties that
         * signature to the exact disclosure set presented. Issuer authenticity is absent because
         * verifier2's default *VP* policy set for `dc+sd-jwt` contains no issuer-signature policy (see
         * VPVerificationPolicyManager.simpleDcSdJwtPolicies).
         */
        val SD_JWT_REQUIRED_POLICIES = listOf("dc+sd-jwt/kb-jwt_signature", "dc+sd-jwt/sd_hash-check")
    }
}
