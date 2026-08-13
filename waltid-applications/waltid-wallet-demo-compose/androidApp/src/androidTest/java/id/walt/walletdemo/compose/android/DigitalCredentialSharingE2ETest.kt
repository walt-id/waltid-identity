@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Base64
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.exceptions.GetCredentialCancellationException
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
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertClaimValueVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertTextContainingVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertTextContainingVisibleInForegroundWindow
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.foregroundWindowSnapshot
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/**
 * OS-mediated Digital Credentials sharing E2Es against the public demo verifier, one per protocol
 * variant the wallet supports natively on Android. Nothing is stubbed: issuer2 issues, Credential
 * Manager mediates, and verifier2 verifies the OpenID4VP variants.
 *
 * Annex C is verified in-process with the shared verifier-side [AnnexCRequestBuilder] and
 * [AnnexCResponseVerifier], because it has no back-channel - the encrypted DeviceResponse returns
 * through the OS to whoever called `getCredential`, so reader and caller are the same party. A hosted
 * session could not be bound to this caller either: it requires a secure-context origin, while
 * Credential Manager asserts `android:apk-key-hash:...` for a native one.
 *
 * These require Google Play services, so only the dedicated Play Store lane runs them.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalDigitalCredentialApi::class)
class DigitalCredentialSharingE2ETest {

    /** The baseline: `response_mode=dc_api`, one mdoc, no encryption. */
    @Test
    fun sharesMdocThroughClearOpenId4VpDcApi() = runBlocking {
        val fixture = start() ?: return@runBlocking
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        fixture.issue(scenario)

        // Both sides hash this origin into the mdoc session transcript. The debug signing key differs
        // per machine and per CI runner, so it is derived at runtime rather than pinned.
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
     * SD-JWT VC plus `transaction_data`, where consent and cryptography must agree: the wallet signs
     * the transaction-data hashes into the KB-JWT, so both halves are asserted - the fields on screen,
     * and the hashes in the KB-JWT.
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

        // OpenID4VP binds sha-256 over the base64url transaction_data entry as sent. Recomputed here so
        // that a wallet which hashed the decoded object, or a different item, fails.
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
            // Named explicitly because it is the policy that would silently not run if the verifier
            // stopped recognising the item.
            requiredPolicyIds = SD_JWT_REQUIRED_POLICIES + "dc+sd-jwt/transaction-data-hash-check",
        )
    }

    /**
     * The EUDI TS-12 SCA payment demo: an mdoc plus one `urn:eudi:sca:payment:1` transaction data entry
     * whose payload nests (`payee.name`) where the walt.id payment-authorization type is flat, presented
     * together with a second credential the transaction data is *not* bound to - the entry names only the
     * payment card, and the age credential comes from an independent DCQL query.
     *
     * Four things this shape exercises, each of which fails silently rather than loudly if broken:
     *
     * 1. The matcher dispatches on the transaction data type. For `urn:eudi:sca:payment:1` it
     *    reads `payload.payee.name` and `payload.currency`/`payload.amount`; pairing that payload with a
     *    different type sends it down a flat-field branch, which finds nothing and produces a prompt with
     *    no payment details. Asserted on Credential Manager's own prompt, not just the wallet's.
     * 2. The wallet's review has to say what is being authorized, so nested leaves are qualified with the
     *    object they came from - "Payee name", not a bare "Name".
     * 3. Signing the transaction data requires its type to be authorized in the mdoc MSO's
     *    KeyAuthorizations. Without that the wallet cannot sign, and
     *    `mso_mdoc/transaction-data-hash-check` is what proves it did.
     * 4. Combining the two is what AndroidX's embedded matcher cannot report, and the reason the wallet
     *    vendors Google's newer one (see `OPENID4VP-MATCHER.md`): it declares the option with arity 2 and
     *    then emits only the payment credential, so the platform discards the whole option and the picker
     *    shows nothing at all. The fault is in its transaction data reporting path rather than in DCQL
     *    matching. What has to hold instead is that both credentials reach the same option, the payment
     *    prompt still renders, and the transaction data binds to the payment card alone.
     */
    @Test
    @Ignore(
        "Enable once the urn:eudi:sca:payment:1 transaction data profile, the scaPaymentCardMdoc " +
            "issuance profile from this branch and euAgeVerificationMdoc are deployed together to " +
            "verifier2.demo.walt.id and issuer2.demo.walt.id.",
    )
    fun sharesMdocWithScaPaymentTransactionDataAndSecondCredential() = runBlocking {
        val fixture = start() ?: return@runBlocking
        val scaScenario = DemoTestBackend.presentationScenarios.first { it.id == "sca-payment-card" }
        val ageScenario = DemoTestBackend.presentationScenarios.first { it.id == "eu-age-verification" }
        fixture.issue(scaScenario)
        fixture.issue(ageScenario)

        val transactionData = DemoTestBackend.scaPaymentTransactionData(credentialId = SCA_CREDENTIAL_QUERY_ID)
        val session = DemoTestBackend.createDcApiVerifierSession(
            credentialQueries = listOf(scaScenario.verifierCredentialQuery, ageScenario.verifierCredentialQuery),
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
            transactionData = listOf(transactionData),
        )

        val credential = fixture.share(
            requestJson = session.requestJson,
            candidateText = SCA_DOC_TYPE,
            onCredentialManagerPrompt = { device ->
                // The payment prompt survives the second credential: these come from the matcher's
                // payment entry, which is the half AndroidX's matcher drops the whole option over.
                listOf(DemoTestBackend.SCA_PAYMENT_PAYEE_NAME, SCA_AMOUNT_TEXT).forEach { expected ->
                    assertTextContainingVisibleInForegroundWindow(
                        device = device,
                        substring = expected,
                        message = "Credential Manager prompt did not show '$expected'",
                    )
                }
                // And the age credential is in the same option rather than a separate one, which is
                // what makes this a combined presentation instead of two consecutive requests.
                assertTextContainingVisibleInForegroundWindow(
                    device = device,
                    substring = AGE_DOC_TYPE,
                    message = "Credential Manager prompt did not surface the second credential",
                )
            },
            beforeShare = { device ->
                // Labels as well as values: an unqualified "Name" row is the regression this guards.
                assertClaimValueVisibleAfterScrolling(
                    device = device,
                    path = "transactionData[0].details.payload.payee.name",
                    label = "Payee name",
                    expectedValues = listOf(DemoTestBackend.SCA_PAYMENT_PAYEE_NAME),
                    message = "SCA payee name missing from the wallet review",
                )
                assertClaimValueVisibleAfterScrolling(
                    device = device,
                    path = "transactionData[0].details.payload.amount",
                    label = "Amount",
                    expectedValues = listOf(SCA_AMOUNT_TEXT),
                    message = "SCA payment amount missing from the wallet review",
                )
                assertClaimValueVisibleAfterScrolling(
                    device = device,
                    path = "transactionData[0].details.payload.currency",
                    label = "Currency",
                    expectedValues = listOf(DemoTestBackend.SCA_PAYMENT_CURRENCY),
                    message = "SCA payment currency missing from the wallet review",
                )
                // The review received both credentials, not just the one the prompt was built from.
                // The requested disclosure is asserted rather than the credential card, because a card
                // would also appear for a credential the platform offered without any claims selected.
                assertTextContainingVisibleAfterScrolling(
                    device = device,
                    substring = AGE_DISCLOSURE_LABEL,
                    message = "Review did not receive the second credential",
                )
            },
        )

        val responseJson = Json.parseToJsonElement(credential.credentialJson).jsonObject
        assertEquals("openid4vp-v1-unsigned", responseJson["protocol"]?.jsonPrimitive?.content)
        val vpToken = requireNotNull(responseJson["data"]?.jsonObject?.get("vp_token")?.jsonObject) {
            "Response carries no vp_token: $responseJson"
        }
        assertEquals(
            "Combined presentation must answer both queries: ${vpToken.keys}",
            setOf(SCA_CREDENTIAL_QUERY_ID, AGE_CREDENTIAL_QUERY_ID),
            vpToken.keys,
        )

        assertVerifierAccepted(
            sessionId = session.sessionId,
            responseJson = credential.credentialJson,
            presentedCredentialId = SCA_CREDENTIAL_QUERY_ID,
            requiredPolicyIds = MDOC_REQUIRED_POLICIES + "mso_mdoc/transaction-data-hash-check",
        )
        // Named separately: the verifier reports per credential, and this is what proves the age
        // credential was verified rather than merely carried along.
        assertNotNull(
            "Verifier did not report the second credential",
            DemoTestBackend.verifierSessionInfo(session.sessionId)["presented_credentials"]
                ?.jsonObject?.get(AGE_CREDENTIAL_QUERY_ID),
        )
    }

    /**
     * The wallet registers only `openid4vp-v1-unsigned`, and the vendored matcher honours that: a signed
     * or multisigned request must not surface this wallet at all, because it cannot fulfill one.
     *
     * Each unsupported request carries the *same* OpenID4VP payload that
     * [sharesMdocThroughClearOpenId4VpDcApi] matches successfully, re-wrapped into the structurally valid
     * signed and multisigned shapes the matcher's own parsers accept (see [signedDcApiRequest] and
     * [multisignedDcApiRequest]). That is what makes this decisive rather than incidental: relabelling
     * the protocol alone would also pass simply because the payload no longer parses. Here, if protocol
     * filtering stopped working - the registry advertising a signed protocol, or the matcher ignoring
     * `supported_protocols` - the matcher would decode these payloads, run the same mDL DCQL query, and
     * surface the issued mDL, failing this test.
     *
     * Asserted through the real caller rather than the picker, because "no provider" is what the caller
     * observes.
     */
    @Test
    fun doesNotSurfaceForSignedOrMultisignedRequests() = runBlocking {
        val fixture = start() ?: return@runBlocking
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        fixture.issue(scenario)
        val session = DemoTestBackend.createDcApiVerifierSession(
            scenario = scenario,
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
        )
        // The payload a matchable unsigned request carries, transplanted verbatim below.
        val openId4VpPayload = requireNotNull(
            Json.parseToJsonElement(session.requestJson).jsonObject["requests"]
                ?.jsonArray?.firstOrNull()?.jsonObject?.get("data")?.jsonObject,
        ) { "Unsigned DC API request carries no object 'data': ${session.requestJson}" }

        listOf(
            "openid4vp-v1-signed" to signedDcApiRequest(openId4VpPayload),
            "openid4vp-v1-multisigned" to multisignedDcApiRequest(openId4VpPayload),
        ).forEach { (protocol, request) ->
            DigitalCredentialTestVerifier.reset(request)
            fixture.context.startActivity(
                Intent(fixture.context, DigitalCredentialTestVerifierActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )

            // A candidate appearing at all is the failure: it would let a user authorize a presentation
            // the wallet then cannot produce.
            assertNull(
                "Credential Manager surfaced a candidate for $protocol",
                fixture.device.wait(Until.findObject(By.textContains(MDL_DOC_TYPE)), UI_ELEMENT_TIMEOUT),
            )
            // With no provider left to ask, Credential Manager puts up its own empty state and waits to
            // be dismissed, so the caller is only told after that. Dismissing it is the user action that
            // ends the request, not a workaround for a missing result.
            assertTrue(
                "Credential Manager did not report an empty result for $protocol: " +
                    foregroundWindowSnapshot(fixture.device),
                fixture.device.wait(Until.hasObject(By.text(CREDENTIAL_SELECTOR_CLOSE_LABEL)), UI_ELEMENT_TIMEOUT),
            )
            fixture.device.findObject(By.text(CREDENTIAL_SELECTOR_CLOSE_LABEL)).click()

            val outcome = withTimeout(CREDENTIAL_OPERATION_TIMEOUT) { DigitalCredentialTestVerifier.await() }
            assertNotNull(
                "$protocol produced a credential: ${outcome.getOrNull()}",
                outcome.exceptionOrNull(),
            )
            fixture.device.wait(Until.gone(By.pkg(CREDENTIAL_SELECTOR_PACKAGE).depth(0)), UI_ELEMENT_TIMEOUT)
        }
    }

    /**
     * `response_mode=dc_api.jwt`, with mdoc rather than SD-JWT: the verifier must both decrypt the JWE
     * *and* rebuild the mdoc session transcript from the thumbprint of the encryption key it published,
     * so a wallet that thumbprinted anything else produces a readable JWE whose device signature does
     * not verify.
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
        // The exact member set, not just that "response" exists: an implementation that encrypted the
        // members *and* left them in the clear would satisfy a weaker assertion while disclosing
        // everything encryption was asked for.
        assertEquals("Encrypted response must carry only 'response': ${data.keys}", setOf("response"), data.keys)
        val compactJwe = requireNotNull(data["response"]?.jsonPrimitive?.content) { "No response member" }
        assertEquals("Encrypted response is not a compact JWE: $compactJwe", 5, compactJwe.split('.').size)

        assertVerifierAccepted(
            sessionId = session.sessionId,
            responseJson = credential.credentialJson,
            presentedCredentialId = "mdl",
            requiredPolicyIds = MDOC_REQUIRED_POLICIES,
        )
    }

    /**
     * Two alternatives in one request envelope - an unsupported protocol at index 0, `org-iso-mdoc` for
     * the mDL at index 1 - with the Annex C one selected. Multipaz's Annex C matcher attributes by
     * protocol rather than by request index, so this covers the attribution path that carries no index.
     *
     * Index 0 is `preview`, the legacy Digital Credentials protocol identifier, rather than an
     * OpenID4VP alternative, because two platform behaviours make an OpenID4VP and an Annex C
     * alternative mutually exclusive on Android today, in either order:
     *
     * 1. Multipaz's Annex C matcher scans `requests[]` and stops at the first `protocol` it recognises,
     *    which includes all three `openid4vp-v1-*` values. An OpenID4VP entry ahead of the Annex C one
     *    therefore ends the scan first; an unrecognised protocol is skipped, which is what makes this
     *    envelope work.
     * 2. When both registries produce a candidate for the same request, only the OpenID4VP registry's
     *    reach the picker, so an Annex C entry ahead of a *matchable* OpenID4VP one is matched and
     *    dropped.
     *
     * Neither is reachable from wallet code, so the envelope is what has to be asserted. Mixed
     * OpenID4VP/Annex C envelopes are otherwise covered by host tests, which need no platform picker.
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

        // HPKE `info` is CBOR(SessionTranscript) over sha256(cbor([encryptionInfoB64, origin])), so
        // decryption succeeds only if the wallet hashed the origin Credential Manager asserted and the
        // same encryptionInfo string the reader sent. Drift in either fails here.
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

        // Without this the test would pass on an empty but well-formed DeviceResponse, which is what a
        // broken matcher selection or a dropped disclosure produces.
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
     * Cancel on the wallet's review answers the request: the caller's `getCredential` ends with
     * [GetCredentialCancellationException] and no further provider is offered the request. Asserted
     * through the real caller, because Credential Manager derives the exception type from the provider
     * result and only the caller sees that derivation.
     */
    @Test
    fun cancellingTheProviderReviewCancelsTheCallersRequest() = runBlocking {
        val fixture = start() ?: return@runBlocking
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        fixture.issue(scenario)
        val session = DemoTestBackend.createDcApiVerifierSession(
            scenario = scenario,
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
        )

        fixture.openProviderReview(session.requestJson, candidateText = MDL_DOC_TYPE)
        clickByTag(fixture.device, WalletDemoSharingReviewTestTags.CancelButton)

        val outcome = withTimeout(CREDENTIAL_OPERATION_TIMEOUT) { DigitalCredentialTestVerifier.await() }
        val error = outcome.exceptionOrNull()
        assertNotNull("Cancel produced a credential instead of a cancellation: ${outcome.getOrNull()}", error)
        assertTrue(
            "Cancel must surface a cancellation, not ${error!!::class.java.name}: ${error.message}",
            error is GetCredentialCancellationException,
        )
    }

    /**
     * The system back gesture at the review root leaves this provider without answering, and Credential
     * Manager puts its selector back up rather than ending the caller's request. That the request is
     * still live is proven by re-entering and completing the same session.
     */
    @Test
    fun backingOutOfTheProviderReviewLeavesTheRequestAnswerable() = runBlocking {
        val fixture = start() ?: return@runBlocking
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        fixture.issue(scenario)
        val session = DemoTestBackend.createDcApiVerifierSession(
            scenario = scenario,
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
        )

        fixture.openProviderReview(session.requestJson, candidateText = MDL_DOC_TYPE)
        fixture.device.pressBack()

        // Neither answered nor failed is the state that lets the platform ask again.
        assertTrue(
            "Provider review stayed up after the back gesture",
            fixture.device.wait(Until.gone(By.res(WALLET_SHARING_REVIEW_TAG)), UI_ELEMENT_TIMEOUT),
        )
        assertFalse(
            "Backing out of the review delivered a Credential Manager result",
            DigitalCredentialTestVerifier.isComplete(),
        )

        val candidate = fixture.device.wait(Until.findObject(By.textContains(MDL_DOC_TYPE)), UI_ELEMENT_TIMEOUT)
        assertNotNull("Credential Manager did not offer the request again after the back gesture", candidate)
        assertTrue(
            "Could not re-enter the provider after the back gesture",
            fixture.device.clickCandidate(MDL_DOC_TYPE),
        )
        val continueButton = fixture.device.wait(Until.findObject(By.res("continue_button")), UI_ELEMENT_TIMEOUT)
            ?: fixture.device.wait(Until.findObject(By.text("Continue")), UI_ELEMENT_TIMEOUT)
        continueButton?.click()
        assertNotNull(
            "Wallet provider review did not reopen",
            waitForResource(fixture.device, WALLET_SHARING_REVIEW_TAG, UI_ELEMENT_TIMEOUT),
        )
        clickByTag(fixture.device, WALLET_SHARE_BUTTON_TAG)

        val response = withTimeout(CREDENTIAL_OPERATION_TIMEOUT) {
            DigitalCredentialTestVerifier.await().getOrThrow()
        }
        val credential = requireNotNull(response.credential as? DigitalCredential) {
            "Caller did not receive a digital credential: ${response.credential}"
        }
        assertVerifierAccepted(
            sessionId = session.sessionId,
            responseJson = credential.credentialJson,
            presentedCredentialId = "mdl",
            requiredPolicyIds = MDOC_REQUIRED_POLICIES,
        )
    }

    /** An unlocked wallet on a device that can run these tests, or null when it cannot. */
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
     * @param onCredentialManagerPrompt Assertions to run on Credential Manager's own prompt, while it is
     *   still up. This is the only place the matcher's output is observable.
     * @param beforeShare Assertions to run on the wallet's own consent surface, before it is accepted.
     */
    private suspend fun Fixture.share(
        requestJson: String,
        candidateText: String,
        clickCandidate: Boolean = false,
        onCredentialManagerPrompt: (UiDevice) -> Unit = {},
        beforeShare: (UiDevice) -> Unit = {},
    ): DigitalCredential {
        openProviderReview(requestJson, candidateText, clickCandidate, onCredentialManagerPrompt)

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
     * Drives [requestJson] through Credential Manager up to the wallet's own review, and leaves it open.
     */
    private fun Fixture.openProviderReview(
        requestJson: String,
        candidateText: String,
        clickCandidate: Boolean = false,
        onCredentialManagerPrompt: (UiDevice) -> Unit = {},
    ) {
        DigitalCredentialTestVerifier.reset(requestJson)
        // The previous test's picker can still be up and showing candidate text, so wait it out:
        // otherwise the candidate found below may belong to a request that is already over.
        device.wait(Until.gone(By.pkg(CREDENTIAL_SELECTOR_PACKAGE).depth(0)), UI_ELEMENT_TIMEOUT)
        context.startActivity(
            Intent(context, DigitalCredentialTestVerifierActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        val candidate = device.wait(Until.findObject(By.textContains(candidateText)), UI_ELEMENT_TIMEOUT)
        assertNotNull(
            "Credential Manager did not surface a '$candidateText' candidate. " +
                foregroundWindowSnapshot(device),
            candidate,
        )

        // Before any click: what the picker shows now is the matcher's output, and selecting a candidate
        // can replace this window.
        onCredentialManagerPrompt(device)

        // Re-resolved rather than reused: the picker may have recomposed since the wait above, and a
        // stale node throws instead of failing an assertion.
        if (clickCandidate) {
            assertTrue(
                "Could not click the '$candidateText' candidate",
                device.clickCandidate(candidateText),
            )
        }

        // Optional: after an explicit candidate click some builds go straight to the provider. A
        // confirmation step that was required but missed still fails, on the Share button below.
        val continueButton = device.wait(Until.findObject(By.res("continue_button")), UI_ELEMENT_TIMEOUT)
            ?: device.wait(Until.findObject(By.text("Continue")), UI_ELEMENT_TIMEOUT)
        continueButton?.click()

        assertNotNull(
            "Wallet provider review did not open. ${foregroundWindowSnapshot(device)}",
            waitForResource(device, WALLET_SHARING_REVIEW_TAG, UI_ELEMENT_TIMEOUT),
        )
    }

    /**
     * Posts the wallet's response to verifier2 and asserts it verified.
     *
     * Unlike direct_post, `response_mode=dc_api` sends nothing from the wallet to the verifier: the
     * response returns through the OS to whoever called `getCredential`, and this test is that caller.
     * Verification is inline, so the session is already terminal when this returns.
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
        // A skipped policy leaves the session SUCCESSFUL, so "no failures" alone would pass on a
        // verifier that checked nothing; [requiredPolicyIds] must therefore be asserted as executed.
        //
        // mso_mdoc/issuer_auth proves the wallet relayed the issuer signature unaltered, but not that
        // the document signer meets the ISO 18013-5 certificate profile: issuer2.demo.walt.id signs
        // with an X.509 v1 certificate carrying neither keyUsage:digitalSignature nor
        // EKU 1.0.18013.5.1.2, which verifier2 0.23.0 does not yet enforce.
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

    /** The reader's recipient key. Annex C fixes the suite at DHKEM(P-256)/HKDF-SHA256/AES-128-GCM. */
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
     * An alternative no registered matcher claims. `preview` is the legacy Digital Credentials protocol
     * identifier, so it is a value a real verifier could offer rather than one invented for the test.
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

    /**
     * A `openid4vp-v1-signed` DC API request carrying [payload] as the payload of a dummy compact JWS.
     *
     * The matcher does not verify the signature while matching: it splits `data.request` on `.` and
     * base64url-decodes segment 1, so an unsigned `alg: none` header and a placeholder signature are
     * enough for it to reach the very same DCQL query. Deliberately not real signing infrastructure -
     * the point is that the payload is *matchable*, not that it is authentic.
     */
    private fun signedDcApiRequest(payload: JsonObject): String {
        val jws = listOf(
            base64Url(buildJsonObject { put("alg", JsonPrimitive("none")) }.toString().encodeToByteArray()),
            base64Url(payload.toString().encodeToByteArray()),
            base64Url("dummy-signature".encodeToByteArray()),
        ).joinToString(".")
        return dcApiRequest("openid4vp-v1-signed", buildJsonObject { put("request", JsonPrimitive(jws)) })
    }

    /**
     * A `openid4vp-v1-multisigned` DC API request carrying [payload] under `request.payload`.
     *
     * `data` is a JSON *string* holding `{"request":{"payload":"<base64url>"}}`, which is the shape the
     * pinned matcher's `extract_multisigned_payload` accepts and the one its own unit tests use. No
     * multisignature verification is involved in matching.
     */
    private fun multisignedDcApiRequest(payload: JsonObject): String {
        val multisigned = buildJsonObject {
            put(
                "request",
                buildJsonObject { put("payload", JsonPrimitive(base64Url(payload.toString().encodeToByteArray()))) },
            )
        }
        return dcApiRequest("openid4vp-v1-multisigned", JsonPrimitive(multisigned.toString()))
    }

    /** The `digital` request object Credential Manager routes, with a single `requests[]` entry. */
    private fun dcApiRequest(protocol: String, data: JsonElement): String = buildJsonObject {
        put(
            "requests",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("protocol", JsonPrimitive(protocol))
                        put("data", data)
                    },
                )
            },
        )
    }.toString()

    /** Unpadded base64url, the only alphabet the matcher's decoder accepts. */
    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

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
     * `android:apk-key-hash:<base64url-sha256(signing cert)>`, the origin Credential Manager asserts for
     * a native caller. Mirrors `AndroidDigitalCredentialProvider.nativeAppOrigin`, which is internal to
     * the wallet-mobile module.
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
     * The lookup is inside the retry because walking to a clickable ancestor touches the accessibility
     * tree several times, and any of those throws [StaleObjectException] if the window recomposed.
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
        private const val SCA_DOC_TYPE = "eu.europa.ec.eudi.sca.payment_card.1"
        private const val SCA_CREDENTIAL_QUERY_ID = "sca_payment_card"
        private const val AGE_DOC_TYPE = "eu.europa.ec.av.1"
        private const val AGE_CREDENTIAL_QUERY_ID = "proof_of_age"

        /** How the review labels `age_over_18`, which it humanizes rather than showing verbatim. */
        private const val AGE_DISCLOSURE_LABEL = "Age over 18"

        /** How the amount reads once rendered, on the prompt and on the review alike. */
        private const val SCA_AMOUNT_TEXT = "11.56"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
        private val REQUESTED_MDL_ELEMENTS = listOf("family_name", "given_name")
        private const val PAYMENT_AUTHORIZATION_DISPLAY_NAME = "Payment Authorization"

        /** Owns `CredentialSelectorActivity`, i.e. the picker window these tests drive. */
        private const val CREDENTIAL_SELECTOR_PACKAGE = "com.google.android.gms"

        /** Dismisses Credential Manager's own "Your info wasn't found" state, which has no candidates. */
        private const val CREDENTIAL_SELECTOR_CLOSE_LABEL = "Close"
        private const val CANDIDATE_CLICK_ATTEMPTS = 3

        /**
         * Compose test tags of the wallet's shared review, exported as Android resource IDs. The same
         * tags the in-app OpenID4VP review is driven by; the provider surface is not a separate UI.
         */
        private val WALLET_SHARING_REVIEW_TAG = WalletDemoSharingReviewTestTags.Review
        private val WALLET_SHARE_BUTTON_TAG = WalletDemoSharingReviewTestTags.ShareButton

        /**
         * Holder binding, which for the DC API is the session-transcript check, plus issuer
         * authenticity. Both must be asserted as executed, not merely as not failed.
         */
        private val MDOC_REQUIRED_POLICIES = listOf("mso_mdoc/device-auth", "mso_mdoc/issuer_auth")

        /**
         * The SD-JWT counterpart: `kb-jwt_signature` is holder binding and `sd_hash-check` ties that
         * signature to the exact disclosure set presented. Issuer authenticity is absent because
         * verifier2's default *VP* policy set for `dc+sd-jwt` contains no issuer-signature policy (see
         * VPVerificationPolicyManager.simpleDcSdJwtPolicies).
         */
        private val SD_JWT_REQUIRED_POLICIES = listOf("dc+sd-jwt/kb-jwt_signature", "dc+sd-jwt/sd_hash-check")
    }
}
