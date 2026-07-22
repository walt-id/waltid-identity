package id.walt.crypto2.kms.vault

import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.DigestValue
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.algorithms.outputSizeBytes
import id.walt.crypto2.keys.AsymmetricCiphertext
import id.walt.crypto2.keys.Decryptor
import id.walt.crypto2.keys.DigestSigner
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
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
import id.walt.crypto2.keys.Verifier
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.kms.KmsProviderException
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

class VaultTransitKeyProvider(
    private val client: HttpClient,
    private val credentialResolver: VaultCredentialResolver,
) : ManagedKeyProvider {
    override val id: ProviderId = ID

    override suspend fun generate(request: GenerateManagedKeyRequest): ManagedKey {
        val options = VaultTransitOptions.decode(request.providerOptions)
        require(request.usages.isNotEmpty()) { "Vault key usages cannot be empty" }
        val allowedUsages = if (request.spec is KeySpec.Rsa) {
            setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.ENCRYPT, KeyUsage.DECRYPT)
        } else {
            setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        }
        require(request.usages.all(allowedUsages::contains)) {
            "Vault key usages are not supported by the requested key specification"
        }
        val remoteName = options.keyName ?: request.id.value
        require(remoteName.isNotBlank()) { "Vault key name cannot be blank" }
        val expectedType = request.spec.toVaultKeyType()

        authorizedRequest(
            options = options,
            method = HttpMethod.Post,
            endpoint = options.transitEndpoint("keys", remoteName),
            body = buildJsonObject {
                put("type", expectedType)
                put("exportable", false)
                put("allow_plaintext_backup", false)
            },
        )
        val keyData = requireNotNull(
            authorizedRequest(
                options = options,
                method = HttpMethod.Get,
                endpoint = options.transitEndpoint("keys", remoteName),
            )
        ).requiredObject("data")
        require(keyData.requiredString("type") == expectedType) {
            "Vault created a key with an unexpected type"
        }
        val version = keyData.requiredInt("latest_version")
        val versionData = keyData.requiredObject("keys").requiredObject(version.toString())
        val publicKey = versionData.requiredString("public_key").toPublicKey(request.spec)
        val providerData = VaultStoredKeyData(
            options = options.copy(keyName = null),
            remoteName = remoteName,
            keyVersion = version,
        )
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
            "Unsupported Vault provider schema: ${stored.providerSchemaVersion}"
        }
        requireNotNull(stored.publicKey) { "Stored Vault key is missing its public key" }
        val providerData = VaultStoredKeyData.decode(stored.providerData)
        require(providerData.remoteName.isNotBlank()) { "Stored Vault key name cannot be blank" }
        require(providerData.keyVersion > 0) { "Stored Vault key version must be positive" }
        stored.spec.toVaultKeyType()
        return key(stored, providerData)
    }

    private fun key(stored: StoredKey.Managed, providerData: VaultStoredKeyData): ManagedKey =
        VaultTransitKey(stored, providerData)

    private inner class VaultTransitKey(
        override val storedKey: StoredKey.Managed,
        private val providerData: VaultStoredKeyData,
    ) : ManagedKey {
        private val supportedAlgorithms = storedKey.spec.signatureAlgorithms()
        private val supportedEncryptionAlgorithms = storedKey.spec.encryptionAlgorithms()
        private val advertisedAlgorithms = supportedAlgorithms.takeIf {
            KeyUsage.SIGN in storedKey.usages || KeyUsage.VERIFY in storedKey.usages
        }.orEmpty()
        private val advertisedEncryptionAlgorithms = supportedEncryptionAlgorithms.takeIf {
            KeyUsage.ENCRYPT in storedKey.usages || KeyUsage.DECRYPT in storedKey.usages
        }.orEmpty()

        override val capabilities: KeyCapabilities = KeyCapabilities(
            signer = KeyUsage.SIGN.takeIf(storedKey.usages::contains)?.let {
                Signer { data, algorithm -> sign(data, algorithm, prehashed = false) }
            },
            digestSigner = KeyUsage.SIGN.takeIf(storedKey.usages::contains)
                ?.takeIf { storedKey.spec !is KeySpec.Edwards }
                ?.let {
                DigestSigner { digest, algorithm -> signDigest(digest, algorithm) }
            },
            verifier = KeyUsage.VERIFY.takeIf(storedKey.usages::contains)?.let {
                Verifier { data, signature, algorithm -> verify(data, signature, algorithm) }
            },
            encryptor = KeyUsage.ENCRYPT.takeIf(storedKey.usages::contains)?.let {
                Encryptor { plaintext, algorithm, associatedData -> encrypt(plaintext, algorithm, associatedData) }
            },
            decryptor = KeyUsage.DECRYPT.takeIf(storedKey.usages::contains)?.let {
                Decryptor { ciphertext, associatedData -> decrypt(ciphertext, associatedData) }
            },
            deleter = KeyDeleter { delete() },
            publicKeyExporter = PublicKeyExporter { requireNotNull(storedKey.publicKey) },
            signatureAlgorithms = advertisedAlgorithms,
            encryptionAlgorithms = advertisedEncryptionAlgorithms,
            supportsSignatureAlgorithm = { it in advertisedAlgorithms },
            supportsEncryptionAlgorithm = { it in advertisedEncryptionAlgorithms },
        )

        private suspend fun signDigest(digest: DigestValue, algorithm: SignatureAlgorithm): ByteArray {
            val expectedDigest = algorithm.digestAlgorithm()
            require(digest.algorithm == expectedDigest) {
                "Digest algorithm does not match the signature algorithm"
            }
            return sign(digest.value.toByteArray(), algorithm, prehashed = true)
        }

        private suspend fun sign(
            input: ByteArray,
            algorithm: SignatureAlgorithm,
            prehashed: Boolean,
        ): ByteArray {
            require(algorithm in supportedAlgorithms) { "Unsupported Vault signature algorithm" }
            require(!prehashed || algorithm !is SignatureAlgorithm.EdDsa) { "Vault EdDSA does not accept prehashed input" }
            val response = authorizedData(
                providerData.options,
                HttpMethod.Post,
                providerData.options.transitEndpoint("sign", providerData.remoteName),
                signatureRequest(input, algorithm, prehashed),
            )
            return response.requiredString("signature").decodeVaultSignature(providerData.keyVersion)
        }

        private suspend fun verify(
            input: ByteArray,
            signature: ByteArray,
            algorithm: SignatureAlgorithm,
        ): Boolean {
            require(algorithm in supportedAlgorithms) { "Unsupported Vault signature algorithm" }
            val body = signatureRequest(input, algorithm, prehashed = false).toMutableMap().apply {
                put(
                    "signature",
                    JsonPrimitive("vault:v${providerData.keyVersion}:${Base64.Default.encode(signature)}"),
                )
            }
            return authorizedData(
                providerData.options,
                HttpMethod.Post,
                providerData.options.transitEndpoint("verify", providerData.remoteName),
                JsonObject(body),
            ).requiredBoolean("valid")
        }

        private suspend fun encrypt(
            plaintext: ByteArray,
            algorithm: AsymmetricEncryptionAlgorithm,
            associatedData: ByteArray?,
        ): AsymmetricCiphertext {
            require(associatedData == null) { "Vault RSA encryption does not support associated data" }
            require(algorithm in supportedEncryptionAlgorithms) { "Unsupported Vault encryption algorithm" }
            val ciphertext = authorizedData(
                providerData.options,
                HttpMethod.Post,
                providerData.options.transitEndpoint("encrypt", providerData.remoteName),
                encryptionRequest(algorithm) {
                    put("plaintext", Base64.Default.encode(plaintext))
                    put("key_version", providerData.keyVersion)
                },
            ).requiredString("ciphertext")
            require(ciphertext.vaultVersion() == providerData.keyVersion) {
                "Vault ciphertext key version does not match the stored key version"
            }
            return AsymmetricCiphertext.Opaque(
                algorithm = algorithm,
                provider = this@VaultTransitKeyProvider.id,
                keyId = storedKey.id,
                blob = BinaryData(ciphertext.encodeToByteArray()),
                keyVersion = providerData.keyVersion.toString(),
            )
        }

        private suspend fun decrypt(
            ciphertext: AsymmetricCiphertext,
            associatedData: ByteArray?,
        ): ByteArray {
            require(associatedData == null) { "Vault RSA decryption does not support associated data" }
            val opaque = ciphertext as? AsymmetricCiphertext.Opaque
                ?: throw IllegalArgumentException("Vault requires provider-opaque ciphertext")
            require(opaque.provider == this@VaultTransitKeyProvider.id) { "Ciphertext belongs to a different provider" }
            require(opaque.keyId == storedKey.id) { "Ciphertext belongs to a different key" }
            require(opaque.keyVersion == providerData.keyVersion.toString()) {
                "Ciphertext key version does not match the stored key version"
            }
            require(opaque.algorithm in supportedEncryptionAlgorithms) { "Unsupported Vault encryption algorithm" }
            val encodedCiphertext = opaque.blob.toByteArray().decodeToString()
            require(encodedCiphertext.vaultVersion() == providerData.keyVersion) {
                "Vault ciphertext envelope version does not match its metadata"
            }
            return Base64.Default.decode(
                authorizedData(
                    providerData.options,
                    HttpMethod.Post,
                    providerData.options.transitEndpoint("decrypt", providerData.remoteName),
                    encryptionRequest(opaque.algorithm) { put("ciphertext", encodedCiphertext) },
                ).requiredString("plaintext")
            )
        }

        private suspend fun delete(): KeyDeletionResult {
            authorizedRequest(
                options = providerData.options,
                method = HttpMethod.Post,
                endpoint = providerData.options.transitEndpoint("keys", providerData.remoteName, "config"),
                body = buildJsonObject { put("deletion_allowed", true) },
            )
            authorizedRequest(
                options = providerData.options,
                method = HttpMethod.Delete,
                endpoint = providerData.options.transitEndpoint("keys", providerData.remoteName),
            )
            return KeyDeletionResult.Deleted
        }

        private fun signatureRequest(
            input: ByteArray,
            algorithm: SignatureAlgorithm,
            prehashed: Boolean,
        ): JsonObject = buildJsonObject {
            put("input", Base64.Default.encode(input))
            put("prehashed", prehashed)
            put("key_version", providerData.keyVersion)
            algorithm.digestAlgorithmOrNull()?.let { put("hash_algorithm", it.toVaultHashAlgorithm()) }
            when (algorithm) {
                is SignatureAlgorithm.Ecdsa -> put(
                    "marshaling_algorithm",
                    when (algorithm.encoding) {
                        EcdsaSignatureEncoding.DER -> "asn1"
                        EcdsaSignatureEncoding.IEEE_P1363 -> "jws"
                    },
                )
                is SignatureAlgorithm.RsaPkcs1 -> put("signature_algorithm", "pkcs1v15")
                is SignatureAlgorithm.RsaPss -> put("signature_algorithm", "pss")
                SignatureAlgorithm.EdDsa -> Unit
                is SignatureAlgorithm.Custom -> error("Unsupported Vault signature algorithm")
            }
        }

        private fun encryptionRequest(
            algorithm: AsymmetricEncryptionAlgorithm,
            content: JsonObjectBuilder.() -> Unit,
        ): JsonObject = buildJsonObject {
            when (algorithm) {
                is AsymmetricEncryptionAlgorithm.RsaOaep -> {
                    require(algorithm.mgfDigest == algorithm.digest) {
                        "Vault RSA-OAEP MGF digest must match the message digest"
                    }
                    put("padding_scheme", "oaep")
                    put("oaep_hash", algorithm.digest.toVaultOaepHash())
                }
                AsymmetricEncryptionAlgorithm.RsaPkcs1 -> put("padding_scheme", "pkcs1v15")
                is AsymmetricEncryptionAlgorithm.Custom -> error("Unsupported Vault encryption algorithm")
            }
            content()
        }
    }

    private suspend fun authorizedData(
        options: VaultTransitOptions,
        method: HttpMethod,
        endpoint: String,
        body: JsonObject? = null,
    ): JsonObject = requireNotNull(authorizedRequest(options, method, endpoint, body))
        .requiredObject("data")

    private suspend fun authorizedRequest(
        options: VaultTransitOptions,
        method: HttpMethod,
        endpoint: String,
        body: JsonObject? = null,
    ): JsonObject? = request(
        options = options,
        method = method,
        endpoint = endpoint,
        token = resolveToken(options),
        body = body,
    )

    private suspend fun resolveToken(options: VaultTransitOptions): String =
        when (val credential = credentialResolver.resolve(options.credentialReference)) {
            is VaultCredential.Token -> credential.value
            is VaultCredential.AppRole -> requireNotNull(request(
                options = options,
                method = HttpMethod.Post,
                endpoint = options.apiEndpoint("auth", credential.mount, "login"),
                body = buildJsonObject {
                    put("role_id", credential.roleId)
                    put("secret_id", credential.secretId)
                },
            )).requiredObject("auth").requiredString("client_token")
            is VaultCredential.UserPassword -> requireNotNull(request(
                options = options,
                method = HttpMethod.Post,
                endpoint = options.apiEndpoint("auth", credential.mount, "login", credential.username),
                body = buildJsonObject { put("password", credential.password) },
            )).requiredObject("auth").requiredString("client_token")
        }

    private suspend fun request(
        options: VaultTransitOptions,
        method: HttpMethod,
        endpoint: String,
        token: String? = null,
        body: JsonObject? = null,
    ): JsonObject? = client.executeJson(
        provider = "Vault",
        endpoint = endpoint,
        method = method,
        headers = buildMap {
            options.namespace?.let { put("X-Vault-Namespace", it) }
            token?.let { put("X-Vault-Token", it) }
        },
        contentType = body?.let { ContentType.Application.Json },
        body = body?.let { json.encodeToString(JsonObject.serializer(), it) },
    )

    companion object {
        val ID = ProviderId("vault-transit-rest")

        suspend fun storedKeyForExisting(
            id: KeyId,
            spec: KeySpec,
            usages: Set<KeyUsage>,
            options: VaultTransitOptions,
            remoteName: String,
            keyVersion: Int,
            publicKey: EncodedKey.SpkiDer,
            metadata: Map<String, String> = emptyMap(),
        ): StoredKey.Managed {
            require(usages.isNotEmpty()) { "Vault key usages cannot be empty" }
            val allowedUsages = if (spec is KeySpec.Rsa) {
                setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.ENCRYPT, KeyUsage.DECRYPT)
            } else {
                setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
            }
            require(usages.all(allowedUsages::contains)) {
                "Vault key usages are not supported by the requested key specification"
            }
            spec.toVaultKeyType()
            require(remoteName.isNotBlank()) { "Vault key name cannot be blank" }
            require(keyVersion > 0) { "Vault key version must be positive" }
            publicKey.toPublicJwk(spec)
            return StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = id,
                spec = spec,
                usages = usages,
                provider = ID,
                providerSchemaVersion = PROVIDER_SCHEMA_VERSION,
                providerData = VaultStoredKeyData(options.copy(keyName = null), remoteName, keyVersion).encode(),
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
private data class VaultStoredKeyData(
    val options: VaultTransitOptions,
    val remoteName: String,
    val keyVersion: Int,
) {
    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }

        fun decode(data: BinaryData): VaultStoredKeyData = json.decodeFromString(data.toByteArray().decodeToString())
    }
}

