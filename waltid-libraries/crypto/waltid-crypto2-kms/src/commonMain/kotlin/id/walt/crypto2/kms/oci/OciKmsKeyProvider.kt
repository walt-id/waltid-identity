package id.walt.crypto2.kms.oci

import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.DigestValue
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.AsymmetricCiphertext
import id.walt.crypto2.keys.Decryptor
import id.walt.crypto2.keys.DigestSigner
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Encryptor
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyDeleter
import id.walt.crypto2.keys.KeyDeletionResult
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.PublicKeyExporter
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.decodePublicKeyPem
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.keys.Verifier
import id.walt.crypto2.kms.KmsProviderException
import id.walt.crypto2.kms.digest
import id.walt.crypto2.kms.executeJson
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.ManagedKeyProvider
import id.walt.crypto2.serialization.BinaryData
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class OciKmsKeyProvider(
    private val client: HttpClient,
    private val credentialResolver: OciCredentialResolver,
    private val now: () -> Instant = { Clock.System.now() },
) : ManagedKeyProvider {
    override val id: ProviderId = ID

    override suspend fun generate(request: GenerateManagedKeyRequest): ManagedKey {
        val options = OciKmsOptions.decode(request.providerOptions)
        val allowedUsages = if (request.spec is KeySpec.Rsa) {
            setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.ENCRYPT, KeyUsage.DECRYPT)
        } else {
            setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        }
        require(request.usages.isNotEmpty() && request.usages.all(allowedUsages::contains)) {
            "OCI key usages are not supported by the requested key specification"
        }
        val created = request(
            options = options,
            endpoint = options.managementEndpoint.apiEndpoint("keys"),
            method = HttpMethod.Post,
            body = buildJsonObject {
                put("compartmentId", options.compartmentOcid)
                put("displayName", request.id.value)
                put("keyShape", buildJsonObject { request.spec.writeOciKeyShape(this) })
                put("protectionMode", options.protectionMode.name)
            },
        )
        val remoteKeyId = created.requiredString("id")
        val keyVersion = created.requiredString("currentKeyVersion")
        created["keyShape"]?.jsonObject?.validateOciKeyShape(request.spec)
        val publicKeyResponse = request(
            options = options,
            endpoint = options.managementEndpoint.apiEndpoint(
                "keys",
                remoteKeyId,
                "keyVersions",
                keyVersion,
            ),
            method = HttpMethod.Get,
        )
        val publicKey = publicKeyResponse.requiredString("publicKey").decodePublicKeyPem()
        val providerData = OciStoredKeyData(options, remoteKeyId, keyVersion)
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
            "Unsupported OCI provider schema: ${stored.providerSchemaVersion}"
        }
        require(stored.publicKey is EncodedKey.SpkiDer) { "Stored OCI key is missing its SPKI public key" }
        val providerData = OciStoredKeyData.decode(stored.providerData)
        require(providerData.remoteKeyId.isNotBlank()) { "Stored OCI key ID cannot be blank" }
        require(providerData.keyVersion.isNotBlank()) { "Stored OCI key version cannot be blank" }
        buildJsonObject { stored.spec.writeOciKeyShape(this) }
        return key(stored, providerData)
    }

    suspend fun storedKeyForExisting(
        id: KeyId,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        options: OciKmsOptions,
        remoteKeyId: String,
        metadata: Map<String, String> = emptyMap(),
    ): StoredKey.Managed {
        val allowedUsages = if (spec is KeySpec.Rsa) {
            setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.ENCRYPT, KeyUsage.DECRYPT)
        } else {
            setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        }
        require(usages.isNotEmpty() && usages.all(allowedUsages::contains)) {
            "OCI key usages are not supported by the requested key specification"
        }
        require(remoteKeyId.isNotBlank()) { "OCI remote key ID cannot be blank" }
        val remoteKey = request(
            options = options,
            endpoint = options.managementEndpoint.apiEndpoint("keys", remoteKeyId),
            method = HttpMethod.Get,
        )
        require(remoteKey.requiredString("id") == remoteKeyId) { "OCI returned an unexpected key ID" }
        remoteKey.requiredObject("keyShape").validateOciKeyShape(spec)
        val keyVersion = remoteKey.requiredString("currentKeyVersion")
        val publicKey = request(
            options = options,
            endpoint = options.managementEndpoint.apiEndpoint("keys", remoteKeyId, "keyVersions", keyVersion),
            method = HttpMethod.Get,
        ).requiredString("publicKey").decodePublicKeyPem()
        publicKey.toPublicJwk(spec)
        return StoredKey.Managed(
            version = StoredKey.CURRENT_VERSION,
            id = id,
            spec = spec,
            usages = usages,
            provider = this.id,
            providerSchemaVersion = PROVIDER_SCHEMA_VERSION,
            providerData = OciStoredKeyData(options, remoteKeyId, keyVersion).encode(),
            publicKey = publicKey,
            metadata = metadata,
        )
    }

    private fun key(stored: StoredKey.Managed, data: OciStoredKeyData): ManagedKey = OciKmsKey(stored, data)

    private inner class OciKmsKey(
        override val storedKey: StoredKey.Managed,
        private val data: OciStoredKeyData,
    ) : ManagedKey {
        private val signatureAlgorithms = storedKey.spec.ociSignatureAlgorithms()
        private val encryptionAlgorithms = storedKey.spec.ociEncryptionAlgorithms()
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
            require(algorithm in signatureAlgorithms) { "Unsupported OCI signature algorithm" }
            require(value.algorithm == algorithm.digestAlgorithm()) { "Digest algorithm does not match signature algorithm" }
            val signature = Base64.Default.decode(
                request(
                    options = data.options,
                    endpoint = data.options.cryptoEndpoint.apiEndpoint("sign"),
                    method = HttpMethod.Post,
                    body = buildJsonObject {
                        put("keyId", data.remoteKeyId)
                        put("keyVersionId", data.keyVersion)
                        put("message", Base64.Default.encode(value.value.toByteArray()))
                        put("messageType", "DIGEST")
                        put("signingAlgorithm", algorithm.toOciAlgorithm())
                    },
                ).requiredString("signature")
            )
            return if (algorithm is SignatureAlgorithm.Ecdsa && algorithm.encoding == EcdsaSignatureEncoding.IEEE_P1363) {
                EcdsaSignatureCodec.derToP1363(signature, storedKey.spec.ecComponentSize())
            } else {
                signature
            }
        }

        private suspend fun verify(message: ByteArray, signature: ByteArray, algorithm: SignatureAlgorithm): Boolean {
            require(algorithm in signatureAlgorithms) { "Unsupported OCI signature algorithm" }
            val value = digest(message, algorithm.digestAlgorithm())
            val ociSignature = if (algorithm is SignatureAlgorithm.Ecdsa && algorithm.encoding == EcdsaSignatureEncoding.IEEE_P1363) {
                EcdsaSignatureCodec.p1363ToDer(signature, storedKey.spec.ecComponentSize())
            } else {
                signature
            }
            return request(
                options = data.options,
                endpoint = data.options.cryptoEndpoint.apiEndpoint("verify"),
                method = HttpMethod.Post,
                body = buildJsonObject {
                    put("keyId", data.remoteKeyId)
                    put("keyVersionId", data.keyVersion)
                    put("message", Base64.Default.encode(value.value.toByteArray()))
                    put("messageType", "DIGEST")
                    put("signature", Base64.Default.encode(ociSignature))
                    put("signingAlgorithm", algorithm.toOciAlgorithm())
                },
            ).requiredBoolean("isSignatureValid")
        }

        private suspend fun encrypt(
            plaintext: ByteArray,
            algorithm: AsymmetricEncryptionAlgorithm,
            associatedData: ByteArray?,
        ): AsymmetricCiphertext {
            require(associatedData == null) { "OCI associated data requires a string map and cannot accept arbitrary bytes" }
            require(algorithm in encryptionAlgorithms) { "Unsupported OCI encryption algorithm" }
            val response = request(
                options = data.options,
                endpoint = data.options.cryptoEndpoint.apiEndpoint("encrypt"),
                method = HttpMethod.Post,
                body = buildJsonObject {
                    put("keyId", data.remoteKeyId)
                    put("keyVersionId", data.keyVersion)
                    put("plaintext", Base64.Default.encode(plaintext))
                    put("encryptionAlgorithm", algorithm.toOciAlgorithm())
                },
            )
            val responseVersion = response.requiredString("keyVersionId")
            require(responseVersion == data.keyVersion) { "OCI encrypted with an unexpected key version" }
            return AsymmetricCiphertext.Opaque(
                algorithm = algorithm,
                provider = this@OciKmsKeyProvider.id,
                keyId = storedKey.id,
                blob = BinaryData(Base64.Default.decode(response.requiredString("ciphertext"))),
                keyVersion = responseVersion,
            )
        }

        private suspend fun decrypt(
            ciphertext: AsymmetricCiphertext,
            associatedData: ByteArray?,
        ): ByteArray {
            require(associatedData == null) { "OCI associated data requires a string map and cannot accept arbitrary bytes" }
            val opaque = ciphertext as? AsymmetricCiphertext.Opaque
                ?: throw IllegalArgumentException("OCI requires provider-opaque ciphertext")
            require(opaque.provider == this@OciKmsKeyProvider.id && opaque.keyId == storedKey.id) {
                "Ciphertext belongs to a different provider or key"
            }
            require(opaque.keyVersion == data.keyVersion) { "Ciphertext belongs to a different key version" }
            require(opaque.algorithm in encryptionAlgorithms) { "Unsupported OCI encryption algorithm" }
            return Base64.Default.decode(
                request(
                    options = data.options,
                    endpoint = data.options.cryptoEndpoint.apiEndpoint("decrypt"),
                    method = HttpMethod.Post,
                    body = buildJsonObject {
                        put("keyId", data.remoteKeyId)
                        put("keyVersionId", data.keyVersion)
                        put("ciphertext", Base64.Default.encode(opaque.blob.toByteArray()))
                        put("encryptionAlgorithm", opaque.algorithm.toOciAlgorithm())
                    },
                ).requiredString("plaintext")
            )
        }

        private suspend fun delete(): KeyDeletionResult {
            val deletionTime = now() + 7.days
            request(
                options = data.options,
                endpoint = data.options.managementEndpoint.apiEndpoint(
                    "keys",
                    data.remoteKeyId,
                    "actions",
                    "scheduleDeletion",
                ),
                method = HttpMethod.Post,
                body = buildJsonObject { put("timeOfDeletion", deletionTime.toString()) },
            )
            return KeyDeletionResult.Scheduled(deletionTime)
        }
    }

    private suspend fun request(
        options: OciKmsOptions,
        endpoint: String,
        method: HttpMethod,
        body: JsonObject? = null,
    ): JsonObject {
        val encodedBody = body?.let { json.encodeToString(JsonObject.serializer(), it) }
        val headers = ociHttpSignatureHeaders(
            method = method,
            endpoint = endpoint,
            body = encodedBody,
            credential = credentialResolver.resolve(options.credentialReference),
            instant = now(),
        )
        return requireNotNull(
            client.executeJson(
                provider = "OCI KMS",
                endpoint = endpoint,
                method = method,
                headers = headers,
                contentType = encodedBody?.let { ContentType.Application.Json },
                body = encodedBody,
            )
        )
    }

    companion object {
        val ID = ProviderId("oci-kms-rest")
        private const val PROVIDER_SCHEMA_VERSION = 1
        private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
    }
}

