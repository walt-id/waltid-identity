package id.walt.crypto2.kms.aws

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
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.PublicKeyExporter
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.keys.Verifier
import id.walt.crypto2.kms.KmsProviderException
import id.walt.crypto2.kms.digest
import id.walt.crypto2.kms.executeJson
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.ManagedKeyProvider
import id.walt.crypto2.serialization.BinaryData
import io.ktor.client.HttpClient
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

class AwsKmsKeyProvider(
    private val client: HttpClient,
    private val credentialResolver: AwsCredentialResolver,
    private val now: () -> Instant = { Clock.System.now() },
) : ManagedKeyProvider {
    override val id: ProviderId = ID

    override suspend fun generate(request: GenerateManagedKeyRequest): ManagedKey {
        val options = AwsKmsOptions.decode(request.providerOptions)
        val signing = request.usages.all { it == KeyUsage.SIGN || it == KeyUsage.VERIFY }
        val encryption = request.usages.all { it == KeyUsage.ENCRYPT || it == KeyUsage.DECRYPT }
        require(request.usages.isNotEmpty() && (signing || encryption)) {
            "AWS KMS keys cannot mix signing and encryption usages"
        }
        if (encryption) require(request.spec is KeySpec.Rsa) { "AWS asymmetric encryption requires an RSA key" }
        val create = request(
            options,
            "TrentService.CreateKey",
            buildJsonObject {
                put("KeySpec", request.spec.toAwsKeySpec())
                put("KeyUsage", if (signing) "SIGN_VERIFY" else "ENCRYPT_DECRYPT")
            },
        )
        val remoteKeyId = create.requiredObject("KeyMetadata").requiredString("KeyId")
        val public = request(
            options,
            "TrentService.GetPublicKey",
            buildJsonObject { put("KeyId", remoteKeyId) },
        )
        require(public.requiredString("KeySpec") == request.spec.toAwsKeySpec()) {
            "AWS returned a public key with an unexpected specification"
        }
        val publicKey = EncodedKey.SpkiDer(BinaryData(Base64.Default.decode(public.requiredString("PublicKey"))))
        publicKey.toPublicJwk(request.spec)
        val providerData = AwsStoredKeyData(options, remoteKeyId)
        return key(
            StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = request.id,
                spec = request.spec,
                usages = request.usages,
                provider = id,
                providerSchemaVersion = PROVIDER_SCHEMA_VERSION,
                providerData = providerData.encode(),
                publicKey = publicKey,
                metadata = request.metadata,
            ),
            providerData,
        )
    }

    override suspend fun restore(stored: StoredKey.Managed): ManagedKey {
        require(stored.provider == id) { "Stored key belongs to a different provider" }
        require(stored.providerSchemaVersion == PROVIDER_SCHEMA_VERSION) {
            "Unsupported AWS provider schema: ${stored.providerSchemaVersion}"
        }
        val publicKey = stored.publicKey as? EncodedKey.SpkiDer
            ?: throw IllegalArgumentException("Stored AWS key is missing its SPKI public key")
        publicKey.toPublicJwk(stored.spec)
        val providerData = AwsStoredKeyData.decode(stored.providerData)
        require(providerData.remoteKeyId.isNotBlank()) { "Stored AWS key ID cannot be blank" }
        stored.spec.toAwsKeySpec()
        return key(stored, providerData)
    }

    private fun key(stored: StoredKey.Managed, data: AwsStoredKeyData): ManagedKey = AwsKmsKey(stored, data)

    private inner class AwsKmsKey(
        override val storedKey: StoredKey.Managed,
        private val data: AwsStoredKeyData,
    ) : ManagedKey {
        private val signatureAlgorithms = storedKey.spec.awsSignatureAlgorithms()
        private val encryptionAlgorithms = storedKey.spec.awsEncryptionAlgorithms()
        private val advertisedSignatureAlgorithms = signatureAlgorithms.takeIf {
            KeyUsage.SIGN in storedKey.usages || KeyUsage.VERIFY in storedKey.usages
        }.orEmpty()
        private val advertisedEncryptionAlgorithms = encryptionAlgorithms.takeIf {
            KeyUsage.ENCRYPT in storedKey.usages || KeyUsage.DECRYPT in storedKey.usages
        }.orEmpty()

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
            signatureAlgorithms = advertisedSignatureAlgorithms,
            encryptionAlgorithms = advertisedEncryptionAlgorithms,
            supportsSignatureAlgorithm = { it in advertisedSignatureAlgorithms },
            supportsEncryptionAlgorithm = { it in advertisedEncryptionAlgorithms },
        )

        private suspend fun signDigest(value: DigestValue, algorithm: SignatureAlgorithm): ByteArray {
            require(algorithm in signatureAlgorithms) { "Unsupported AWS signature algorithm" }
            require(value.algorithm == algorithm.digestAlgorithm()) { "Digest algorithm does not match signature algorithm" }
            val derOrRsa = Base64.Default.decode(
                request(
                    data.options,
                    "TrentService.Sign",
                    buildJsonObject {
                        put("KeyId", data.remoteKeyId)
                        put("Message", Base64.Default.encode(value.value.toByteArray()))
                        put("MessageType", "DIGEST")
                        put("SigningAlgorithm", algorithm.toAwsAlgorithm())
                    },
                ).requiredString("Signature")
            )
            return if (algorithm is SignatureAlgorithm.Ecdsa && algorithm.encoding == EcdsaSignatureEncoding.IEEE_P1363) {
                EcdsaSignatureCodec.derToP1363(derOrRsa, storedKey.spec.ecComponentSize())
            } else {
                derOrRsa
            }
        }

        private suspend fun verify(message: ByteArray, signature: ByteArray, algorithm: SignatureAlgorithm): Boolean {
            require(algorithm in signatureAlgorithms) { "Unsupported AWS signature algorithm" }
            val value = digest(message, algorithm.digestAlgorithm())
            val awsSignature = if (algorithm is SignatureAlgorithm.Ecdsa && algorithm.encoding == EcdsaSignatureEncoding.IEEE_P1363) {
                EcdsaSignatureCodec.p1363ToDer(signature, storedKey.spec.ecComponentSize())
            } else {
                signature
            }
            return request(
                data.options,
                "TrentService.Verify",
                buildJsonObject {
                    put("KeyId", data.remoteKeyId)
                    put("Message", Base64.Default.encode(value.value.toByteArray()))
                    put("MessageType", "DIGEST")
                    put("Signature", Base64.Default.encode(awsSignature))
                    put("SigningAlgorithm", algorithm.toAwsAlgorithm())
                },
            ).requiredBoolean("SignatureValid")
        }

        private suspend fun encrypt(
            plaintext: ByteArray,
            algorithm: AsymmetricEncryptionAlgorithm,
            associatedData: ByteArray?,
        ): AsymmetricCiphertext {
            require(associatedData == null) { "AWS RSA encryption does not support associated data" }
            require(algorithm in encryptionAlgorithms) { "Unsupported AWS encryption algorithm" }
            val ciphertext = Base64.Default.decode(
                request(
                    data.options,
                    "TrentService.Encrypt",
                    buildJsonObject {
                        put("KeyId", data.remoteKeyId)
                        put("Plaintext", Base64.Default.encode(plaintext))
                        put("EncryptionAlgorithm", algorithm.toAwsAlgorithm())
                    },
                ).requiredString("CiphertextBlob")
            )
            return AsymmetricCiphertext.Opaque(
                algorithm = algorithm,
                provider = this@AwsKmsKeyProvider.id,
                keyId = storedKey.id,
                blob = BinaryData(ciphertext),
            )
        }

        private suspend fun decrypt(ciphertext: AsymmetricCiphertext, associatedData: ByteArray?): ByteArray {
            require(associatedData == null) { "AWS RSA decryption does not support associated data" }
            val opaque = ciphertext as? AsymmetricCiphertext.Opaque
                ?: throw IllegalArgumentException("AWS requires provider-opaque ciphertext")
            require(opaque.provider == this@AwsKmsKeyProvider.id && opaque.keyId == storedKey.id) {
                "Ciphertext belongs to a different provider or key"
            }
            require(opaque.algorithm in encryptionAlgorithms) { "Unsupported AWS encryption algorithm" }
            return Base64.Default.decode(
                request(
                    data.options,
                    "TrentService.Decrypt",
                    buildJsonObject {
                        put("KeyId", data.remoteKeyId)
                        put("CiphertextBlob", Base64.Default.encode(opaque.blob.toByteArray()))
                        put("EncryptionAlgorithm", opaque.algorithm.toAwsAlgorithm())
                    },
                ).requiredString("Plaintext")
            )
        }

        private suspend fun delete(): KeyDeletionResult {
            val response = request(
                data.options,
                "TrentService.ScheduleKeyDeletion",
                buildJsonObject {
                    put("KeyId", data.remoteKeyId)
                    put("PendingWindowInDays", 7)
                },
            )
            val deletionTime = response["DeletionDate"]?.jsonPrimitive?.double
                ?.let { Instant.fromEpochMilliseconds((it * 1000).toLong()) }
                ?: throw KmsProviderException("AWS deletion response is missing DeletionDate")
            return KeyDeletionResult.Scheduled(deletionTime)
        }
    }

    private suspend fun request(options: AwsKmsOptions, target: String, body: JsonObject): JsonObject {
        val payload = json.encodeToString(JsonObject.serializer(), body)
        val credentials = credentialResolver.resolve(options.credentialReference)
        return requireNotNull(
            client.executeJson(
                provider = "AWS KMS",
                endpoint = options.endpointUrl(),
                method = HttpMethod.Post,
                headers = awsSigV4Headers(options, credentials, payload, target, now()),
                contentType = AWS_JSON_CONTENT_TYPE,
                body = payload,
            )
        )
    }

    companion object {
        val ID = ProviderId("aws-kms-rest")

        suspend fun storedKeyForExisting(
            id: KeyId,
            spec: KeySpec,
            usages: Set<KeyUsage>,
            options: AwsKmsOptions,
            remoteKeyId: String,
            publicKey: EncodedKey.SpkiDer,
            metadata: Map<String, String> = emptyMap(),
        ): StoredKey.Managed {
            val signing = usages.all { it == KeyUsage.SIGN || it == KeyUsage.VERIFY }
            val encryption = usages.all { it == KeyUsage.ENCRYPT || it == KeyUsage.DECRYPT }
            require(usages.isNotEmpty() && (signing || encryption)) {
                "AWS KMS keys cannot mix signing and encryption usages"
            }
            if (encryption) require(spec is KeySpec.Rsa) { "AWS asymmetric encryption requires an RSA key" }
            spec.toAwsKeySpec()
            require(remoteKeyId.isNotBlank()) { "AWS remote key ID cannot be blank" }
            publicKey.toPublicJwk(spec)
            return StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = id,
                spec = spec,
                usages = usages,
                provider = ID,
                providerSchemaVersion = PROVIDER_SCHEMA_VERSION,
                providerData = AwsStoredKeyData(options, remoteKeyId).encode(),
                publicKey = publicKey,
                metadata = metadata,
            )
        }

        private const val PROVIDER_SCHEMA_VERSION = 1
        private val json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }
    }
}

@Serializable
private data class AwsStoredKeyData(val options: AwsKmsOptions, val remoteKeyId: String) {
    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        fun decode(data: BinaryData): AwsStoredKeyData = json.decodeFromString(data.toByteArray().decodeToString())
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

private fun JsonObject.requiredObject(name: String): JsonObject =
    this[name]?.jsonObject ?: throw KmsProviderException("AWS response is missing object: $name")

private fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
        ?: throw KmsProviderException("AWS response is missing string: $name")

private fun JsonObject.requiredBoolean(name: String): Boolean =
    try {
        this[name]?.jsonPrimitive?.boolean ?: throw IllegalArgumentException()
    } catch (_: IllegalArgumentException) {
        throw KmsProviderException("AWS response is missing boolean: $name")
    }