private fun KeySpec.toVaultKeyType(): String = when (this) {
    KeySpec.Ec(EcCurve.P256) -> "ecdsa-p256"
    KeySpec.Ec(EcCurve.P384) -> "ecdsa-p384"
    KeySpec.Ec(EcCurve.P521) -> "ecdsa-p521"
    KeySpec.Edwards(EdwardsCurve.ED25519) -> "ed25519"
    is KeySpec.Rsa -> when (bits) {
        2048, 3072, 4096 -> "rsa-$bits"
        else -> throw IllegalArgumentException("Vault does not support RSA-$bits")
    }
    else -> throw IllegalArgumentException("Unsupported Vault key specification: $this")
}

private fun KeySpec.signatureAlgorithms(): Set<SignatureAlgorithm> = when (this) {
    is KeySpec.Ec -> listOf(DigestAlgorithm.SHA_256, DigestAlgorithm.SHA_384, DigestAlgorithm.SHA_512)
        .flatMap { digest -> EcdsaSignatureEncoding.entries.map { SignatureAlgorithm.Ecdsa(digest, it) } }
        .toSet()
    is KeySpec.Edwards -> setOf(SignatureAlgorithm.EdDsa)
    is KeySpec.Rsa -> listOf(DigestAlgorithm.SHA_256, DigestAlgorithm.SHA_384, DigestAlgorithm.SHA_512)
        .flatMap { digest ->
            listOf(
                SignatureAlgorithm.RsaPkcs1(digest),
                SignatureAlgorithm.RsaPss(digest, saltLengthBytes = requireNotNull(digest.outputSizeBytes)),
            )
        }.toSet()
    else -> emptySet()
}

