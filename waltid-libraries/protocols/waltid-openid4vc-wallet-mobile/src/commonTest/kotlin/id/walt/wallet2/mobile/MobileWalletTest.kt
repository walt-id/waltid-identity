package id.walt.wallet2.mobile

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.SdJwtExamples
import id.walt.credentials.formats.SdJwtCredential
import id.walt.crypto.keys.KeyType
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MobileWalletTest {

    @Test
    fun mobileWalletConfigUsesStableDefaults() {
        val config = MobileWalletConfig()

        assertEquals("default", config.walletId)
        assertEquals(MobileWalletKeyType.secp256r1, config.defaultKeyType)
        assertEquals(null, config.attestationConfig)
        assertEquals(MobileWalletPersistence(), config.persistence)
        assertEquals(emptyList(), config.transactionDataProfiles)
        assertIs<MobileWalletDatabaseKey.Managed>(config.persistence.databaseKey)
        assertEquals(MobileWalletStores(), config.persistence.stores)
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
    fun persistenceCanCombineProvidedDatabaseKeyWithIndependentStoreOverrides() {
        val keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1"))
        val didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap())))
        val credentialStore = RecordingCredentialStore()
        val databaseKeyProvider = RecordingDatabaseKeyProvider()
        val keys = MobileWalletKeys(
            store = keyStore,
            generate = { error("Existing custom-store wallets should not generate a new key") },
        )

        val persistence = MobileWalletPersistence(
            databaseKey = MobileWalletDatabaseKey.Provided(databaseKeyProvider),
            stores = MobileWalletStores(
                credentials = credentialStore,
                dids = didStore,
                keys = keys,
            ),
        )

        assertSame(databaseKeyProvider, assertIs<MobileWalletDatabaseKey.Provided>(persistence.databaseKey).provider)
        assertSame(credentialStore, persistence.stores.credentials)
        assertSame(didStore, persistence.stores.dids)
        assertSame(keyStore, persistence.stores.keys?.store)
        assertSame(keys.generate, persistence.stores.keys?.generate)
    }

    @Test
    fun walletCanUseInjectedStoresAndAtomicKeyConfiguration() = runTest {
        val keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1"))
        val didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap())))
        val credentialStore = RecordingCredentialStore()
        val keys = MobileWalletKeys(
            store = keyStore,
            generate = { error("Existing custom-store wallets should not generate a new key") },
        )
        val wallet = MobileWallet(
            walletId = "custom-wallet",
            keyStore = keys.store,
            didStore = didStore,
            credentialStore = credentialStore,
            keyGenerator = keys.generate,
        )

        val bootstrap = wallet.bootstrap()

        assertEquals("custom-key", bootstrap.keyId)
        assertEquals("did:key:custom", bootstrap.did)
        assertEquals(1, keyStore.listKeysCalls)
        assertEquals(1, didStore.listDidsCalls)
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
            keyGenerator = { error("deleteWallet should not generate a key") },
        )

        wallet.deleteWallet()

        assertEquals(listOf("custom-key"), keyStore.removedKeyIds)
        assertEquals(listOf("did:key:custom"), didStore.removedDids)
        assertEquals(emptyList(), credentialStore.removedCredentialIds)
    }

    @Test
    fun mobileWalletKeyTypeMapsToCryptoKeyTypeInternally() {
        assertEquals(KeyType.Ed25519, MobileWalletKeyType.Ed25519.toKeyType())
        assertEquals(KeyType.secp256k1, MobileWalletKeyType.secp256k1.toKeyType())
        assertEquals(KeyType.secp256r1, MobileWalletKeyType.secp256r1.toKeyType())
        assertEquals(KeyType.secp384r1, MobileWalletKeyType.secp384r1.toKeyType())
        assertEquals(KeyType.secp521r1, MobileWalletKeyType.secp521r1.toKeyType())
        assertEquals(KeyType.RSA, MobileWalletKeyType.RSA.toKeyType())
        assertEquals(KeyType.RSA3072, MobileWalletKeyType.RSA3072.toKeyType())
        assertEquals(KeyType.RSA4096, MobileWalletKeyType.RSA4096.toKeyType())
    }

    @Test
    fun walletSessionEventsMapToMobileWalletEventsInCommonCode() {
        val progress = WalletSessionEvent.issuance_offer_resolved.toMobileWalletEvent()
        val completed = WalletSessionEvent.presentation_completed.toMobileWalletEvent()
        val failed = WalletSessionEvent.issuance_failed.toMobileWalletEvent()

        assertEquals(MobileWalletEventPhase.issuance, progress.phase)
        assertEquals(MobileWalletEventStatus.progress, progress.status)
        assertEquals("issuance_offer_resolved", progress.name)

        assertEquals(MobileWalletEventPhase.presentation, completed.phase)
        assertEquals(MobileWalletEventStatus.completed, completed.status)
        assertEquals("presentation_completed", completed.name)

        assertEquals(MobileWalletEventPhase.issuance, failed.phase)
        assertEquals(MobileWalletEventStatus.failed, failed.status)
        assertEquals("issuance_failed", failed.name)
    }

    @Test
    fun presentationResultCarriesVerifierResponseAsJsonString() {
        val result = MobileWalletPresentationResult(
            success = true,
            redirectTo = "wallet://return",
            verifierResponseJson = """{"accepted":true}""",
        )

        assertEquals("""{"accepted":true}""", result.verifierResponseJson)
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
            keyGenerator = { error("Injected credential listing should not generate a key") },
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
            keyGenerator = { error("Injected credential listing should not generate a key") },
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
            request = MobileWalletPresentationRequestInfo(
                clientId = "https://verifier.example",
                verifierName = "Example Verifier",
                responseUri = "https://verifier.example/direct-post",
                state = "state-1",
                nonce = "nonce-1",
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
                delay(Long.MAX_VALUE)
            }
        }

        runCurrent()

        repeat(100) { index ->
            val emitted = stream.tryEmit(progressEvent("issuance_progress_$index"))

            assertTrue(emitted, "Progress event $index should not suspend or fail when the buffer is full")
        }

        collector.cancel()
    }

    private fun progressEvent(name: String) = MobileWalletEvent(
        name = name,
        phase = MobileWalletEventPhase.issuance,
        status = MobileWalletEventStatus.progress,
    )

    private val displayJson = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private class RecordingDatabaseKeyProvider : DatabaseEncryptionKeyProvider {
        override suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey =
            DatabaseEncryptionKey("$walletId:$databaseName", ByteArray(32))

        override suspend fun deleteKey(walletId: String, databaseName: String) = Unit
    }

    private class PreloadedKeyStore(private val keyInfo: WalletKeyInfo) : WalletKeyStore {
        var listKeysCalls = 0
        val removedKeyIds = mutableListOf<String>()

        override suspend fun getKey(keyId: String) = null

        override suspend fun listKeys(): Flow<WalletKeyInfo> {
            listKeysCalls++
            return listOf(keyInfo).asFlow()
        }

        override suspend fun addKey(key: id.walt.crypto.keys.Key): String =
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
