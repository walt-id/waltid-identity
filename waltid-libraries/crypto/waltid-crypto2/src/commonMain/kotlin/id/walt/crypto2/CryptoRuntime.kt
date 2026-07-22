package id.walt.crypto2

import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.keys.StorableKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.CryptoOperation
import id.walt.crypto2.providers.CryptoRequirement
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.ManagedKeyProvider
import id.walt.crypto2.providers.ProviderSelection
import id.walt.crypto2.providers.SoftwareKeyProvider
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

class CryptoRuntime(
    softwareProviders: List<SoftwareKeyProvider>,
    managedProviders: List<ManagedKeyProvider> = emptyList(),
) {
    private val softwareProviders = softwareProviders.toList().also {
        requireDistinctIds(it.map(SoftwareKeyProvider::id))
    }
    private val softwareProvidersById = this.softwareProviders.associateBy(SoftwareKeyProvider::id)
    private val managedProvidersById = managedProviders.toList().also {
        requireDistinctIds(it.map(ManagedKeyProvider::id))
    }.associateBy(ManagedKeyProvider::id)
    private val lifecycleMutex = Mutex()
    private var closed = false

    init {
        require(this.softwareProviders.isNotEmpty() || managedProvidersById.isNotEmpty()) {
            "At least one key provider is required"
        }
    }

    suspend fun generateSoftwareKey(
        request: GenerateSoftwareKeyRequest,
        selection: ProviderSelection = ProviderSelection.Automatic,
    ): SoftwareKey = withOpenRuntime {
        val generated = resolveSoftwareProvider(
            requirement = CryptoRequirement(
                operation = CryptoOperation.GENERATE_KEY,
                spec = request.spec,
                usages = request.usages,
                keyEncoding = request.keyEncoding,
            ),
            selection = selection,
        ).generate(request)
        require(generated.storedKey.id == request.id) { "Generated software-key ID changed" }
        require(generated.storedKey.spec == request.spec) { "Generated software-key specification changed" }
        require(generated.storedKey.usages == request.usages) { "Generated software-key usages changed" }
        generated
    }

    suspend fun restore(
        stored: StoredKey,
        selection: ProviderSelection = ProviderSelection.Automatic,
    ): Key = withOpenRuntime { restoreStored(stored, selection) }

    suspend fun restore(
        softwareKey: SoftwareKey,
        selection: ProviderSelection = ProviderSelection.Automatic,
    ): SoftwareKey = withOpenRuntime { restoreSoftwareKey(softwareKey.storedKey, selection) }

    suspend fun restore(
        managedKey: ManagedKey,
        selection: ProviderSelection = ProviderSelection.Automatic,
    ): ManagedKey = withOpenRuntime { restoreManagedKey(managedKey.storedKey, selection) }

    /** Materializes a deserialized, non-operational key handle with this runtime's explicit providers. */
    suspend fun restore(
        storableKey: StorableKey,
        selection: ProviderSelection = ProviderSelection.Automatic,
    ): Key = withOpenRuntime { restoreStored(storableKey.storedKey, selection) }

    suspend fun generateManagedKey(provider: ProviderId, request: GenerateManagedKeyRequest): ManagedKey = withOpenRuntime {
        val generated = requireNotNull(managedProvidersById[provider]) {
            "Managed key provider is not registered: ${provider.value}"
        }.generate(request)
        val stored = generated.storedKey
        require(stored.provider == provider) { "Generated managed-key provider changed" }
        require(stored.id == request.id) { "Generated managed-key ID changed" }
        require(stored.spec == request.spec) { "Generated managed-key specification changed" }
        require(stored.usages == request.usages) { "Generated managed-key usages changed" }
        generated
    }

    suspend fun close() = withContext(NonCancellable) {
        lifecycleMutex.lock()
        try {
            if (closed) return@withContext
            closed = true
            var failure: Throwable? = null
            (softwareProviders + managedProvidersById.values).forEach { provider ->
                try {
                    when (provider) {
                        is SoftwareKeyProvider -> provider.close()
                        is ManagedKeyProvider -> provider.close()
                    }
                } catch (cause: Throwable) {
                    failure?.addSuppressed(cause) ?: run { failure = cause }
                }
            }
            failure?.let { throw it }
        } finally {
            lifecycleMutex.unlock()
        }
    }

    fun resolveSoftwareProvider(
        requirement: CryptoRequirement,
        selection: ProviderSelection = ProviderSelection.Automatic,
    ): SoftwareKeyProvider {
        val candidates = when (selection) {
            ProviderSelection.Automatic -> softwareProviders
            is ProviderSelection.Only -> listOf(requireSoftwareProvider(selection.provider))
            is ProviderSelection.FirstAvailable -> selection.providers.mapNotNull(softwareProvidersById::get)
        }
        return candidates.firstOrNull { it.supports(requirement) }
            ?: error("No software provider supports $requirement")
    }

    private fun requireSoftwareProvider(id: ProviderId): SoftwareKeyProvider =
        requireNotNull(softwareProvidersById[id]) { "Software key provider is not registered: ${id.value}" }

    private fun requireDistinctIds(ids: List<ProviderId>) {
        require(ids.distinct().size == ids.size) { "Provider IDs must be unique" }
    }

    private fun requireManagedSelection(provider: ProviderId, selection: ProviderSelection) {
        val permitted = when (selection) {
            ProviderSelection.Automatic -> true
            is ProviderSelection.Only -> selection.provider == provider
            is ProviderSelection.FirstAvailable -> provider in selection.providers
        }
        require(permitted) { "Managed key is bound to provider ${provider.value}" }
    }

    private suspend fun restoreStored(stored: StoredKey, selection: ProviderSelection): Key = when (stored) {
        is StoredKey.Software -> restoreSoftwareKey(stored, selection)
        is StoredKey.Managed -> restoreManagedKey(stored, selection)
    }

    private suspend fun restoreSoftwareKey(
        stored: StoredKey.Software,
        selection: ProviderSelection,
    ): SoftwareKey {
        val restored = resolveSoftwareProvider(
            requirement = CryptoRequirement(
                operation = CryptoOperation.IMPORT_KEY,
                spec = stored.spec,
                usages = stored.usages,
                keyEncoding = stored.material.encodingFormat,
            ),
            selection = selection,
        ).restore(stored)
        require(restored.storedKey.id == stored.id) { "Restored software-key ID changed" }
        require(restored.storedKey.spec == stored.spec) { "Restored software-key specification changed" }
        require(restored.storedKey.usages == stored.usages) { "Restored software-key usages changed" }
        return restored
    }

    private suspend fun restoreManagedKey(
        stored: StoredKey.Managed,
        selection: ProviderSelection,
    ): ManagedKey {
        requireManagedSelection(stored.provider, selection)
        val restored = requireNotNull(managedProvidersById[stored.provider]) {
            "Managed key provider is not registered: ${stored.provider.value}"
        }.restore(stored)
        validateManagedIdentity(restored, stored)
        return restored
    }

    private fun validateManagedIdentity(actual: ManagedKey, expected: StoredKey.Managed) {
        val stored = actual.storedKey
        require(stored.version == expected.version) { "Restored managed-key version changed" }
        require(stored.provider == expected.provider) { "Restored managed-key provider changed" }
        require(stored.id == expected.id) { "Restored managed-key ID changed" }
        require(stored.spec == expected.spec) { "Restored managed-key specification changed" }
        require(stored.usages == expected.usages) { "Restored managed-key usages changed" }
        require(stored.providerSchemaVersion == expected.providerSchemaVersion) {
            "Restored managed-key provider schema changed"
        }
        require(stored.providerData == expected.providerData) { "Restored managed-key provider data changed" }
        require(stored.publicKey == expected.publicKey) { "Restored managed-key public key changed" }
        require(stored.metadata == expected.metadata) { "Restored managed-key metadata changed" }
        validateManagedCapabilities(actual.capabilities, expected.usages)
    }

    private fun validateManagedCapabilities(capabilities: KeyCapabilities, usages: Set<KeyUsage>) {
        val overgranted = buildList {
            if (capabilities.signer != null && KeyUsage.SIGN !in usages) add("signer")
            if (capabilities.digestSigner != null && KeyUsage.SIGN !in usages) add("digestSigner")
            if (capabilities.verifier != null && KeyUsage.VERIFY !in usages) add("verifier")
            if (capabilities.encryptor != null && KeyUsage.ENCRYPT !in usages) add("encryptor")
            if (capabilities.decryptor != null && KeyUsage.DECRYPT !in usages) add("decryptor")
            if (capabilities.keyAgreement != null && KeyUsage.KEY_AGREEMENT !in usages) add("keyAgreement")
            if (capabilities.keyWrapper != null && KeyUsage.WRAP !in usages) add("keyWrapper")
            if (capabilities.keyUnwrapper != null && KeyUsage.UNWRAP !in usages) add("keyUnwrapper")
            if (capabilities.privateKeyExporter != null) add("privateKeyExporter")
            if (capabilities.signatureAlgorithms.isNotEmpty() && usages.none {
                    it == KeyUsage.SIGN || it == KeyUsage.VERIFY
                }) add("signatureAlgorithms")
            if (capabilities.encryptionAlgorithms.isNotEmpty() && usages.none {
                    it == KeyUsage.ENCRYPT || it == KeyUsage.DECRYPT
                }) add("encryptionAlgorithms")
            if (capabilities.keyAgreementAlgorithms.isNotEmpty() && KeyUsage.KEY_AGREEMENT !in usages) {
                add("keyAgreementAlgorithms")
            }
            if (capabilities.keyWrappingAlgorithms.isNotEmpty() && usages.none {
                    it == KeyUsage.WRAP || it == KeyUsage.UNWRAP
                }) add("keyWrappingAlgorithms")
        }
        require(overgranted.isEmpty()) {
            "Restored managed-key capabilities exceed persisted usages: ${overgranted.joinToString()}"
        }
    }

    private suspend fun <T> withOpenRuntime(block: suspend () -> T): T {
        lifecycleMutex.lock()
        return try {
            check(!closed) { "CryptoRuntime is closed" }
            block()
        } finally {
            lifecycleMutex.unlock()
        }
    }

}
