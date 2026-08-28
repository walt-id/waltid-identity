package id.walt.wallet2.mobile

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.MdocsExamples
import id.walt.credentials.examples.SdJwtExamples
import id.walt.crypto.utils.Base64Utils.base64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Presentation over the OS-mediated Digital Credentials API, driven through the [MobileWallet] facade
 * rather than the protocol module, which owns two translations no protocol-level test can reach:
 * platform registry entry ids become wallet credential ids, and configured transaction-data profiles
 * become the metadata the provider consent UI renders.
 */
class MobileWalletDigitalCredentialPresentationTest {

    @Test
    fun previewsAndSubmitsAnSdJwtVcRequestThroughTheDigitalCredentialsApi() = runTest {
        val fixture = walletFixture(sdJwtCredential())

        val preview = fixture.wallet.previewDigitalCredentialPresentation(
            dcApiRequest(
                data = sdJwtQuery(),
                selectedRegistryEntryIds = listOf(fixture.registryEntryId("pid-1")),
            )
        )

        assertEquals(MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED, preview.protocol)
        assertEquals("https://verifier.example", preview.verifiedOrigin)
        // The unsigned protocol ignores the request-supplied client_id; the platform origin is the
        // only authenticated requester identity.
        assertEquals(null, preview.request.clientId)
        assertEquals("dc_api", preview.request.responseMode)
        assertEquals("dc+sd-jwt", preview.credentialOptions.single().format)
        assertEquals("pid-1", preview.credentialOptions.single().credentialId)
        assertEquals(MobileWalletReaderTrust.NotApplicable, preview.readerTrust)

        val response = fixture.wallet.submitDigitalCredentialPresentation(
            requestId = preview.requestId,
            selectedCredentialOptions = preview.credentialOptions.selections(),
        )

        assertEquals(MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED, response.protocol)
        val data = Json.parseToJsonElement(response.dataJson).jsonObject
        // response_mode=dc_api returns the members in cleartext for the platform to relay.
        assertEquals(setOf("vp_token"), data.keys)
        // An SD-JWT VC presentation ends in the KB-JWT that proves the holder answered this request
        // rather than replaying an earlier presentation.
        val presentation = fixture.presentationFor("pid", data)
        assertEquals(3, presentation.substringAfterLast('~').split('.').size, "missing KB-JWT: $presentation")
    }

    @Test
    fun previewsAndSubmitsAnMdocRequestThroughTheDigitalCredentialsApi() = runTest {
        val fixture = walletFixture(mdocCredential())

        val preview = fixture.wallet.previewDigitalCredentialPresentation(
            dcApiRequest(
                data = mdocQuery(),
                selectedRegistryEntryIds = listOf(fixture.registryEntryId("mdl-1")),
            )
        )

        assertEquals("mso_mdoc", preview.credentialOptions.single().format)

        val response = fixture.wallet.submitDigitalCredentialPresentation(
            requestId = preview.requestId,
            selectedCredentialOptions = preview.credentialOptions.selections(),
        )

        val data = Json.parseToJsonElement(response.dataJson).jsonObject
        assertEquals(setOf("vp_token"), data.keys)
        assertTrue(
            fixture.presentationFor("mdl", data).isNotBlank(),
            "mdoc DeviceResponse missing from the vp_token: $data",
        )
    }

    /**
     * Under `dc_api.jwt` both the OS and the website that called `getCredential` relay the response,
     * so the disclosed claims must be unreadable to both. Asserting the member set is what pins
     * that: a facade emitting `vp_token` alongside `response` would encrypt nothing in practice.
     */
    @Test
    fun anEncryptedResponseModeReturnsOnlyAJweToThePlatform() = runTest {
        val fixture = walletFixture(sdJwtCredential())

        val preview = fixture.wallet.previewDigitalCredentialPresentation(
            dcApiRequest(
                data = sdJwtQuery(responseMode = "dc_api.jwt", clientMetadata = ENCRYPTION_CLIENT_METADATA),
                selectedRegistryEntryIds = listOf(fixture.registryEntryId("pid-1")),
            )
        )

        assertEquals("dc_api.jwt", preview.request.responseMode)

        val response = fixture.wallet.submitDigitalCredentialPresentation(
            requestId = preview.requestId,
            selectedCredentialOptions = preview.credentialOptions.selections(),
        )

        val data = Json.parseToJsonElement(response.dataJson).jsonObject
        assertEquals(setOf("response"), data.keys)
        val jwe = assertNotNull(data["response"]?.jsonPrimitive?.content)
        assertEquals(5, jwe.split('.').size, "response is not a compact JWE: $jwe")
        assertFalse(jwe.contains("vp_token"), "the response members leaked outside the JWE ciphertext")
    }

