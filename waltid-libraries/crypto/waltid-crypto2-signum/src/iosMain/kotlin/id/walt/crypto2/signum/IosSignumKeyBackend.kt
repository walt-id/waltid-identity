package id.walt.crypto2.signum

import at.asitplus.signum.internals.CoreFoundationException
import at.asitplus.signum.supreme.CFCryptoOperationFailed
import at.asitplus.signum.supreme.os.IosSigner
import at.asitplus.signum.supreme.os.IosKeychainProvider
import at.asitplus.signum.supreme.os.IosSecureEnclaveConfiguration
import at.asitplus.signum.supreme.os.PlatformSigningProviderSigner
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.keys.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSOSStatusErrorDomain
import platform.Foundation.NSProcessInfo

/** Uses stable Signum where it satisfies the key contract, with isolated Apple lifecycle extensions. */
class IosSignumKeyBackend : SignumPlatformBackend, SignumPrivateKeyImportBackend {
    override val id = ProviderId("ios-keychain-signum")

    override fun supports(spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): Boolean =
        policy.platform !is SignumPlatformPolicy.AndroidKeystore &&
            spec.isSupportedSignumSpec() &&
            usages.all { it == KeyUsage.SIGN || it == KeyUsage.VERIFY || it == KeyUsage.KEY_AGREEMENT } &&
            (KeyUsage.KEY_AGREEMENT !in usages || spec is KeySpec.Ec) &&
            (KeyUsage.KEY_AGREEMENT in usages) == policy.keyAgreement &&
            (policy.hardware != SignumHardwarePolicy.REQUIRED || spec == KeySpec.Ec(EcCurve.P256)) &&
            (iosKeyEngine(spec, policy) != IosKeyEngine.APPLE_KEYCHAIN ||
                (spec == KeySpec.Ec(EcCurve.P256) && AppleKeychainKeys.supports(policy, importing = false)))

    override fun supportsImport(spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): Boolean =
        spec == KeySpec.Ec(EcCurve.P256) && usages == setOf(KeyUsage.SIGN, KeyUsage.VERIFY) &&
            AppleKeychainKeys.supports(policy, importing = true)

    override suspend fun create(alias: String, spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): SignumPlatformKey {
        require(supports(spec, usages, policy)) { "Unsupported iOS key policy" }
        return createOwned(alias, spec, usages, policy, null)
    }

    override suspend fun importPrivateKey(alias: String, material: EncodedKey.Jwk,
        spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): SignumPlatformKey {
        require(supportsImport(spec, usages, policy)) { "Unsupported iOS private-key import policy" }
        return createOwned(alias, spec, usages, policy, material)
    }

    private suspend fun createOwned(alias: String, spec: KeySpec, usages: Set<KeyUsage>,
        policy: SignumKeyPolicy, material: EncodedKey.Jwk?): SignumPlatformKey = lifecycle.withLock {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        withContext(NonCancellable) {
            require(IosKeyOwnershipStore.read(alias, policy) == null) { "Key alias already has an ownership record" }
            // Do not let Signum's orphan cleanup replace an externally created private key.
            require(IosKeyEngine.entries.none { iosKeyExists(alias, policy, it) }) { "Native key alias already exists" }
            val engine = if (material != null) IosKeyEngine.APPLE_KEYCHAIN else iosKeyEngine(spec, policy)
            val effective = if (policy.hardware == SignumHardwarePolicy.PREFERRED && isSimulator) {
                policy.copy(hardware = SignumHardwarePolicy.DISCOURAGED)
            } else policy
            val key = when (engine) {
                IosKeyEngine.APPLE_KEYCHAIN -> AppleKeychainKeys.create(alias, effective, material)
                IosKeyEngine.SIGNUM -> {
                    val signer = IosKeychainProvider.createSigningKey(alias) {
                        configureSignumKey(spec, usages, effective) {
                            val ios = this as IosSecureEnclaveConfiguration
                            val accessibility = policy.iosSettings().accessibility
                            ios.availability = when (accessibility) {
                                SignumKeychainAccessibility.AFTER_FIRST_UNLOCK,
                                SignumKeychainAccessibility.AFTER_FIRST_UNLOCK_DEVICE_ONLY -> IosSecureEnclaveConfiguration.Availability.AFTER_FIRST_UNLOCK
                                else -> IosSecureEnclaveConfiguration.Availability.WHILE_UNLOCKED
                            }
                            ios.allowBackup = accessibility == SignumKeychainAccessibility.WHEN_UNLOCKED ||
                                accessibility == SignumKeychainAccessibility.AFTER_FIRST_UNLOCK
                        }
                    }.getOrElse { throw it.mapSignumFailure(alias) }
                    try { signumHandle(alias, spec, usages, policy, signer) }
                    catch (cause: Throwable) {
                        try { deleteNative(alias, policy, engine) } catch (cleanup: Throwable) { cause.addSuppressed(cleanup) }
                        throw cause
                    }
                }
            }
            var receiptWritten = false
            try {
                val native = (key as? AppleKeychainKey)?.nativeIdentity
                    ?: iosKeyIdentity(alias, policy, engine) ?: throw SignumKeyNotFoundException(alias)
                native.validate(alias, policy)
                val record = IosKeyOwnership(engine = engine, policy = policy.immutableIosPolicy(), spec = spec,
                    usages = usages, origin = if (material == null) SignumKeyOrigin.GENERATED else SignumKeyOrigin.IMPORTED, publicKey = key.publicKey, native = native)
                IosKeyOwnershipStore.write(alias, policy, record)
                receiptWritten = true
                caller.ensureActive()
                ownedHandle(key, record)
            } catch (cause: Throwable) {
                withContext(NonCancellable) {
                    try {
                        deleteNative(alias, policy, engine)
                        if (receiptWritten) IosKeyOwnershipStore.delete(alias, policy)
                    } catch (cleanup: Throwable) { cause.addSuppressed(cleanup) }
                }
                throw cause
            }
        }
    }

