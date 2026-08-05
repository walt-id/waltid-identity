package id.walt.crypto2.signum

import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyAgreement
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyDeleter
import id.walt.crypto2.keys.KeyDeletionResult
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.PublicKeyExporter
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.Verifier
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.ManagedKeyProvider
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SignumManagedKeyProvider(
    private val backend: SignumPlatformBackend,
) : ManagedKeyProvider {
    override val id = backend.id

    override suspend fun generate(request: GenerateManagedKeyRequest): ManagedKey = generateSignumKey(request)

    suspend fun generateSignumKey(request: GenerateManagedKeyRequest): SignumManagedKey {
        val options = SignumKeyOptions.decode(request.providerOptions)
        validateRequest(request.spec, request.usages, options.policy)
        val alias = options.alias ?: request.id.value
        val handle = backend.create(alias, request.spec, request.usages, options.policy)
        try {
            validateHandle(handle, alias, request.spec, request.usages, options.policy)
        } catch (cause: Throwable) {
            try {
                withContext(NonCancellable) { backend.delete(alias) }
            } catch (cleanupFailure: Throwable) {
                cause.addSuppressed(cleanupFailure)
            }
            throw cause
        }
        val providerData = SignumStoredKeyData(
            alias = alias,
            policy = options.policy,
            protectionLevel = handle.protectionLevel,
            attestation = handle.attestation,
        )
        return key(
            stored = StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = request.id,
                spec = request.spec,
                usages = request.usages,
                provider = id,
                providerSchemaVersion = PROVIDER_SCHEMA_VERSION,
                providerData = providerData.encode(),
                publicKey = handle.publicKey,
                metadata = request.metadata,
            ),
            providerData = providerData,
            handle = handle,
        )
    }

    suspend fun storedKeyForExisting(
        id: KeyId,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        alias: String,
        policy: SignumKeyPolicy = SignumKeyPolicy(),
        metadata: Map<String, String> = emptyMap(),
    ): StoredKey.Managed {
        require(alias.isNotBlank()) { "Signum key alias cannot be blank" }
        validateRequest(spec, usages, policy)
        val handle = backend.load(alias, spec, usages, policy)
            ?: throw IllegalStateException("Signum key alias does not exist: $alias")
        validateHandle(handle, alias, spec, usages, policy)
        val providerData = SignumStoredKeyData(
            alias = alias,
            policy = policy,
            protectionLevel = handle.protectionLevel,
            attestation = handle.attestation,
        )
        return StoredKey.Managed(
            version = StoredKey.CURRENT_VERSION,
            id = id,
            spec = spec,
            usages = usages,
            provider = this.id,
            providerSchemaVersion = PROVIDER_SCHEMA_VERSION,
            providerData = providerData.encode(),
            publicKey = handle.publicKey,
            metadata = metadata,
        )
    }

    override suspend fun restore(stored: StoredKey.Managed): ManagedKey = restoreSignumKey(stored)

    suspend fun restoreSignumKey(stored: StoredKey.Managed): SignumManagedKey {
        require(stored.provider == id) { "Stored key belongs to a different provider" }
        require(stored.providerSchemaVersion == PROVIDER_SCHEMA_VERSION) {
            "Unsupported Signum provider schema: ${stored.providerSchemaVersion}"
        }
        val publicKey = stored.publicKey as? EncodedKey.SpkiDer
            ?: throw IllegalArgumentException("Stored Signum key is missing its SPKI public key")
        val providerData = SignumStoredKeyData.decode(stored.providerData)
        validateRequest(stored.spec, stored.usages, providerData.policy)
        val handle = backend.load(providerData.alias, stored.spec, stored.usages, providerData.policy)
            ?: throw IllegalStateException("Signum key alias does not exist: ${providerData.alias}")
        validateHandle(handle, providerData.alias, stored.spec, stored.usages, providerData.policy)
        require(handle.publicKey.data == publicKey.data) { "Signum key public key changed after restore" }
        require(handle.protectionLevel == providerData.protectionLevel) {
            "Signum key protection level changed after restore"
        }
        require(handle.attestation == providerData.attestation) { "Signum key attestation changed after restore" }
        return key(stored, providerData, handle)
    }

    suspend fun delete(stored: StoredKey.Managed, expectedAlias: String? = null): KeyDeletionResult {
        require(stored.provider == id) { "Stored key belongs to a different provider" }
        require(stored.providerSchemaVersion == PROVIDER_SCHEMA_VERSION) {
            "Unsupported Signum provider schema: ${stored.providerSchemaVersion}"
        }
        val providerData = SignumStoredKeyData.decode(stored.providerData)
        expectedAlias?.let {
            require(providerData.alias == it) { "Stored Signum key alias does not match the expected alias" }
        }
        backend.delete(providerData.alias)
        return KeyDeletionResult.Deleted
    }

    private fun key(
        stored: StoredKey.Managed,
        providerData: SignumStoredKeyData,
        handle: SignumPlatformKey,
    ): SignumManagedKey = DefaultSignumManagedKey(stored, providerData, handle)

    private inner class DefaultSignumManagedKey(
        override val storedKey: StoredKey.Managed,
        private val providerData: SignumStoredKeyData,
        private val handle: SignumPlatformKey,
    ) : SignumManagedKey {
        override val protectionLevel: SignumProtectionLevel = providerData.protectionLevel
        override val attestation: SignumKeyAttestation? = providerData.attestation
        private val signatureAlgorithms = handle.signatureAlgorithms.expandEcdsaEncodings()
        private val advertisedSignatureAlgorithms = signatureAlgorithms.takeIf {
            KeyUsage.SIGN in storedKey.usages || KeyUsage.VERIFY in storedKey.usages
        }.orEmpty()
        private val advertisedKeyAgreementAlgorithms = handle.keyAgreementAlgorithms.takeIf {
            KeyUsage.KEY_AGREEMENT in storedKey.usages
        }.orEmpty()

        override val capabilities = KeyCapabilities(
            signer = KeyUsage.SIGN.takeIf(storedKey.usages::contains)?.let {
                Signer { data, algorithm -> sign(data, algorithm) }
            },
            verifier = KeyUsage.VERIFY.takeIf(storedKey.usages::contains)?.let {
                Verifier { data, signature, algorithm -> verify(data, signature, algorithm) }
            },
            keyAgreement = KeyUsage.KEY_AGREEMENT.takeIf(storedKey.usages::contains)?.let {
                KeyAgreement { peer, algorithm ->
                    require(algorithm in advertisedKeyAgreementAlgorithms) {
                        "Unsupported Signum key-agreement algorithm"
                    }
                    handle.generateSharedSecret(peer, algorithm)
                }
            },
            deleter = KeyDeleter { delete(storedKey) },
            publicKeyExporter = PublicKeyExporter { handle.publicKey },
            signatureAlgorithms = advertisedSignatureAlgorithms,
            keyAgreementAlgorithms = advertisedKeyAgreementAlgorithms,
            supportsSignatureAlgorithm = { it in advertisedSignatureAlgorithms },
            supportsKeyAgreementAlgorithm = { it in advertisedKeyAgreementAlgorithms },
        )

        private suspend fun sign(data: ByteArray, algorithm: SignatureAlgorithm): ByteArray {
            require(algorithm in signatureAlgorithms) { "Unsupported Signum signature algorithm" }
            val nativeAlgorithm = algorithm.nativeEncoding()
            val signature = handle.sign(data, nativeAlgorithm)
            return if (algorithm is SignatureAlgorithm.Ecdsa && algorithm.encoding == EcdsaSignatureEncoding.DER) {
                EcdsaSignatureCodec.p1363ToDer(signature, storedKey.spec.ecComponentSize())
            } else signature
        }

        private suspend fun verify(data: ByteArray, signature: ByteArray, algorithm: SignatureAlgorithm): Boolean {
            require(algorithm in signatureAlgorithms) { "Unsupported Signum signature algorithm" }
            val nativeSignature = if (algorithm is SignatureAlgorithm.Ecdsa && algorithm.encoding == EcdsaSignatureEncoding.DER) {
                EcdsaSignatureCodec.derToP1363(signature, storedKey.spec.ecComponentSize())
            } else signature
            return handle.verify(data, nativeSignature, algorithm.nativeEncoding())
        }
    }

    private fun validateHandle(
        handle: SignumPlatformKey,
        alias: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy,
    ) {
        require(handle.alias == alias) { "Signum backend changed the key alias" }
        require(handle.spec == spec) { "Signum backend changed the key specification" }
        require(handle.publicKey.data.size > 0) { "Signum backend returned an empty public key" }
        if (KeyUsage.KEY_AGREEMENT in usages) {
            require(KeyAgreementAlgorithm.Ecdh in handle.keyAgreementAlgorithms) {
                "Signum backend did not provide requested ECDH capability"
            }
        }
        if (policy.hardware == SignumHardwarePolicy.REQUIRED) {
            require(handle.protectionLevel == SignumProtectionLevel.HARDWARE && handle.attestation != null) {
                "Signum backend did not provide attested hardware protection"
            }
        }
        if (policy.attestationChallenge != null) {
            requireNotNull(handle.attestation) { "Signum backend did not return required attestation" }
        }
    }

    private fun validateRequest(spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy) {
        require(usages.isNotEmpty()) { "Signum key usages cannot be empty" }
        require(usages.all { it == KeyUsage.SIGN || it == KeyUsage.VERIFY || it == KeyUsage.KEY_AGREEMENT }) {
            "Signum keys support SIGN, VERIFY, and KEY_AGREEMENT usages"
        }
        val keyAgreement = KeyUsage.KEY_AGREEMENT in usages
        require(!keyAgreement || spec is KeySpec.Ec) { "Signum ECDH requires an EC key" }
        require(keyAgreement == policy.keyAgreement) {
            "Signum KEY_AGREEMENT usage and keyAgreement policy must be enabled together"
        }
        require(backend.supports(spec, usages, policy)) {
            "Signum backend ${backend.id.value} does not support the requested key and policy"
        }
    }

    companion object {
        private const val PROVIDER_SCHEMA_VERSION = 1
    }
}