private fun KeySpec.encryptionAlgorithms(): Set<AsymmetricEncryptionAlgorithm> = when (this) {
    is KeySpec.Rsa -> setOf(
        AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_1),
        AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_256),
        AsymmetricEncryptionAlgorithm.RsaPkcs1,
    )
    else -> emptySet()
}

private fun SignatureAlgorithm.digestAlgorithm(): DigestAlgorithm =
    digestAlgorithmOrNull() ?: throw IllegalArgumentException("Signature algorithm does not accept a digest")

private fun SignatureAlgorithm.digestAlgorithmOrNull(): DigestAlgorithm? = when (this) {
    is SignatureAlgorithm.Ecdsa -> digest
    is SignatureAlgorithm.RsaPkcs1 -> digest
    is SignatureAlgorithm.RsaPss -> digest.also {
        require(mgfDigest == digest) { "Vault RSA-PSS MGF digest must match the message digest" }
        require(saltLengthBytes == digest.outputSizeBytes) {
            "Vault RSA-PSS salt length must match the digest length"
        }
    }
    SignatureAlgorithm.EdDsa -> null
    is SignatureAlgorithm.Custom -> null
}

private fun DigestAlgorithm.toVaultHashAlgorithm(): String = when (this) {
    DigestAlgorithm.SHA_256 -> "sha2-256"
    DigestAlgorithm.SHA_384 -> "sha2-384"
    DigestAlgorithm.SHA_512 -> "sha2-512"
    else -> throw IllegalArgumentException("Unsupported Vault digest: $name")
}

