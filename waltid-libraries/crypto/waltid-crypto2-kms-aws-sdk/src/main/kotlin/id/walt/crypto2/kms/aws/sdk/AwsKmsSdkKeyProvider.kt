package id.walt.crypto2.kms.aws.sdk

import aws.sdk.kotlin.runtime.AwsServiceException
import aws.sdk.kotlin.services.kms.model.CreateAliasRequest
import aws.sdk.kotlin.services.kms.model.CreateKeyRequest
import aws.sdk.kotlin.services.kms.model.DecryptRequest
import aws.sdk.kotlin.services.kms.model.EncryptRequest
import aws.sdk.kotlin.services.kms.model.EncryptionAlgorithmSpec
import aws.sdk.kotlin.services.kms.model.GetPublicKeyRequest
import aws.sdk.kotlin.services.kms.model.KeySpec as AwsKeySpec
import aws.sdk.kotlin.services.kms.model.KeyUsageType
import aws.sdk.kotlin.services.kms.model.MessageType
import aws.sdk.kotlin.services.kms.model.ReplicateKeyRequest
import aws.sdk.kotlin.services.kms.model.ScheduleKeyDeletionRequest
import aws.sdk.kotlin.services.kms.model.SignRequest
import aws.sdk.kotlin.services.kms.model.SigningAlgorithmSpec
import aws.sdk.kotlin.services.kms.model.Tag
import aws.sdk.kotlin.services.kms.model.VerifyRequest
import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.DigestValue
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.algorithms.outputSizeBytes
import id.walt.crypto2.keys.AsymmetricCiphertext
import id.walt.crypto2.keys.Decryptor
import id.walt.crypto2.keys.DigestSigner
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Encryptor
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyDeleter
import id.walt.crypto2.keys.KeyDeletionResult
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.PublicKeyExporter
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.Verifier
import id.walt.crypto2.kms.digest
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.ManagedKeyProvider
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.time.Instant