interface SignumManagedKey : ManagedKey {
    val protectionLevel: SignumProtectionLevel
    val attestation: SignumKeyAttestation?
}

@Serializable
private data class SignumStoredKeyData(
    val alias: String,
    val policy: SignumKeyPolicy,
    val protectionLevel: SignumProtectionLevel,
    val attestation: SignumKeyAttestation?,
) {
    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        fun decode(data: BinaryData): SignumStoredKeyData = json.decodeFromString(data.toByteArray().decodeToString())
    }
}

private fun Set<SignatureAlgorithm>.expandEcdsaEncodings(): Set<SignatureAlgorithm> = flatMapTo(mutableSetOf()) {
    if (it is SignatureAlgorithm.Ecdsa) {
        EcdsaSignatureEncoding.entries.map { encoding -> it.copy(encoding = encoding) }
    } else listOf(it)
}

private fun SignatureAlgorithm.nativeEncoding(): SignatureAlgorithm =
    if (this is SignatureAlgorithm.Ecdsa) copy(encoding = EcdsaSignatureEncoding.IEEE_P1363) else this

private fun KeySpec.ecComponentSize(): Int = when ((this as? KeySpec.Ec)?.curve) {
    EcCurve.P256, EcCurve.SECP256K1 -> 32
    EcCurve.P384 -> 48
    EcCurve.P521 -> 66
    else -> throw IllegalArgumentException("Signum ECDSA requires a supported EC key")
}
