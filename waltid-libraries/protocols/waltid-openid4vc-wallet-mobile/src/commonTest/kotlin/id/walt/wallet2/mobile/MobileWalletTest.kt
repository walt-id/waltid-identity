@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.SdJwtExamples
import id.walt.credentials.formats.SdJwtCredential
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import io.ktor.http.URLBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import id.walt.crypto2.keys.Key as ManagedKeyMaterial

class MobileWalletTest {

    @Test
    fun presentationErrorCodesMatchOAuthAndOpenId4VpValues() {
        assertEquals(
            listOf(
                "access_denied",
                "invalid_request",
                "invalid_client",
                "invalid_scope",
                "unauthorized_client",
                "unsupported_response_type",
                "server_error",
                "temporarily_unavailable",
                "vp_formats_not_supported",
                "invalid_request_uri_method",
                "invalid_transaction_data",
                "wallet_unavailable",
            ),
            MobileWalletPresentationErrorCode.entries.map { it.errorCode },
        )
    }

    @Test
    fun mobileWalletConfigUsesStableDefaults() {
        val config = MobileWalletConfig()
        val (walletId, defaultKeyType, attestationConfig, persistence, onEvent, preferredLocales, transactionDataProfiles) = config

        assertEquals("default", walletId)
        assertEquals(MobileWalletKeyType.secp256r1, defaultKeyType)
        assertEquals(null, attestationConfig)
        assertEquals(MobileWalletPersistence(), persistence)
        assertEquals(emptyList(), preferredLocales)
        assertEquals(emptyList(), transactionDataProfiles)
        assertSame(config.onEvent, onEvent)
        assertIs<MobileWalletDatabaseKey.Managed>(config.persistence.databaseKey)
        assertEquals(null, config.persistence.credentialStore)
        assertEquals(null, config.persistence.didStore)
    }

    @Test
    fun defaultTrustConfigurationRejectsUnknownPreRegisteredVerifier() = runTest {
        val verifierKey = JWKKey.generate(KeyType.Ed25519)
        val requestUrl = preRegisteredRequestUrl(verifierKey)
        val wallet = MobileWallet(
            walletId = "default-trust-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "unused-key", keyType = "Ed25519")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:unused", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(),
            generateAndPersistKey = unusedKeyGenerator(),
        )

        val failure = assertFailsWith<AuthorizationRequestResolver.SignedAuthorizationRequestValidationException> {
            wallet.previewPresentation(requestUrl)
        }

        assertEquals(ClientIdError.PreRegisteredClientNotFound("verifier2"), failure.clientIdError)
    }

    @Test
    fun explicitTrustConfigurationAuthenticatesPreRegisteredVerifier() = runTest {
        val verifierKey = JWKKey.generate(KeyType.Ed25519)
        val requestUrl = preRegisteredRequestUrl(verifierKey)
        val wallet = walletWithTrust(
            ClientIdTrustConfiguration(
                preRegisteredClients = mapOf(
                    "verifier2" to ClientMetadata(
                        jwks = ClientMetadata.Jwks(listOf(verifierKey.getPublicKey().exportJWKObject())),
                    )
                ),
            )
        )

        val preview = assertIs<MobileWalletPresentationPreviewResult.Invalid>(
            wallet.previewPresentation(requestUrl)
        )

        assertEquals("verifier2", preview.request.clientId)
        assertEquals(MobileWalletPresentationErrorCode.invalidRequest, preview.errorCode)
    }

    @Test
    fun mobileWalletConfigAcceptsCustomTransactionDataProfiles() {
        val profiles = listOf(
            MobileWalletTransactionDataProfile(
                type = "example.transaction",
                displayName = "Example Transaction",
                fields = listOf("amount"),
            )
        )

        val config = MobileWalletConfig(transactionDataProfiles = profiles)

        assertEquals(profiles, config.transactionDataProfiles)
        val registry = profiles.toTransactionDataTypeRegistry()
        assertEquals(setOf("example.transaction"), registry.types)
    }