class AwsKmsSdkKeyProvider(
    private val clientFactory: AwsKmsSdkClientFactory = DefaultAwsKmsSdkClientFactory(),
    private val failoverPolicy: AwsKmsSdkFailoverPolicy = DefaultAwsKmsSdkFailoverPolicy,
) : ManagedKeyProvider {
    override val id: ProviderId = ID

    override suspend fun generate(request: GenerateManagedKeyRequest): ManagedKey {
        val options = AwsKmsSdkOptions.decode(request.providerOptions)
        val signing = request.usages.all { it == KeyUsage.SIGN || it == KeyUsage.VERIFY }
        val encryption = request.usages.all { it == KeyUsage.ENCRYPT || it == KeyUsage.DECRYPT }
        require(request.usages.isNotEmpty() && (signing || encryption)) {
            "AWS KMS keys cannot mix signing and encryption usages"
        }
        if (encryption) require(request.spec is KeySpec.Rsa) { "AWS asymmetric encryption requires an RSA key" }
        val created = withClient(options.primaryRegion) { client ->
            client.createKey(
                CreateKeyRequest {
                    keySpec = AwsKeySpec.fromValue(request.spec.toAwsKeySpec())
                    keyUsage = if (signing) KeyUsageType.SignVerify else KeyUsageType.EncryptDecrypt
                    description = options.description
                    multiRegion = options.multiRegion
                    tags = options.tags.map { (key, value) -> Tag { tagKey = key; tagValue = value } }
                }
            )
        }
        val remoteKeyId = requireNotNull(created.keyMetadata?.keyId) { "AWS KMS did not return a key ID" }
        require(created.keyMetadata?.keySpec?.value == request.spec.toAwsKeySpec()) {
            "AWS KMS created a key with an unexpected specification"
        }
        try {
            options.alias?.let { alias ->
                withClient(options.primaryRegion) { client ->
                    client.createAlias(
                        CreateAliasRequest {
                            aliasName = alias.takeIf { it.startsWith("alias/") } ?: "alias/$alias"
                            targetKeyId = remoteKeyId
                        }
                    )
                }
            }
            options.replicaRegions.forEach { region ->
                withClient(options.primaryRegion) { client ->
                    client.replicateKey(
                        ReplicateKeyRequest {
                            keyId = remoteKeyId
                            replicaRegion = region
                            description = options.description
                            tags = options.tags.map { (key, value) -> Tag { tagKey = key; tagValue = value } }
                        }
                    )
                }
            }
        } catch (cause: Throwable) {
            try {
                withContext(NonCancellable) { scheduleDeletion(options.primaryRegion, remoteKeyId) }
            } catch (rollbackFailure: Throwable) {
                cause.addSuppressed(rollbackFailure)
            }
            throw cause
        }
        val publicKey = withClient(options.primaryRegion) { client ->
            requireNotNull(client.getPublicKey(GetPublicKeyRequest { keyId = remoteKeyId }).publicKey) {
                "AWS KMS did not return a public key"
            }
        }
        val storedData = AwsSdkStoredKeyData(options.copy(tags = emptyMap()), remoteKeyId)
        return key(
            StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = request.id,
                spec = request.spec,
                usages = request.usages,
                provider = id,
                providerSchemaVersion = PROVIDER_SCHEMA_VERSION,
                providerData = storedData.encode(),
                publicKey = EncodedKey.SpkiDer(BinaryData(publicKey)),
                metadata = request.metadata,
            ),
            storedData,
        )
    }

    override suspend fun restore(stored: StoredKey.Managed): ManagedKey {
        require(stored.provider == id) { "Stored key belongs to a different provider" }
        require(stored.providerSchemaVersion == PROVIDER_SCHEMA_VERSION) {
            "Unsupported AWS SDK provider schema: ${stored.providerSchemaVersion}"
        }
        require(stored.publicKey is EncodedKey.SpkiDer) { "Stored AWS SDK key is missing its SPKI public key" }
        val data = AwsSdkStoredKeyData.decode(stored.providerData)
        require(data.remoteKeyId.isNotBlank()) { "Stored AWS key ID cannot be blank" }
        stored.spec.toAwsKeySpec()
        return key(stored, data)
    }

    private fun key(stored: StoredKey.Managed, data: AwsSdkStoredKeyData): ManagedKey = AwsSdkKey(stored, data)

    private inner class AwsSdkKey(
        override val storedKey: StoredKey.Managed,
        private val data: AwsSdkStoredKeyData,
    ) : ManagedKey {
        private val signatureAlgorithms = storedKey.spec.awsSignatureAlgorithms()
        private val encryptionAlgorithms = storedKey.spec.awsEncryptionAlgorithms()

        override val capabilities = KeyCapabilities(
            signer = KeyUsage.SIGN.takeIf(storedKey.usages::contains)?.let {
                Signer { message, algorithm -> signDigest(digest(message, algorithm.digestAlgorithm()), algorithm) }
            },
            digestSigner = KeyUsage.SIGN.takeIf(storedKey.usages::contains)?.let {
                DigestSigner { value, algorithm -> signDigest(value, algorithm) }
            },
            verifier = KeyUsage.VERIFY.takeIf(storedKey.usages::contains)?.let {
                Verifier { message, signature, algorithm -> verify(message, signature, algorithm) }
            },
            encryptor = KeyUsage.ENCRYPT.takeIf(storedKey.usages::contains)?.let {
                Encryptor { plaintext, algorithm, associatedData -> encrypt(plaintext, algorithm, associatedData) }
            },
            decryptor = KeyUsage.DECRYPT.takeIf(storedKey.usages::contains)?.let {
                Decryptor { ciphertext, associatedData -> decrypt(ciphertext, associatedData) }
            },
            deleter = KeyDeleter { delete() },
            publicKeyExporter = PublicKeyExporter { requireNotNull(storedKey.publicKey) },
            signatureAlgorithms = signatureAlgorithms,
            encryptionAlgorithms = encryptionAlgorithms,
            supportsSignatureAlgorithm = { it in signatureAlgorithms },
            supportsEncryptionAlgorithm = { it in encryptionAlgorithms },
        )

        private suspend fun signDigest(value: DigestValue, algorithm: SignatureAlgorithm): ByteArray {
            require(algorithm in signatureAlgorithms) { "Unsupported AWS SDK signature algorithm" }
            require(value.algorithm == algorithm.digestAlgorithm()) { "Digest algorithm does not match signature algorithm" }
            val signature = executeWithFailover(data.options) { client ->
                requireNotNull(
                    client.sign(
                        SignRequest {
                            keyId = data.remoteKeyId
                            message = value.value.toByteArray()
                            messageType = MessageType.Digest
                            signingAlgorithm = SigningAlgorithmSpec.fromValue(algorithm.toAwsAlgorithm())
                        }
                    ).signature
                ) { "AWS KMS did not return a signature" }
            }
            return if (algorithm is SignatureAlgorithm.Ecdsa && algorithm.encoding == EcdsaSignatureEncoding.IEEE_P1363) {
                EcdsaSignatureCodec.derToP1363(signature, storedKey.spec.ecComponentSize())
            } else signature
        }

        private suspend fun verify(message: ByteArray, signature: ByteArray, algorithm: SignatureAlgorithm): Boolean {
            require(algorithm in signatureAlgorithms) { "Unsupported AWS SDK signature algorithm" }
            val value = digest(message, algorithm.digestAlgorithm())
            val awsSignature = if (algorithm is SignatureAlgorithm.Ecdsa && algorithm.encoding == EcdsaSignatureEncoding.IEEE_P1363) {
                EcdsaSignatureCodec.p1363ToDer(signature, storedKey.spec.ecComponentSize())
            } else signature
            return executeWithFailover(data.options) { client ->
                client.verify(
                    VerifyRequest {
                        keyId = data.remoteKeyId
                        this.message = value.value.toByteArray()
                        messageType = MessageType.Digest
                        this.signature = awsSignature
                        signingAlgorithm = SigningAlgorithmSpec.fromValue(algorithm.toAwsAlgorithm())
                    }
                ).signatureValid == true
            }
        }

        private suspend fun encrypt(
            plaintext: ByteArray,
            algorithm: AsymmetricEncryptionAlgorithm,
            associatedData: ByteArray?,
        ): AsymmetricCiphertext {
            require(associatedData == null) { "AWS RSA encryption does not support associated data" }
            require(algorithm in encryptionAlgorithms) { "Unsupported AWS SDK encryption algorithm" }
            val blob = executeWithFailover(data.options) { client ->
                requireNotNull(
                    client.encrypt(
                        EncryptRequest {
                            keyId = data.remoteKeyId
                            this.plaintext = plaintext
                            encryptionAlgorithm = EncryptionAlgorithmSpec.fromValue(algorithm.toAwsAlgorithm())
                        }
                    ).ciphertextBlob
                ) { "AWS KMS did not return ciphertext" }
            }
            return AsymmetricCiphertext.Opaque(
                algorithm = algorithm,
                provider = this@AwsKmsSdkKeyProvider.id,
                keyId = storedKey.id,
                blob = BinaryData(blob),
            )
        }

        private suspend fun decrypt(ciphertext: AsymmetricCiphertext, associatedData: ByteArray?): ByteArray {
            require(associatedData == null) { "AWS RSA decryption does not support associated data" }
            val opaque = ciphertext as? AsymmetricCiphertext.Opaque
                ?: throw IllegalArgumentException("AWS requires provider-opaque ciphertext")
            require(opaque.provider == this@AwsKmsSdkKeyProvider.id && opaque.keyId == storedKey.id) {
                "Ciphertext belongs to a different provider or key"
            }
            require(opaque.algorithm in encryptionAlgorithms) { "Unsupported AWS SDK encryption algorithm" }
            return executeWithFailover(data.options) { client ->
                requireNotNull(
                    client.decrypt(
                        DecryptRequest {
                            keyId = data.remoteKeyId
                            ciphertextBlob = opaque.blob.toByteArray()
                            encryptionAlgorithm = EncryptionAlgorithmSpec.fromValue(opaque.algorithm.toAwsAlgorithm())
                        }
                    ).plaintext
                ) { "AWS KMS did not return plaintext" }
            }
        }

        private suspend fun delete(): KeyDeletionResult {
            val dates = data.options.replicaRegions.map { scheduleDeletion(it, data.remoteKeyId) } +
                scheduleDeletion(data.options.primaryRegion, data.remoteKeyId)
            return KeyDeletionResult.Scheduled(dates.max())
        }
    }

    private suspend fun scheduleDeletion(region: String, keyId: String): Instant = withClient(region) { client ->
        val date = client.scheduleKeyDeletion(
            ScheduleKeyDeletionRequest {
                this.keyId = keyId
                pendingWindowInDays = 7
            }
        ).deletionDate ?: error("AWS KMS did not return a deletion date")
        Instant.parse(date.toString())
    }

    private suspend fun <T> executeWithFailover(
        options: AwsKmsSdkOptions,
        operation: suspend (AwsKmsSdkClient) -> T,
    ): T {
        var lastFailure: Throwable? = null
        options.operationRegions().forEachIndexed { index, region ->
            try {
                return withClient(region, operation)
            } catch (cause: Throwable) {
                if (cause is CancellationException) throw cause
                lastFailure = cause
                if (index == options.operationRegions().lastIndex || !failoverPolicy.shouldFailover(cause)) throw cause
            }
        }
        throw requireNotNull(lastFailure)
    }

    private suspend fun <T> withClient(region: String, block: suspend (AwsKmsSdkClient) -> T): T =
        clientFactory.create(region).use { block(it) }

    companion object {
        val ID = ProviderId("aws-kms-sdk")
        private const val PROVIDER_SCHEMA_VERSION = 1
    }
}

