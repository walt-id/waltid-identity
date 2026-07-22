package id.walt.crypto2.kms.azure

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
import id.walt.crypto2.keys.EncodedKeyMaterial
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
import id.walt.crypto2.kms.KmsProviderException
import id.walt.crypto2.kms.digest
import id.walt.crypto2.kms.executeJson
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.ManagedKeyProvider
import id.walt.crypto2.serialization.BinaryData
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

class AzureKeyVaultKeyProvider(
    private val client: HttpClient,
    private val tokenProvider: AzureAccessTokenProvider,
) : ManagedKeyProvider {
    constructor(client: HttpClient, credentialResolver: AzureCredentialResolver) : this(
        client = client,
        tokenProvider = AzureClientSecretTokenProvider(client, credentialResolver),
    )

    override val id: ProviderId = ID

    override suspend fun generate(request: GenerateManagedKeyRequest): ManagedKey {
        val options = AzureKeyVaultOptions.decode(request.providerOptions)
        val allowedUsages = if (request.spec is KeySpec.Rsa) {
            setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.ENCRYPT, KeyUsage.DECRYPT)
        } else {
            setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        }
        require(request.usages.isNotEmpty() && request.usages.all(allowedUsages::contains)) {
            "Azure key usages are not supported by the requested key specification"
        }
        val keyName = options.keyName ?: request.id.value
        val response = authorizedJson(
            options = options,
            endpoint = "${options.keyVaultUrl.trimEnd('/')}/keys/${keyName.encodeURLPathPart()}/create?$API_VERSION",
            method = HttpMethod.Post,
            body = buildJsonObject {
                request.spec.writeCreateParameters(this)
                put("key_ops", JsonArray(request.usages.map { JsonPrimitive(it.toAzureOperation()) }))
            },
        )
        val publicJwk = response.requiredObject("key").toPublicJwk(request.spec)
        val remoteId = response.requiredObject("key").requiredString("kid")
        val version = remoteId.substringAfterLast('/').takeIf(String::isNotBlank)
            ?: throw KmsProviderException("Azure key response contains an invalid key ID")
        val providerData = AzureStoredKeyData(
            options = options.copy(keyName = null),
            keyIdUrl = remoteId,
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
                publicKey = publicJwk,
                metadata = request.metadata,
            ),
            providerData,
        )
    }

    override suspend fun restore(stored: StoredKey.Managed): ManagedKey {
        require(stored.provider == id) { "Stored key belongs to a different provider" }
        require(stored.providerSchemaVersion == PROVIDER_SCHEMA_VERSION) {
            "Unsupported Azure provider schema: ${stored.providerSchemaVersion}"
        }
        val publicKey = requireNotNull(stored.publicKey) { "Stored Azure key is missing its public key" }
        EncodedKeyMaterial(stored.spec, publicKey as? EncodedKey.Jwk
            ?: throw IllegalArgumentException("Stored Azure public key must be a JWK"))
        val providerData = AzureStoredKeyData.decode(stored.providerData)
        require(providerData.keyIdUrl.startsWith(providerData.options.keyVaultUrl.trimEnd('/') + "/keys/")) {
            "Stored Azure key ID does not belong to the configured vault"
        }
        require(providerData.keyIdUrl.substringAfterLast('/') == providerData.keyVersion) {
            "Stored Azure key version does not match its key ID"
        }
        return key(stored, providerData)
    }

    private fun key(stored: StoredKey.Managed, providerData: AzureStoredKeyData): ManagedKey =
        AzureKeyVaultKey(stored, providerData)

    private inner class AzureKeyVaultKey(
        override val storedKey: StoredKey.Managed,
        private val providerData: AzureStoredKeyData,
    ) : ManagedKey {
        private val signatureAlgorithms = storedKey.spec.azureSignatureAlgorithms()
        private val encryptionAlgorithms = storedKey.spec.azureEncryptionAlgorithms()
        private val advertisedSignatureAlgorithms = signatureAlgorithms.takeIf {
            KeyUsage.SIGN in storedKey.usages || KeyUsage.VERIFY in storedKey.usages
        }.orEmpty()
        private val advertisedEncryptionAlgorithms = encryptionAlgorithms.takeIf {
            KeyUsage.ENCRYPT in storedKey.usages || KeyUsage.DECRYPT in storedKey.usages
        }.orEmpty()

        override val capabilities = KeyCapabilities(
            signer = KeyUsage.SIGN.takeIf(storedKey.usages::contains)?.let {
                Signer { data, algorithm -> signDigest(digest(data, algorithm.digestAlgorithm()), algorithm) }
            },
            digestSigner = KeyUsage.SIGN.takeIf(storedKey.usages::contains)?.let {
                DigestSigner { value, algorithm -> signDigest(value, algorithm) }
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
            signatureAlgorithms = advertisedSignatureAlgorithms,
            encryptionAlgorithms = advertisedEncryptionAlgorithms,
            supportsSignatureAlgorithm = { it in advertisedSignatureAlgorithms },
            supportsEncryptionAlgorithm = { it in advertisedEncryptionAlgorithms },
        )

        private suspend fun signDigest(value: DigestValue, algorithm: SignatureAlgorithm): ByteArray {
            require(algorithm in signatureAlgorithms) { "Unsupported Azure signature algorithm" }
            require(value.algorithm == algorithm.digestAlgorithm()) { "Digest algorithm does not match signature algorithm" }
            val signature = base64Url.decode(
                authorizedJson(
                    options = providerData.options,
                    endpoint = "${providerData.keyIdUrl}/sign?$API_VERSION",
                    method = HttpMethod.Post,
                    body = buildJsonObject {
                        put("alg", algorithm.toAzureAlgorithm(storedKey.spec))
                        put("value", base64Url.encode(value.value.toByteArray()))
                    },
                ).requiredString("value")
            )
            return if (algorithm is SignatureAlgorithm.Ecdsa && algorithm.encoding == EcdsaSignatureEncoding.DER) {
                EcdsaSignatureCodec.p1363ToDer(signature, storedKey.spec.ecComponentSize())
            } else {
                signature
            }
        }

        private suspend fun verify(data: ByteArray, signature: ByteArray, algorithm: SignatureAlgorithm): Boolean {
            require(algorithm in signatureAlgorithms) { "Unsupported Azure signature algorithm" }
            val digest = digest(data, algorithm.digestAlgorithm())
            val azureSignature = if (algorithm is SignatureAlgorithm.Ecdsa && algorithm.encoding == EcdsaSignatureEncoding.DER) {
                EcdsaSignatureCodec.derToP1363(signature, storedKey.spec.ecComponentSize())
            } else {
                signature
            }
            return authorizedJson(
                options = providerData.options,
                endpoint = "${providerData.keyIdUrl}/verify?$API_VERSION",
                method = HttpMethod.Post,
                body = buildJsonObject {
                    put("alg", algorithm.toAzureAlgorithm(storedKey.spec))
                    put("digest", base64Url.encode(digest.value.toByteArray()))
                    put("signature", base64Url.encode(azureSignature))
                },
            )["value"]?.jsonPrimitive?.content == "true"
        }

        private suspend fun encrypt(
            plaintext: ByteArray,
            algorithm: AsymmetricEncryptionAlgorithm,
            associatedData: ByteArray?,
        ): AsymmetricCiphertext {
            require(associatedData == null) { "Azure RSA encryption does not support associated data" }
            require(algorithm in encryptionAlgorithms) { "Unsupported Azure encryption algorithm" }
            val value = base64Url.decode(
                authorizedJson(
                    options = providerData.options,
                    endpoint = "${providerData.keyIdUrl}/encrypt?$API_VERSION",
                    method = HttpMethod.Post,
                    body = buildJsonObject {
                        put("alg", algorithm.toAzureAlgorithm())
                        put("value", base64Url.encode(plaintext))
                    },
                ).requiredString("value")
            )
            return AsymmetricCiphertext.Opaque(
                algorithm = algorithm,
                provider = this@AzureKeyVaultKeyProvider.id,
                keyId = storedKey.id,
                blob = BinaryData(value),
                keyVersion = providerData.keyVersion,
            )
        }

        private suspend fun decrypt(ciphertext: AsymmetricCiphertext, associatedData: ByteArray?): ByteArray {
            require(associatedData == null) { "Azure RSA decryption does not support associated data" }
            val opaque = ciphertext as? AsymmetricCiphertext.Opaque
                ?: throw IllegalArgumentException("Azure requires provider-opaque ciphertext")
            require(opaque.provider == this@AzureKeyVaultKeyProvider.id) { "Ciphertext belongs to a different provider" }
            require(opaque.keyId == storedKey.id) { "Ciphertext belongs to a different key" }
            require(opaque.keyVersion == providerData.keyVersion) { "Ciphertext belongs to a different key version" }
            require(opaque.algorithm in encryptionAlgorithms) { "Unsupported Azure encryption algorithm" }
            return base64Url.decode(
                authorizedJson(
                    options = providerData.options,
                    endpoint = "${providerData.keyIdUrl}/decrypt?$API_VERSION",
                    method = HttpMethod.Post,
                    body = buildJsonObject {
                        put("alg", opaque.algorithm.toAzureAlgorithm())
                        put("value", base64Url.encode(opaque.blob.toByteArray()))
                    },
                ).requiredString("value")
            )
        }

        private suspend fun delete(): KeyDeletionResult {
            val keyUrl = providerData.keyIdUrl.substringBeforeLast('/')
            authorizedJson(
                options = providerData.options,
                endpoint = "$keyUrl?$API_VERSION",
                method = HttpMethod.Delete,
            )
            return KeyDeletionResult.Deleted
        }
    }

    private suspend fun authorizedJson(
        options: AzureKeyVaultOptions,
        endpoint: String,
        method: HttpMethod,
        body: JsonObject? = null,
    ): JsonObject {
        val token = tokenProvider.getAccessToken(options)
        return requireNotNull(
            client.executeJson(
                provider = "Azure Key Vault",
                endpoint = endpoint,
                method = method,
                headers = mapOf(HttpHeaders.Authorization to "Bearer $token"),
                contentType = body?.let { ContentType.Application.Json },
                body = body?.let { json.encodeToString(JsonObject.serializer(), it) },
            )
        )
    }

    companion object {
        val ID = ProviderId("azure-key-vault-rest")

        fun storedKeyForExisting(
            id: KeyId,
            spec: KeySpec,
            usages: Set<KeyUsage>,
            options: AzureKeyVaultOptions,
            keyIdUrl: String,
            publicKey: EncodedKey.Jwk,
            metadata: Map<String, String> = emptyMap(),
        ): StoredKey.Managed {
            val allowedUsages = if (spec is KeySpec.Rsa) {
                setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.ENCRYPT, KeyUsage.DECRYPT)
            } else {
                setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
            }
            require(usages.isNotEmpty() && usages.all(allowedUsages::contains)) {
                "Azure key usages are not supported by the requested key specification"
            }
            val normalizedPublicKey = publicKey.normalizeAzureCurve(spec)
            EncodedKeyMaterial(spec, normalizedPublicKey)
            val vaultPrefix = options.keyVaultUrl.trimEnd('/') + "/keys/"
            require(keyIdUrl.startsWith(vaultPrefix)) { "Azure key ID does not belong to the configured vault" }
            val keyVersion = keyIdUrl.substringAfterLast('/').takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("Azure key ID does not contain a version")
            return StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = id,
                spec = spec,
                usages = usages,
                provider = ID,
                providerSchemaVersion = PROVIDER_SCHEMA_VERSION,
                providerData = AzureStoredKeyData(
                    options = options.copy(keyName = null),
                    keyIdUrl = keyIdUrl,
                    keyVersion = keyVersion,
                ).encode(),
                publicKey = normalizedPublicKey,
                metadata = metadata,
            )
        }

        private const val PROVIDER_SCHEMA_VERSION = 1
        private const val API_VERSION = "api-version=7.4"
        private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
        private val json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }
    }
}

