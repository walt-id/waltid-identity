@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Base64
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.iso18013.annexc.AnnexCRequest
import id.walt.iso18013.annexc.AnnexCRequestBuilder
import id.walt.iso18013.annexc.AnnexCResponseVerifier
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertTextContainingVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.launchAndUnlock
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.sendDeepLink
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForStatus
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewTestTags
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
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
 * OS-mediated Digital Credentials sharing E2Es against the public demo verifier, one per protocol
 * variant the wallet claims to support natively on Android.
 *
 * The whole chain is real in every case: issuer2 issues into the wallet, Android Credential Manager
 * mediates the picker, the wallet's own provider activity builds the response, and it is verified by
 * the code that would verify it in production - verifier2 for the OpenID4VP variants, and the shared
 * `waltid-18013-7-verifier` reader for Annex C. Nothing is stubbed, so a wrong session transcript, a
 * re-encoded issuer signature or a missing disclosure fails a test instead of passing it.
 *
 * Annex C is verified in-process rather than against the hosted verifier, and that is not a shortcut
 * around the wallet. Annex C has no back-channel: the encrypted DeviceResponse travels back through
 * the OS to whoever called `getCredential`, so the reader and the caller are necessarily the same
 * party. The request is built with the shared verifier-side [AnnexCRequestBuilder] and decrypted with
 * [AnnexCResponseVerifier], which is what the deployed verifier runs. A hosted Annex C session could
 * not be bound to this caller anyway: its setup requires a secure-context origin, while Credential
 * Manager asserts `android:apk-key-hash:...` for a native caller.
 *
 * These require an emulator image with Google Play services. The regular AOSP device-test lane
 * intentionally skips them; the dedicated Play Store lane runs them.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalDigitalCredentialApi::class)
class DigitalCredentialSharingE2ETest {

    /**
     * The baseline: `response_mode=dc_api`, one mdoc, no encryption, verified by verifier2's real
     * policy set.
     */
    @Test
    fun sharesMdocThroughClearOpenId4VpDcApi() = runBlocking {
        val fixture = start() ?: return@runBlocking
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        fixture.issue(scenario)

        // The verifier hashes expectedOrigins.first() into the mdoc session transcript and the wallet
        // hashes what Credential Manager asserts for this (native, non-browser) caller. The debug
        // signing key differs per machine and per CI runner, so derive it at runtime rather than
        // pinning a fingerprint.
        val session = DemoTestBackend.createDcApiVerifierSession(
            scenario = scenario,
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
        )

        val credential = fixture.share(session.requestJson, candidateText = MDL_DOC_TYPE)
        val responseJson = Json.parseToJsonElement(credential.credentialJson).jsonObject
        assertEquals("openid4vp-v1-unsigned", responseJson["protocol"]?.jsonPrimitive?.content)
        val data = responseJson["data"]?.jsonObject
        assertNotNull("DC API response carries no data object", data)
        assertNotNull("Clear dc_api response carries no vp_token", data!!["vp_token"])

        assertVerifierAccepted(
            sessionId = session.sessionId,
            responseJson = credential.credentialJson,
            presentedCredentialId = "mdl",
            requiredPolicyIds = MDOC_REQUIRED_POLICIES,
        )
    }

    /**
     * SD-JWT VC plus `transaction_data`, which is the case where consent and cryptography have to
     * agree: the wallet signs the transaction-data hashes into the KB-JWT, so anything it did not
     * show the user is something it authorized on their behalf unseen. Both halves are asserted -
     * the fields on screen, and the hashes in the KB-JWT.
     */
    @Test
    fun sharesSdJwtWithTransactionDataThroughCredentialManager() = runBlocking {
        val fixture = start() ?: return@runBlocking
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "eudi-pid-sdjwt" }
        fixture.issue(scenario)