fun interface AwsKmsSdkFailoverPolicy {
    fun shouldFailover(cause: Throwable): Boolean
}

object DefaultAwsKmsSdkFailoverPolicy : AwsKmsSdkFailoverPolicy {
    override fun shouldFailover(cause: Throwable): Boolean = when (cause) {
        is CancellationException -> false
        is IOException -> true
        is AwsServiceException -> cause.sdkErrorMetadata.errorCode in transientAwsErrorCodes ||
            cause.cause?.let(::shouldFailover) == true
        else -> cause.cause?.let(::shouldFailover) == true
    }

    private val transientAwsErrorCodes = setOf(
        "DependencyTimeoutException",
        "KMSInternalException",
        "ThrottlingException",
    )
}

@Serializable
private data class AwsSdkStoredKeyData(val options: AwsKmsSdkOptions, val remoteKeyId: String) {
    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        fun decode(data: BinaryData): AwsSdkStoredKeyData = json.decodeFromString(data.toByteArray().decodeToString())
    }
}

private fun KeySpec.toAwsKeySpec(): String = when (this) {
    KeySpec.Ec(EcCurve.P256) -> "ECC_NIST_P256"
    KeySpec.Ec(EcCurve.P384) -> "ECC_NIST_P384"
    KeySpec.Ec(EcCurve.P521) -> "ECC_NIST_P521"
    KeySpec.Ec(EcCurve.SECP256K1) -> "ECC_SECG_P256K1"
    is KeySpec.Rsa -> when (bits) {
        2048, 3072, 4096 -> "RSA_$bits"
        else -> throw IllegalArgumentException("Unsupported AWS RSA size: $bits")
    }
    else -> throw IllegalArgumentException("Unsupported AWS key specification: $this")
}