@Serializable
private data class AzureStoredKeyData(
    val options: AzureKeyVaultOptions,
    val keyIdUrl: String,
    val keyVersion: String,
) {
    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }

        fun decode(data: BinaryData): AzureStoredKeyData = json.decodeFromString(data.toByteArray().decodeToString())
    }
}

private fun KeySpec.writeCreateParameters(builder: JsonObjectBuilder) = with(builder) {
    when (this@writeCreateParameters) {
        is KeySpec.Ec -> {
            put("kty", "EC")
            put("crv", curve.toAzureCurve())
        }
        is KeySpec.Rsa -> {
            require(bits == 2048 || bits == 3072 || bits == 4096) { "Unsupported Azure RSA size: $bits" }
            put("kty", "RSA")
            put("key_size", bits)
        }
        else -> throw IllegalArgumentException("Unsupported Azure key specification: $this@writeCreateParameters")
    }
}

private fun KeyUsage.toAzureOperation(): String = when (this) {
    KeyUsage.SIGN -> "sign"
    KeyUsage.VERIFY -> "verify"
    KeyUsage.ENCRYPT -> "encrypt"
    KeyUsage.DECRYPT -> "decrypt"
    else -> throw IllegalArgumentException("Unsupported Azure key usage: $this")
}

private fun JsonObject.toPublicJwk(spec: KeySpec): EncodedKey.Jwk {
    val members = when (spec) {
        is KeySpec.Ec -> listOf("kty", "crv", "x", "y")
        is KeySpec.Rsa -> listOf("kty", "n", "e")
        else -> error("Unsupported Azure key specification")
    }
    val jwk = JsonObject(members.associateWith { name ->
        if (name == "crv" && spec == KeySpec.Ec(EcCurve.SECP256K1)) {
            JsonPrimitive("secp256k1")
        } else this[name] ?: throw KmsProviderException("Azure key response is missing JWK member: $name")
    })
    return EncodedKey.Jwk(BinaryData(Json.encodeToString(jwk).encodeToByteArray()), privateMaterial = false)
        .also { EncodedKeyMaterial(spec, it) }
}