        val transactionData = DemoTestBackend.paymentAuthorizationTransactionData(credentialId = "pid")
        val session = DemoTestBackend.createDcApiVerifierSession(
            credentialQueries = listOf(scenario.verifierCredentialQuery),
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
            transactionData = listOf(transactionData),
        )

        val credential = fixture.share(
            requestJson = session.requestJson,
            // The registry entry's subtitle is the credential type, and for this SD-JWT VC the vct is
            // the issuer-scoped URL ending in the configuration id.
            candidateText = scenario.credentialConfigurationId,
            // Everything the presentation will sign over must be on the consent surface before the
            // Share button is used, so this is asserted between the two.
            beforeShare = { device ->
                listOf(PAYMENT_AUTHORIZATION_DISPLAY_NAME, "42.00", "EUR", "ACME Corp").forEach { expected ->
                    assertTextContainingVisibleAfterScrolling(
                        device = device,
                        substring = expected,
                        message = "Review did not show '$expected' before sharing",
                    )
                }
            },
        )

        val responseJson = Json.parseToJsonElement(credential.credentialJson).jsonObject
        assertEquals("openid4vp-v1-unsigned", responseJson["protocol"]?.jsonPrimitive?.content)
        val presentation = requireNotNull(
            responseJson["data"]?.jsonObject
                ?.get("vp_token")?.jsonObject
                ?.get("pid")?.jsonArray?.firstOrNull()?.jsonPrimitive?.content,
        ) { "SD-JWT response carries no presentation for query 'pid': $responseJson" }