private fun DigestAlgorithm.toVaultOaepHash(): String = when (this) {
    DigestAlgorithm.SHA_1 -> "sha1"
    DigestAlgorithm.SHA_256 -> "sha256"
    else -> throw IllegalArgumentException("Unsupported Vault RSA-OAEP digest: $name")
}

private fun VaultTransitOptions.apiEndpoint(vararg segments: String): String =
    apiBaseUrl.trimEnd('/') + segments.joinToString(separator = "/", prefix = "/") { it.encodeURLPathPart() }

private fun VaultTransitOptions.transitEndpoint(vararg segments: String): String =
    apiEndpoint(transitMount, *segments)

private fun String.toPublicKey(spec: KeySpec): EncodedKey.SpkiDer {
    val decoded = Base64.Default.decode(
        lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("-----") }
            .joinToString("")
    )
    val spki = if (spec == KeySpec.Edwards(EdwardsCurve.ED25519) && decoded.size == 32) {
        ED25519_SPKI_PREFIX + decoded
    } else {
        decoded
    }
    require(spki.isNotEmpty()) { "Vault public key cannot be empty" }
    return EncodedKey.SpkiDer(BinaryData(spki))
}

private fun String.decodeVaultSignature(expectedVersion: Int): ByteArray {
    val prefix = "vault:v$expectedVersion:"
    require(startsWith(prefix)) { "Vault signature key version does not match the stored key version" }
    return Base64.Default.decode(removePrefix(prefix))
}

private fun String.vaultVersion(): Int {
    require(startsWith("vault:v")) { "Vault value has an invalid envelope" }
    return substringAfter("vault:v").substringBefore(':').toIntOrNull()
        ?: throw IllegalArgumentException("Vault value has an invalid key version")
}

private fun JsonObject.requiredObject(name: String): JsonObject =
    this[name]?.jsonObject ?: throw KmsProviderException("Vault response is missing object: $name")

private fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
        ?: throw KmsProviderException("Vault response is missing string: $name")

private fun JsonObject.requiredInt(name: String): Int =
    try {
        this[name]?.jsonPrimitive?.int ?: throw IllegalArgumentException()
    } catch (_: IllegalArgumentException) {
        throw KmsProviderException("Vault response is missing integer: $name")
    }

private fun JsonObject.requiredBoolean(name: String): Boolean =
    try {
        this[name]?.jsonPrimitive?.boolean ?: throw IllegalArgumentException()
    } catch (_: IllegalArgumentException) {
        throw KmsProviderException("Vault response is missing boolean: $name")
    }

private val ED25519_SPKI_PREFIX = byteArrayOf(
    0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00,
)