private fun KeySpec.awsSignatureAlgorithms(): Set<SignatureAlgorithm> = when (this) {
    is KeySpec.Ec -> {
        val digest = when (curve) {
            EcCurve.P256, EcCurve.SECP256K1 -> DigestAlgorithm.SHA_256
            EcCurve.P384 -> DigestAlgorithm.SHA_384
            EcCurve.P521 -> DigestAlgorithm.SHA_512
            else -> throw IllegalArgumentException("Unsupported AWS EC curve: ${curve.name}")
        }
        EcdsaSignatureEncoding.entries.mapTo(mutableSetOf()) { SignatureAlgorithm.Ecdsa(digest, it) }
    }
    is KeySpec.Rsa -> listOf(DigestAlgorithm.SHA_256, DigestAlgorithm.SHA_384, DigestAlgorithm.SHA_512)
        .flatMap { digest ->
            listOf(
                SignatureAlgorithm.RsaPkcs1(digest),
                SignatureAlgorithm.RsaPss(digest, saltLengthBytes = requireNotNull(digest.outputSizeBytes)),
            )
        }.toSet()
    else -> emptySet()
}

private fun KeySpec.awsEncryptionAlgorithms(): Set<AsymmetricEncryptionAlgorithm> = when (this) {
    is KeySpec.Rsa -> setOf(
        AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_1),
        AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_256),
    )
    else -> emptySet()
}

