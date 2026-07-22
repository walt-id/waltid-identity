package id.walt.crypto2

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.StorableKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.CryptoRequirement
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.ManagedKeyProvider
import id.walt.crypto2.providers.ProviderSelection
import id.walt.crypto2.providers.SoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class CryptoRuntimeTest {
    private val spec = KeySpec.Ec(EcCurve.P256)

    @Test
    fun `automatic selection uses the first compatible provider`() = runTest {
        val first = FakeSoftwareProvider("first", supports = false)
        val second = FakeSoftwareProvider("second", supports = true)
        val runtime = CryptoRuntime(listOf(first, second))

        runtime.generateSoftwareKey(request())

        assertEquals(0, first.generateCalls)
        assertEquals(1, second.generateCalls)
    }

    @Test
    fun `explicit selection never falls back`() = runTest {
        val unsupported = FakeSoftwareProvider("unsupported", supports = false)
        val fallback = FakeSoftwareProvider("fallback", supports = true)
        val runtime = CryptoRuntime(listOf(unsupported, fallback))

        assertFailsWith<IllegalStateException> {
            runtime.generateSoftwareKey(request(), ProviderSelection.Only(unsupported.id))
        }
        assertEquals(0, fallback.generateCalls)
    }

    @Test
    fun `ordered fallback is explicit`() = runTest {
        val unsupported = FakeSoftwareProvider("unsupported", supports = false)
        val selected = FakeSoftwareProvider("selected", supports = true)
        val runtime = CryptoRuntime(listOf(selected, unsupported))

        runtime.generateSoftwareKey(
            request(),
            ProviderSelection.FirstAvailable(listOf(unsupported.id, selected.id)),
        )

        assertEquals(1, selected.generateCalls)
    }

    @Test
    fun `ordered fallback skips unavailable providers`() = runTest {
        val selected = FakeSoftwareProvider("selected", supports = true)
        val runtime = CryptoRuntime(listOf(selected))

        runtime.generateSoftwareKey(
            request(),
            ProviderSelection.FirstAvailable(listOf(ProviderId("not-installed"), selected.id)),
        )

        assertEquals(1, selected.generateCalls)
    }

    @Test
    fun `execution failure does not fall back`() = runTest {
        val failing = FakeSoftwareProvider("failing", supports = true, generationFailure = true)
        val fallback = FakeSoftwareProvider("fallback", supports = true)
        val runtime = CryptoRuntime(listOf(failing, fallback))

        assertFailsWith<IllegalStateException> {
            runtime.generateSoftwareKey(request())
        }
        assertEquals(0, fallback.generateCalls)
    }

    @Test
    fun `managed keys always route to their bound provider`() = runTest {
        val provider = FakeManagedProvider(ProviderId("pkcs11"))
        val runtime = CryptoRuntime(
            softwareProviders = listOf(FakeSoftwareProvider("software", supports = true)),
            managedProviders = listOf(provider),
        )
        val stored = StoredKey.Managed(
            version = StoredKey.CURRENT_VERSION,
            id = KeyId("managed"),
            spec = KeySpec.Rsa(2048),
            usages = setOf(KeyUsage.SIGN),
            provider = provider.id,
            providerSchemaVersion = 1,
            providerData = BinaryData(byteArrayOf(1)),
        )

        val restored = assertIs<ManagedKey>(runtime.restore(stored))

        assertSame(stored, restored.storedKey)

        assertFailsWith<IllegalArgumentException> {
            runtime.restore(stored, ProviderSelection.Only(ProviderId("another-provider")))
        }
    }

    @Test
    fun `duplicate provider IDs are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CryptoRuntime(
                listOf(
                    FakeSoftwareProvider("duplicate", supports = true),
                    FakeSoftwareProvider("duplicate", supports = true),
                ),
            )
        }
    }

    @Test
    fun `managed-only runtime is supported`() = runTest {
        val provider = FakeManagedProvider(ProviderId("managed-only"))
        val runtime = CryptoRuntime(emptyList(), listOf(provider))
        val stored = StoredKey.Managed(
            version = StoredKey.CURRENT_VERSION,
            id = KeyId("managed"),
            spec = KeySpec.Rsa(2048),
            usages = setOf(KeyUsage.DECRYPT),
            provider = provider.id,
            providerSchemaVersion = 1,
            providerData = BinaryData(byteArrayOf(1)),
        )

        assertSame(stored, assertIs<ManagedKey>(runtime.restore(stored)).storedKey)
    }

    @Test
    fun `core rejects changed managed-key identity`() = runTest {
        val provider = FakeManagedProvider(
            id = ProviderId("changed"),
            restoreTransform = { it.copy(id = KeyId("different")) },
        )
        val runtime = CryptoRuntime(emptyList(), listOf(provider))
        val stored = StoredKey.Managed(
            version = StoredKey.CURRENT_VERSION,
            id = KeyId("original"),
            spec = KeySpec.Rsa(2048),
            usages = setOf(KeyUsage.DECRYPT),
            provider = provider.id,
            providerSchemaVersion = 1,
            providerData = BinaryData(byteArrayOf(1)),
        )

        assertFailsWith<IllegalArgumentException> { runtime.restore(stored) }

        val retargetingProvider = FakeManagedProvider(
            id = ProviderId("retargeting"),
            restoreTransform = { it.copy(providerData = BinaryData(byteArrayOf(2))) },
        )
        val retargetingRuntime = CryptoRuntime(emptyList(), listOf(retargetingProvider))
        assertFailsWith<IllegalArgumentException> {
            retargetingRuntime.restore(stored.copy(provider = retargetingProvider.id))
        }
    }

    @Test
    fun `core rejects changed managed public key and metadata`() = runTest {
        val publicKey = EncodedKey.SpkiDer(BinaryData(byteArrayOf(1)))
        val expectedMetadata = mapOf("tenant" to "one")
        val stored = managedStored(
            provider = ProviderId("public-key-change"),
            publicKey = publicKey,
            metadata = expectedMetadata,
        )
        val publicKeyProvider = FakeManagedProvider(
            id = stored.provider,
            restoreTransform = {
                it.copy(publicKey = EncodedKey.SpkiDer(BinaryData(byteArrayOf(2))))
            },
        )
        val metadataProvider = FakeManagedProvider(
            id = ProviderId("metadata-change"),
            restoreTransform = { it.copy(metadata = mapOf("tenant" to "two")) },
        )

        val publicKeyFailure = assertFailsWith<IllegalArgumentException> {
            CryptoRuntime(emptyList(), listOf(publicKeyProvider)).restore(stored)
        }
        assertContains(publicKeyFailure.message.orEmpty(), "public key changed")

        val metadataFailure = assertFailsWith<IllegalArgumentException> {
            CryptoRuntime(emptyList(), listOf(metadataProvider)).restore(
                stored.copy(provider = metadataProvider.id),
            )
        }
        assertContains(metadataFailure.message.orEmpty(), "metadata changed")
    }

    @Test
    fun `core rejects managed capabilities beyond persisted usages`() = runTest {
        val provider = FakeManagedProvider(
            id = ProviderId("overgrant"),
            capabilities = KeyCapabilities(signer = Signer { _, _ -> byteArrayOf(1) }),
        )
        val stored = managedStored(provider.id, usages = setOf(KeyUsage.VERIFY))

        val failure = assertFailsWith<IllegalArgumentException> {
            CryptoRuntime(emptyList(), listOf(provider)).restore(stored)
        }

        assertContains(failure.message.orEmpty(), "capabilities exceed persisted usages: signer")
    }

    @Test
    fun `typed handles restore to their known key types`() = runTest {
        val softwareProvider = FakeSoftwareProvider("software", supports = true)
        val managedProvider = FakeManagedProvider(ProviderId("managed"))
        val runtime = CryptoRuntime(listOf(softwareProvider), listOf(managedProvider))
        val softwareStored = StoredKey.Software(
            version = StoredKey.CURRENT_VERSION,
            id = KeyId("software"),
            spec = spec,
            usages = setOf(KeyUsage.SIGN),
            material = EncodedKey.Jwk(BinaryData("{}".encodeToByteArray()), true),
        )
        val managedStored = managedStored(managedProvider.id)

        val software: SoftwareKey = runtime.restore(FakeSoftwareKey(softwareStored))
        val managed: ManagedKey = runtime.restore(FakeManagedKey(managedStored))
        val storable: StorableKey = FakeManagedKey(managedStored)
        val generic: Key = runtime.restore(storable)

        assertSame(softwareStored, software.storedKey)
        assertSame(managedStored, managed.storedKey)
        assertIs<ManagedKey>(generic)
    }

    @Test
    fun `close waits for restoration and later runtime operations fail clearly`() = runTest {
        val restoreStarted = CompletableDeferred<Unit>()
        val continueRestore = CompletableDeferred<Unit>()
        val provider = FakeManagedProvider(
            id = ProviderId("lifecycle"),
            beforeRestore = {
                restoreStarted.complete(Unit)
                continueRestore.await()
            },
        )
        val runtime = CryptoRuntime(emptyList(), listOf(provider))
        val stored = managedStored(provider.id)
        val restoring = async { runtime.restore(stored) }
        restoreStarted.await()

        val closing = async { runtime.close() }
        testScheduler.runCurrent()
        assertFalse(closing.isCompleted)
        assertEquals(0, provider.closeCalls)

        continueRestore.complete(Unit)
        restoring.await()
        closing.await()
        assertEquals(1, provider.closeCalls)

        val restoreFailure = assertFailsWith<IllegalStateException> { runtime.restore(stored) }
        assertContains(restoreFailure.message.orEmpty(), "CryptoRuntime is closed")
        val generateFailure = assertFailsWith<IllegalStateException> {
            runtime.generateManagedKey(provider.id, managedRequest())
        }
        assertContains(generateFailure.message.orEmpty(), "CryptoRuntime is closed")
        runtime.close()
        assertEquals(1, provider.closeCalls)
    }

    @Test
    fun `runtime closes every provider after a close failure`() = runTest {
        val failing = FakeManagedProvider(ProviderId("failing"), failClose = true)
        val succeeding = FakeManagedProvider(ProviderId("succeeding"))
        val runtime = CryptoRuntime(emptyList(), listOf(failing, succeeding))

        assertFailsWith<IllegalStateException> { runtime.close() }
        assertEquals(1, failing.closeCalls)
        assertEquals(1, succeeding.closeCalls)
    }

    private fun request() = GenerateSoftwareKeyRequest(
        id = KeyId("test"),
        spec = spec,
        usages = setOf(KeyUsage.SIGN),
    )

    private fun managedRequest() = GenerateManagedKeyRequest(
        id = KeyId("managed"),
        spec = KeySpec.Rsa(2048),
        usages = setOf(KeyUsage.SIGN),
        providerOptions = BinaryData(byteArrayOf(1)),
    )

    private fun managedStored(
        provider: ProviderId,
        usages: Set<KeyUsage> = setOf(KeyUsage.SIGN),
        publicKey: EncodedKey? = null,
        metadata: Map<String, String> = emptyMap(),
    ) = StoredKey.Managed(
        version = StoredKey.CURRENT_VERSION,
        id = KeyId("managed"),
        spec = KeySpec.Rsa(2048),
        usages = usages,
        provider = provider,
        providerSchemaVersion = 1,
        providerData = BinaryData(byteArrayOf(1)),
        publicKey = publicKey,
        metadata = metadata,
    )

    private class FakeSoftwareProvider(
        id: String,
        private val supports: Boolean,
        private val generationFailure: Boolean = false,
    ) : SoftwareKeyProvider {
        override val id = ProviderId(id)
        var generateCalls = 0

        override fun supports(requirement: CryptoRequirement): Boolean = supports

        override suspend fun generate(request: GenerateSoftwareKeyRequest): SoftwareKey {
            generateCalls++
            check(!generationFailure) { "Provider failed" }
            return FakeSoftwareKey(
                StoredKey.Software(
                    version = StoredKey.CURRENT_VERSION,
                    id = request.id,
                    spec = request.spec,
                    usages = request.usages,
                    material = EncodedKey.Jwk(BinaryData("{}".encodeToByteArray()), true),
                ),
            )
        }

        override suspend fun restore(stored: StoredKey.Software): SoftwareKey = FakeSoftwareKey(stored)
    }

    private class FakeSoftwareKey(override val storedKey: StoredKey.Software) : SoftwareKey

    private class FakeManagedProvider(
        override val id: ProviderId,
        private val restoreTransform: (StoredKey.Managed) -> StoredKey.Managed = { it },
        private val failClose: Boolean = false,
        private val capabilities: KeyCapabilities = KeyCapabilities(),
        private val beforeRestore: suspend () -> Unit = {},
    ) : ManagedKeyProvider {
        var closeCalls = 0

        override suspend fun generate(request: GenerateManagedKeyRequest): ManagedKey = FakeManagedKey(
            StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = request.id,
                spec = request.spec,
                usages = request.usages,
                provider = id,
                providerSchemaVersion = 1,
                providerData = request.providerOptions,
                metadata = request.metadata,
            ),
        )

        override suspend fun restore(stored: StoredKey.Managed): ManagedKey {
            beforeRestore()
            return FakeManagedKey(restoreTransform(stored), capabilities)
        }

        override suspend fun close() {
            closeCalls++
            check(!failClose) { "Close failed" }
        }
    }

    private class FakeManagedKey(
        override val storedKey: StoredKey.Managed,
        override val capabilities: KeyCapabilities = KeyCapabilities(),
    ) : ManagedKey
}