    @Test
    fun persistenceCanCombineProvidedDatabaseKeyWithCredentialAndDidStoreOverrides() {
        val didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap())))
        val credentialStore = RecordingCredentialStore()
        val databaseKeyProvider = RecordingDatabaseKeyProvider()

        val persistence = MobileWalletPersistence(
            databaseKey = MobileWalletDatabaseKey.Provided(databaseKeyProvider),
            credentialStore = credentialStore,
            didStore = didStore,
        )

        assertSame(databaseKeyProvider, assertIs<MobileWalletDatabaseKey.Provided>(persistence.databaseKey).provider)
        assertSame(credentialStore, persistence.credentialStore)
        assertSame(didStore, persistence.didStore)
    }

    @Test
    fun persistedManagedKeyIsRestoredWithoutLegacyKeyAfterRestart() = runTest {
        val managedKey = object : ManagedKeyMaterial {
            override val id = KeyId("managed-key")
            override val spec = KeySpec.Ec(EcCurve.P256)
            override val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        }
        val keyStore = PreloadedKeyStore(
            keyInfo = WalletKeyInfo(keyId = managedKey.id.value, keyType = "secp256r1"),
            managedKey = managedKey,
            failIfLegacyKeyRequested = true,
        )
        val wallet = MobileWallet(
            walletId = "managed-key-wallet",
            keyStore = keyStore,
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:managed", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(),
            generateAndPersistKey = unusedKeyGenerator(),
        )

        val bootstrap = wallet.bootstrap()

        assertEquals(managedKey.id.value, bootstrap.keyId)
        assertEquals("did:key:managed", bootstrap.did)
        assertEquals(1, keyStore.managedKeyLookupCalls)
    }

    @Test
    fun persistedDidWithMissingPlatformKeyFailsBootstrap() = runTest {
        val keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "missing-key", keyType = "secp256r1"))
        val wallet = MobileWallet(
            walletId = "missing-key-wallet",
            keyStore = keyStore,
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:missing", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(),
            generateAndPersistKey = unusedKeyGenerator(),
        )

        val failure = assertFailsWith<IllegalArgumentException> { wallet.bootstrap() }

        assertTrue("missing-key" in failure.message.orEmpty())
    }

    @Test
    fun deleteWalletRemovesEntriesFromActiveStores() = runTest {
        val keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1"))
        val didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap())))
        val credentialStore = RecordingCredentialStore()
        val wallet = MobileWallet(
            walletId = "custom-wallet",
            keyStore = keyStore,
            didStore = didStore,
            credentialStore = credentialStore,
            generateAndPersistKey = unusedKeyGenerator(),
        )

        wallet.deleteWallet()

        assertEquals(listOf("custom-key"), keyStore.removedKeyIds)
        assertEquals(listOf("did:key:custom"), didStore.removedDids)
        assertEquals(emptyList(), credentialStore.removedCredentialIds)
    }

    @Test
    fun mobileWalletKeyTypeMapsToInternalKeySpec() {
        assertEquals(KeySpec.Edwards(EdwardsCurve.ED25519), MobileWalletKeyType.Ed25519.toKeySpec())
        assertEquals(KeySpec.Ec(EcCurve.SECP256K1), MobileWalletKeyType.secp256k1.toKeySpec())
        assertEquals(KeySpec.Ec(EcCurve.P256), MobileWalletKeyType.secp256r1.toKeySpec())
        assertEquals(KeySpec.Ec(EcCurve.P384), MobileWalletKeyType.secp384r1.toKeySpec())
        assertEquals(KeySpec.Ec(EcCurve.P521), MobileWalletKeyType.secp521r1.toKeySpec())
        assertEquals(KeySpec.Rsa(2048), MobileWalletKeyType.RSA.toKeySpec())
        assertEquals(KeySpec.Rsa(3072), MobileWalletKeyType.RSA3072.toKeySpec())
        assertEquals(KeySpec.Rsa(4096), MobileWalletKeyType.RSA4096.toKeySpec())
    }

    @Test
    fun walletSessionEventsMapExhaustivelyToMobileWalletEvents() {
        assertEquals(WalletSessionEvent.entries.size, MobileWalletEvent.entries.size)
        WalletSessionEvent.entries.forEach { event ->
            assertEquals(event.name, event.toMobileWalletEvent().name)
        }

        assertEquals(MobileWalletEventPhase.issuance, MobileWalletEvent.issuance_offer_resolved.phase)
        assertEquals(MobileWalletEventStatus.progress, MobileWalletEvent.issuance_offer_resolved.status)
        assertEquals(MobileWalletEventPhase.presentation, MobileWalletEvent.presentation_completed.phase)
        assertEquals(MobileWalletEventStatus.completed, MobileWalletEvent.presentation_completed.status)
        assertEquals(MobileWalletEventStatus.failed, MobileWalletEvent.issuance_failed.status)
    }

    @Test
    fun presentationCredentialRequirementsRejectEmptyCombinations() {
        assertFailsWith<IllegalArgumentException> {
            MobileWalletPresentationCredentialRequirement(emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletPresentationCredentialRequirement(listOf(emptyList()))
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletPresentationCredentialRequirement(listOf(listOf(" ")))
        }
    }

    @Test
    fun presentationOutputModelsRejectMissingCredentialQueryIds() {
        assertFailsWith<IllegalArgumentException> {
            MobileWalletPresentationCredentialOption(
                queryId = " ",
                credentialId = "credential-1",
                format = "vc+sd-jwt",
                issuer = null,
                subject = null,
                label = null,
                credentialDataJson = "{}",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            presentationTransactionData(credentialQueryIds = emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            presentationTransactionData(credentialQueryIds = listOf(" "))
        }
    }

    @Test
    fun presentationRequestInfoRequiresClientIdAndNonce() {
        assertFailsWith<IllegalArgumentException> {
            presentationRequestInfo(clientId = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            presentationRequestInfo(nonce = " ")
        }
    }

    @Test
    fun presentationRequestContextRequiresClientIdButAllowsMissingNonce() {
        assertFailsWith<IllegalArgumentException> {
            MobileWalletPresentationRequestContext(
                clientId = " ",
                verifierMetadata = null,
                responseUri = null,
                state = null,
                nonce = null,
                responseEncryption = MobileWalletResponseEncryption.NotRequired,
            )
        }

        val context = MobileWalletPresentationRequestContext(
            clientId = "https://verifier.example",
            verifierMetadata = null,
            responseUri = null,
            state = null,
            nonce = null,
            responseEncryption = MobileWalletResponseEncryption.NotRequired,
        )
        assertEquals("https://verifier.example", context.clientId)
        assertEquals(null, context.nonce)
    }

    @Test
    fun presentationDisclosuresRejectImpossibleSelectableStates() {
        assertFailsWith<IllegalArgumentException> {
            presentationDisclosure(selectivelyDisclosable = false, required = false, selectable = true)
        }
        assertFailsWith<IllegalArgumentException> {
            presentationDisclosure(selectivelyDisclosable = true, required = true, selectable = true)
        }
    }

    @Test
    fun presentationResultCarriesVerifierResponseAsJsonString() {
        val result = MobileWalletPresentationResult.Transmitted.Succeeded(
            verifierResponseJson = """{"accepted":true}""",
            redirectUrl = "wallet://return",
        )

        assertEquals("""{"accepted":true}""", result.verifierResponseJson)
        assertEquals("wallet://return", result.redirectUrl)
    }

    @Test
    fun presentationResultPreservesFrontChannelResponseArtifacts() {
        val responseUrl = WalletPresentResult(getUrl = "https://verifier.example/callback?error=access_denied")
            .toMobilePresentationResult()
        val formPost = WalletPresentResult(formPostHtml = "<form></form>").toMobilePresentationResult()

        assertEquals(
            MobileWalletPresentationResult.Prepared.OpenUrl(
                "https://verifier.example/callback?error=access_denied"
            ),
            responseUrl,
        )
        assertEquals(MobileWalletPresentationResult.Prepared.SubmitForm("<form></form>"), formPost)
    }

    @Test
    fun presentationResultHonorsExplicitFailedTransmission() {
        val result = WalletPresentResult(
            transmissionSuccess = false,
            verifierResponse = buildJsonObject { put("error", "server_error") },
        ).toMobilePresentationResult()

        assertEquals(
            MobileWalletPresentationResult.Transmitted.Failed("""{"error":"server_error"}"""),
            result,
        )
    }

    @Test
    fun presentationResultRejectsIncompatibleCoreArtifacts() {
        assertFailsWith<IllegalArgumentException> {
            WalletPresentResult(
                getUrl = "https://verifier.example/callback",
                formPostHtml = "<form></form>",
            ).toMobilePresentationResult()
        }
        assertFailsWith<IllegalArgumentException> {
            WalletPresentResult(transmissionSuccess = true).toMobilePresentationResult()
        }
    }

    @Test
    fun credentialsExposeStoredCredentialDataAsJsonString() = runTest {
        val credentialStore = RecordingCredentialStore(
            StoredCredential(
                id = "credential-1",
                credential = SdJwtCredential(
                    dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
                    credentialData = buildJsonObject {
                        put("given_name", "Ada")
                    },
                    issuer = "https://issuer.example",
                    subject = "did:key:subject",
                    signature = null,
                    signed = null,
                ),
                label = "PID",
            )
        )
        val wallet = MobileWallet(
            walletId = "custom-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = credentialStore,
            generateAndPersistKey = unusedKeyGenerator(),
        )

        val credential = wallet.credentials().single()

        assertEquals("credential-1", credential.id)
        assertEquals("dc+sd-jwt", credential.format)
        assertEquals("https://issuer.example", credential.issuer)
        assertEquals("did:key:subject", credential.subject)
        assertEquals("PID", credential.label)
        assertEquals("""{"given_name":"Ada"}""", credential.credentialDataJson)
    }

    @Test
    fun credentialsExposeResolvedSdJwtClaimsWhenDisclosuresAreAvailable() = runTest {
        val (_, parsedCredential) = CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2)
        val credentialStore = RecordingCredentialStore(
            StoredCredential(
                id = "credential-sd-jwt",
                credential = parsedCredential,
                label = "PID",
            )
        )
        val wallet = MobileWallet(
            walletId = "custom-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = credentialStore,
            generateAndPersistKey = unusedKeyGenerator(),
        )

        val displayData = displayJson.parseToJsonElement(wallet.credentials().single().credentialDataJson).jsonObject

        assertEquals("Inga", displayData["given_name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Silverstone", displayData["family_name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("1991-11-06", displayData["birthdate"]?.jsonPrimitive?.contentOrNull)
        assertFalse(displayData.containsKey("_sd"), "resolved SD-JWT display data should not expose digest commitments as the primary content")
    }

    @Test
    fun presentationPreviewUsesSwiftFriendlyCredentialAndClaimDtos() {
        val preview = MobileWalletPresentationPreview(
            previewHandle = MobileWalletPresentationPreviewHandle("preview-1"),
            request = MobileWalletPresentationRequestInfo(
                clientId = "https://verifier.example",
                verifierMetadata = MobileWalletVerifierMetadata(
                    display = MobileWalletMetadataDisplay(
                        name = "Example Verifier",
                        locale = "en",
                        logoUri = null,
                        logoAltText = null,
                    ),
                    clientUri = "https://verifier.example",
                    policyUri = null,
                    termsOfServiceUri = null,
                ),
                responseUri = "https://verifier.example/direct-post",
                state = "state-1",
                nonce = "nonce-1",
                responseEncryption = MobileWalletResponseEncryption.NotRequired,
            ),
            credentialOptions = listOf(
                MobileWalletPresentationCredentialOption(
                    queryId = "pid",
                    credentialId = "credential-1",
                    multiple = true,
                    format = "vc+sd-jwt",
                    issuer = "https://issuer.example",
                    subject = "did:key:subject",
                    label = "PID",
                    credentialDataJson = """{"given_name":"Ada"}""",
                    disclosures = listOf(
                        MobileWalletPresentationDisclosure(
                            path = "$.given_name",
                            name = "given_name",
                            valueJson = """"Ada"""",
                            displayValue = "Ada",
                            selectivelyDisclosable = true,
                        )
                    ),
                )
            ),
            credentialRequirements = listOf(
                MobileWalletPresentationCredentialRequirement(options = listOf(listOf("pid")))
            ),
        )

        assertEquals("https://verifier.example", preview.request.clientId)
        assertEquals("credential-1", preview.credentialOptions.single().credentialId)
        assertEquals(true, preview.credentialOptions.single().multiple)
        assertEquals("Ada", preview.credentialOptions.single().disclosures.single().displayValue)
        assertEquals(listOf(listOf("pid")), preview.credentialRequirements.single().options)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun mobileWalletEventStreamDoesNotBackpressureSlowCollectors() = runTest {
        val stream = MobileWalletEventStream(replay = 1, extraBufferCapacity = 1)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            stream.events.collect {
                delay(Long.MAX_VALUE.milliseconds)
            }
        }

        runCurrent()

        repeat(100) { index ->
            val emitted = stream.tryEmit(MobileWalletEvent.issuance_offer_resolved)

            assertTrue(emitted, "Progress event $index should not suspend or fail when the buffer is full")
        }

        collector.cancel()
    }

    private fun presentationRequestInfo(
        clientId: String = "https://verifier.example",
        nonce: String = "nonce-1",
    ) = MobileWalletPresentationRequestInfo(
        clientId = clientId,
        verifierMetadata = MobileWalletVerifierMetadata(
            display = MobileWalletMetadataDisplay(
                name = "Example Verifier",
                locale = "en",
                logoUri = null,
                logoAltText = null,
            ),
            clientUri = null,
            policyUri = null,
            termsOfServiceUri = null,
        ),
        responseUri = "https://verifier.example/direct-post",
        state = null,
        nonce = nonce,
        responseEncryption = MobileWalletResponseEncryption.NotRequired,
    )

    private fun presentationTransactionData(
        credentialQueryIds: List<String>,
    ) = MobileWalletTransactionDataItem(
        type = "example",
        displayName = "Example",
        credentialQueryIds = credentialQueryIds,
        supportedFields = emptyList(),
        rawJson = "{}",
        detailsJson = "{}",
    )

    private fun presentationDisclosure(
        selectivelyDisclosable: Boolean,
        required: Boolean,
        selectable: Boolean,
    ) = MobileWalletPresentationDisclosure(
        path = "$.claim",
        name = "claim",
        valueJson = "true",
        displayValue = "true",
        selectivelyDisclosable = selectivelyDisclosable,
        required = required,
        selectable = selectable,
    )

    private fun walletWithTrust(trustConfiguration: ClientIdTrustConfiguration): MobileWallet = MobileWallet(
        walletId = "trust-test-wallet",
        keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "unused-key", keyType = "Ed25519")),
        didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:unused", document = JsonObject(emptyMap()))),
        credentialStore = RecordingCredentialStore(),
        generateAndPersistKey = unusedKeyGenerator(),
        clientIdTrustConfiguration = trustConfiguration,
    )

    private suspend fun preRegisteredRequestUrl(verifierKey: Key): String {
        val requestObject = verifierKey.signJws(
            buildJsonObject {
                put("client_id", "verifier2")
                put("nonce", "nonce-123")
                put("response_type", "vp_token")
                put("response_mode", "direct_post")
                put("response_uri", "https://verifier.example/direct-post")
                put("dcql_query", buildJsonObject { put("credentials", buildJsonArray {}) })
            }.toString().encodeToByteArray(),
            mapOf("typ" to JsonPrimitive("oauth-authz-req+jwt")),
        )
        return URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request", requestObject)
        }.buildString()
    }

    private fun unusedKeyGenerator(): suspend (MobileWalletKeyType) -> ManagedKeyMaterial =
        { error("This test must not bootstrap a new key") }

    private val displayJson = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private class RecordingDatabaseKeyProvider : DatabaseEncryptionKeyProvider {
        override suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey =
            DatabaseEncryptionKey("$walletId:$databaseName", ByteArray(32))

        override suspend fun deleteKey(walletId: String, databaseName: String) = Unit
    }

    private class PreloadedKeyStore(
        private val keyInfo: WalletKeyInfo,
        private val key: Key? = null,
        private val managedKey: ManagedKeyMaterial? = null,
        private val failIfLegacyKeyRequested: Boolean = false,
    ) : WalletKeyStore {
        var listKeysCalls = 0
        var managedKeyLookupCalls = 0
        val removedKeyIds = mutableListOf<String>()

        override suspend fun getKey(keyId: String): Key? {
            check(!failIfLegacyKeyRequested) { "Managed-key bootstrap must not load a legacy key" }
            return key.takeIf { keyId == keyInfo.keyId }
        }

        override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>): ManagedKeyMaterial? {
            managedKeyLookupCalls++
            return managedKey.takeIf { keyId == keyInfo.keyId }
        }

        override suspend fun listKeys(): Flow<WalletKeyInfo> {
            listKeysCalls++
            return listOf(keyInfo).asFlow()
        }

        override suspend fun addKey(key: Key): String =
            error("Preloaded test key store should not add keys")

        override suspend fun removeKey(keyId: String): Boolean {
            removedKeyIds += keyId
            return true
        }
    }

    private class PreloadedDidStore(private val did: WalletDidEntry) : WalletDidStore {
        var listDidsCalls = 0
        val removedDids = mutableListOf<String>()

        override suspend fun getDid(did: String): WalletDidEntry? = this.did.takeIf { it.did == did }

        override suspend fun listDids(): Flow<WalletDidEntry> {
            listDidsCalls++
            return listOf(did).asFlow()
        }

        override suspend fun addDid(entry: WalletDidEntry) =
            error("Preloaded test DID store should not add DIDs")

        override suspend fun removeDid(did: String): Boolean {
            removedDids += did
            return true
        }
    }

    private class RecordingCredentialStore(
        private vararg val credentials: StoredCredential,
    ) : WalletCredentialStore {
        val removedCredentialIds = mutableListOf<String>()

        override suspend fun getCredential(id: String): StoredCredential? = null

        override suspend fun listCredentials(): Flow<StoredCredential> =
            credentials.toList().asFlow()

        override suspend fun addCredential(entry: StoredCredential) =
            error("Recording credential store should not add credentials in this test")

        override suspend fun removeCredential(id: String): Boolean {
            removedCredentialIds += id
            return true
        }
    }
}