        // sha-256 over the base64url transaction_data entry the verifier sent, which is what
        // OpenID4VP binds. Recomputing it here rather than trusting whatever the wallet emitted is
        // the point: a wallet that hashed the decoded object, or hashed a different item, fails.
        val requestedItem = requireNotNull(
            Json.parseToJsonElement(session.requestJson).jsonObject["requests"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("transaction_data")?.jsonArray
                ?.singleOrNull()?.jsonPrimitive?.content,
        ) { "Verifier session did not carry exactly one transaction_data item" }
        val expectedHash = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(requestedItem.encodeToByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val keyBindingClaims = keyBindingJwtClaims(presentation)
        assertEquals(
            "KB-JWT does not bind the requested transaction data: $keyBindingClaims",
            listOf(expectedHash),
            keyBindingClaims["transaction_data_hashes"]?.jsonArray?.map { it.jsonPrimitive.content },
        )

        assertVerifierAccepted(
            sessionId = session.sessionId,
            responseJson = credential.credentialJson,
            presentedCredentialId = "pid",
            // The transaction-data policy is named explicitly: it is the verifier-side counterpart of
            // the KB-JWT assertion above, and it is the one that would silently not run if the
            // verifier stopped recognising the item.
            requiredPolicyIds = SD_JWT_REQUIRED_POLICIES + "dc+sd-jwt/transaction-data-hash-check",
        )
    }

    /**
     * `response_mode=dc_api.jwt`. mdoc is chosen deliberately over SD-JWT: with an encrypted response
     * the verifier must both decrypt the JWE *and* rebuild the mdoc session transcript from the
     * thumbprint of the very encryption key it published, so a wallet that thumbprinted anything else
     * produces a readable JWE whose device signature does not verify.
     */
    @Test
    fun sharesMdocThroughEncryptedDcApiJwt() = runBlocking {
        val fixture = start() ?: return@runBlocking
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        fixture.issue(scenario)

        val session = DemoTestBackend.createDcApiVerifierSession(
            credentialQueries = listOf(scenario.verifierCredentialQuery),
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
            encryptedResponse = true,
        )

        val credential = fixture.share(session.requestJson, candidateText = MDL_DOC_TYPE)
        val responseJson = Json.parseToJsonElement(credential.credentialJson).jsonObject
        assertEquals("openid4vp-v1-unsigned", responseJson["protocol"]?.jsonPrimitive?.content)
        val data = requireNotNull(responseJson["data"]?.jsonObject) { "Response carries no data: $responseJson" }
        // Asserting the exact member set, not just that "response" exists: an implementation that
        // encrypted the members *and* left them in the clear would satisfy a weaker assertion while
        // disclosing everything encryption was asked for.
        assertEquals("Encrypted response must carry only 'response': ${data.keys}", setOf("response"), data.keys)
        val compactJwe = requireNotNull(data["response"]?.jsonPrimitive?.content) { "No response member" }
        assertEquals("Encrypted response is not a compact JWE: $compactJwe", 5, compactJwe.split('.').size)

        // Verification is what proves this readable: verifier2 decrypts with its own private key and
        // then runs device-auth against the transcript it derives from its published key.
        assertVerifierAccepted(
            sessionId = session.sessionId,
            responseJson = credential.credentialJson,
            presentedCredentialId = "mdl",
            requiredPolicyIds = MDOC_REQUIRED_POLICIES,
        )
    }

    /**
     * Two alternatives in one request envelope - a protocol this wallet does not support at index 0,
     * `org-iso-mdoc` for the mDL at index 1 - with the Annex C one selected.
     *
     * This is the case a wallet that picks a protocol itself gets wrong. The user chooses an
     * alternative in the picker and the wallet must answer *that* one; answering index 0 because it
     * happens to be first answers a request the user never saw. Selecting the non-zero alternative is
     * therefore the whole point, and Multipaz's Annex C matcher attributes its selection by protocol
     * rather than by request index, so this also covers the attribution path that carries no index at
     * all.
     *
     * Index 0 is `preview` - the legacy Digital Credentials protocol identifier - rather than an
     * OpenID4VP alternative, and that is a platform constraint rather than a preference. Two
     * independent behaviours of the registered matcher pair make an OpenID4VP alternative and an Annex
     * C one mutually exclusive on Android today, in either order:
     *
     * 1. Multipaz's Annex C matcher iterates `requests[]` and stops at the first entry whose
     *    `protocol` it recognises - and it recognises all three `openid4vp-v1-*` values as well as
     *    `org-iso-mdoc`. An OpenID4VP entry ahead of the Annex C one therefore ends its scan before it
     *    reaches Annex C, whether or not that entry matches anything. An unrecognised protocol is
     *    skipped, which is what makes this envelope work.
     * 2. When both registries produce a candidate for the same request, only the OpenID4VP registry's
     *    candidates reach the picker, so an Annex C entry ahead of a *matchable* OpenID4VP one is
     *    matched and then dropped.
     *
     * Neither is wallet behaviour and neither is reachable from wallet code: the AndroidX OpenID4VP
     * matcher is embedded in `androidx.credentials.registry` and `OpenId4VpRegistry` accepts no
     * replacement. The wallet's own selection across a mixed OpenID4VP/Annex C envelope is covered by
     * host tests instead, which is where it can be exercised without the platform picker.
     */
    @Test
    fun selectsNonZeroAnnexCAlternativeFromMultiProtocolRequest() = runBlocking {
        val fixture = start() ?: return@runBlocking
        val mdlScenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        fixture.issue(mdlScenario)

        val readerKey = annexCReaderKey()
        val annexCRequest = AnnexCRequestBuilder.build(
            docType = MDL_DOC_TYPE,
            requestedElements = mapOf(MDL_NAMESPACE to REQUESTED_MDL_ELEMENTS),
            nonce = ByteArray(16) { (it * 7 + 1).toByte() },
            recipientPublicKey = readerKey.publicCoseKey(),
        )
        val envelope = Json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put(
                    "requests",
                    buildJsonArray {
                        add(unsupportedProtocolRequestEntry())
                        add(annexCRequestEntry(annexCRequest))
                    },
                )
            },
        )