    /**
     * `dc_api.jwt` without usable verifier encryption keys must fail rather than degrade to a
     * cleartext response, which the verifier would still accept as an answer to its encrypted request.
     *
     * It must fail at *preview*: a successful preview has already matched DCQL over the store and
     * returned the matches to the caller, so an unanswerable request would still have disclosed what
     * the wallet holds. Every case therefore also asserts the store was read no more than by a request
     * rejected before resolution.
     */
    @Test
    fun anUnusableEncryptedResponseConfigurationIsRejectedBeforeAnyCredentialIsMatched() = runTest {
        // What the wallet reads before looking at the request at all: the registry projection is rebuilt
        // to map platform entry ids onto credential ids, for every request including a stale one.
        // Measured rather than hardcoded, so this asserts ordering and not how the projection is built.
        val readsBeforeAnyResolution = run {
            val fixture = walletFixture(sdJwtCredential())
            fixture.registryEntryId("pid-1")
            fixture.forgetCredentialReads()
            assertFailsWith<MobileWalletStaleRegistryEntryException> {
                fixture.wallet.previewDigitalCredentialPresentation(
                    dcApiRequest(
                        data = sdJwtQuery(),
                        selectedRegistryEntryIds = listOf("dc-entry-from-a-replaced-projection"),
                    )
                )
            }
            fixture.credentialReads()
        }

        // Each case is a verifier configuration the wallet cannot encrypt to, paired with why.
        val unusableClientMetadata = listOf(
            "no client_metadata at all" to null,
            "an empty jwks" to """{"jwks": {"keys": []}}""",
            "no key usable for key agreement" to """
                {"jwks": {"keys": [{
                  "kty": "EC", "crv": "P-256", "kid": "sig-key", "use": "sig", "alg": "ES256",
                  "x": "y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30",
                  "y": "jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg"
                }]}}
            """.trimIndent(),
            "an unsupported key-management alg" to """
                {"jwks": {"keys": [{
                  "kty": "EC", "crv": "P-256", "kid": "enc-key", "use": "enc", "alg": "ECDH-ES+A256KW",
                  "x": "y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30",
                  "y": "jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg"
                }]}}
            """.trimIndent(),
            "an unsupported curve" to """
                {"jwks": {"keys": [{
                  "kty": "EC", "crv": "P-224", "kid": "enc-key", "use": "enc", "alg": "ECDH-ES",
                  "x": "y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30",
                  "y": "jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg"
                }]}}
            """.trimIndent(),
            "malformed key material" to """
                {"jwks": {"keys": [{
                  "kty": "EC", "crv": "P-256", "kid": "enc-key", "use": "enc", "alg": "ECDH-ES",
                  "x": "not-base64url-coordinates"
                }]}}
            """.trimIndent(),
            // A verifier publishing its private key is either compromised or misconfigured; either
            // way the wallet must not encrypt to it as though the key were confidential.
            "a private key published as the recipient" to """
                {"jwks": {"keys": [{
                  "kty": "EC", "crv": "P-256", "kid": "enc-key", "use": "enc", "alg": "ECDH-ES",
                  "x": "y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30",
                  "y": "jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg",
                  "d": "9s7NRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg"
                }]}}
            """.trimIndent(),
            // A usable key, but no content-encryption algorithm in common.
            "no compatible content encryption" to """
                {
                  "jwks": {"keys": [{
                    "kty": "EC", "crv": "P-256", "kid": "enc-key", "use": "enc", "alg": "ECDH-ES",
                    "x": "y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30",
                    "y": "jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg"
                  }]},
                  "encrypted_response_enc_values_supported": ["XC20P"]
                }
            """.trimIndent(),
        )

        unusableClientMetadata.forEach { (why, clientMetadata) ->
            val fixture = walletFixture(sdJwtCredential())
            val registryEntryId = fixture.registryEntryId("pid-1")
            // Registration itself reads the store, so count only what the preview reads.
            fixture.forgetCredentialReads()

            assertFailsWith<IllegalArgumentException>("dc_api.jwt with $why was previewed") {
                fixture.wallet.previewDigitalCredentialPresentation(
                    dcApiRequest(
                        data = sdJwtQuery(responseMode = "dc_api.jwt", clientMetadata = clientMetadata),
                        selectedRegistryEntryIds = listOf(registryEntryId),
                    )
                )
            }

            assertEquals(
                readsBeforeAnyResolution,
                fixture.credentialReads(),
                "dc_api.jwt with $why matched credentials before rejecting the request",
            )
        }
    }

