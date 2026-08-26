package id.walt.wallet2.data

import id.walt.credentials.formats.MdocsCredential
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

/** Versioned record connecting a stored credential to the key that can present it. */
@Serializable
data class HolderKeyBinding(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    /**
     * Provider-qualified opaque reference interpreted only by the wallet key resolver.
     * A configured key-store slot is part of that resolver namespace; persisted wallets must keep
     * their provider order stable. The public-key thumbprint makes accidental reordering fail closed.
     */
    val keyReference: String,
    /** Semantic public-key identity used to detect stale or changed provider references. */
    val publicKeyThumbprint: PublicKeyThumbprint,
    val origin: HolderKeyBindingOrigin,
    val createdAt: Instant,
    val extractorVersion: Int = CURRENT_EXTRACTOR_VERSION,
) {
    init {
        require(schemaVersion > 0) { "Holder-key binding schema version must be positive" }
        require(keyReference.isNotBlank()) { "Holder-key reference must not be blank" }
        require(extractorVersion > 0) { "Holder-key extractor version must be positive" }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
        const val CURRENT_EXTRACTOR_VERSION: Int = 1
    }
}

/** Algorithm-qualified public-key identity. */
@Serializable
data class PublicKeyThumbprint(
    val algorithm: String = RFC7638_SHA256,
    val value: String,
) {
    init {
        require(algorithm.isNotBlank()) { "Public-key thumbprint algorithm must not be blank" }
        require(value.isNotBlank()) { "Public-key thumbprint value must not be blank" }
    }

    companion object {
        const val RFC7638_SHA256: String = "rfc7638-sha256"
    }
}

@Serializable
enum class HolderKeyBindingOrigin {
    @SerialName("issuance")
    ISSUANCE,

    @SerialName("import")
    IMPORT,
}

/** Stable machine-readable reason why an mdoc cannot use a holder key. */
@Serializable
enum class HolderKeyBindingErrorCode {
    NOT_AN_MDOC,
    UNSUPPORTED_BINDING_VERSION,
    UNSUPPORTED_THUMBPRINT_ALGORITHM,
    CREDENTIAL_KEY_EXTRACTION_FAILED,
    CREDENTIAL_NOT_FOUND,
    BINDING_MISSING,
    BINDING_DOES_NOT_MATCH_CREDENTIAL,
    KEY_REFERENCE_INVALID,
    KEY_NOT_FOUND,
    KEY_DOES_NOT_MATCH_BINDING,
    KEY_PUBLIC_MATERIAL_UNAVAILABLE,
    KEY_USAGE_UNSUPPORTED,
    KEY_PROVIDER_UNAVAILABLE,
    NO_MATCHING_LOCAL_KEY,
    MULTIPLE_MATCHING_LOCAL_KEYS,
}