    override suspend fun load(alias: String, spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): SignumPlatformKey? =
        loadOwned(alias, spec, usages, policy, SignumKeyOrigin.GENERATED)

    override suspend fun loadImportedKey(alias: String, spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): SignumPlatformKey? =
        loadOwned(alias, spec, usages, policy, SignumKeyOrigin.IMPORTED)

    private suspend fun loadOwned(alias: String, spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy,
        origin: SignumKeyOrigin): SignumPlatformKey? = lifecycle.withLock {
        val record = IosKeyOwnershipStore.read(alias, policy)
        if (record == null) {
            if (IosKeyEngine.entries.any { iosKeyExists(alias, policy, it) }) {
                throw SignumKeyPolicyMismatchException(alias, "existing key has no SDK creation record; import recovered material into a fresh alias")
            }
            return@withLock null
        }
        record.validate(alias, policy, spec, usages, origin)
        validateOwnedEntry(alias, record)
        val key = when (record.engine) {
            IosKeyEngine.APPLE_KEYCHAIN -> AppleKeychainKeys.load(alias, policy, origin == SignumKeyOrigin.IMPORTED, record.publicKey, record.native)
                ?: throw SignumKeyNotFoundException(alias)
            IosKeyEngine.SIGNUM -> signumHandle(alias, spec, usages, policy,
                IosKeychainProvider.getSignerForKey(alias).getOrElse { throw it.mapSignumFailure(alias) })
        }
        if (key.publicKey != record.publicKey) throw SignumKeyPolicyMismatchException(alias, "public key differs from its creation record")
        ownedHandle(key, record)
    }

    override suspend fun deleteImportedKey(alias: String, policy: SignumKeyPolicy) = delete(alias, policy)
    override suspend fun delete(alias: String) {
        val record = IosKeyOwnershipStore.read(alias, SignumKeyPolicy()) ?: return
        delete(alias, record.policy)
    }

    override suspend fun delete(alias: String, policy: SignumKeyPolicy) = lifecycle.withLock {
        val record = IosKeyOwnershipStore.read(alias, policy) ?: return@withLock
        if (record.policy != policy.immutableIosPolicy()) {
            throw SignumKeyPolicyMismatchException(alias, "deletion policy differs from the creation record")
        }
        try { validateOwnedEntry(alias, record) } catch (_: SignumKeyNotFoundException) { /* Delete a stale receipt too. */ }
        deleteNative(alias, policy, record.engine)
        IosKeyOwnershipStore.delete(alias, policy)
    }

    private suspend fun deleteNative(alias: String, policy: SignumKeyPolicy, engine: IosKeyEngine) {
        when (engine) {
            IosKeyEngine.APPLE_KEYCHAIN -> AppleKeychainKeys.delete(alias, policy)
            IosKeyEngine.SIGNUM -> IosKeychainProvider.deleteSigningKey(alias).getOrElse {
                val mapped = it.mapSignumFailure(alias)
                if (mapped !is SignumKeyNotFoundException) throw mapped
            }
        }
    }

    private fun validateOwnedEntry(alias: String, record: IosKeyOwnership) {
        if (record.engine == IosKeyEngine.APPLE_KEYCHAIN) {
            AppleKeychainKeys.validateIdentity(alias, record.policy, record.native)
            return
        }
        val native = iosKeyIdentity(alias, record.policy, record.engine) ?: throw SignumKeyNotFoundException(alias)
        if (native != record.native) throw SignumKeyPolicyMismatchException(alias, "native key differs from its creation record")
        native.validate(alias, record.policy)
    }