@Serializable
private data class OciStoredKeyData(
    val options: OciKmsOptions,
    val remoteKeyId: String,
    val keyVersion: String,
) {
    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        fun decode(data: BinaryData): OciStoredKeyData = json.decodeFromString(data.toByteArray().decodeToString())
    }
}

private fun KeySpec.writeOciKeyShape(builder: JsonObjectBuilder) = with(builder) {
    when (this@writeOciKeyShape) {
        is KeySpec.Ec -> {
            val (length, curveId) = when (curve) {
                EcCurve.P256 -> 32 to "NIST_P256"
                EcCurve.P384 -> 48 to "NIST_P384"
                EcCurve.P521 -> 66 to "NIST_P521"
                else -> throw IllegalArgumentException("Unsupported OCI EC curve: ${curve.name}")
            }
            put("algorithm", "ECDSA")
            put("length", length)
            put("curveId", curveId)
        }
        is KeySpec.Rsa -> {
            require(bits == 2048 || bits == 3072 || bits == 4096) { "Unsupported OCI RSA size: $bits" }
            put("algorithm", "RSA")
            put("length", bits / Byte.SIZE_BITS)
        }
        else -> throw IllegalArgumentException("Unsupported OCI key specification: $this@writeOciKeyShape")
    }
}

private fun JsonObject.validateOciKeyShape(spec: KeySpec) {
    val expected = buildJsonObject { spec.writeOciKeyShape(this) }
    require(requiredString("algorithm") == expected.requiredString("algorithm")) {
        "OCI returned an unexpected key algorithm"
    }
    require(this["length"]?.jsonPrimitive?.content == expected["length"]?.jsonPrimitive?.content) {
        "OCI returned an unexpected key size"
    }
    if (spec is KeySpec.Ec) {
        require(requiredString("curveId") == expected.requiredString("curveId")) {
            "OCI returned an unexpected EC curve"
        }
    }
}

