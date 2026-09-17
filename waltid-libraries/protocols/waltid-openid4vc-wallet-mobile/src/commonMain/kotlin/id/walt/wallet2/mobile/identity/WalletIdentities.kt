package id.walt.wallet2.mobile.identity

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.StorableKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.keys.toSpkiDer
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.keys.KeyOrigin
import id.walt.crypto2.keys.KeyProtectionLevel
import id.walt.crypto2.keys.KeySecurityLevel
import id.walt.did.dids.Crypto2DidService
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.mobile.MobileDidSupport
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationFailure
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationException
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPrompt
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationSupport
import id.walt.wallet2.persistence.keys.PlatformKeyFacts
import id.walt.wallet2.persistence.keys.PlatformManagedKeyProvider
import id.walt.wallet2.persistence.keys.WalletKeyCreationRequest
import id.walt.wallet2.persistence.keys.WalletKeyProtection
import id.walt.wallet2.persistence.keys.WalletKeyRequirements
import id.walt.wallet2.persistence.stores.SqlDelightKeyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/**
 * Owns one wallet's active signing identity. Creation and recovery use complete SDK-issued options.
 * Native/backup prerequisites are revalidated at execution. The encrypted database journals interrupted
 * operations; a failed backup submission does not activate the new identity.
 * Successful activation and initialization refresh the wallet's platform credential registration.
 *
 * Keep one writable wallet instance per database. Provider extensions may read established identities.
 */