    private fun ownedHandle(key: SignumPlatformKey, record: IosKeyOwnership): SignumPlatformKey = object : SignumPlatformKey by key {
        override val origin = record.origin
        override val protectionLevel = if (record.native.secureEnclave) SignumProtectionLevel.HARDWARE else SignumProtectionLevel.SOFTWARE
        override val securityLevel = if (record.native.secureEnclave) SignumSecurityLevel.SECURE_ENCLAVE else SignumSecurityLevel.SOFTWARE
        override val privateKeyExporter = key.privateKeyExporter?.let { exporter -> PrivateKeyExporter {
            lifecycle.withLock { validateOwnedEntry(key.alias, record); exporter.exportPrivateKey() }
        } }
        override suspend fun sign(data: ByteArray, algorithm: SignatureAlgorithm): ByteArray = lifecycle.withLock {
            validateOwnedEntry(key.alias, record)
            key.sign(data, algorithm)
        }
        override suspend fun generateSharedSecret(peerPublicKey: EncodedKey, algorithm: KeyAgreementAlgorithm): BinaryData = lifecycle.withLock {
            validateOwnedEntry(key.alias, record)
            key.generateSharedSecret(peerPublicKey, algorithm)
        }
    }

    private fun signumHandle(alias: String, spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy,
        signer: PlatformSigningProviderSigner<*, *>): SignumPlatformKey {
        val iosSigner = signer as? IosSigner ?: throw SignumKeyPolicyMismatchException(alias, "signer is not Keychain-backed")
        val native = iosKeyIdentity(alias, policy, IosKeyEngine.SIGNUM) ?: throw SignumKeyNotFoundException(alias)
        validateIosNativePolicy(alias, policy, iosSigner.needsAuthenticationForEveryUse, iosSigner.needsAuthentication, native.secureEnclave)
        return SignumPlatformKeyHandle(
            alias = alias, spec = spec, protectionLevel = SignumProtectionLevel.UNKNOWN,
            attestation = signer.toAttestation(), authentication = policy.authentication,
            signerFor = { algorithm -> IosKeychainProvider.getSignerForKey(alias) {
                configureSignumOperation(algorithm, policy.authentication)
            }.getOrElse { throw it.mapSignumFailure(alias) } },
            operationFailureMapper = { it.mapSignumFailure(alias) },
            nativePublicKey = signer.publicKey,
            keyAgreementEnabled = KeyUsage.KEY_AGREEMENT in usages && policy.keyAgreement,
        )
    }

    internal fun validateIosNativePolicy(
        alias: String,
        policy: SignumKeyPolicy,
        needsAuthenticationForEveryUse: Boolean,
        needsAuthentication: Boolean = needsAuthenticationForEveryUse,
        isSecureEnclave: Boolean,
    ) {
        if (policy.hardware == SignumHardwarePolicy.REQUIRED && !isSecureEnclave) {
            throw SignumKeyPolicyMismatchException(alias, "the native key is not Secure Enclave-backed")
        }
        val authentication = policy.authentication as? SignumAuthenticationPolicy.UserPresence
        if (needsAuthentication != (authentication != null) ||
            (authentication != null && needsAuthenticationForEveryUse != (authentication.timeoutSeconds == 0))) {
            throw SignumKeyPolicyMismatchException(alias, "Signum authorization metadata differs from the creation policy")
        }
        // Factor selection comes from the bound creation record; these booleans do not expose ACL flags.

    }

}

/** Stable Signum does not expose persistent private-key import/export or per-key timed sessions. */
internal fun iosKeyEngine(spec: KeySpec, policy: SignumKeyPolicy): IosKeyEngine {
    val settings = policy.iosSettings()
    val timeout = (policy.authentication as? SignumAuthenticationPolicy.UserPresence)?.timeoutSeconds ?: 0
    val needsApple = settings.accessGroup != null ||
        settings.accessibility == SignumKeychainAccessibility.WHEN_PASSCODE_SET_DEVICE_ONLY || timeout > 0 ||
        (spec == KeySpec.Ec(EcCurve.P256) && !policy.keyAgreement && policy.hardware != SignumHardwarePolicy.REQUIRED)
    return if (needsApple) IosKeyEngine.APPLE_KEYCHAIN else IosKeyEngine.SIGNUM
}

internal fun Throwable.mapSignumFailure(alias: String): Throwable {
    if (this is CancellationException && this !is SignumUserCancelledException) return this
    val root = if (this is SignumUserCancelledException) reason else this
    val native = generateSequence(root) { it.cause }.mapNotNull {
        when (it) {
            is CFCryptoOperationFailed -> IosKeychainException(NSOSStatusErrorDomain, it.osStatus.toLong(),
                it.message ?: "Keychain operation failed", this)
            is CoreFoundationException -> it.nsError.toKeychainException(this)
            else -> null
        }
    }.firstOrNull()
    return when {
        native != null -> iosKeychainFailure(alias, native)
        this is SignumUserCancelledException -> SignumAuthorizationException(cause = reason)
        else -> this
    }
}

private fun KeySpec.isSupportedSignumSpec(): Boolean = when (this) {
    is KeySpec.Ec -> curve == EcCurve.P256 || curve == EcCurve.P384 || curve == EcCurve.P521
    is KeySpec.Rsa -> bits == 2048 || bits == 3072 || bits == 4096
    else -> false
}

private val lifecycle = Mutex()
private val isSimulator: Boolean by lazy {
    NSProcessInfo.processInfo.environment.keys.any { it == "SIMULATOR_UDID" || it == "SIMULATOR_DEVICE_NAME" }
}