private fun KeySpec.ociSignatureAlgorithms(): Set<SignatureAlgorithm> = when (this) {
    is KeySpec.Ec -> {
        val digest = when (curve) {
            EcCurve.P256 -> DigestAlgorithm.SHA_256
            EcCurve.P384 -> DigestAlgorithm.SHA_384
            EcCurve.P521 -> DigestAlgorithm.SHA_512
            else -> throw IllegalArgumentException("Unsupported OCI EC curve: ${curve.name}")
        }
        EcdsaSignatureEncoding.entries.mapTo(mutableSetOf()) { SignatureAlgorithm.Ecdsa(digest, it) }
    }
    is KeySpec.Rsa -> listOf(DigestAlgorithm.SHA_256, DigestAlgorithm.SHA_384, DigestAlgorithm.SHA_512)
        .mapTo(mutableSetOf()) { SignatureAlgorithm.RsaPkcs1(it) }
    else -> emptySet()
}

private fun KeySpec.ociEncryptionAlgorithms(): Set<AsymmetricEncryptionAlgorithm> = when (this) {
    is KeySpec.Rsa -> setOf(
        AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_1),
        AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_256),
    )
    else -> emptySet()
}

private fun SignatureAlgorithm.digestAlgorithm(): DigestAlgorithm = when (this) {
    is SignatureAlgorithm.Ecdsa -> digest
    is SignatureAlgorithm.RsaPkcs1 -> digest
    else -> throw IllegalArgumentException("Unsupported OCI REST signature algorithm: $this")
}