        // The mDL can only have come from requests[1]: requests[0] is a protocol no registered matcher
        // claims, so nothing it contains can produce a candidate.
        val credential = fixture.share(envelope, candidateText = MDL_DOC_TYPE, clickCandidate = true)
        val responseJson = Json.parseToJsonElement(credential.credentialJson).jsonObject
        assertEquals("org-iso-mdoc", responseJson["protocol"]?.jsonPrimitive?.content)
        val encryptedResponse = requireNotNull(
            responseJson["data"]?.jsonObject?.get("response")?.jsonPrimitive?.content,
        ) { "Annex C response carries no encrypted response: $responseJson" }

        // The single real control in this flow. `info` is CBOR(SessionTranscript) over
        // sha256(cbor([encryptionInfoB64, origin])), so decryption succeeds only if the wallet hashed
        // the origin Credential Manager asserted for this caller and the same encryptionInfo string
        // the reader sent. Any drift in either fails here rather than producing a wrong plaintext.
        val deviceResponse = coseCompliantCbor.decodeFromByteArray(
            DeviceResponse.serializer(),
            AnnexCResponseVerifier.decryptToDeviceResponse(
                encryptedResponseB64 = encryptedResponse,
                encryptionInfoB64 = annexCRequest.encryptionInfoB64,
                origin = nativeAppOrigin(fixture.context),
                recipientPrivateKey = readerKey,
            ),
        )
        assertEquals("1.0", deviceResponse.version)
        assertEquals(0u, deviceResponse.status)
        val documents = requireNotNull(deviceResponse.documents) { "Annex C DeviceResponse has no documents" }
        assertEquals(1, documents.size)
        val document = documents.single()
        assertEquals(MDL_DOC_TYPE, document.docType)

