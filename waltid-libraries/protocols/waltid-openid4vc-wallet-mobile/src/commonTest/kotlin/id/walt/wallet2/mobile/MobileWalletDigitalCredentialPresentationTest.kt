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
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
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
 * Presentation over the OS-mediated Digital Credentials API, driven through the public
 * [MobileWallet] facade rather than the protocol module, because the facade owns two translations no
 * protocol-level test can reach: the platform registry entry ids the OS hands back become wallet
 * credential ids, and the wallet's configured transaction-data profiles become the metadata the
 * provider consent UI renders.
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
     * cleartext response, which the verifier would still accept as an answer to its encrypted
     * request. The wallet may not release a presentation it cannot protect.
     */
    @Test
    fun anEncryptedResponseModeWithoutVerifierKeysFailsRatherThanFallingBackToCleartext() = runTest {
        val fixture = walletFixture(sdJwtCredential())

        val preview = fixture.wallet.previewDigitalCredentialPresentation(
            dcApiRequest(
                data = sdJwtQuery(responseMode = "dc_api.jwt"),
                selectedRegistryEntryIds = listOf(fixture.registryEntryId("pid-1")),
            )
        )

        assertFailsWith<IllegalArgumentException> {
            fixture.wallet.submitDigitalCredentialPresentation(
                requestId = preview.requestId,
                selectedCredentialOptions = preview.credentialOptions.selections(),
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
     * The verifier's `transaction_data` has to survive the Credential Manager transport and reach
     * the provider UI as the existing [MobileWalletTransactionDataItem], with the display name and
     * field list coming from the wallet's configured profile rather than from the request - a
     * verifier must not be able to label its own authorization prompt.
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
     * A transaction-data type the wallet holds no profile for must be rejected before consent: the
     * provider UI cannot describe what it has no profile for, so presenting anyway would ask the
     * user to authorize something unlabelled.
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

    private class Fixture(val wallet: MobileWallet, private val registry: CapturingRegistry) {
        /**
         * The registry entry id the platform would have been handed for [credentialId]. Read back
         * from the projection the wallet actually published rather than recomputed here, so a test
         * cannot pass against an entry id the OS would never have seen.
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
        val wallet = MobileWallet(
            walletId = "dc-api-presentation-wallet",
            keyStore = InMemoryKeyStore().also { it.addCrypto2Key(holderKey) },
            didStore = InMemoryDidStore().also {
                it.addDid(WalletDidEntry(did = "did:key:holder", document = JsonObject(emptyMap())))
            },
            credentialStore = InMemoryCredentialStore().also { store ->
                credentials.forEach { store.addCredential(it) }
            },
            generateAndPersistKey = { error("Digital Credentials presentation must not bootstrap a key") },
            transactionDataProfiles = transactionDataProfiles,
            credentialRegistry = registry,
        )
        return Fixture(wallet, registry)
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
     * Parsed rather than hand-built, because the parser is what puts `docType` into
     * `credentialData` - and that key is the only thing DCQL's `doctype_value` constraint reads. A
     * hand-built fixture omitting it matches nothing, which no assertion here could tell apart from
     * a routing bug.
     */
    private suspend fun mdocCredential(id: String = "mdl-1"): StoredCredential = StoredCredential(
        id = id,
        credential = CredentialParser.detectAndParse(MdocsExamples.mdocsExampleBase64Url).second,
        label = "mDL",
    )

    private fun List<MobileWalletPresentationCredentialOption>.selections() =
        map { MobileWalletPresentationCredentialSelection(it.queryId, it.credentialId) }

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