private fun SignatureAlgorithm.toOciAlgorithm(): String = when (this) {
    is SignatureAlgorithm.Ecdsa -> "ECDSA_SHA_${digest.bits()}"
    is SignatureAlgorithm.RsaPkcs1 -> "SHA_${digest.bits()}_RSA_PKCS1_V1_5"
    else -> throw IllegalArgumentException("Unsupported OCI REST signature algorithm: $this")
}

private fun AsymmetricEncryptionAlgorithm.toOciAlgorithm(): String = when (this) {
    is AsymmetricEncryptionAlgorithm.RsaOaep -> {
        require(mgfDigest == digest) { "OCI RSA-OAEP MGF digest must match the message digest" }
        when (digest) {
            DigestAlgorithm.SHA_1 -> "RSA_OAEP_SHA_1"
            DigestAlgorithm.SHA_256 -> "RSA_OAEP_SHA_256"
            else -> throw IllegalArgumentException("Unsupported OCI RSA-OAEP digest: ${digest.name}")
        }
    }
    else -> throw IllegalArgumentException("Unsupported OCI encryption algorithm: $this")
}

private fun DigestAlgorithm.bits(): Int = when (this) {
    DigestAlgorithm.SHA_256 -> 256
    DigestAlgorithm.SHA_384 -> 384
    DigestAlgorithm.SHA_512 -> 512
    else -> throw IllegalArgumentException("Unsupported OCI digest: $name")
}

private fun KeySpec.ecComponentSize(): Int = when ((this as? KeySpec.Ec)?.curve) {
    EcCurve.P256 -> 32
    EcCurve.P384 -> 48
    EcCurve.P521 -> 66
    else -> throw IllegalArgumentException("OCI ECDSA requires a supported EC key")
}

private fun String.apiEndpoint(vararg segments: String): String =
    trimEnd('/') + "/20180608" + segments.joinToString(separator = "/", prefix = "/") { it.encodeURLPathPart() }

private fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
        ?: throw KmsProviderException("OCI response is missing string: $name")

private fun JsonObject.requiredObject(name: String): JsonObject =
    this[name] as? JsonObject ?: throw KmsProviderException("OCI response is missing object: $name")

private fun JsonObject.requiredBoolean(name: String): Boolean =
    try {
        this[name]?.jsonPrimitive?.boolean ?: throw IllegalArgumentException()
    } catch (_: IllegalArgumentException) {
        throw KmsProviderException("OCI response is missing boolean: $name")
    }