    /**
     * The OS matcher may hand back an entry id from a registry projection the wallet has since
     * replaced. Resolving that against the current store would either present the wrong credential
     * or silently widen the selection to everything, so it must be rejected by identity.
     */
    @Test
    fun aStaleRegistrySelectionIsRejectedBeforeAnyCredentialIsMatched() = runTest {
        val fixture = walletFixture(sdJwtCredential())

        val failure = assertFailsWith<MobileWalletStaleRegistryEntryException> {
            fixture.wallet.previewDigitalCredentialPresentation(
                dcApiRequest(
                    data = sdJwtQuery(),
                    selectedRegistryEntryIds = listOf("dc-entry-from-a-replaced-projection"),
                )
            )
        }

        assertEquals("dc-entry-from-a-replaced-projection", failure.registryEntryId)
    }

    /**
     * Credential Manager can return several entries for one request. Every returned entry must
     * become an offered option - dropping one would silently narrow the user's choice - and an entry
     * the OS did not return must not be presentable, even though it satisfies the same query.
     */
    @Test
    fun everySelectedRegistryEntryBecomesAnOfferedOptionAndUnselectedOnesStayUnpresentable() = runTest {
        val fixture = walletFixture(
            sdJwtCredential(id = "pid-1"),
            sdJwtCredential(id = "pid-2"),
            sdJwtCredential(id = "pid-3"),
        )

        val preview = fixture.wallet.previewDigitalCredentialPresentation(
            dcApiRequest(
                data = sdJwtQuery(multiple = true),
                selectedRegistryEntryIds = listOf(
                    fixture.registryEntryId("pid-1"),
                    fixture.registryEntryId("pid-3"),
                ),
            )
        )

        assertEquals(
            setOf("pid-1", "pid-3"),
            preview.credentialOptions.mapTo(mutableSetOf()) { it.credentialId },
        )

        assertFailsWith<IllegalArgumentException> {
            fixture.wallet.submitDigitalCredentialPresentation(
                requestId = preview.requestId,
                selectedCredentialOptions = listOf(
                    MobileWalletPresentationCredentialSelection(
                        preview.credentialOptions.first().queryId,
                        "pid-2",
                    ),
                ),
            )
        }
    }

    /**
     * The verifier's `transaction_data` has to survive the Credential Manager transport and reach the
     * provider UI as a [MobileWalletTransactionDataItem]. Display name and field list come from the
     * wallet's configured profile: a verifier must not be able to label its own authorization prompt.
     */
    @Test
    fun transactionDataReachesThePreviewThroughTheConfiguredWalletProfile() = runTest {
        val fixture = walletFixture(
            sdJwtCredential(),
            transactionDataProfiles = listOf(
                MobileWalletTransactionDataProfile(
                    type = PAYMENT_TYPE,
                    displayName = "Payment Authorization",
                    fields = listOf("amount", "currency"),
                ),
            ),
        )

        val preview = fixture.wallet.previewDigitalCredentialPresentation(
            dcApiRequest(
                data = sdJwtQuery(transactionData = listOf(paymentTransactionData())),
                selectedRegistryEntryIds = listOf(fixture.registryEntryId("pid-1")),
            )
        )

        val item = assertNotNull(
            preview.request.transactionData.singleOrNull(),
            "preview must expose the requested transaction data: ${preview.request.transactionData}",
        )
        assertEquals(PAYMENT_TYPE, item.type)
        assertEquals("Payment Authorization", item.displayName)
        assertEquals(listOf("amount", "currency"), item.supportedFields)
        assertEquals(listOf("pid"), item.credentialQueryIds)
        val details = Json.parseToJsonElement(item.detailsJson).jsonObject
        assertEquals("42.00", details["amount"]?.jsonPrimitive?.content)
        assertEquals("EUR", details["currency"]?.jsonPrimitive?.content)

        // Submission keeps the existing SD-JWT binding: the consented transaction data is hashed
        // into the KB-JWT, which is what makes the authorization non-repudiable.
        val response = fixture.wallet.submitDigitalCredentialPresentation(
            requestId = preview.requestId,
            selectedCredentialOptions = preview.credentialOptions.selections(),
        )
        val presentation = fixture.presentationFor("pid", Json.parseToJsonElement(response.dataJson).jsonObject)
        val kbJwtPayload = Json.parseToJsonElement(
            base64Url.decode(presentation.substringAfterLast('~').split('.')[1]).decodeToString(),
        ).jsonObject
        assertTrue(
            kbJwtPayload.containsKey("transaction_data_hashes"),
            "KB-JWT must bind the consented transaction data: $kbJwtPayload",
        )
    }

