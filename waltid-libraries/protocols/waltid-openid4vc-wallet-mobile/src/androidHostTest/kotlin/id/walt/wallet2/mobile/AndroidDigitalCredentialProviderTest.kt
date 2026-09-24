package id.walt.wallet2.mobile

import android.content.Intent
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Bundle
import android.service.credentials.CredentialProviderService
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.GetDigitalCredentialOption
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSigningInfo
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val EXTRA_CREDENTIAL_SET_ID =
    "androidx.credentials.registry.provider.extra.CREDENTIAL_SET_ID"
private const val EXTRA_CREDENTIAL_SET_ELEMENT_LENGTH =
    "androidx.credentials.registry.provider.extra.CREDENTIAL_SET_ELEMENT_LENGTH"
private const val EXTRA_CREDENTIAL_SET_ELEMENT_ID_PREFIX =
    "androidx.credentials.registry.provider.extra.CREDENTIAL_SET_ELEMENT_ID_"
private const val EXTRA_CREDENTIAL_SET_ELEMENT_METADATA_PREFIX =
    "androidx.credentials.registry.provider.extra.CREDENTIAL_SET_ELEMENT_METADATA_"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDigitalCredentialProviderTest {
    @OptIn(ExperimentalDigitalCredentialApi::class)
    @Config(sdk = [35])
    @Test
    fun extractsTheOfficialProviderRequestAndUsesTheAuthenticatedNativeCallerOrigin() {
        val signature = Signature(byteArrayOf(1, 2, 3, 4))
        val intent = providerIntent(
            requestJson = """{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}""",
            packageName = "id.walt.caller",
            signingInfo = signingInfo(signature),
            selectedCredentialSet = selectedCredentialSetExtras(),
        )

        val input = AndroidDigitalCredentialProvider.extract(intent, """{"apps":[]}""")

        assertEquals("id.walt.caller", input.providerRequest.callingAppInfo.packageName)
        assertEquals(
            AndroidDigitalCredentialProvider.nativeAppOrigin(signature.toByteArray()),
            input.request.verifiedOrigin,
        )
        assertEquals(MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED, input.request.protocol)
    }

    @OptIn(ExperimentalDigitalCredentialApi::class)
    @Config(sdk = [35])
    @Test
    fun rejectsAClaimedBrowserOriginWhenTheCallerIsNotAllowlisted() {
        val intent = providerIntent(
            requestJson = """{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}""",
            packageName = "id.walt.untrusted-browser",
            signingInfo = signingInfo(Signature(byteArrayOf(5, 6, 7, 8))),
            origin = "https://verifier.example",
            selectedCredentialSet = selectedCredentialSetExtras(),
        )

        assertFailsWith<IllegalStateException> {
            AndroidDigitalCredentialProvider.extract(intent, """{"apps":[]}""")
        }
    }

    /**
     * A Credential Manager request without a selected credential set is refused, and specifically not
     * treated as "the platform asserted no selection".
     *
     * This wallet registers through `RegistryManager`, so the platform always reports what the user
     * picked. Accepting the absence would produce `selectedRegistryEntryIds = []`, which
     * `MobileWallet.previewDigitalCredentialPresentation` reads as unrestricted matching over the whole
     * store.
     *
     * The envelope offers a single alternative because that is the one shape whose *protocol request*
     * resolution needs no attribution, so a missing selection could otherwise pass unnoticed.
     */
    @OptIn(ExperimentalDigitalCredentialApi::class)
    @Config(sdk = [35])
    @Test
    fun rejectsARegistryRequestThatCarriesNoSelectedCredentialSet() {
        val intent = providerIntent(
            requestJson = """{"requests":[{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}]}""",
            packageName = "id.walt.caller",
            signingInfo = signingInfo(Signature(byteArrayOf(1, 2, 3, 4))),
        )

        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.extract(intent, """{"apps":[]}""")
        }
    }

    /**
     * The refusal happens during extraction, before any wallet is consulted. `extract` is the only
     * boundary between Credential Manager and credential matching, so nothing downstream can receive a
     * request built from a missing selection.
     */
    @OptIn(ExperimentalDigitalCredentialApi::class)
    @Config(sdk = [35])
    @Test
    fun refusesAMissingSelectionBeforeProducingAnyPlatformNeutralRequest() {
        val extraction = runCatching {
            AndroidDigitalCredentialProvider.extract(
                providerIntent(
                    requestJson = """{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}""",
                    packageName = "id.walt.caller",
                    signingInfo = signingInfo(Signature(byteArrayOf(1, 2, 3, 4))),
                ),
                """{"apps":[]}""",
            )
        }

        assertNull(extraction.getOrNull())
        // And the same intent with a selection added does produce one, so the absence is what failed.
        assertEquals(
            listOf("opaque-entry"),
            AndroidDigitalCredentialProvider.extract(
                providerIntent(
                    requestJson = """{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}""",
                    packageName = "id.walt.caller",
                    signingInfo = signingInfo(Signature(byteArrayOf(1, 2, 3, 4))),
                    selectedCredentialSet = selectedCredentialSetExtras(),
                ),
                """{"apps":[]}""",
            ).request.selectedRegistryEntryIds,
        )
    }

    @OptIn(ExperimentalDigitalCredentialApi::class)
    @Config(sdk = [35])
    @Test
    fun writesOfficialCredentialManagerResponsesCancellationAndFailures() {
        // Credential Manager needs the answered request to return a response too large for an
        // intent extra, so the response is written against a genuinely extracted one.
        val providerRequest = AndroidDigitalCredentialProvider.extract(
            providerIntent(
                requestJson = """{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}""",
                packageName = "id.walt.caller",
                signingInfo = signingInfo(Signature(byteArrayOf(1, 2, 3, 4))),
                selectedCredentialSet = selectedCredentialSetExtras(),
            ),
            """{"apps":[]}""",
        ).providerRequest
        val responseIntent = Intent()
        AndroidDigitalCredentialProvider.setResponse(
            responseIntent,
            MobileWalletDigitalCredentialResponse(
                protocol = MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
                dataJson = """{"vp_token":{"pid":["presentation"]}}""",
            ),
            providerRequest,
        )

        val response = assertNotNull(PendingIntentHandler.retrieveGetCredentialResponse(responseIntent))
        val credential = assertIs<DigitalCredential>(response.credential)
        val responseJson = Json.parseToJsonElement(credential.credentialJson).jsonObject
        assertEquals(
            MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
            responseJson["protocol"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "presentation",
            responseJson["data"]?.jsonObject
                ?.get("vp_token")?.jsonObject
                ?.get("pid")?.jsonArray?.single()?.jsonPrimitive?.content,
        )

        val cancellationIntent = Intent()
        AndroidDigitalCredentialProvider.setCancellation(cancellationIntent)
        assertIs<GetCredentialCancellationException>(
            PendingIntentHandler.retrieveGetCredentialException(cancellationIntent),
        )

        val failureIntent = Intent()
        AndroidDigitalCredentialProvider.setFailure(failureIntent, "safe failure")
        val failure = assertIs<GetCredentialUnknownException>(
            PendingIntentHandler.retrieveGetCredentialException(failureIntent),
        )
        assertEquals("safe failure", failure.errorMessage)
    }

    @OptIn(ExperimentalDigitalCredentialApi::class)
    private fun providerIntent(
        requestJson: String,
        packageName: String,
        signingInfo: SigningInfo,
        origin: String? = null,
        selectedCredentialSet: Bundle? = null,
    ): Intent {
        val option = GetDigitalCredentialOption(requestJson)
        val frameworkOption = android.credentials.CredentialOption.Builder(
            option.type,
            option.requestData,
            option.candidateQueryData,
        ).build()
        val callingApp = if (origin == null) {
            android.service.credentials.CallingAppInfo(packageName, signingInfo)
        } else {
            android.service.credentials.CallingAppInfo(packageName, signingInfo, origin)
        }
        return Intent().putExtra(
            CredentialProviderService.EXTRA_GET_CREDENTIAL_REQUEST,
            android.service.credentials.GetCredentialRequest(callingApp, listOf(frameworkOption)),
        ).also { intent ->
            selectedCredentialSet?.let(intent::putExtras)
        }
    }

    /**
     * The intent extras Credential Manager adds for a registry-based selection, in the shape
     * `ProviderGetCredentialRequest.selectedCredentialSet` reads them back from. The keys are private
     * to `androidx.credentials.registry.provider`, so they are written literally rather than through an
     * API that does not exist for providers.
     */
    private fun selectedCredentialSetExtras(
        credentialSetId: String = "req:0;null",
        credentials: List<Pair<String, String?>> = listOf(
            "opaque-entry" to """{"dc_request_index":0,"dcql_cred_id":"pid"}""",
        ),
    ): Bundle = Bundle().apply {
        putString(EXTRA_CREDENTIAL_SET_ID, credentialSetId)
        putInt(EXTRA_CREDENTIAL_SET_ELEMENT_LENGTH, credentials.size)
        credentials.forEachIndexed { index, (credentialId, metadata) ->
            putString("$EXTRA_CREDENTIAL_SET_ELEMENT_ID_PREFIX$index", credentialId)
            metadata?.let { putString("$EXTRA_CREDENTIAL_SET_ELEMENT_METADATA_PREFIX$index", it) }
        }
    }

    private fun signingInfo(signature: Signature): SigningInfo = SigningInfo().also { signingInfo ->
        Shadow.extract<ShadowSigningInfo>(signingInfo).apply {
            setSignatures(arrayOf(signature))
            setPastSigningCertificates(arrayOf(signature))
        }
    }

    @Test
    fun resolvesASingleRequestEnvelopeAndPreservesSelectedOpaqueEntries() {
        val request = AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
            requestJson = """{"requests":[{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}]}""",
            verifiedOrigin = "https://verifier.example",
            selection = openId4VpSelection(requestIndex = 0, registryEntryIds = listOf("opaque-entry")),
        )

        assertEquals(MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED, request.protocol)
        assertEquals("n", Json.parseToJsonElement(request.dataJson).jsonObject["nonce"].toString().trim('"'))
        assertEquals(listOf("opaque-entry"), request.selectedRegistryEntryIds)
    }

    /**
     * With one alternative there is nothing to attribute, so a matcher that supplies no index is not
     * an error: index 0 is the only possibility.
     */
    @Test
    fun resolvesAnUnattributedSelectionWhenTheVerifierOffersOneAlternative() {
        val request = AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
            requestJson = """{"requests":[{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}]}""",
            verifiedOrigin = "https://verifier.example",
            selection = AndroidDigitalCredentialProvider.parseMatcherSelection(
                credentialSetId = "",
                credentials = listOf("opaque-entry" to ""),
            ),
        )

        assertEquals(MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED, request.protocol)
        assertEquals(listOf("opaque-entry"), request.selectedRegistryEntryIds)
    }

    /**
     * The alternative the user chose is answered even though the wallet also supports the entry at index
     * 0. The two entries differ only in `nonce`, so answering the wrong one produces a response bound to
     * a request the user was never shown.
     */
    @Test
    fun answersTheAlternativeTheMatcherAttributedTheSelectionTo() {
        val request = AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
            requestJson = """
                {"requests":[
                  {"protocol":"openid4vp-v1-unsigned","data":{"nonce":"first"}},
                  {"protocol":"openid4vp-v1-unsigned","data":{"nonce":"second"}}
                ]}
            """.trimIndent(),
            verifiedOrigin = "https://verifier.example",
            selection = openId4VpSelection(requestIndex = 1, registryEntryIds = listOf("oid4vp-entry")),
        )

        assertEquals(MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED, request.protocol)
        assertEquals("second", Json.parseToJsonElement(request.dataJson).jsonObject["nonce"].toString().trim('"'))
    }

    /**
     * When both OpenID4VP and Annex C are offered, the matcher-attributed one wins in either array
     * order; the wallet has no protocol preference of its own to apply.
     */
    @Test
    fun answersTheMatcherSelectedProtocolWhateverOrderTheVerifierOffers() {
        val annexCFirst = """
            {"requests":[
              {"protocol":"org-iso-mdoc","data":{"deviceRequest":"d"}},
              {"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}
            ]}
        """.trimIndent()
        val openId4VpFirst = """
            {"requests":[
              {"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}},
              {"protocol":"org-iso-mdoc","data":{"deviceRequest":"d"}}
            ]}
        """.trimIndent()

        // Annex C selected, listed first and then second. Its matcher names only the protocol, so the
        // resolution is by protocol in both orders. The leading `7` is multipaz's combination counter
        // and is deliberately not the index of either alternative.
        listOf(annexCFirst, openId4VpFirst).forEach { requestJson ->
            val request = AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                requestJson = requestJson,
                verifiedOrigin = "https://verifier.example",
                selection = annexCSelection(combinationIndex = 7, documentIds = listOf("annex-c-doc")),
            )
            assertEquals(MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C, request.protocol)
            assertEquals(listOf("annex-c-doc"), request.selectedRegistryEntryIds)
            assertEquals("d", Json.parseToJsonElement(request.dataJson).jsonObject["deviceRequest"].toString().trim('"'))
        }

        // OpenID4VP selected, listed second and then first.
        listOf(annexCFirst to 1, openId4VpFirst to 0).forEach { (requestJson, index) ->
            val request = AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                requestJson = requestJson,
                verifiedOrigin = "https://verifier.example",
                selection = openId4VpSelection(requestIndex = index, registryEntryIds = listOf("oid4vp-entry")),
            )
            assertEquals(MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED, request.protocol)
            assertEquals(listOf("oid4vp-entry"), request.selectedRegistryEntryIds)
        }
    }

    /** A DCQL query answered from several credentials arrives as one set; all of it is retained. */
    @Test
    fun retainsEverySelectedCredentialFromOneCredentialSet() {
        val request = AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
            requestJson = """{"requests":[{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}]}""",
            verifiedOrigin = "https://verifier.example",
            selection = AndroidDigitalCredentialProvider.parseMatcherSelection(
                credentialSetId = "req:0;set:s;option:0",
                credentials = listOf(
                    "pid-entry" to """{"dc_request_index":0,"dcql_cred_id":"pid"}""",
                    "mdl-entry" to """{"dc_request_index":0,"dcql_cred_id":"mdl"}""",
                ),
            ),
        )

        assertEquals(listOf("pid-entry", "mdl-entry"), request.selectedRegistryEntryIds)
    }

    /**
     * Credentials attributed to two different `requests` entries are not one answerable selection.
     * Resolving them against either entry would bind a credential the user picked for the other query.
     */
    @Test
    fun rejectsASelectionSpanningTwoProtocolRequests() {
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.parseMatcherSelection(
                credentialSetId = "req:0;null",
                credentials = listOf(
                    "first-entry" to """{"dc_request_index":0}""",
                    "second-entry" to """{"dc_request_index":1}""",
                ),
            )
        }
    }

    /** An index the verifier never offered is refused rather than clamped or ignored. */
    @Test
    fun rejectsASelectedRequestIndexOutsideTheOfferedAlternatives() {
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                requestJson = """{"requests":[{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}]}""",
                verifiedOrigin = "https://verifier.example",
                selection = openId4VpSelection(requestIndex = 3, registryEntryIds = listOf("oid4vp-entry")),
            )
        }
    }

    /**
     * The matcher's protocol attribution and the envelope must agree. A disagreement means one of the
     * two is not describing the request the user was shown, and neither can be trusted over the other.
     */
    @Test
    fun rejectsAttributedProtocolThatDisagreesWithTheSelectedRequest() {
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                requestJson = """
                    {"requests":[
                      {"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}},
                      {"protocol":"org-iso-mdoc","data":{"deviceRequest":"d"}}
                    ]}
                """.trimIndent(),
                verifiedOrigin = "https://verifier.example",
                // Annex C attribution, but request 0 is OpenID4VP.
                selection = MatcherSelectedRequest(
                    requestIndex = 0,
                    protocol = MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
                    registryEntryIds = listOf("annex-c-doc"),
                ),
            )
        }
    }

    /**
     * A selected signed alternative is answered as signed. It must not fall through to the unsigned
     * sibling, which would answer a request the user never saw.
     */
    @Test
    fun acceptsASelectedSignedAlternativeInsteadOfSwitchingToTheUnsignedSibling() {
        val request = AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
            requestJson = """
                {"requests":[
                  {"protocol":"openid4vp-v1-signed","data":{"request":"a.b.c"}},
                  {"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}
                ]}
            """.trimIndent(),
            verifiedOrigin = "https://verifier.example",
            selection = openId4VpSelection(requestIndex = 0, registryEntryIds = listOf("signed-entry")),
        )
        assertEquals(MobileWalletDigitalCredentialProtocols.OPENID4VP_SIGNED, request.protocol)
        assertEquals("""{"request":"a.b.c"}""", request.dataJson)
        assertEquals(listOf("signed-entry"), request.selectedRegistryEntryIds)
    }

    /**
     * A selected multisigned alternative fails, and specifically does not fall through to the unsigned
     * sibling, which would answer a request the user never saw.
     */
    @Test
    fun rejectsASelectedMultisignedAlternativeInsteadOfSwitchingToTheUnsignedSibling() {
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                requestJson = """
                    {"requests":[
                      {"protocol":"openid4vp-v1-multisigned","data":{"request":{}}},
                      {"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}
                    ]}
                """.trimIndent(),
                verifiedOrigin = "https://verifier.example",
                selection = openId4VpSelection(requestIndex = 0, registryEntryIds = listOf("signed-entry")),
            )
        }
    }

    /** Metadata that is not a JSON object, or names a non-numeric index, is malformed. */
    @Test
    fun rejectsMalformedMatcherMetadata() {
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.parseMatcherSelection(
                credentialSetId = "req:0;null",
                credentials = listOf("oid4vp-entry" to "not json"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.parseMatcherSelection(
                credentialSetId = "req:0;null",
                credentials = listOf("oid4vp-entry" to """{"dc_request_index":"first"}"""),
            )
        }
        // A set id naming one request while its credentials name another describes no single request.
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.parseMatcherSelection(
                credentialSetId = "req:1;null",
                credentials = listOf("oid4vp-entry" to """{"dc_request_index":0}"""),
            )
        }
    }

    /** An opaque selection across several alternatives fails closed rather than picking one. */
    @Test
    fun rejectsAnUnattributedSelectionWhenSeveralAlternativesAreOffered() {
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                requestJson = """
                    {"requests":[
                      {"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}},
                      {"protocol":"org-iso-mdoc","data":{"deviceRequest":"d"}}
                    ]}
                """.trimIndent(),
                verifiedOrigin = "https://verifier.example",
                selection = AndroidDigitalCredentialProvider.parseMatcherSelection(
                    credentialSetId = "",
                    credentials = listOf("opaque-entry" to ""),
                ),
            )
        }
    }

    /**
     * No malformed selection may reach `selectedRegistryEntryIds = []`.
     *
     * `MobileWallet.previewDigitalCredentialPresentation` reads an empty list as unrestricted DCQL
     * matching over the whole store, so a malformed matcher result must not be able to *widen* the
     * candidate set.
     */
    @Test
    fun neverTurnsAMalformedSelectionIntoAnUnrestrictedOne() {
        val malformedSelections = listOf(
            MatcherSelectedRequest(requestIndex = 0, protocol = null, registryEntryIds = emptyList()),
            MatcherSelectedRequest(requestIndex = null, protocol = null, registryEntryIds = emptyList()),
        )
        malformedSelections.forEach { selection ->
            assertFailsWith<IllegalArgumentException>("$selection produced an unrestricted request") {
                AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                    requestJson = """{"requests":[{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}]}""",
                    verifiedOrigin = "https://verifier.example",
                    selection = selection,
                )
            }
        }
        // An empty selected credential set is refused where it is read, before it can become an
        // attribution-free selection.
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.parseMatcherSelection(
                credentialSetId = "req:0;null",
                credentials = emptyList(),
            )
        }
    }

    /** Multisigned stays unsupported: an envelope offering only that must fail closed. */
    @Test
    fun rejectsAnEnvelopeThatOffersOnlyUnsupportedProtocols() {
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                requestJson = """
                    {"requests":[
                      {"protocol":"openid4vp-v1-multisigned","data":{"request":{}}}
                    ]}
                """.trimIndent(),
                verifiedOrigin = "https://verifier.example",
                selection = openId4VpSelection(requestIndex = 0, registryEntryIds = listOf("signed-entry")),
            )
        }
    }

    @Test
    fun rejectsEmptyOrMalformedProtocolRequests() {
        val selection = openId4VpSelection(requestIndex = 0, registryEntryIds = listOf("oid4vp-entry"))
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                requestJson = """{"requests":[]}""",
                verifiedOrigin = "https://verifier.example",
                selection = selection,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                requestJson = """{"protocol":"openid4vp-v1-unsigned","data":"secret"}""",
                verifiedOrigin = "https://verifier.example",
                selection = selection,
            )
        }
        // A protocol-less entry is malformed even when a sibling entry is servable.
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                requestJson = """{"requests":[{"data":{"nonce":"n"}},{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}]}""",
                verifiedOrigin = "https://verifier.example",
                selection = selection,
            )
        }
    }

    @Test
    fun bindsTheNativeCallerSigningCertificateIntoTheOrigin() {
        assertTrue(AndroidDigitalCredentialProvider.nativeAppOrigin(byteArrayOf(1, 2, 3)).startsWith("android:apk-key-hash:"))
    }

    /**
     * The adapter must not canonicalize origins itself; it delegates to
     * `DcApiWallet.canonicalizePlatformOrigin`, which also feeds the mdoc session transcript. Asserted
     * through `extract`, because a private normalizer would still satisfy a helper-level test while
     * producing a transcript the verifier cannot reproduce.
     */
    @OptIn(ExperimentalDigitalCredentialApi::class)
    @Config(sdk = [35])
    @Test
    fun canonicalizesAnAllowlistedBrowserOriginThroughTheSharedProtocolRule() {
        val signature = Signature(byteArrayOf(9, 9, 9, 9))
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        val fingerprint = digest.joinToString(":") { byte -> "%02X".format(byte) }
        val intent = providerIntent(
            requestJson = """{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}""",
            packageName = "id.walt.browser",
            signingInfo = signingInfo(signature),
            origin = "https://VERIFIER.example:443",
            selectedCredentialSet = selectedCredentialSetExtras(),
        )

        val input = AndroidDigitalCredentialProvider.extract(
            intent,
            """{"apps":[{"type":"android","info":{"package_name":"id.walt.browser","signatures":[{"build":"release","cert_fingerprint_sha256":"$fingerprint"}]}}]}""",
        )

        assertEquals("https://verifier.example", input.request.verifiedOrigin)
    }

    /**
     * Each matcher's emission is read on its own terms, and an id in neither shape is passed through
     * untouched. The AndroidX matcher registers whatever entry id the wallet gave it - here a UUID -
     * which must not be reinterpreted as a composite.
     */
    @Test
    fun readsEachMatcherIdOnItsOwnTermsAndLeavesOpaqueIdsAlone() {
        assertEquals(
            MatcherCredentialSelection(
                registryEntryId = "annex-c-doc",
                requestIndex = null,
                protocol = MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
            ),
            AndroidDigitalCredentialProvider.parseMatcherCredentialId(
                credentialId = "3 org-iso-mdoc annex-c-doc",
                metadata = "",
            ),
        )
        assertEquals(
            MatcherCredentialSelection(registryEntryId = "opaque-entry", requestIndex = 2),
            AndroidDigitalCredentialProvider.parseMatcherCredentialId(
                credentialId = "opaque-entry",
                metadata = """{"dc_request_index":2,"dcql_cred_id":"pid"}""",
            ),
        )
        // Space-separated but not the Annex C shape: no leading integer, and no protocol.
        assertEquals(
            MatcherCredentialSelection(registryEntryId = "not a recognized-envelope"),
            AndroidDigitalCredentialProvider.parseMatcherCredentialId(
                credentialId = "not a recognized-envelope",
                metadata = "",
            ),
        )
    }

    /**
     * A selection as the AndroidX OpenID4VP matcher emits it: the registry entry id in `credentialId`,
     * the `requests` index in `metadata.dc_request_index`, and a corroborating `credentialSetId`.
     */
    private fun openId4VpSelection(
        requestIndex: Int,
        registryEntryIds: List<String>,
    ): MatcherSelectedRequest = AndroidDigitalCredentialProvider.parseMatcherSelection(
        credentialSetId = "req:$requestIndex;null",
        credentials = registryEntryIds.map { entryId ->
            entryId to """{"dc_request_index":$requestIndex,"dcql_cred_id":"$entryId"}"""
        },
    )

    /**
     * A selection as multipaz's `identitycredentialmatcher.wasm` emits it: `"<n> <protocol>"` as the
     * set id and `"<n> <protocol> <documentId>"` per credential, where `n` is a combination counter
     * over the matcher's own candidate paths and carries no `requests` attribution.
     */
    private fun annexCSelection(
        combinationIndex: Int,
        documentIds: List<String>,
    ): MatcherSelectedRequest {
        val protocol = MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C
        return AndroidDigitalCredentialProvider.parseMatcherSelection(
            credentialSetId = "$combinationIndex $protocol",
            credentials = documentIds.map { documentId ->
                "$combinationIndex $protocol $documentId" to ""
            },
        )
    }
}