class HolderKeyBindingException(
    val code: HolderKeyBindingErrorCode,
    val credentialId: String,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

data class ResolvedHolderKey(
    val keyMaterial: WalletKeyStoreEntry,
    val binding: HolderKeyBinding,
)

private const val WALLET_KEY_REFERENCE_PREFIX = "urn:waltid:wallet-key:v1:"
private sealed interface WalletKeyLocation {
    val keyId: String

    data class Store(val index: Int, override val keyId: String) : WalletKeyLocation
    data class Static(override val keyId: String) : WalletKeyLocation
}

private data class WalletKeyCandidate(
    val material: WalletKeyStoreEntry,
    val thumbprint: PublicKeyThumbprint,
)

/**
 * Resolves the exact Crypto2 key bound to [credential]. Bindings are created during issuance or
 * import; unbound mdocs are never repaired implicitly during presentation.
 */
suspend fun Wallet.resolveHolderKey(
    credential: StoredCredential,
    requiredUsages: Set<KeyUsage> = setOf(KeyUsage.SIGN),
): ResolvedHolderKey {
    val mdoc = credential.credential as? MdocsCredential
        ?: throw bindingError(
            credential,
            HolderKeyBindingErrorCode.NOT_AN_MDOC,
            "Credential '${credential.id}' is not an mdoc and has no MSO DeviceKey",
        )
    val credentialThumbprint = try {
        mdoc.holderKeyThumbprint()
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        throw bindingError(
            credential,
            HolderKeyBindingErrorCode.CREDENTIAL_KEY_EXTRACTION_FAILED,
            "Could not extract the MSO DeviceKey for credential '${credential.id}'",
            cause,
        )
    }
    val existing = credential.holderKeyBinding ?: throw bindingError(
        credential,
        HolderKeyBindingErrorCode.BINDING_MISSING,
        "Credential '${credential.id}' has no holder-key binding",
    )
    validateBindingContract(credential, existing, credentialThumbprint)
    val candidate = resolveReferencedCandidate(credential, existing.keyReference, requiredUsages)
    if (candidate.thumbprint != existing.publicKeyThumbprint) {
        throw bindingError(
            credential,
            HolderKeyBindingErrorCode.KEY_DOES_NOT_MATCH_BINDING,
            "The key referenced by credential '${credential.id}' no longer matches its holder-key binding",
        )
    }
    candidate.material.requireUsages(credential, requiredUsages)
    return ResolvedHolderKey(candidate.material, existing)
}

suspend fun Wallet.resolveHolderKey(
    credentialId: String,
    requiredUsages: Set<KeyUsage> = setOf(KeyUsage.SIGN),
): ResolvedHolderKey = resolveHolderKey(
    credential = findCredential(credentialId)
        ?: throw HolderKeyBindingException(
            HolderKeyBindingErrorCode.CREDENTIAL_NOT_FOUND,
            credentialId,
            "Credential '$credentialId' is no longer available",
        ),
    requiredUsages = requiredUsages,
)

/** Adds a verified issuance binding when [keyMaterial] is durably resolvable by this wallet. */
suspend fun Wallet.withVerifiedHolderKeyBinding(
    credential: StoredCredential,
    keyMaterial: WalletKeyStoreEntry,
    origin: HolderKeyBindingOrigin,
    createdAt: Instant = Clock.System.now(),
): StoredCredential {
    val mdoc = credential.credential as? MdocsCredential ?: return credential
    val credentialThumbprint = try {
        mdoc.holderKeyThumbprint()
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        throw bindingError(
            credential,
            HolderKeyBindingErrorCode.CREDENTIAL_KEY_EXTRACTION_FAILED,
            "Could not extract the MSO DeviceKey for credential '${credential.id}'",
            cause,
        )
    }
    val suppliedThumbprint = try {
        keyMaterial.publicKeyThumbprint()
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        throw bindingError(
            credential,
            HolderKeyBindingErrorCode.KEY_PUBLIC_MATERIAL_UNAVAILABLE,
            "The issuance key for credential '${credential.id}' has no usable public material",
            cause,
        )
    }
    if (credentialThumbprint != suppliedThumbprint) {
        throw bindingError(
            credential,
            HolderKeyBindingErrorCode.BINDING_DOES_NOT_MATCH_CREDENTIAL,
            "Issued mdoc '${credential.id}' MSO DeviceKey does not match its proof-of-possession key",
        )
    }
    val candidate = keyMaterial.keyReference?.let { reference ->
        val resolved = resolveReferencedCandidate(credential, reference, setOf(KeyUsage.SIGN))
        if (resolved.thumbprint != suppliedThumbprint) {
            throw bindingError(
                credential,
                HolderKeyBindingErrorCode.KEY_DOES_NOT_MATCH_BINDING,
                "The issuance key reference for credential '${credential.id}' resolves to different key material",
            )
        }
        resolved
    } ?: allKeyCandidates(credential, setOf(KeyUsage.SIGN))
        .filter { it.material.keyId == keyMaterial.keyId && it.thumbprint == suppliedThumbprint }
        .let { matching ->
            when (matching.size) {
                0 -> throw bindingError(
                    credential,
                    HolderKeyBindingErrorCode.NO_MATCHING_LOCAL_KEY,
                    "The issuance key for credential '${credential.id}' is not durably available in this wallet",
                )

                1 -> matching.single()
                else -> throw bindingError(
                    credential,
                    HolderKeyBindingErrorCode.MULTIPLE_MATCHING_LOCAL_KEYS,
                    "The issuance key for credential '${credential.id}' is ambiguous across wallet key providers",
                )
            }
        }
    candidate.material.requireUsages(credential, setOf(KeyUsage.SIGN))
    return credential.copy(holderKeyBinding = candidate.binding(origin, createdAt))
}

/** Binds an imported mdoc only when one unambiguous matching local signing key exists. */
suspend fun Wallet.withImportedHolderKeyBinding(credential: StoredCredential): StoredCredential {
    if (credential.credential !is MdocsCredential) return credential
    return try {
        withRequiredUniqueHolderKeyBinding(credential, HolderKeyBindingOrigin.IMPORT)
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: HolderKeyBindingException) {
        credential
    }
}

/** Adds a binding only when one unambiguous matching local signing key exists. */
suspend fun Wallet.withRequiredUniqueHolderKeyBinding(
    credential: StoredCredential,
    origin: HolderKeyBindingOrigin,
): StoredCredential {
    val mdoc = credential.credential as? MdocsCredential ?: return credential
    val thumbprint = try {
        mdoc.holderKeyThumbprint()
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        throw bindingError(
            credential,
            HolderKeyBindingErrorCode.CREDENTIAL_KEY_EXTRACTION_FAILED,
            "Could not extract the MSO DeviceKey for credential '${credential.id}'",
            cause,
        )
    }
    val matching = allKeyCandidates(credential, setOf(KeyUsage.SIGN)).filter { it.thumbprint == thumbprint }
    val candidate = when (matching.size) {
        0 -> throw bindingError(
            credential,
            HolderKeyBindingErrorCode.NO_MATCHING_LOCAL_KEY,
            "No local key matches the MSO DeviceKey for credential '${credential.id}'",
        )

        1 -> matching.single()
        else -> throw bindingError(
            credential,
            HolderKeyBindingErrorCode.MULTIPLE_MATCHING_LOCAL_KEYS,
            "More than one local key matches the MSO DeviceKey for credential '${credential.id}'",
        )
    }
    candidate.material.requireUsages(credential, setOf(KeyUsage.SIGN))
    return credential.copy(holderKeyBinding = candidate.binding(origin))
}

private fun validateBindingContract(
    credential: StoredCredential,
    binding: HolderKeyBinding,
    credentialThumbprint: PublicKeyThumbprint,
) {
    if (
        binding.schemaVersion != HolderKeyBinding.CURRENT_SCHEMA_VERSION ||
        binding.extractorVersion != HolderKeyBinding.CURRENT_EXTRACTOR_VERSION
    ) {
        throw bindingError(
            credential,
            HolderKeyBindingErrorCode.UNSUPPORTED_BINDING_VERSION,
            "Credential '${credential.id}' uses an unsupported holder-key binding version",
        )
    }
    if (binding.publicKeyThumbprint.algorithm != PublicKeyThumbprint.RFC7638_SHA256) {
        throw bindingError(
            credential,
            HolderKeyBindingErrorCode.UNSUPPORTED_THUMBPRINT_ALGORITHM,
            "Credential '${credential.id}' uses an unsupported holder-key thumbprint algorithm",
        )
    }
    if (binding.publicKeyThumbprint != credentialThumbprint) {
        throw bindingError(
            credential,
            HolderKeyBindingErrorCode.BINDING_DOES_NOT_MATCH_CREDENTIAL,
            "Credential '${credential.id}' holder-key binding does not match its MSO DeviceKey",
        )
    }
}

private suspend fun Wallet.resolveReferencedCandidate(
    credential: StoredCredential,
    reference: String,
    requiredUsages: Set<KeyUsage>,
): WalletKeyCandidate {
    val location = reference.decodeWalletKeyReference()
        ?: throw bindingError(
            credential,
            HolderKeyBindingErrorCode.KEY_REFERENCE_INVALID,
            "Credential '${credential.id}' has an unsupported holder-key reference",
        )
    val material = try {
        when (location) {
            is WalletKeyLocation.Store -> keyStores.getOrNull(location.index)
                ?.getKeyMaterial(location.keyId, requiredUsages)
                ?.copy(keyReference = reference)

            is WalletKeyLocation.Static -> attachedStaticCrypto2Key()
                ?.takeIf { it.id.value == location.keyId }
                ?.let { crypto2Key ->
                    WalletKeyStoreEntry(
                        keyId = location.keyId,
                        legacyKey = null,
                        crypto2Key = crypto2Key,
                        keyReference = reference,
                    )
                }
        }
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: IllegalArgumentException) {
        throw bindingError(
            credential,
            HolderKeyBindingErrorCode.KEY_USAGE_UNSUPPORTED,
            "The holder key for credential '${credential.id}' does not permit $requiredUsages",
            cause,
        )
    } catch (cause: Exception) {
        throw bindingError(
            credential,
            HolderKeyBindingErrorCode.KEY_PROVIDER_UNAVAILABLE,
            "The holder-key provider for credential '${credential.id}' is unavailable",
            cause,
        )
    } ?: throw bindingError(
        credential,
        HolderKeyBindingErrorCode.KEY_NOT_FOUND,
        "The holder key for credential '${credential.id}' is no longer available",
    )
    return candidate(credential, material)
}

private suspend fun Wallet.allKeyCandidates(
    credential: StoredCredential,
    requiredUsages: Set<KeyUsage>,
): List<WalletKeyCandidate> = buildList {
    keyStores.forEachIndexed { index, store ->
        val ids = try {
            store.listKeys().toList().map { it.keyId }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            throw bindingError(
                credential,
                HolderKeyBindingErrorCode.KEY_PROVIDER_UNAVAILABLE,
                "A wallet key provider is unavailable while resolving credential '${credential.id}'",
                cause,
            )
        }
        ids.forEach { keyId ->
            val material = try {
                store.getKeyMaterial(keyId, requiredUsages)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: IllegalArgumentException) {
                throw bindingError(
                    credential,
                    HolderKeyBindingErrorCode.KEY_USAGE_UNSUPPORTED,
                    "Wallet key '$keyId' does not permit $requiredUsages for credential '${credential.id}'",
                    cause,
                )
            } catch (cause: Exception) {
                throw bindingError(
                    credential,
                    HolderKeyBindingErrorCode.KEY_PROVIDER_UNAVAILABLE,
                    "A wallet key provider is unavailable while resolving credential '${credential.id}'",
                    cause,
                )
            } ?: throw bindingError(
                credential,
                HolderKeyBindingErrorCode.KEY_PROVIDER_UNAVAILABLE,
                "Wallet key '$keyId' is listed but unavailable while resolving credential '${credential.id}'",
            )
            if (material.crypto2Key != null) {
                add(candidate(credential, material.copy(legacyKey = null, keyReference = walletStoreKeyReference(index, keyId))))
            }
        }
    }
    attachedStaticCrypto2Key()?.let { crypto2Key ->
        val keyId = crypto2Key.id.value
        add(
            candidate(
                credential,
                WalletKeyStoreEntry(
                    keyId = keyId,
                    legacyKey = null,
                    crypto2Key = crypto2Key,
                    keyReference = staticWalletKeyReference(keyId),
                ),
            )
        )
    }
}

private suspend fun candidate(
    credential: StoredCredential,
    material: WalletKeyStoreEntry,
): WalletKeyCandidate = try {
    WalletKeyCandidate(material, material.publicKeyThumbprint())
} catch (cause: CancellationException) {
    throw cause
} catch (cause: Exception) {
    throw bindingError(
        credential,
        HolderKeyBindingErrorCode.KEY_PUBLIC_MATERIAL_UNAVAILABLE,
        "Wallet key '${material.keyId}' has no consistent public material",
        cause,
    )
}

private fun WalletKeyCandidate.binding(
    origin: HolderKeyBindingOrigin,
    createdAt: Instant = Clock.System.now(),
): HolderKeyBinding = HolderKeyBinding(
    keyReference = requireNotNull(material.keyReference),
    publicKeyThumbprint = thumbprint,
    origin = origin,
    createdAt = createdAt,
)

private fun WalletKeyStoreEntry.requireUsages(
    credential: StoredCredential,
    requiredUsages: Set<KeyUsage>,
) {
    val supported = crypto2Key?.let { requiredUsages.all(it.usages::contains) } == true
    if (!supported) {
        throw bindingError(
            credential,
            HolderKeyBindingErrorCode.KEY_USAGE_UNSUPPORTED,
            "The holder key for credential '${credential.id}' does not permit $requiredUsages",
        )
    }
}

private suspend fun MdocsCredential.holderKeyThumbprint(): PublicKeyThumbprint =
    getHolderCrypto2Key().publicKeyThumbprint()

private suspend fun WalletKeyStoreEntry.publicKeyThumbprint(): PublicKeyThumbprint {
    return requireNotNull(crypto2Key) { "Key '$keyId' is not available through crypto2" }
        .publicKeyThumbprint()
}

private suspend fun id.walt.crypto2.keys.Key.publicKeyThumbprint(): PublicKeyThumbprint {
    val exported = requireNotNull(capabilities.publicKeyExporter) {
        "Key '${id.value}' does not export public material"
    }.exportPublicKey().toPublicJwk(spec)
    return PublicKeyThumbprint(value = Jwk.sha256Thumbprint(exported))
}

internal fun walletStoreKeyReference(storeIndex: Int, keyId: String): String =
    "$WALLET_KEY_REFERENCE_PREFIX" + "store:$storeIndex:${keyId.encodeToByteArray().encodeToBase64Url()}"

internal fun staticWalletKeyReference(keyId: String): String =
    "$WALLET_KEY_REFERENCE_PREFIX" + "static:${keyId.encodeToByteArray().encodeToBase64Url()}"

private fun String.decodeWalletKeyReference(): WalletKeyLocation? = runCatching {
    if (!startsWith(WALLET_KEY_REFERENCE_PREFIX)) return@runCatching null
    val value = removePrefix(WALLET_KEY_REFERENCE_PREFIX)
    when {
        value.startsWith("store:") -> {
            val parts = value.removePrefix("store:").split(':', limit = 2)
            val index = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it >= 0 } ?: return@runCatching null
            val keyId = parts.getOrNull(1)?.decodeFromBase64Url()?.decodeToString()?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            WalletKeyLocation.Store(index, keyId)
        }

        value.startsWith("static:") -> {
            val keyId = value.removePrefix("static:").decodeFromBase64Url().decodeToString().takeIf { it.isNotBlank() }
                ?: return@runCatching null
            WalletKeyLocation.Static(keyId)
        }

        else -> null
    }
}.getOrNull()

private fun bindingError(
    credential: StoredCredential,
    code: HolderKeyBindingErrorCode,
    message: String,
    cause: Throwable? = null,
) = HolderKeyBindingException(code, credential.id, message, cause)