    /**
     * A transaction-data type the wallet holds no profile for is rejected before consent, rather than
     * asking the user to authorize something the provider UI cannot label.
     */
    @Test
    fun anUnconfiguredTransactionDataTypeIsRejectedBeforeConsent() = runTest {
        val fixture = walletFixture(sdJwtCredential())

        assertFailsWith<IllegalArgumentException> {
            fixture.wallet.previewDigitalCredentialPresentation(
                dcApiRequest(
                    data = sdJwtQuery(transactionData = listOf(paymentTransactionData())),
                    selectedRegistryEntryIds = listOf(fixture.registryEntryId("pid-1")),
                )
            )
        }
    }

    /** Annex C requests use the dedicated facade, so the OpenID4VP entry point must turn them away. */
    @Test
    fun annexCRequestsAreNotAcceptedByTheOpenId4VpEntryPoint() = runTest {
        val fixture = walletFixture(mdocCredential())

        assertFailsWith<IllegalArgumentException> {
            fixture.wallet.previewDigitalCredentialPresentation(
                dcApiRequest(data = mdocQuery()).copy(
                    protocol = MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
                )
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------------------------

    private class Fixture(
        val wallet: MobileWallet,
        private val registry: CapturingRegistry,
        private val credentialStore: RecordingCredentialStore,
    ) {
        /** How many times the credential store has been read since the last [forgetCredentialReads]. */
        fun credentialReads(): Int = credentialStore.reads

        fun forgetCredentialReads() {
            credentialStore.reads = 0
        }

        /**
         * The registry entry id the platform would have been handed for [credentialId], read back from
         * the published projection rather than recomputed, so no test can pass against an entry id the
         * OS would never have seen.
         */
        suspend fun registryEntryId(credentialId: String): String {
            if (registry.records.isEmpty()) wallet.refreshDigitalCredentialRegistration()
            return assertNotNull(
                registry.records.firstOrNull { it.credentialId == credentialId }?.registryEntryId,
                "wallet did not register '$credentialId': ${registry.records}",
            )
        }

        fun presentationFor(queryId: String, data: JsonObject): String = assertNotNull(
            data["vp_token"]?.jsonObject?.get(queryId)?.jsonArray?.singleOrNull()?.jsonPrimitive?.content,
            "vp_token has no single presentation for query '$queryId': $data",
        )
    }

    private suspend fun walletFixture(
        vararg credentials: StoredCredential,
        transactionDataProfiles: List<MobileWalletTransactionDataProfile> = emptyList(),
    ): Fixture {
        // The DC API is a crypto2-only surface, so the wallet holds nothing but a managed key.
        val holderKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("dc-api-holder-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val registry = CapturingRegistry()
        val credentialStore = RecordingCredentialStore().also { store ->
            credentials.forEach { store.addCredential(it) }
        }
        val wallet = MobileWallet(
            walletId = "dc-api-presentation-wallet",
            keyStore = InMemoryMobileWalletKeyStore().also { it.addCrypto2Key(holderKey) },
            didStore = InMemoryDidStore().also {
                it.addDid(WalletDidEntry(did = "did:key:holder", document = JsonObject(emptyMap())))
            },
            credentialStore = credentialStore,
            generateAndPersistKey = { _, _ -> error("Digital Credentials presentation must not bootstrap a key") },
            transactionDataProfiles = transactionDataProfiles,
            credentialRegistry = registry,
        )
        return Fixture(wallet, registry, credentialStore)
    }

    private fun dcApiRequest(
        data: String,
        selectedRegistryEntryIds: List<String> = emptyList(),
    ) = MobileWalletDigitalCredentialRequest(
        protocol = MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
        dataJson = data,
        verifiedOrigin = "https://verifier.example",
        selectedRegistryEntryIds = selectedRegistryEntryIds,
    )

    private fun sdJwtQuery(
        responseMode: String = "dc_api",
        clientMetadata: String? = null,
        transactionData: List<String> = emptyList(),
        multiple: Boolean = false,
    ): String = dcApiRequestData(
        responseMode = responseMode,
        clientMetadata = clientMetadata,
        transactionData = transactionData,
        credentialQuery = """
            {
              "id": "pid",
              "format": "dc+sd-jwt",
              "multiple": $multiple,
              "meta": {"vct_values": ["$SD_JWT_VCT"]},
              "claims": [{"path": ["family_name"]}]
            }
        """.trimIndent(),
    )

    private fun mdocQuery(): String = dcApiRequestData(
        credentialQuery = """
            {
              "id": "mdl",
              "format": "mso_mdoc",
              "meta": {"doctype_value": "$MDOC_DOCTYPE"},
              "claims": [{"path": ["$MDOC_NAMESPACE", "given_name"]}]
            }
        """.trimIndent(),
    )

    private fun dcApiRequestData(
        credentialQuery: String,
        responseMode: String = "dc_api",
        clientMetadata: String? = null,
        transactionData: List<String> = emptyList(),
    ): String = buildString {
        append("""{"response_type":"vp_token","response_mode":"$responseMode","nonce":"nonce-123",""")
        // A request-supplied client_id the unsigned protocol must ignore rather than trust.
        append(""""client_id":"attacker-supplied",""")
        clientMetadata?.let { append(""""client_metadata":$it,""") }
        if (transactionData.isNotEmpty()) {
            append(""""transaction_data":[${transactionData.joinToString(",") { "\"$it\"" }}],""")
        }
        append(""""dcql_query":{"credentials":[$credentialQuery]}}""")
    }

    /** Base64url `transaction_data` item in the OpenID4VP shape. */
    private fun paymentTransactionData(): String =
        """{"type":"$PAYMENT_TYPE","credential_ids":["pid"],"amount":"42.00","currency":"EUR"}"""
            .encodeToByteArray()
            .encodeToBase64Url()

    private suspend fun sdJwtCredential(id: String = "pid-1"): StoredCredential = StoredCredential(
        id = id,
        credential = CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2).second,
        label = "PID",
    )

    /**
     * Parsed rather than hand-built: the parser is what puts `docType` into `credentialData`, which is
     * the only key DCQL's `doctype_value` constraint reads. A fixture omitting it matches nothing, which
     * would be indistinguishable here from a routing bug.
     */
    private suspend fun mdocCredential(id: String = "mdl-1"): StoredCredential = StoredCredential(
        id = id,
        credential = CredentialParser.detectAndParse(MdocsExamples.mdocsExampleBase64Url).second,
        label = "mDL",
    )

    private fun List<MobileWalletPresentationCredentialOption>.selections() =
        map { MobileWalletPresentationCredentialSelection(it.queryId, it.credentialId) }

    /**
     * Credential store that counts reads, so a test can assert a request was refused *before* the wallet
     * looked at what it holds; the thrown exception alone would not distinguish that from a rejection
     * after DCQL matching.
     */
    private class RecordingCredentialStore : WalletCredentialStore {
        private val credentials = mutableMapOf<String, StoredCredential>()
        var reads: Int = 0

        override suspend fun getCredential(id: String): StoredCredential? {
            reads++
            return credentials[id]
        }

        override suspend fun listCredentials(): Flow<StoredCredential> {
            reads++
            return credentials.values.toList().asFlow()
        }

        override suspend fun addCredential(entry: StoredCredential) {
            credentials[entry.id] = entry
        }

        override suspend fun removeCredential(id: String): Boolean = credentials.remove(id) != null
    }

    /** Registry that keeps the last published projection so tests can read real entry ids back. */
    private class CapturingRegistry : MobileWalletCredentialRegistry {
        var records: List<MobileWalletCredentialRegistryRecord> = emptyList()
        override val capabilities = UnavailableMobileWalletCredentialRegistry.capabilities

        override suspend fun replace(
            registryId: String,
            records: List<MobileWalletCredentialRegistryRecord>,
        ): MobileWalletCredentialRegistrationResult {
            this.records = records
            return MobileWalletCredentialRegistrationResult(available = true, registeredEntryCount = records.size)
        }
    }

    private companion object {
        const val MDOC_DOCTYPE = "org.iso.18013.5.1.mDL"
        const val MDOC_NAMESPACE = "org.iso.18013.5.1"
        const val SD_JWT_VCT = "https://credentials.example.com/identity_credential"
        const val PAYMENT_TYPE = "payment_authorization"

        /** P-256 `use:enc` verifier key, matching the protocol-level DC API encryption fixture. */
        val ENCRYPTION_CLIENT_METADATA = """
            {
              "jwks": {"keys": [{
                "kty": "EC",
                "crv": "P-256",
                "x": "y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30",
                "y": "jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg",
                "use": "enc",
                "kid": "enc-key",
                "alg": "ECDH-ES"
              }]},
              "encrypted_response_enc_values_supported": ["A256GCM"]
            }
        """.trimIndent()
    }
}
