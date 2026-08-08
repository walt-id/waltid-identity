package id.walt.wallet2.mobile

import android.content.Intent
import android.content.pm.Signature
import android.content.pm.SigningInfo
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
import kotlin.test.assertTrue

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
        )

        assertFailsWith<IllegalStateException> {
            AndroidDigitalCredentialProvider.extract(intent, """{"apps":[]}""")
        }
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
        )
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
     * The alternative the user chose is the one that is answered, even though the wallet also supports
     * the entry at index 0. Choosing by index rather than by protocol support is the whole point: the
     * two entries here differ in `nonce`, so answering the wrong one produces a response bound to a
     * request the user was never shown.
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
     * order. There is no wallet preference left to consult: Credential Manager already showed the user
     * one candidate list and recorded which entry they picked.
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

        // Annex C selected, listed first and then second. Its matcher names only the protocol, so
        // the resolution is by protocol in both orders. The leading `7` is multipaz's combination
        // counter and is deliberately not the index of either alternative: reading it as one is the
        // bug this replaces.
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
     * A selected signed alternative fails, and specifically does not fall through to the unsigned
     * sibling: the user consented to the signed request, and answering the other one would produce a
     * response for a request they never saw.
     */
    @Test
    fun rejectsASelectedSignedAlternativeInsteadOfSwitchingToTheUnsignedSibling() {
        listOf(
            MobileWalletDigitalCredentialProtocols.OPENID4VP_SIGNED,
            MobileWalletDigitalCredentialProtocols.OPENID4VP_MULTISIGNED,
        ).forEach { signedProtocol ->
            assertFailsWith<IllegalArgumentException>("$signedProtocol was not rejected") {
                AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                    requestJson = """
                        {"requests":[
                          {"protocol":"$signedProtocol","data":{"request":"a.b.c"}},
                          {"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}
                        ]}
                    """.trimIndent(),
                    verifiedOrigin = "https://verifier.example",
                    selection = openId4VpSelection(requestIndex = 0, registryEntryIds = listOf("signed-entry")),
                )
            }
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

    /**
     * An opaque selection across several alternatives fails closed. Falling back to a wallet-chosen
     * alternative here is exactly the second, independent protocol decision this must not make.
     */
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
     * `MobileWallet.previewDigitalCredentialPresentation` reads an empty list as `eligibleCredentialIds
     * = null`, meaning unrestricted DCQL matching over the whole store. That is correct for a platform
     * that asserts no selection (iOS), and must be unreachable from a platform selection that could
     * not be resolved - otherwise a malformed matcher result would *widen* the candidate set.
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
        // Only the absence of any platform selection yields an unrestricted request, which is the iOS
        // shape rather than anything Credential Manager can produce.
        assertEquals(
            emptyList(),
            AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                requestJson = """{"requests":[{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}]}""",
                verifiedOrigin = "https://verifier.example",
                selection = null,
            ).selectedRegistryEntryIds,
        )
    }

    /** Signed variants stay unsupported: an envelope offering only those must fail closed. */
    @Test
    fun rejectsAnEnvelopeThatOffersOnlyUnsupportedProtocols() {
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                requestJson = """
                    {"requests":[
                      {"protocol":"openid4vp-v1-signed","data":{"request":"a.b.c"}},
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
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                requestJson = """{"requests":[]}""",
                verifiedOrigin = "https://verifier.example",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                requestJson = """{"protocol":"openid4vp-v1-unsigned","data":"secret"}""",
                verifiedOrigin = "https://verifier.example",
            )
        }
        // A protocol-less entry is malformed even when a sibling entry is servable, because the
        // envelope no longer describes what the verifier is asking for.
        assertFailsWith<IllegalArgumentException> {
            AndroidDigitalCredentialProvider.resolveSelectedProtocolRequest(
                requestJson = """{"requests":[{"data":{"nonce":"n"}},{"protocol":"openid4vp-v1-unsigned","data":{"nonce":"n"}}]}""",
                verifiedOrigin = "https://verifier.example",
            )
        }
    }

    @Test
    fun bindsTheNativeCallerSigningCertificateIntoTheOrigin() {
        assertTrue(AndroidDigitalCredentialProvider.nativeAppOrigin(byteArrayOf(1, 2, 3)).startsWith("android:apk-key-hash:"))
    }

    /**
     * The adapter must not canonicalize; it delegates to `DcApiWallet.canonicalizePlatformOrigin`,
     * which also feeds the mdoc session transcript. Asserting through `extract` rather than on a
     * helper is deliberate: a reintroduced private normalizer would still satisfy a helper-level
     * test while producing a transcript the verifier cannot reproduce.
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
            // Uppercase host and an explicit default port: the two cases where the deleted adapter
            // copy and the protocol rule used to disagree.
            origin = "https://VERIFIER.example:443",
        )

        val input = AndroidDigitalCredentialProvider.extract(
            intent,
            """{"apps":[{"type":"android","info":{"package_name":"id.walt.browser","signatures":[{"build":"release","cert_fingerprint_sha256":"$fingerprint"}]}}]}""",
        )

        assertEquals("https://verifier.example", input.request.verifiedOrigin)
    }

    /**
     * Each matcher's own emission is read on its terms, and an id in neither shape is passed through
     * untouched. The AndroidX matcher registers whatever entry id the wallet gave it, which for this
     * wallet is a UUID and must not be reinterpreted as a composite.
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