private fun EncodedKey.Jwk.normalizeAzureCurve(spec: KeySpec): EncodedKey.Jwk {
    if (spec != KeySpec.Ec(EcCurve.SECP256K1)) return this
    val jwk = Json.parseToJsonElement(data.toByteArray().decodeToString()).jsonObject
    val curve = jwk["crv"]?.jsonPrimitive?.content
    require(curve == "P-256K" || curve == "secp256k1") { "Azure secp256k1 JWK has an invalid curve" }
    return EncodedKey.Jwk(
        BinaryData(Json.encodeToString(JsonObject(jwk + ("crv" to JsonPrimitive("secp256k1")))).encodeToByteArray()),
        privateMaterial = false,
    )
}

private fun KeySpec.azureSignatureAlgorithms(): Set<SignatureAlgorithm> = when (this) {
    is KeySpec.Ec -> {
        val digest = when (curve) {
            EcCurve.P256, EcCurve.SECP256K1 -> DigestAlgorithm.SHA_256
            EcCurve.P384 -> DigestAlgorithm.SHA_384
            EcCurve.P521 -> DigestAlgorithm.SHA_512
            else -> throw IllegalArgumentException("Unsupported Azure EC curve: ${curve.name}")
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

private fun KeySpec.azureEncryptionAlgorithms(): Set<AsymmetricEncryptionAlgorithm> = when (this) {
    is KeySpec.Rsa -> setOf(
        AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_1),
        AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_256),
        AsymmetricEncryptionAlgorithm.RsaPkcs1,
    )
    else -> emptySet()
}

private fun SignatureAlgorithm.digestAlgorithm(): DigestAlgorithm = when (this) {
    is SignatureAlgorithm.Ecdsa -> digest
    is SignatureAlgorithm.RsaPkcs1 -> digest
    is SignatureAlgorithm.RsaPss -> digest.also {
        require(mgfDigest == digest && saltLengthBytes == digest.outputSizeBytes) {
            "Azure RSA-PSS requires matching digest, MGF digest, and salt length"
        }
    }
    else -> throw IllegalArgumentException("Unsupported Azure signature algorithm: $this")
}

private fun SignatureAlgorithm.toAzureAlgorithm(spec: KeySpec): String = when (this) {
    is SignatureAlgorithm.Ecdsa -> when (digest) {
        DigestAlgorithm.SHA_256 -> if (spec == KeySpec.Ec(EcCurve.SECP256K1)) "ES256K" else "ES256"
        DigestAlgorithm.SHA_384 -> "ES384"
        DigestAlgorithm.SHA_512 -> "ES512"
        else -> throw IllegalArgumentException("Unsupported Azure ECDSA digest: ${digest.name}")
    }
    is SignatureAlgorithm.RsaPkcs1 -> "RS${digest.joseBits()}"
    is SignatureAlgorithm.RsaPss -> "PS${digest.joseBits()}"
    else -> throw IllegalArgumentException("Unsupported Azure signature algorithm: $this")
}

private fun AsymmetricEncryptionAlgorithm.toAzureAlgorithm(): String = when (this) {
    is AsymmetricEncryptionAlgorithm.RsaOaep -> {
        require(mgfDigest == digest) { "Azure RSA-OAEP MGF digest must match the message digest" }
        when (digest) {
            DigestAlgorithm.SHA_1 -> "RSA-OAEP"
            DigestAlgorithm.SHA_256 -> "RSA-OAEP-256"
            else -> throw IllegalArgumentException("Unsupported Azure RSA-OAEP digest: ${digest.name}")
        }
    }
    AsymmetricEncryptionAlgorithm.RsaPkcs1 -> "RSA1_5"
    is AsymmetricEncryptionAlgorithm.Custom -> throw IllegalArgumentException("Unsupported Azure encryption algorithm")
}

private fun DigestAlgorithm.joseBits(): Int = when (this) {
    DigestAlgorithm.SHA_256 -> 256
    DigestAlgorithm.SHA_384 -> 384
    DigestAlgorithm.SHA_512 -> 512
    else -> throw IllegalArgumentException("Unsupported Azure digest: $name")
}

private fun EcCurve.toAzureCurve(): String = when (this) {
    EcCurve.P256 -> "P-256"
    EcCurve.SECP256K1 -> "P-256K"
    EcCurve.P384 -> "P-384"
    EcCurve.P521 -> "P-521"
    else -> throw IllegalArgumentException("Unsupported Azure EC curve: $name")
}

private fun KeySpec.ecComponentSize(): Int = when ((this as? KeySpec.Ec)?.curve) {
    EcCurve.P256, EcCurve.SECP256K1 -> 32
    EcCurve.P384 -> 48
    EcCurve.P521 -> 66
    else -> throw IllegalArgumentException("Azure ECDSA requires a supported EC key")
}

private fun JsonObject.requiredObject(name: String): JsonObject =
    this[name]?.jsonObject ?: throw KmsProviderException("Azure response is missing object: $name")

private fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
        ?: throw KmsProviderException("Azure response is missing string: $name")