        // Without this the test would pass on an empty but well-formed DeviceResponse, which is
        // exactly what a broken matcher selection or a dropped disclosure produces.
        val issuerNamespaces = requireNotNull(document.issuerSigned.namespaces) {
            "Annex C document carries no issuer-signed namespaces"
        }
        val disclosed = requireNotNull(issuerNamespaces[MDL_NAMESPACE]) {
            "Annex C document does not disclose $MDL_NAMESPACE: ${issuerNamespaces.keys}"
        }.entries.map { it.value.elementIdentifier }.toSet()
        assertEquals(
            "Annex C response disclosed the wrong elements",
            REQUESTED_MDL_ELEMENTS.toSet(),
            disclosed,
        )
        assertNotNull("Annex C document carries no device signature", document.deviceSigned)
    }

    /**
     * An unlocked wallet on a device that can run these tests, or null when it cannot.
     *
     * Bundling the instrumentation handles keeps each test's own body about the protocol variant it
     * covers rather than about UiAutomator setup.
     */
    private class Fixture(val context: Context, val device: UiDevice)

    private fun start(): Fixture? {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(
            "Digital Credentials E2E requires an Android emulator with Google Play services",
            hasGooglePlayServices(context),
        )
        val fixture = Fixture(context, UiDevice.getInstance(instrumentation))
        launchAndUnlock(fixture.context, fixture.device)
        return fixture
    }

    /** Issues one credential from the public demo issuer through the wallet's own receive flow. */
    private suspend fun Fixture.issue(scenario: DemoTestBackend.CredentialScenario) {
        val offer = DemoTestBackend.createOffer(scenario)
        sendDeepLink(context, offer.offerUrl)
        clickByTag(device, "wallet.receiveButton")
        assertTrue(
            "Offer preview for ${scenario.id} did not appear",
            waitForStatus(
                device = device,
                timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
                matcher = { it.startsWith("Review credential offer") },
                failurePrefixes = listOf("Receive failed", "Bootstrap failed"),
            ),
        )
        clickByTag(device, "wallet.offerAcceptButton")
        assertTrue(
            "${scenario.id} was not received",
            waitForStatus(
                device = device,
                timeoutMs = CREDENTIAL_OPERATION_TIMEOUT,
                matcher = { it.startsWith("Received") },
                failurePrefixes = listOf("Receive failed", "Bootstrap failed"),
            ),
        )
    }

    /**
     * Runs [requestJson] through Credential Manager and returns the wallet's response.
     *
     * @param candidateText Text that must appear among the picker's candidates. For a request with
     *   more than one alternative this is what identifies which one is being chosen.
     * @param clickCandidate Whether to select that candidate explicitly. Needed only when the picker
     *   offers a choice; with a single candidate the OS pre-selects it.
     * @param beforeShare Assertions to run on the wallet's own consent surface, before it is accepted.
     */
    private suspend fun Fixture.share(
        requestJson: String,
        candidateText: String,
        clickCandidate: Boolean = false,
        beforeShare: (UiDevice) -> Unit = {},
    ): DigitalCredential {
        DigitalCredentialTestVerifier.reset(requestJson)
        // The previous test's picker window can still be up, and it showed candidate text too. A node
        // bound from it goes stale the moment it tears down, so wait it out before looking at all -
        // otherwise the candidate found below may belong to a request that is already over.
        device.wait(Until.gone(By.pkg(CREDENTIAL_SELECTOR_PACKAGE).depth(0)), UI_ELEMENT_TIMEOUT)
        context.startActivity(
            Intent(context, DigitalCredentialTestVerifierActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        val candidate = device.wait(Until.findObject(By.textContains(candidateText)), UI_ELEMENT_TIMEOUT)
        assertNotNull("Credential Manager did not surface a '$candidateText' candidate", candidate)
        // Re-resolved rather than reused: between the wait above and this click the picker may have
        // recomposed, and a stale node throws instead of failing an assertion.
        if (clickCandidate) {
            assertTrue(
                "Could not click the '$candidateText' candidate",
                device.clickCandidate(candidateText),
            )
        }

        // The picker only asks for confirmation when it has something to confirm; after an explicit
        // candidate click some builds go straight to the provider. A missing consent step that was
        // actually required still fails, on the Share button that then never appears.
        val continueButton = device.wait(Until.findObject(By.res("continue_button")), UI_ELEMENT_TIMEOUT)
            ?: device.wait(Until.findObject(By.text("Continue")), UI_ELEMENT_TIMEOUT)
        continueButton?.click()

        // The provider surface is the wallet's own Compose review, so it is driven by the shared test
        // tags the in-app presentation flow uses. Waiting on the review root before the Share button
        // keeps a failure attributable: a review that never opened reads differently from a review
        // that opened without an enabled Share action.
        assertNotNull(
            "Wallet provider review did not open",
            waitForResource(device, WALLET_SHARING_REVIEW_TAG, UI_ELEMENT_TIMEOUT),
        )

        beforeShare(device)

        clickByTag(device, WALLET_SHARE_BUTTON_TAG)

        val response = withTimeout(CREDENTIAL_OPERATION_TIMEOUT) {
            DigitalCredentialTestVerifier.await().getOrThrow()
        }
        return requireNotNull(response.credential as? DigitalCredential) {
            "Caller did not receive a digital credential: ${response.credential}"
        }
    }

    /**
     * Posts the wallet's response to verifier2 and asserts it verified.
     *
     * Unlike direct_post, `response_mode=dc_api` sends nothing from the wallet to the verifier: the
     * response comes back through the OS to whoever called `getCredential`, and that caller delivers
     * it. This test is that caller, so it posts - which is also what makes the verifier run its real
     * policies over the wallet's output. Verification is inline, so the session is already terminal
     * when this returns and there is nothing to poll for.
     */
    private suspend fun assertVerifierAccepted(
        sessionId: String,
        responseJson: String,
        presentedCredentialId: String,
        requiredPolicyIds: List<String>,
    ) {
        DemoTestBackend.submitDcApiResponse(sessionId, responseJson)
        val info = DemoTestBackend.verifierSessionInfo(sessionId)
        assertEquals("SUCCESSFUL", info["status"]?.jsonPrimitive?.content)
        assertNotNull(
            "Verifier did not report the presented credential '$presentedCredentialId': $info",
            info["presented_credentials"]?.jsonObject?.get(presentedCredentialId),
        )
        // The sessions are created without a vp_policies override, so the verifier applies its full
        // default set. [requiredPolicyIds] names the ones that would otherwise fail silently by not
        // running - a policy that is skipped rather than failed leaves the session SUCCESSFUL, so
        // "no failures" alone would pass on a verifier that checked nothing.
        //
        // Note what mso_mdoc/issuer_auth passing here does *not* establish. It proves the wallet
        // relayed the issuer signature unaltered - a re-encoded COSE_Sign1 would fail it - but not
        // that the document signer meets the ISO 18013-5 profile. verifier2.demo.walt.id 0.23.0
        // predates that enforcement, and the certificate issuer2.demo.walt.id actually signs with is
        // X.509 v1 with no extensions, so it has neither keyUsage:digitalSignature nor
        // EKU 1.0.18013.5.1.2. A verifier that does enforce the profile rejects this same
        // presentation - which is a deployment gap on the issuer, not something a wallet change
        // fixes, and not something this assertion can be strengthened to catch.
        val policyResults = info["policy_results"] ?: error("Session info has no policy_results: $info")
        val executed = policyResults.executedPolicyIds()
        requiredPolicyIds.forEach { policyId ->
            assertTrue("$policyId did not run. Executed: $executed", executed.contains(policyId))
        }
        assertTrue(
            "Failed policies: ${policyResults.failedPolicies()}",
            policyResults.failedPolicies().isEmpty(),
        )
    }

    /**
     * The reader's recipient key. Annex C fixes the suite at
     * DHKEM(P-256)/HKDF-SHA256/AES-128-GCM, so this is P-256 key agreement and nothing is negotiable.
     */
    private suspend fun annexCReaderKey(): Key =
        CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("annex-c-e2e-reader"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )

    private suspend fun Key.publicCoseKey() =
        requireNotNull(capabilities.publicKeyExporter) { "Reader recipient key does not export its public key" }
            .exportPublicKey().toPublicJwk(spec).toCoseKey()

    /**
     * An alternative no registered matcher claims. `preview` is the legacy Digital Credentials
     * protocol identifier, so this is a value a real verifier could offer for backwards compatibility
     * rather than one invented for the test - and one this wallet reports as unsupported.
     */
    private fun unsupportedProtocolRequestEntry(): JsonObject = buildJsonObject {
        put("protocol", JsonPrimitive("preview"))
        put("data", buildJsonObject { put("selector", buildJsonObject { }) })
    }

    /** One `requests[]` entry as Credential Manager routes it, by `protocol`. */
    private fun annexCRequestEntry(request: AnnexCRequest): JsonObject = buildJsonObject {
        put("protocol", JsonPrimitive("org-iso-mdoc"))
        put(
            "data",
            buildJsonObject {
                put("deviceRequest", JsonPrimitive(request.deviceRequestB64))
                put("encryptionInfo", JsonPrimitive(request.encryptionInfoB64))
            },
        )
    }

    /** The claims of an SD-JWT VC presentation's key binding JWT, which is its final `~` segment. */
    private fun keyBindingJwtClaims(presentation: String): JsonObject {
        val keyBindingJwt = presentation.substringAfterLast('~')
        require(keyBindingJwt.isNotBlank()) { "SD-JWT presentation carries no key binding JWT" }
        val payload = keyBindingJwt.split('.').getOrNull(1)
            ?: error("SD-JWT key binding JWT is not a compact JWS: $keyBindingJwt")
        return Json.parseToJsonElement(
            Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING).decodeToString(),
        ).jsonObject
    }

    /**
     * `android:apk-key-hash:<base64url-sha256(signing cert)>` - the origin Credential Manager
     * asserts for a native caller. Mirrors `AndroidDigitalCredentialProvider.nativeAppOrigin`,
     * which is internal to the wallet-mobile module.
     */
    private fun nativeAppOrigin(context: Context): String {
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

    /**
     * Clicks the candidate whose text contains [candidateText], retrying while the picker is still
     * settling.
     *
     * The node is looked up inside the retry rather than passed in: walking to a clickable ancestor
     * touches the accessibility tree several times, and any of those touches throws
     * [StaleObjectException] if the window recomposed in between. Retrying the whole lookup is what
     * makes that recoverable; reusing a node is what made it fatal.
     */
    private fun UiDevice.clickCandidate(candidateText: String): Boolean {
        repeat(CANDIDATE_CLICK_ATTEMPTS) {
            val clicked = runCatching {
                val candidate = wait(Until.findObject(By.textContains(candidateText)), UI_ELEMENT_TIMEOUT)
                    ?: return@runCatching false
                (candidate.clickableAncestorOrSelf() ?: candidate).click()
                true
            }
            clicked.getOrNull()?.let { if (it) return true }
            if (clicked.exceptionOrNull() !is StaleObjectException) return false
        }
        return false
    }

    private fun UiObject2.clickableAncestorOrSelf(): UiObject2? {
        var node: UiObject2? = this
        while (node != null) {
            if (node.isClickable) return node
            node = node.parent
        }
        return null
    }

    private fun hasGooglePlayServices(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo("com.google.android.gms", 0) }.isSuccess

    private companion object {
        private const val MDL_DOC_TYPE = "org.iso.18013.5.1.mDL"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
        private val REQUESTED_MDL_ELEMENTS = listOf("family_name", "given_name")
        private const val PAYMENT_AUTHORIZATION_DISPLAY_NAME = "Payment Authorization"

        /** Owns `CredentialSelectorActivity`, i.e. the picker window these tests drive. */
        private const val CREDENTIAL_SELECTOR_PACKAGE = "com.google.android.gms"
        private const val CANDIDATE_CLICK_ATTEMPTS = 3

        /**
         * Compose test tags of the wallet's shared review, exported as Android resource IDs.
         *
         * These are the same tags the in-app OpenID4VP review is driven by, which is the point: the
         * provider surface is not a separate UI with its own selectors, so a change that broke one of
         * these would break both lanes rather than only this one.
         */
        private val WALLET_SHARING_REVIEW_TAG = WalletDemoSharingReviewTestTags.Review
        private val WALLET_SHARE_BUTTON_TAG = WalletDemoSharingReviewTestTags.ShareButton

        /**
         * The policy ids verifier2 reports for its default mdoc set, restricted to the two that must
         * not merely "not fail": holder binding, which for the DC API is the session-transcript check
         * and so is the one control specific to this flow, and issuer authenticity, whose absence
         * would quietly reduce the whole assertion to a binding-only check.
         */
        private val MDOC_REQUIRED_POLICIES = listOf("mso_mdoc/device-auth", "mso_mdoc/issuer_auth")

        /**
         * The SD-JWT counterpart. `kb-jwt_signature` is holder binding, and `sd_hash-check` is what
         * ties that signature to the exact disclosure set presented - without it a wallet could sign
         * a KB-JWT over one presentation and ship another. Issuer authenticity is deliberately not
         * listed: verifier2's default *VP* policy set for `dc+sd-jwt` contains no issuer-signature
         * policy (see VPVerificationPolicyManager.simpleDcSdJwtPolicies), so naming one here would
         * assert coverage this session does not have.
         */
        private val SD_JWT_REQUIRED_POLICIES = listOf("dc+sd-jwt/kb-jwt_signature", "dc+sd-jwt/sd_hash-check")
    }
}