public class WalletIdentities internal constructor(
    private val walletId: String,
    private val configuration: IdentityConfiguration,
    private val defaultAuthorization: KeyUseAuthorizationPolicy,
    private val prompt: KeyUseAuthorizationPrompt,
    private val keys: SqlDelightKeyStore,
    private val dids: WalletDidStore,
    private val native: PlatformManagedKeyProvider,
    private val queries: WalletPersistenceQueries,
    private val didService: Crypto2DidService,
    private val onActive: suspend () -> Unit,
) {
    private val owner = Any()
    private val mutex = Mutex()
    private val software = CryptoRuntime(defaultSoftwareKeyProviders())
    private val providers = configuration.recoveryProviders.associateBy { it.id }
    private val custodians = configuration.keyCustodians.associateBy { it.id }
    private val authorization: KeyUseAuthorizationPolicy get() = when (val value = configuration.authorization) {
        IdentityAuthorization.WalletDefault -> defaultAuthorization
        is IdentityAuthorization.Explicit -> value.policy
    }

    private val authorizations: List<KeyUseAuthorizationPolicy>
        get() = (listOf(authorization) + configuration.alternativeAuthorizations).distinct()

    /** Offers only complete choices compatible with the host policy and current native/provider prerequisites. */
    public suspend fun creationOptions(intent: IdentityIntent = IdentityIntent.WithoutRecovery,
        attestation: IdentityAttestationRequest = IdentityAttestationRequest.None): IdentityOptions = mutex.withLock {
        choices(intent, attestation)
    }

    /** Returns established identity metadata, including an explicit unavailable or interrupted state. */
    public suspend fun state(): WalletIdentityState = mutex.withLock { currentState() }

    /** Reopens the selected identity, or creates the recommended identity without recovery using configured defaults.
     * Pending and unavailable states never cause replacement key generation. Use creationOptions for an explicit choice.
     */
    public suspend fun initialize(): IdentityOperationResult = when (val state = state()) {
        is WalletIdentityState.Active -> IdentityOperationResult.Active(state.identity).notifyActive()
        is WalletIdentityState.Pending -> IdentityOperationResult.Pending(state.identityId, state.reason)
        is WalletIdentityState.Unavailable -> failed(state.reason)
        WalletIdentityState.Absent -> when (val options = creationOptions()) {
            is IdentityOptions.Available -> (listOf(options.recommended) + options.alternatives)
                .firstOrNull { it.authorization == authorization }?.let { create(it) }
                ?: failed(IdentityFailure.UnsupportedPolicy)
            is IdentityOptions.Unavailable -> failed(IdentityFailure.UnsupportedPolicy)
        }
    }

    /** Creates P-256 and did:jwk. No provider is selected and no backup is made by default. */
    public suspend fun create(option: IdentityCreationOption): IdentityOperationResult = mutex.withLock {
        if (option.owner !== owner || !valid(option)) return@withLock failed(IdentityFailure.StaleOption)
        if (currentState() !is WalletIdentityState.Absent) return@withLock failed(IdentityFailure.ExistingIdentity)
        val id = Uuid.random().toString()
        val keyId = "wallet_identity_${Uuid.random()}"
        val seed = if (option.recoverable) IdentityRecoveryMaterial.createSeed() else null
        try {
            val material = seed?.let { IdentityRecoveryMaterial.derive(it, id) }
            val preparing = IdentityRecord(
                id = id, keyId = keyId, nativeAlias = "$keyId.identity.${Uuid.random()}", phase = IdentityPhase.Preparing, storage = option.storage,
                requirements = requirements(option.storage, option.authorization, option.attestation), policy = configuration.policy,
                backup = option.providerId?.let { IdentityBackupReference(it, id) },
                recoveryAvailability = option.recoveryAvailability, recoveryConfirmation = configuration.recoveryConfirmation,
            )
            reserve(preparing)
            try {
                val key = createKey(preparing, material)
                MobileDidSupport.ensureInitialized()
                val registered = didService.registerByKey("jwk", key, DidJwkCreateOptions())
                val publicJwk = publicJwk(key)
                val recovery = seed?.let { RecoveryRecord(
                    identityId = id, keyId = keyId, did = registered.did, publicJwk = publicJwk,
                    secret = RecoverySecret.Derived(recoveryBase64.encode(it), id),
                    constraints = recoveryConstraints(preparing),
                ) }
                val identity = describe(preparing, registered.did, publicJwk, key)
                proveOriginalKey(key, publicJwk)
                val prepared = preparing.copy(identity = identity, recovery = recovery)
                write(prepared)
                dids.addDid(WalletDidEntry(registered.did, registered.didDocument.toJsonObject()))
                finish(prepared)
            } catch (cause: Throwable) {
                // Once backup submission can have happened, retain the journal for idempotent retry.
                if (read()?.phase == IdentityPhase.Preparing) cleanup(read() ?: preparing, material != null, cause)
                if (cause is CancellationException) throw cause
                failed(classify(cause))
            }
        } finally { seed?.fill(0) }
    }.notifyActive()

    /** Retries a persisted backup submission, or removes an interrupted pre-activation key operation. */
    public suspend fun resumePending(identityId: String): IdentityOperationResult = mutex.withLock {
        val record = read()?.takeIf { it.id == identityId } ?: return@withLock failed(IdentityFailure.StaleOption)
        when (record.phase) {
            IdentityPhase.Active -> when (val state = currentState()) {
                is WalletIdentityState.Active -> IdentityOperationResult.Active(state.identity)
                else -> failed(IdentityFailure.KeyUnavailable)
            }
            IdentityPhase.AwaitingBackup -> {
                if (!permitsRecovery(record)) return@withLock failed(IdentityFailure.UnsupportedPolicy)
                val key = liveKey(record) ?: return@withLock failed(IdentityFailure.KeyUnavailable)
                try { proveOriginalKey(key, requireNotNull(record.identity).publicJwk) }
                catch (cause: CancellationException) { throw cause }
                catch (cause: Exception) { return@withLock failed(classify(cause)) }
                finish(record)
            }
            IdentityPhase.Preparing -> {
                cleanup(record, record.backup != null, null)
                failed(IdentityFailure.NativeOperationFailed)
            }
        }
    }.notifyActive()

    /** Removes only pending local state. Any record already accepted by a provider remains discoverable
     * and can be deleted separately; cancellation never silently destroys a recovery copy.
     */
    public suspend fun cancelPending(identityId: String): Unit = mutex.withLock {
        val record = read() ?: return@withLock
        require(record.id == identityId && record.phase != IdentityPhase.Active) { "No matching pending identity" }
        cleanup(record, imported = record.backup != null, cause = null)
    }

    /** Offers backup for an existing retained recovery secret or an explicitly exportable software key. */
    public suspend fun backupOptions(identityId: String): List<IdentityBackupOption> = mutex.withLock {
        val record = read()?.takeIf { it.id == identityId && it.phase == IdentityPhase.Active } ?: return@withLock emptyList()
        if (!permitsRecovery(record) || liveKey(record) == null) return@withLock emptyList()
        val recoverable = record.recovery != null || record.backup?.providerId in providers ||
            keys.getCrypto2Key(record.keyId)?.capabilities?.privateKeyExporter != null
        if (!recoverable) return@withLock emptyList()
        availableProviders().map { (provider, availability) ->
            IdentityBackupOption(owner, identityId, provider.displayName, provider.id, availability)
        }
    }

    /** Submits the same identity to an explicitly selected provider; it never invents a seed for an existing key. */
    public suspend fun backup(option: IdentityBackupOption): IdentityOperationResult = mutex.withLock {
        if (option.owner !== owner) return@withLock failed(IdentityFailure.StaleOption)
        val record = read()?.takeIf { it.id == option.identityId && it.phase == IdentityPhase.Active }
            ?: return@withLock failed(IdentityFailure.StaleOption)
        if (record.policy != IdentityKeyPolicy.GeneralPurpose || configuration.policy != IdentityKeyPolicy.GeneralPurpose)
            return@withLock failed(IdentityFailure.UnsupportedPolicy)
        if (liveKey(record) == null) return@withLock failed(IdentityFailure.KeyUnavailable)
        val provider = providers[option.providerId] ?: return@withLock failed(IdentityFailure.ProviderUnavailable)
        if (provider.availability() != option.recoveryAvailability) return@withLock failed(IdentityFailure.StaleOption)
        val identity = requireNotNull(record.identity)
        val reference = IdentityBackupReference(provider.id, identity.id)
        try {
            val recovery = recoveryRecord(record) ?: return@withLock failed(IdentityFailure.UnsupportedPolicy)
            val receipt = submit(provider, reference.recordId, recovery, record)
            val updated = identity.copy(recovery = IdentityRecoveryState.Submitted(reference, receipt))
            write(record.copy(identity = updated, recovery = retained(recovery), backup = reference,
                recoveryAvailability = option.recoveryAvailability, recoveryConfirmation = requiredConfirmation(record)))
            IdentityOperationResult.Active(updated)
        } catch (cause: CancellationException) { throw cause }
        catch (cause: Exception) { failed(providerFailure(cause)) }
    }

    /** Credential bindings inherit the identity's retained restriction. A recoverable identity cannot
     * become device-bound merely because a later issuance request asks for that property. */
    internal suspend fun requireKeyPolicy(keyId: String, policy: IdentityKeyPolicy): Unit = mutex.withLock {
        if (policy == IdentityKeyPolicy.GeneralPurpose) return@withLock
        val record = read()
        require(record != null && record.keyId == keyId && record.phase == IdentityPhase.Active && liveKey(record) != null) {
            "The selected holder key is not an active managed identity"
        }
        require(record.policy == policy || record.policy == IdentityKeyPolicy.HardwareGenerated) {
            "The selected identity does not satisfy the required holder-key policy"
        }
        require(record.backup == null && record.recovery == null && record.identity?.custody.orEmpty().isEmpty()) {
            "The required holder-key policy prohibits external private-key copies"
        }
    }

    private suspend fun recoveryRecord(record: IdentityRecord): RecoveryRecord? {
        val identity = requireNotNull(record.identity)
        val source = record.backup?.let { providers[it.providerId] }
        val existing = if (record.recovery == null) source?.retrieve(record.backup.recordId)?.copyBytes() else null
        return record.recovery ?: existing?.let { bytes ->
            try {
                decodeRecovery(bytes, identity.id).also {
                    require(it.keyId == identity.keyId && it.did == identity.did && it.publicJwk == identity.publicJwk) {
                        "Recovery record belongs to another identity"
                    }
                }
            } finally { bytes.fill(0) }
        } ?: run {
            val key = keys.getCrypto2Key(identity.keyId) ?: return null
            val exported = key.capabilities.privateKeyExporter?.exportPrivateKey() as? EncodedKey.Jwk
                ?: return null
            RecoveryRecord(identityId = identity.id, keyId = identity.keyId, did = identity.did,
                publicJwk = identity.publicJwk, secret = RecoverySecret.Exported(exported.data.toByteArray().decodeToString()),
                constraints = recoveryConstraints(record))
                .also {
                    val bytes = it.encode()
                    try { decodeRecovery(bytes, identity.id) } finally { bytes.fill(0) }
                }
        }
    }

    /** Offers registered custody destinations only when the current policy permits private-key export. */
    public suspend fun custodyOptions(identityId: String): List<IdentityCustodyOption> = mutex.withLock {
        val record = read()?.takeIf { it.id == identityId && it.phase == IdentityPhase.Active }
            ?: return@withLock emptyList()
        if (!permitsRecovery(record) || liveKey(record) == null) return@withLock emptyList()
        if (record.recovery == null && record.backup?.providerId !in providers &&
            keys.getCrypto2Key(record.keyId)?.capabilities?.privateKeyExporter == null) return@withLock emptyList()
        custodians.values.map { IdentityCustodyOption(owner, record.id, it.displayName, it.id) }
    }

    /** Gives an explicit custodian an additional copy of the key. The local key is retained;
     * this does not create a recovery record or enable remote signing. */
    public suspend fun transferToCustody(option: IdentityCustodyOption): IdentityCustodyResult = mutex.withLock {
        val record = read()?.takeIf { it.id == option.identityId && it.phase == IdentityPhase.Active }
        if (option.owner !== owner || record == null) return@withLock IdentityCustodyResult.Failed(IdentityFailure.StaleOption)
        if (!permitsRecovery(record)) return@withLock IdentityCustodyResult.Failed(IdentityFailure.UnsupportedPolicy)
        val key = liveKey(record) ?: return@withLock IdentityCustodyResult.Failed(IdentityFailure.KeyUnavailable)
        val custodian = custodians[option.custodianId]
            ?: return@withLock IdentityCustodyResult.Failed(IdentityFailure.StaleOption)
        try {
            val recovery = recoveryRecord(record) ?: return@withLock IdentityCustodyResult.Failed(IdentityFailure.UnsupportedPolicy)
            val receipt = custodian.importKey(requireNotNull(record.identity), recovery.privateKey())
            requirePublicJwk(receipt.publicJwk)
            val observed = EncodedKey.Jwk(BinaryData(receipt.publicJwk.encodeToByteArray()), false).toSpkiDer(identitySpec)
            if (observed != key.capabilities.publicKeyExporter?.exportPublicKey()?.toSpkiDer(key.spec))
                return@withLock IdentityCustodyResult.Failed(IdentityFailure.ProviderConflict)
            val reference = IdentityCustodyReference(custodian.id, receipt.keyReference)
            write(record.copy(identity = record.identity.copy(custody = (record.identity.custody + reference).distinct())))
            IdentityCustodyResult.Imported(reference)
        } catch (cause: CancellationException) { throw cause }
        catch (cause: Exception) { IdentityCustodyResult.Failed(providerFailure(cause)) }
    }

    /** Lists safe references only. A reference is not proof that its record is valid or restorable. */
    public suspend fun recoveryCandidates(): List<RecoveryCandidate> = mutex.withLock {
        availableProviders().flatMap { (provider, _) ->
            try {
                provider.list().distinct().filter { it.length in 1..256 }.map {
                    RecoveryCandidate(owner, IdentityBackupReference(provider.id, it), provider.displayName)
                }
            } catch (cause: CancellationException) { throw cause }
            catch (_: Exception) { emptyList() }
        }
    }

    /** Deletes a selected backup through its owning provider. Other device copies may remain outside its control. */
    public suspend fun deleteRecovery(candidate: RecoveryCandidate): RecoveryReceipt = mutex.withLock {
        require(candidate.owner === owner) { "Recovery reference belongs to another wallet instance" }
        val provider = requireNotNull(providers[candidate.reference.providerId]) { "Recovery provider is no longer registered" }
        val receipt = provider.delete(candidate.reference.recordId)
        val record = read()
        if (record?.backup == candidate.reference && record.phase == IdentityPhase.Active) {
            write(record.copy(identity = requireNotNull(record.identity).copy(
                recovery = IdentityRecoveryState.RemovalRequested(candidate.reference, receipt))))
        }
        receipt
    }

    /** Validates the record, private/public key and exact DID before offering supported destinations. */
    public suspend fun restorationOptions(candidate: RecoveryCandidate): List<IdentityRestorationOption> = mutex.withLock {
        if (candidate.owner !== owner || configuration.policy != IdentityKeyPolicy.GeneralPurpose) return@withLock emptyList()
        val provider = providers[candidate.reference.providerId] ?: return@withLock emptyList()
        if (provider.availability() !is RecoveryAvailability.Available) return@withLock emptyList()
        val bytes = provider.retrieve(candidate.reference.recordId)?.copyBytes() ?: return@withLock emptyList()
        try {
            val record = decodeRecovery(bytes, candidate.reference.recordId)
            authorizations.flatMap { authorization -> supportedStorage(importing = true, authorization)
                .filter { record.constraints.permits(it, authorization) }.map { storage ->
                IdentityRestorationOption(owner, record.did, storage, authorization, candidate.reference, fingerprint(bytes))
            } }
        } catch (cause: CancellationException) { throw cause }
        catch (_: Exception) { emptyList() }
        finally { bytes.fill(0) }
    }

    /** Restores into an empty wallet, or repairs the same identity after its native key is missing/invalidated. */
    public suspend fun restore(option: IdentityRestorationOption): IdentityOperationResult = mutex.withLock {
        if (option.owner !== owner || configuration.policy != IdentityKeyPolicy.GeneralPurpose ||
            option.authorization !in authorizations || option.storage !in supportedStorage(importing = true, option.authorization)) return@withLock failed(IdentityFailure.StaleOption)
        val provider = providers[option.reference.providerId] ?: return@withLock failed(IdentityFailure.ProviderUnavailable)
        val bytes = try {
            if (provider.availability() !is RecoveryAvailability.Available) return@withLock failed(IdentityFailure.ProviderUnavailable)
            provider.retrieve(option.reference.recordId)?.copyBytes()
                ?: return@withLock failed(IdentityFailure.ProviderUnavailable)
        } catch (cause: CancellationException) { throw cause }
        catch (cause: Exception) { return@withLock failed(providerFailure(cause)) }
        try {
            if (!fingerprint(bytes).contentEquals(option.fingerprint)) return@withLock failed(IdentityFailure.StaleOption)
            val recovery = decodeRecovery(bytes, option.reference.recordId)
            if (!recovery.constraints.permits(option.storage, option.authorization)) return@withLock failed(IdentityFailure.UnsupportedPolicy)
            val previous = read()
            val previousKey = previous?.let { repairableKey(it, recovery) }
            if (previous != null && previousKey == null) return@withLock failed(IdentityFailure.ExistingIdentity)
            if (previous == null && currentState() !is WalletIdentityState.Absent) return@withLock failed(IdentityFailure.ExistingIdentity)
            val preparing = IdentityRecord(id = recovery.identityId, keyId = recovery.keyId,
                nativeAlias = "${recovery.keyId}.identity.${Uuid.random()}",
                phase = IdentityPhase.Preparing, storage = option.storage, requirements = requirements(option.storage, option.authorization),
                policy = configuration.policy, recovery = recovery, backup = option.reference,
                recoveryConfirmation = recovery.constraints.confirmation,
                previous = previous, previousKey = previousKey)
            reserve(preparing)
            try {
                if (previousKey != null) keys.removeKey(previousKey.id.value)
                val key = createKey(preparing, recovery.privateKey())
                proveOriginalKey(key, recovery.publicJwk)
                val identity = describe(preparing, recovery.did, recovery.publicJwk, key).copy(
                    recovery = IdentityRecoveryState.Recovered(option.reference))
                val prepared = preparing.copy(identity = identity)
                write(prepared)
                dids.addDid(WalletDidEntry(recovery.did, didService.resolve(recovery.did).getOrThrow()))
                activate(prepared)
            } catch (cause: Throwable) {
                cleanup(read() ?: preparing, imported = true, cause)
                if (cause is CancellationException) throw cause
                failed(classify(cause))
            }
        } catch (cause: CancellationException) { throw cause }
        catch (_: Exception) { failed(IdentityFailure.InvalidRecoveryRecord) }
        finally { bytes.fill(0) }
    }.notifyActive()

    // Publish only committed identities, after releasing the lifecycle lock so host callbacks may read state.
    private suspend fun IdentityOperationResult.notifyActive(): IdentityOperationResult = also {
        if (this is IdentityOperationResult.Active) onActive()
    }

    private suspend fun choices(intent: IdentityIntent, attestation: IdentityAttestationRequest): IdentityOptions {
        val recovering = intent == IdentityIntent.Recoverable
        if (recovering && configuration.policy != IdentityKeyPolicy.GeneralPurpose)
            return IdentityOptions.Unavailable(listOf("The configured identity policy prohibits recovery-secret backup"))
        val options = authorizations.flatMap { authorization ->
            val storage = supportedStorage(recovering, authorization, attestation)
            if (recovering) availableProviders().flatMap { (provider, availability) ->
                storage.map { IdentityCreationOption(owner, it, authorization, provider.displayName, provider.id, availability, attestation) }
            } else storage.map { IdentityCreationOption(owner, it, authorization, null, null, null, attestation) }
        }
        return options.firstOrNull()?.let { IdentityOptions.Available(it, options.drop(1)) }
            ?: IdentityOptions.Unavailable(listOf("No configured destination meets the current key and recovery requirements"))
    }

    private suspend fun supportedStorage(importing: Boolean, authorization: KeyUseAuthorizationPolicy = this.authorization,
        attestation: IdentityAttestationRequest = IdentityAttestationRequest.None): List<IdentityKeyStorage> = IdentityKeyStorage.entries.filter { storage ->
        if (attestation is IdentityAttestationRequest.Native && (importing || storage != IdentityKeyStorage.Hardware)) false
        else if (configuration.policy == IdentityKeyPolicy.HardwareGenerated && (importing || storage != IdentityKeyStorage.Hardware)) false
        else if (storage == IdentityKeyStorage.EncryptedDatabase) authorization == KeyUseAuthorizationPolicy.None
        else if (!nativeSettingsPermitIdentitySigning()) false
        else requirements(storage, authorization, attestation).let { required ->
            native.preflight(required) is KeyUseAuthorizationSupport.Supported && (!importing || native.supportsPrivateKeyImport(required))
        }
    }

    private fun nativeSettingsPermitIdentitySigning(): Boolean {
        val settings = configuration.platform as? id.walt.crypto2.keys.PlatformKeyConfiguration.AndroidKeystore ?: return true
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        // Identity signatures cover protocol bytes. Protected Confirmation requires signing its own CBOR structure.
        if (settings.userConfirmationRequired || settings.userPresenceRequired) return false
        // Activation consumes one signature to prove possession of the original public key.
        if (settings.maxUsageCount == 1) return false
        if (settings.validFromEpochMillis?.let { it > now } == true || settings.validUntilEpochMillis?.let { it <= now } == true) return false
        return true
    }

    private fun requirements(storage: IdentityKeyStorage, authorization: KeyUseAuthorizationPolicy = this.authorization,
        attestation: IdentityAttestationRequest = IdentityAttestationRequest.None): WalletKeyRequirements = WalletKeyRequirements(
        spec = identitySpec, usages = identityUsages, authorizationPolicy = authorization,
        protection = if (storage == IdentityKeyStorage.Hardware) WalletKeyProtection.HardwareRequired else WalletKeyProtection.NativeStorage,
        platform = configuration.platform,
        attestationChallenge = (attestation as? IdentityAttestationRequest.Native)?.challenge,
    )

    private suspend fun valid(option: IdentityCreationOption): Boolean =
        option.authorization in authorizations && option.storage in supportedStorage(option.recoverable, option.authorization, option.attestation) &&
            (!option.recoverable || (configuration.policy == IdentityKeyPolicy.GeneralPurpose &&
                providers[option.providerId]?.availability() == option.recoveryAvailability))

    /** Reports each configured recovery provider, including its unmet prerequisites. */
    public suspend fun recoveryProviderStatuses(): List<IdentityRecoveryProviderStatus> = providers.values.map { provider ->
        val availability = try { provider.availability() }
        catch (cause: CancellationException) { throw cause }
        catch (_: Exception) { RecoveryAvailability.Unavailable("The recovery service could not be reached. Try again.") }
        IdentityRecoveryProviderStatus(provider.id, provider.displayName, availability)
    }

    private suspend fun availableProviders(): List<Pair<IdentityRecoveryProvider, RecoveryAvailability.Available>> =
        recoveryProviderStatuses().mapNotNull { status ->
            (status.availability as? RecoveryAvailability.Available)?.let { providers.getValue(status.id) to it }
        }

    private suspend fun createKey(record: IdentityRecord, material: EncodedKey.Jwk?): Key {
        val request = WalletKeyCreationRequest(KeyId(record.keyId), record.requirements, prompt, record.nativeAlias)
        return if (record.storage == IdentityKeyStorage.EncryptedDatabase) {
            if (material == null) keys.generateSoftwareKey(request)
            else keys.importSoftwareKey(softwareDescriptor(record.keyId, material))
        } else {
            val key = if (material == null) native.generateManagedKey(request) else native.importManagedKey(request, material)
            keys.addCrypto2Key(key)
            key
        }
    }

    private suspend fun describe(record: IdentityRecord, did: String, publicJwk: String, key: Key): WalletIdentity {
        val stored = (key as StorableKey).storedKey
        val facts = facts(stored).let { observed ->
            if (stored is StoredKey.Software && record.phase == IdentityPhase.Preparing) observed.copy(origin =
                if (record.backup == null) KeyOrigin.GENERATED else KeyOrigin.IMPORTED)
            else observed
        }
        check(record.storage != IdentityKeyStorage.Hardware || facts.protection == KeyProtectionLevel.HARDWARE)
        check(record.policy != IdentityKeyPolicy.HardwareGenerated ||
            (facts.origin == KeyOrigin.GENERATED && facts.protection == KeyProtectionLevel.HARDWARE))
        return WalletIdentity(record.id, record.keyId, did, publicJwk, record.storage,
            record.requirements.authorizationPolicy, facts)
    }

    private suspend fun facts(stored: StoredKey): PlatformKeyFacts = when (stored) {
        is StoredKey.Managed -> native.keyFacts(stored)
        is StoredKey.Software -> PlatformKeyFacts(securityLevel = KeySecurityLevel.SOFTWARE, protection = KeyProtectionLevel.SOFTWARE)
    }

    private fun permitsRecovery(record: IdentityRecord): Boolean =
        configuration.policy == IdentityKeyPolicy.GeneralPurpose && record.policy == IdentityKeyPolicy.GeneralPurpose

    private suspend fun liveKey(record: IdentityRecord): Key? = try {
        keys.getCrypto2Key(record.keyId)?.takeIf { key ->
            val expected = record.identity?.publicJwk ?: return@takeIf false
            val publicKey = key.capabilities.publicKeyExporter?.exportPublicKey() ?: return@takeIf false
            publicKey.toSpkiDer(key.spec) == EncodedKey.Jwk(BinaryData(expected.encodeToByteArray()), false).toSpkiDer(identitySpec)
        }
    } catch (cause: CancellationException) { throw cause }
    catch (_: Exception) { null }

    private suspend fun finish(record: IdentityRecord): IdentityOperationResult {
        if (record.backup == null) return activate(record)
        if (!permitsRecovery(record)) return failed(IdentityFailure.UnsupportedPolicy)
        val pending = record.copy(phase = IdentityPhase.AwaitingBackup)
        write(pending)
        val provider = providers[record.backup.providerId] ?: return IdentityOperationResult.Pending(record.id)
        return try {
            if (provider.availability() != record.recoveryAvailability) return IdentityOperationResult.Pending(record.id)
            val recovery = requireNotNull(record.recovery)
            val receipt = submit(provider, record.backup.recordId, recovery, record)
            activate(record.copy(identity = requireNotNull(record.identity).copy(
                recovery = IdentityRecoveryState.Submitted(record.backup, receipt))))
        } catch (cause: CancellationException) { throw cause }
        catch (cause: Exception) {
            val reason = providerFailure(cause)
            write(pending.copy(pendingReason = reason))
            IdentityOperationResult.Pending(record.id, reason)
        }
    }

    private suspend fun submit(provider: IdentityRecoveryProvider, id: String, recovery: RecoveryRecord, record: IdentityRecord): RecoveryReceipt {
        val expected = recovery.encode()
        try {
            val receipt = provider.store(id, IdentityRecoveryData(expected))
            val returned = provider.retrieve(id)?.copyBytes()
            try { check(returned != null && returned.contentEquals(expected)) { "Provider did not preserve the recovery record" } }
            finally { returned?.fill(0) }
            if (requiredConfirmation(record) == RecoveryConfirmation.ProviderConfirmation) {
                if (receipt != RecoveryReceipt.ConfirmedByProvider) throw IdentityProviderException(IdentityProviderFailure.ConfirmationPending)
            }
            return receipt
        } finally { expected.fill(0) }
    }

    private fun recoveryConstraints(record: IdentityRecord) = RecoveryConstraints(
        record.storage, record.requirements.authorizationPolicy, requiredConfirmation(record))

    private fun requiredConfirmation(record: IdentityRecord): RecoveryConfirmation =
        if (configuration.recoveryConfirmation == RecoveryConfirmation.ProviderConfirmation ||
            record.recoveryConfirmation == RecoveryConfirmation.ProviderConfirmation) RecoveryConfirmation.ProviderConfirmation
        else RecoveryConfirmation.LocalAcceptance

    private fun activate(record: IdentityRecord): IdentityOperationResult.Active {
        val identity = requireNotNull(record.identity)
        queries.transaction {
            write(record.copy(phase = IdentityPhase.Active, recovery = retained(record.recovery),
                recoveryConfirmation = requiredConfirmation(record), previous = null, previousKey = null))
            queries.setActiveIdentity(walletId, identity.id, identity.keyId, identity.did)
        }
        return IdentityOperationResult.Active(identity)
    }

    private suspend fun currentState(): WalletIdentityState {
        val record = read()
        if (record == null) {
            val didEntries = dids.listDids().toList()
            val keyEntries = keys.listKeys().toList()
            if (didEntries.isEmpty() && keyEntries.isEmpty()) return WalletIdentityState.Absent
            return WalletIdentityState.Unavailable(null, IdentityFailure.KeyUnavailable)
        }
        if (record.phase != IdentityPhase.Active) return WalletIdentityState.Pending(record.id, record.pendingReason)
        return try {
            if (liveKey(record) == null) WalletIdentityState.Unavailable(record.id, IdentityFailure.KeyUnavailable)
            else WalletIdentityState.Active(requireNotNull(record.identity))
        } catch (cause: CancellationException) { throw cause }
        catch (_: Exception) { WalletIdentityState.Unavailable(record.id, IdentityFailure.KeyUnavailable) }
    }

    private fun retained(record: RecoveryRecord?): RecoveryRecord? =
        record.takeIf { configuration.localRecoveryMaterial == LocalRecoveryMaterialRetention.Retain }

    private suspend fun decodeRecovery(bytes: ByteArray, recordId: String): RecoveryRecord {
        require(bytes.size in 1..IdentityRecoveryData.MAX_BYTES)
        val record = recordJson.decodeFromString<RecoveryRecord>(bytes.decodeToString(throwOnInvalidSequence = true))
        require(record.identityId == recordId) { "Recovery record identifier mismatch" }
        requirePublicJwk(record.publicJwk)
        software.restore(softwareDescriptor(record.keyId, record.privateKey()))
        require(matchesDid(record.did, record.publicJwk)) { "Recovery DID does not identify the original public key" }
        return record
    }

    private suspend fun matchesDid(did: String, publicJwk: String, spec: id.walt.crypto2.keys.KeySpec = identitySpec): Boolean {
        if (!did.startsWith("did:jwk:") && !did.startsWith("did:key:")) return false
        requirePublicJwk(publicJwk)
        if (did.startsWith("did:jwk:")) requirePublicJwk(recoveryBase64.decode(did.removePrefix("did:jwk:")).decodeToString())
        MobileDidSupport.ensureInitialized()
        val expected = EncodedKey.Jwk(BinaryData(publicJwk.encodeToByteArray()), false).toSpkiDer(spec)
        return didService.resolveToKeys(did).getOrThrow().any { key ->
            key.capabilities.publicKeyExporter?.exportPublicKey()?.toSpkiDer(key.spec) == expected
        }
    }

    private fun requirePublicJwk(value: String) {
        val jwk = recordJson.parseToJsonElement(value) as? kotlinx.serialization.json.JsonObject
            ?: error("Expected a public JWK")
        require(jwk.keys.none { it in setOf("d", "p", "q", "dp", "dq", "qi", "oth", "k") }) {
            "Private members are forbidden in public identity metadata"
        }
    }

    private suspend fun proveOriginalKey(key: Key, publicJwk: String) {
        val public = software.restore(StoredKey.Software(StoredKey.CURRENT_VERSION, KeyId("identity-proof"), identitySpec,
            setOf(id.walt.crypto2.keys.KeyUsage.VERIFY), EncodedKey.Jwk(BinaryData(publicJwk.encodeToByteArray()), false)))
        val challenge = IdentityRecoveryMaterial.createSeed()
        try {
            val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
            val signature = requireNotNull(key.capabilities.signer).sign(challenge, algorithm)
            check(requireNotNull(public.capabilities.verifier).verify(challenge, signature, algorithm))
        } finally { challenge.fill(0) }
    }

    private suspend fun cleanup(record: IdentityRecord, imported: Boolean, cause: Throwable?) = withContext(NonCancellable) {
        try {
            if (!keys.removeKey(record.keyId) && record.storage != IdentityKeyStorage.EncryptedDatabase)
                native.deleteUncommittedKey(WalletKeyCreationRequest(KeyId(record.keyId), record.requirements, prompt, record.nativeAlias), imported)
            if (record.previous == null) {
                record.identity?.let { dids.removeDid(it.did) }
                queries.deleteIdentityRecord(walletId)
            } else {
                record.previousKey?.let { key -> queries.insert(key.id.value,
                    kotlin.time.Clock.System.now().toEpochMilliseconds(),
                    id.walt.crypto2.serialization.StoredKeyCodec.encodeToString(key)) }
                write(record.previous)
            }
        } catch (cleanupFailure: Throwable) {
            if (cause != null) cause.addSuppressed(cleanupFailure) else throw cleanupFailure
        }
    }

    private suspend fun repairableKey(record: IdentityRecord, recovery: RecoveryRecord): StoredKey.Managed? {
        val identity = record.identity ?: return null
        if (record.phase != IdentityPhase.Active || record.policy != IdentityKeyPolicy.GeneralPurpose ||
            record.id != recovery.identityId || record.keyId != recovery.keyId || identity.did != recovery.did ||
            identity.publicJwk != recovery.publicJwk) return null
        val stored = keys.storedKey(record.keyId) as? StoredKey.Managed ?: return null
        return try {
            if (native.restoreManagedKey(stored) is id.walt.wallet2.persistence.keys.PlatformManagedKeyRestoration.Missing) stored else null
        } catch (cause: CancellationException) { throw cause }
        catch (cause: Exception) {
            if (generateSequence<Throwable>(cause) { it.cause }.any { it is id.walt.crypto2.signum.SignumKeyInvalidatedException }) stored
            else null
        }
    }

    private fun reserve(record: IdentityRecord) = queries.transaction {
        check(read() == record.previous) { "Identity state changed while reserving the operation" }
        check(keys.storedKey(record.keyId) == record.previousKey) { "Recovery key ID already exists or changed" }
        write(record)
    }

    private fun read(): IdentityRecord? = queries.selectIdentityRecord(walletId).executeAsOneOrNull()?.let {
        recordJson.decodeFromString<IdentityRecord>(it.payload).also { record -> require(record.version == 1) }
    }
    private fun write(record: IdentityRecord) = queries.putIdentityRecord(walletId, record.phase.name, recordJson.encodeToString(record))
    private fun softwareDescriptor(id: String, material: EncodedKey.Jwk) =
        StoredKey.Software(StoredKey.CURRENT_VERSION, KeyId(id), identitySpec, identityUsages, material)
    private suspend fun publicJwk(key: Key): String = requireNotNull(key.capabilities.publicKeyExporter)
        .exportPublicKey().toPublicJwk(key.spec).data.toByteArray().decodeToString()
    private suspend fun fingerprint(bytes: ByteArray): ByteArray = CryptographyProvider.Default.get(SHA256).hasher().hash(bytes)
    private fun failed(reason: IdentityFailure) = IdentityOperationResult.Failed(reason)
    private fun providerFailure(cause: Exception): IdentityFailure = when ((cause as? IdentityProviderException)?.failure) {
        IdentityProviderFailure.InteractionRequired -> IdentityFailure.ProviderInteractionRequired
        IdentityProviderFailure.Rejected -> IdentityFailure.ProviderRejected
        IdentityProviderFailure.Conflict -> IdentityFailure.ProviderConflict
        IdentityProviderFailure.ConfirmationPending -> IdentityFailure.ProviderConfirmationPending
        IdentityProviderFailure.TemporarilyUnavailable, null -> IdentityFailure.ProviderUnavailable
    }
    private fun classify(cause: Throwable): IdentityFailure = when ((cause as? KeyUseAuthorizationException)?.failure) {
        KeyUseAuthorizationFailure.UnsupportedCombination -> IdentityFailure.UnsupportedPolicy
        KeyUseAuthorizationFailure.ProtectedKeyUnavailable -> IdentityFailure.KeyUnavailable
        KeyUseAuthorizationFailure.InvalidStoredKeyMetadata -> IdentityFailure.InvalidRecoveryRecord
        KeyUseAuthorizationFailure.BiometricUnavailable, KeyUseAuthorizationFailure.BiometricNotEnrolled,
        KeyUseAuthorizationFailure.DeviceCredentialNotSet,
        KeyUseAuthorizationFailure.InteractionContextUnavailable, KeyUseAuthorizationFailure.AuthorizationNotCompleted ->
            IdentityFailure.AuthorizationNotCompleted
        null -> IdentityFailure.NativeOperationFailed
    }
}