private fun SignatureAlgorithm.digestAlgorithm(): DigestAlgorithm = when (this) {
    is SignatureAlgorithm.Ecdsa -> digest
    is SignatureAlgorithm.RsaPkcs1 -> digest
    is SignatureAlgorithm.RsaPss -> digest.also {
        require(mgfDigest == digest && saltLengthBytes == digest.outputSizeBytes) {
            "AWS RSA-PSS requires matching digest, MGF digest, and salt length"
        }
    }
    else -> throw IllegalArgumentException("Unsupported AWS signature algorithm: $this")
}

private fun SignatureAlgorithm.toAwsAlgorithm(): String = when (this) {
    is SignatureAlgorithm.Ecdsa -> "ECDSA_SHA_${digest.bits()}"
    is SignatureAlgorithm.RsaPkcs1 -> "RSASSA_PKCS1_V1_5_SHA_${digest.bits()}"
    is SignatureAlgorithm.RsaPss -> "RSASSA_PSS_SHA_${digest.bits()}"
    else -> throw IllegalArgumentException("Unsupported AWS signature algorithm: $this")
}

private fun AsymmetricEncryptionAlgorithm.toAwsAlgorithm(): String = when (this) {
    is AsymmetricEncryptionAlgorithm.RsaOaep -> {
        require(mgfDigest == digest) { "AWS RSA-OAEP MGF digest must match the message digest" }
        when (digest) {
            DigestAlgorithm.SHA_1 -> "RSAES_OAEP_SHA_1"
            DigestAlgorithm.SHA_256 -> "RSAES_OAEP_SHA_256"
            else -> throw IllegalArgumentException("Unsupported AWS RSA-OAEP digest: ${digest.name}")
        }
    }
    else -> throw IllegalArgumentException("Unsupported AWS encryption algorithm: $this")
}

private fun DigestAlgorithm.bits(): Int = when (this) {
    DigestAlgorithm.SHA_256 -> 256
    DigestAlgorithm.SHA_384 -> 384
    DigestAlgorithm.SHA_512 -> 512
    else -> throw IllegalArgumentException("Unsupported AWS digest: $name")
}

private fun KeySpec.ecComponentSize(): Int = when ((this as? KeySpec.Ec)?.curve) {
    EcCurve.P256, EcCurve.SECP256K1 -> 32
    EcCurve.P384 -> 48
    EcCurve.P521 -> 66
    else -> throw IllegalArgumentException("AWS ECDSA requires a supported EC key")
}
