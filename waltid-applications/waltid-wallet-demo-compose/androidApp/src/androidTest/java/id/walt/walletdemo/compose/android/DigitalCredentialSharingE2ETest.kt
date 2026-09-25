@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.walletdemo.compose.android

import android.util.Base64
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import id.walt.cose.coseCompliantCbor
import id.walt.iso18013.annexc.AnnexCRequestBuilder
import id.walt.iso18013.annexc.AnnexCResponseVerifier
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.identity.SigningIdentityOperationResult
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtection
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionMode
import id.walt.walletdemo.compose.logic.createAndroidDemoMobileWallet
import id.walt.walletdemo.compose.logic.createAndroidDemoSharingSettingsStore
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.CREDENTIAL_OPERATION_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.UI_ELEMENT_TIMEOUT
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertClaimValueVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertTextContainingVisibleAfterScrolling
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.assertTextContainingVisibleInForegroundWindow
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.clickByTag
import id.walt.walletdemo.compose.android.WalletComposeE2EHelper.waitForResource
import id.walt.walletdemo.compose.ui.WalletDemoSharingReviewTestTags
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/** Unattended Credential Manager coverage using the ordinary demo fixture. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalDigitalCredentialApi::class)
internal class DigitalCredentialSharingE2ETest : DigitalCredentialSharingE2E() {
    override val wallet: MobileWallet get() = provisionedWallet
    override val issuedCredentialIds: Set<String> get() = provisionedCredentialIds
    /**
     * A small platform gate before the protocol assertions: registration has to be visible to the
     * platform and a real Digital Credentials request has to open Google's selector. The remaining
     * tests then exercise the same selector through issuance, matching, review, and submission.
     */
    @Test
    fun credentialManagerPlatformSmoke() = runBlocking {
        val fixture = fixture()
        val registration = wallet.refreshDigitalCredentialRegistration()
        assertTrue(
            "Credential Manager registration was unavailable in the platform smoke: ${registration.reason}",
            registration.available,
        )
        assertTrue(
            "Credential Manager registered no credentials in the platform smoke",
            registration.registeredEntryCount > 0,
        )

        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        val session = DemoTestBackend.createDcApiVerifierSession(
            scenario = scenario,
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
        )
        val request = fixture.startCredentialRequest(session.requestJson)
        try {
            assertNotNull(
                "Credential Manager selector did not open for a real DC API request",
                fixture.device.wait(Until.findObject(By.pkg(CREDENTIAL_SELECTOR_PACKAGE)), UI_ELEMENT_TIMEOUT),
            )
        } finally {
            request.abandon()
        }
    }

    /** Disabling the wallet preview submits the picker's default selection without showing a sheet. */
    @Test
    fun sharesMdocWithoutWalletReviewWhenPreviewIsDisabled() = runBlocking {
        val fixture = fixture()
        val settings = createAndroidDemoSharingSettingsStore(fixture.context)
        settings.setShowDcApiPresentationPreview(false)
        assertFalse(settings.showDcApiPresentationPreview())

        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        val session = DemoTestBackend.createDcApiVerifierSession(
            scenario = scenario,
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
        )

        val credential = fixture.shareWithoutWalletReview(
            request = fixture.startCredentialRequest(session.requestJson),
            candidateText = MDL_DOC_TYPE,
        )
        assertFalse("Wallet review appeared while preview was disabled", fixture.walletReviewVisible())

        assertVerifierAccepted(
            sessionId = session.sessionId,
            responseJson = credential.credentialJson,
            presentedCredentialId = "mdl",
            requiredPolicyIds = MDOC_REQUIRED_POLICIES,
        )
    }

    /** Clear `dc_api`: one mdoc, including its full issued portrait. */
    @Test
    fun sharesMdocThroughClearOpenId4VpDcApi() = runBlocking {
        val fixture = fixture()
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }

        // Both sides hash this origin into the mdoc session transcript. The debug signing key differs
        // per machine and per CI runner, so it is derived at runtime rather than pinned.
        val session = DemoTestBackend.createDcApiVerifierSession(
            credentialQueries = listOf(imageQuery(scenario)),
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
        )

        val credential = fixture.share(
            request = fixture.startCredentialRequest(session.requestJson),
            candidateText = MDL_DOC_TYPE,
        )
        val responseJson = Json.parseToJsonElement(credential.credentialJson).jsonObject
        assertEquals("openid4vp-v1-unsigned", responseJson["protocol"]?.jsonPrimitive?.content)
        val data = responseJson["data"]?.jsonObject
        assertNotNull("DC API response carries no data object", data)
        assertNotNull("Clear dc_api response carries no vp_token", data!!["vp_token"])

        assertVerifierAccepted(
            sessionId = session.sessionId,
            responseJson = credential.credentialJson,
            expectedImage = true,
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
        val fixture = fixture()
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "eudi-pid-sdjwt" }

        val transactionData = DemoTestBackend.paymentAuthorizationTransactionData(credentialId = "pid")
        val session = DemoTestBackend.createDcApiVerifierSession(
            credentialQueries = listOf(imageQuery(scenario)),
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
            transactionData = listOf(transactionData),
        )

        val credential = fixture.share(
            request = fixture.startCredentialRequest(session.requestJson),
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

        val portraitDisclosure = presentation.substringBeforeLast('~').split('~').drop(1)
            .map { Json.parseToJsonElement(Base64.decode(it, Base64.URL_SAFE).decodeToString()).jsonArray }
            .single { it[1].jsonPrimitive.content == "portrait" }
        assertArrayEquals(
            "SD-JWT must return the image as a selective disclosure",
            IMAGE_BYTES,
            Base64.decode(portraitDisclosure[2].jsonPrimitive.content, Base64.DEFAULT),
        )

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
            expectedImage = true,
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
    fun sharesMdocWithScaPaymentTransactionDataAndSecondCredential() = runBlocking {
        val fixture = fixture()
        val scaScenario = DemoTestBackend.presentationScenarios.first { it.id == "sca-payment-card" }
        val ageScenario = DemoTestBackend.presentationScenarios.first { it.id == "eu-age-verification" }
        val extraCredentialIds = mutableListOf<String>()
        try {
            extraCredentialIds += issueFromDemoIssuer(wallet, scaScenario)
            val ageCredentialIds = issueFromDemoIssuer(wallet, ageScenario)
            extraCredentialIds += ageCredentialIds
            val ageCredentialId = ageCredentialIds.single()
            val registration = wallet.refreshDigitalCredentialRegistration()
            assertTrue("Temporary SCA/age registration was unavailable: ${registration.reason}", registration.available)
            assertEquals(4, registration.registeredEntryCount)

            val transactionData = DemoTestBackend.scaPaymentTransactionData(credentialId = SCA_CREDENTIAL_QUERY_ID)
            val session = DemoTestBackend.createDcApiVerifierSession(
                credentialQueries = listOf(scaScenario.verifierCredentialQuery, ageScenario.verifierCredentialQuery),
                expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
                transactionData = listOf(transactionData),
            )

            val credential = fixture.share(
                request = fixture.startCredentialRequest(session.requestJson),
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
                    // Open the exact age credential and assert its requested disclosure: a card can also
                    // appear when the platform offered a credential without selecting claims.
                    clickByTag(
                        device = device,
                        tag = WalletDemoSharingReviewTestTags.credentialCard(
                            queryId = AGE_CREDENTIAL_QUERY_ID,
                            credentialId = ageCredentialId,
                        ),
                    )
                    assertNotNull(
                        "Age credential claims dialog did not open",
                        waitForResource(
                            device = device,
                            tag = WalletDemoSharingReviewTestTags.ClaimsDialog,
                            timeoutMs = UI_ELEMENT_TIMEOUT,
                        ),
                    )
                    assertTextContainingVisibleAfterScrolling(
                        device = device,
                        substring = AGE_DISCLOSURE_LABEL,
                        message = "Review did not receive the second credential",
                    )
                    clickByTag(device, WalletDemoSharingReviewTestTags.ClaimsCloseButton)
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
        } finally {
            extraCredentialIds.forEach { credentialId ->
                check(wallet.deleteCredential(credentialId)) { "Failed to delete temporary credential $credentialId" }
            }
            val registration = wallet.refreshDigitalCredentialRegistration()
            assertEquals(issuedCredentialIds, wallet.credentials().map { it.id }.toSet())
            assertTrue("Baseline registration became unavailable: ${registration.reason}", registration.available)
            assertEquals(2, registration.registeredEntryCount)
        }
    }

    /**
     * The wallet registers unsigned and signed compact OpenID4VP, but not multisigned. A multisigned
     * request must not surface this wallet, because it cannot fulfill JWS JSON Serialization.
     *
     * Each unsupported request carries an unsigned OpenID4VP query for the issued mDL,
     * re-wrapped into the structurally valid
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
    fun doesNotSurfaceForMultisignedRequests(): Unit = runBlocking {
        val fixture = fixture()
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        val session = DemoTestBackend.createDcApiVerifierSession(
            scenario = scenario,
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
        )
        val openId4VpPayload = requireNotNull(
            Json.parseToJsonElement(session.requestJson).jsonObject["requests"]
                ?.jsonArray?.firstOrNull()?.jsonObject?.get("data")?.jsonObject,
        ) { "Unsigned DC API request carries no object 'data': ${session.requestJson}" }

        val requestHandle = fixture.startCredentialRequest(multisignedDcApiRequest(openId4VpPayload))
        fixture.awaitUnsupportedRequestEmptyState(requestHandle, "openid4vp-v1-multisigned", MDL_DOC_TYPE)
        val outcome = withTimeout(CREDENTIAL_OPERATION_TIMEOUT) { requestHandle.await() }
        assertNotNull(
            "openid4vp-v1-multisigned produced a credential: ${outcome.getOrNull()}",
            outcome.exceptionOrNull(),
        )
        assertTrue(
            "Credential Manager selector did not close after the unsupported request",
            fixture.device.wait(Until.gone(By.pkg(CREDENTIAL_SELECTOR_PACKAGE).depth(0)), UI_ELEMENT_TIMEOUT),
        )
    }

    /**
     * Signed compact Request Objects are advertised to Credential Manager. The matcher does not verify
     * the JAR, so a structurally valid signed wrapper of a matchable unsigned payload must surface the
     * issued mDL. Cryptographic verification of that JAR is covered by the wallet SDK tests rather than
     * this OS-mediated picker assertion.
     */
    @Test
    fun surfacesForSignedRequests() = runBlocking {
        val fixture = fixture()
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        val session = DemoTestBackend.createDcApiVerifierSession(
            scenario = scenario,
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
        )
        val openId4VpPayload = requireNotNull(
            Json.parseToJsonElement(session.requestJson).jsonObject["requests"]
                ?.jsonArray?.firstOrNull()?.jsonObject?.get("data")?.jsonObject,
        ) { "Unsigned DC API request carries no object 'data': ${session.requestJson}" }

        val requestHandle = fixture.startCredentialRequest(signedDcApiRequest(openId4VpPayload))
        try {
            // Credential Manager displays the registered title, not the raw docType. Stop at its
            // disclosure sheet: this synthetic JWS tests matching, not proof acceptance or sharing.
            assertNotNull(
                "Credential Manager did not surface the signed-request mDL.\n" +
                    pickerDiagnostic(requestHandle, "Mobile Driving License", candidateSelected = false),
                fixture.device.wait(
                    Until.findObject(By.pkg(CREDENTIAL_SELECTOR_PACKAGE).text("Mobile Driving License")),
                    UI_ELEMENT_TIMEOUT,
                ),
            )
            for (claim in listOf("given_name", "family_name")) {
                assertNotNull("Credential Manager did not display requested claim '$claim'",
                    fixture.device.findCredentialManagerText(claim))
            }
            assertNotNull("Credential Manager did not offer to continue with the matching credential",
                fixture.device.findEnabledCredentialManagerContinue())
        } finally {
            requestHandle.abandon()
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
        val fixture = fixture()
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }

        val session = DemoTestBackend.createDcApiVerifierSession(
            credentialQueries = listOf(imageQuery(scenario)),
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
            encryptedResponse = true,
        )

        val credential = fixture.share(
            request = fixture.startCredentialRequest(session.requestJson),
            candidateText = MDL_DOC_TYPE,
        )
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
            expectedImage = true,
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
        val fixture = fixture()

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
        val credential = fixture.share(
            request = fixture.startCredentialRequest(envelope),
            candidateText = MDL_DOC_TYPE,
        )
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
        val portrait = issuerNamespaces.getValue(MDL_NAMESPACE).entries
            .single { it.value.elementIdentifier == "portrait" }.value.elementValue
        assertTrue("Annex C portrait must remain a CBOR byte string", portrait is CborByteString)
        assertArrayEquals("Annex C changed the issued image", IMAGE_BYTES, (portrait as CborByteString).toByteArray())
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
        val fixture = fixture()
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        val session = DemoTestBackend.createDcApiVerifierSession(
            scenario = scenario,
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
        )

        val request = fixture.startCredentialRequest(session.requestJson)
        fixture.enterProviderReview(request, candidateText = MDL_DOC_TYPE)
        clickByTag(fixture.device, WalletDemoSharingReviewTestTags.CancelButton)

        val outcome = fixture.awaitCancellationOutcome(request)
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
        val fixture = fixture()
        val scenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
        val session = DemoTestBackend.createDcApiVerifierSession(
            scenario = scenario,
            expectedOrigins = listOf(nativeAppOrigin(fixture.context)),
        )

        val request = fixture.startCredentialRequest(session.requestJson)
        fixture.enterProviderReview(request, candidateText = MDL_DOC_TYPE)
        fixture.device.pressBack()

        // Neither answered nor failed is the state that lets the platform ask again.
        assertTrue(
            "Provider review stayed up after the back gesture",
            fixture.device.wait(Until.gone(By.res(WALLET_SHARING_REVIEW_TAG)), UI_ELEMENT_TIMEOUT),
        )
        assertFalse(
            "Backing out of the review delivered a Credential Manager result",
            request.isComplete,
        )

        fixture.enterProviderReview(request, candidateText = MDL_DOC_TYPE)
        assertNotNull(
            "Wallet provider review did not reopen",
            waitForResource(fixture.device, WALLET_SHARING_REVIEW_TAG, UI_ELEMENT_TIMEOUT),
        )
        clickByTag(fixture.device, WALLET_SHARE_BUTTON_TAG)

        val response = withTimeout(CREDENTIAL_OPERATION_TIMEOUT) {
            request.await().getOrThrow()
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

    companion object {
        private lateinit var provisionedWallet: MobileWallet
        private lateinit var provisionedCredentialIds: Set<String>
        @JvmStatic
        @BeforeClass
        fun provisionCredentials() = runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            assertTrue(
                "Digital Credentials E2E requires an Android emulator with Google Play services",
                hasGooglePlayServices(context),
            )

            val created = createAndroidDemoMobileWallet(
                context = context,
                // This Play Store emulator cannot enforce protected signing keys; production
                // defaults remain covered by the app, while this fixture needs ordinary keys.
                config = demoWalletConfig().copy(
                    signingProtectionMode = WalletDemoSigningProtectionMode.Disabled,
                ),
            )
            provisionedWallet = created.wallet
            val identity = provisionedWallet.signingIdentity.initialize()
            assertTrue("Could not initialize the fixture signing identity: $identity", identity is SigningIdentityOperationResult.Active)
            created.bootstrap(WalletDemoSigningProtection.None)
            demoWalletConfig().signingProtectionStore(context).save(WalletDemoSigningProtection.None)

            provisionedWallet.credentials().forEach { credential ->
                check(provisionedWallet.deleteCredential(credential.id)) {
                    "Failed to delete stale credential ${credential.id}"
                }
            }

            val emptyRegistration = provisionedWallet.refreshDigitalCredentialRegistration()
            assertTrue(
                "Credential Manager registration was unavailable after clearing the wallet: " +
                    emptyRegistration.reason,
                emptyRegistration.available,
            )
            assertEquals(0, emptyRegistration.registeredEntryCount)

            val mdlScenario = DemoTestBackend.presentationScenarios.first { it.id == "iso-mdl" }
            val sdJwtScenario = DemoTestBackend.presentationScenarios.first { it.id == "eudi-pid-sdjwt" }
            provisionedCredentialIds = (
                issueFromDemoIssuer(provisionedWallet, mdlScenario) + issueFromDemoIssuer(provisionedWallet, sdJwtScenario)
                ).toSet()

            val stored = provisionedWallet.credentials()
            assertEquals("Live setup must store exactly two credentials", 2, stored.size)
            assertEquals("Issued IDs do not match stored IDs", provisionedCredentialIds, stored.map { it.id }.toSet())

            val mdoc = requireNotNull(stored.singleOrNull { it.format == "mso_mdoc" }) {
                "Expected one mso_mdoc credential, got ${stored.map { it.format }}"
            }
            assertEquals(
                MDL_DOC_TYPE,
                Json.parseToJsonElement(mdoc.credentialDataJson).jsonObject["docType"]?.jsonPrimitive?.content,
            )

            val sdJwt = requireNotNull(stored.singleOrNull { it.format == "dc+sd-jwt" }) {
                "Expected one dc+sd-jwt credential, got ${stored.map { it.format }}"
            }
            assertEquals(
                EUDI_PID_SD_JWT_VCT,
                Json.parseToJsonElement(sdJwt.credentialDataJson).jsonObject["vct"]?.jsonPrimitive?.content,
            )

            val registration = provisionedWallet.refreshDigitalCredentialRegistration()
            assertTrue(
                "Credential Manager registration was unavailable after live provisioning: " +
                    registration.reason,
                registration.available,
            )
            assertEquals(2, registration.registeredEntryCount)
        }

    }
}
