package id.walt.crypto.keys.azure

import com.azure.core.exception.ResourceNotFoundException
import com.azure.security.keyvault.keys.cryptography.CryptographyAsyncClient
import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm
import com.azure.security.keyvault.keys.models.CreateEcKeyOptions
import com.azure.security.keyvault.keys.models.CreateRsaKeyOptions
import com.azure.security.keyvault.keys.models.KeyCurveName
import com.azure.security.keyvault.keys.models.KeyVaultKey
import id.walt.crypto.exceptions.*
import id.walt.crypto.keys.*
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256

@Serializable
@SerialName("azure")
class AzureKey(
    val config: AzureKeyMetadataSDK,
    val id: String,
    private var _publicKey: DirectSerializedKey? = null,
    private var _keyType: KeyType? = null,
) : Key() {

    override var keyType: KeyType
        get() = _keyType!!
        set(value) {
            _keyType = value
        }

    override val hasPrivateKey: Boolean
        get() = true

    override fun toString(): String = "[Azure ${keyType.name} key @KeyVault - $id]"

    override suspend fun getKeyId(): String = getPublicKey().getThumbprint()

    override suspend fun getThumbprint(): String = getPublicKey().getThumbprint()

    override suspend fun exportJWK(): String = getPublicKey().exportJWK()

    override suspend fun exportJWKObject(): JsonObject = PublicKeyIds.run { publicJwkForPublish() }

    override suspend fun exportPEM(): String = throw NotImplementedError("PEM export is not available for remote keys.")


    private val azureSignatureAlgorithm: SignatureAlgorithm
        get() = when (keyType) {
            KeyType.secp256r1 -> SignatureAlgorithm.ES256
            KeyType.secp256k1 -> SignatureAlgorithm.ES256K
            KeyType.RSA -> SignatureAlgorithm.RS256
            else -> {
                throw KeyTypeNotSupportedException(keyType.name)
            }
        }


    private fun getHashFunction(): (ByteArray) -> ByteArray = when (keyType) {
        KeyType.secp256r1, KeyType.secp256k1, KeyType.RSA -> { data -> SHA256().digest(data) }
        else -> {
            throw KeyTypeNotSupportedException(keyType.name)
        }
    }

    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
        return try {
            val hashFunction = getHashFunction()
            val digest = hashFunction(plaintext)

            val cryptoClient = versionedCryptoClient()
            val signResult = cryptoClient.sign(azureSignatureAlgorithm, digest).awaitSingle()
            val rawSignature = signResult.signature
                ?: throw SigningException("Azure Key Vault returned null signature")

            // Azure Key Vault returns EC signatures in IEEE P1363 (R||S) format, but the
            // waltid-crypto contract for signRaw is ASN.1 DER for EC (matches JWKKey, AWSKey,
            // OCIKey, and AzureKeyRestApi). Consumers like BouncyCastle's ContentSigner used
            // for X.509 issuance decode the result as DER, so convert here to keep parity.
            if (keyType in KeyTypes.EC_KEYS) {
                EcdsaSignatureCodec.p1363ToDer(rawSignature, EC_COMPONENT_SIZE)
            } else {
                rawSignature
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: KeyNotFoundException) {
            throw e
        } catch (e: ResourceNotFoundException) {
            throw KeyNotFoundException(id, "Key not found in Azure Key Vault", e)
        } catch (e: com.azure.core.exception.HttpResponseException) {
            when (e.response.statusCode) {
                403 -> throw UnauthorizedKeyAccess("Access denied to key '$id'. Check RBAC permissions.", e)
                else -> throw KeyVaultUnavailable("Azure Key Vault request failed: ${e.message}", e)
            }
        } catch (e: Exception) {
            throw SigningException("Failed to sign with Azure Key Vault: ${e.message}", e)
        }
    }

    override suspend fun signJws(
        plaintext: ByteArray,
        headers: Map<String, JsonElement>,
    ): String {
        val appendedHeader = HashMap(headers).apply {
            put("alg", keyType.jwsAlg.toJsonElement())
        }

        val header = Json.encodeToString(appendedHeader).encodeToByteArray().encodeToBase64Url()
        val payload = plaintext.encodeToBase64Url()

        val rawDerOrRsaSignature = signRaw("$header.$payload".encodeToByteArray())
        val jwsSignature = if (keyType in KeyTypes.EC_KEYS) {
            EcdsaSignatureCodec.derToP1363(rawDerOrRsaSignature, EC_COMPONENT_SIZE)
        } else {
            rawDerOrRsaSignature
        }

        val encodedSignature = jwsSignature.encodeToBase64Url()
        return "$header.$payload.$encodedSignature"
    }

    override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?,
        customSignatureAlgorithm: String?,
    ): Result<ByteArray> {
        return try {
            val messageToVerify = detachedPlaintext
                ?: return Result.failure(IllegalArgumentException("Detached plaintext is required for verification"))

            val hashFunction = getHashFunction()
            val digest = hashFunction(messageToVerify)

            val cryptoClient = versionedCryptoClient()
            if (verifyWithAzure(cryptoClient, digest, signed)) {
                Result.success(messageToVerify)
            } else {
                Result.failure(VerificationException("Signature verification failed"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: KeyNotFoundException) {
            Result.failure(e)
        } catch (e: ResourceNotFoundException) {
            Result.failure(KeyNotFoundException(id, "Key not found in Azure Key Vault", e))
        } catch (e: com.azure.core.exception.HttpResponseException) {
            when (e.response.statusCode) {
                403 -> Result.failure(UnauthorizedKeyAccess("Access denied to key '$id'", e))
                else -> Result.failure(KeyVaultUnavailable("Azure Key Vault request failed: ${e.message}", e))
            }
        } catch (e: Exception) {
            Result.failure(VerificationException("Failed to verify with Azure Key Vault: ${e.message}", e))
        }
    }

    private suspend fun verifyWithAzure(client: CryptographyAsyncClient, digest: ByteArray, signed: ByteArray): Boolean {
        if (keyType !in KeyTypes.EC_KEYS) return client.verify(azureSignatureAlgorithm, digest, signed).awaitSingle().isValid

        val raw = try {
            EcdsaSignatureCodec.derToP1363(signed, EC_COMPONENT_SIZE)
        } catch (e: IllegalArgumentException) {
            // Retain support for callers that already pass Azure's fixed-width encoding.
            if (signed.size != EC_COMPONENT_SIZE * 2) throw e
            signed
        }
        if (client.verify(azureSignatureAlgorithm, digest, raw).awaitSingle().isValid) return true
        // Fixed-width inputs can also happen to parse as DER. Prefer DER and try the original bytes if necessary.
        return signed.size == EC_COMPONENT_SIZE * 2 && !raw.contentEquals(signed) &&
            client.verify(azureSignatureAlgorithm, digest, signed).awaitSingle().isValid
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        val publicKey = getPublicKey()
        return publicKey.verifyJws(signedJws)
    }

    @Transient
    private val keyVersionLock = Mutex()

    @Transient
    private var keyVersion: String? = null

    override suspend fun getPublicKey(): Key = keyVersionLock.withLock {
        (_publicKey ?: DirectSerializedKey(getAzurePublicKey(config, id)).also { _publicKey = it }).key
    }

    private suspend fun versionedCryptoClient(): CryptographyAsyncClient =
        KeyVaultClientFactory.cryptoClient(config.auth.keyVaultUrl, id, keyVersionLock.withLock { resolveKeyVersion() })

    // Called under keyVersionLock. Existing records may have only the key name and the original public material.
    private suspend fun resolveKeyVersion(): String = keyVersion ?: withContext(Dispatchers.IO) {
        val keys = KeyVaultClientFactory.keyClient(config.auth.keyVaultUrl)
        val latest = keys.getKey(id)
        val latestVersion = latest.version()
        val latestPublicKey = latest.toWaltPublicKey()
        val expectedThumbprint = _publicKey?.key?.getThumbprint()
        val matchingVersion = if (expectedThumbprint == null || latestPublicKey.getThumbprint() == expectedThumbprint) {
            latestVersion
        } else {
            keys.listPropertiesOfKeyVersions(id).firstNotNullOfOrNull { properties ->
                val version = properties.version?.takeIf { it.isNotBlank() && it != latestVersion }
                    ?: return@firstNotNullOfOrNull null
                keys.getKey(id, version).takeIf { it.toWaltPublicKey().getThumbprint() == expectedThumbprint }?.version()
            } ?: throw KeyNotFoundException(id, "No Azure Key Vault version matches the stored public key")
        }
        if (_publicKey == null) _publicKey = DirectSerializedKey(latestPublicKey)
        matchingVersion.also { keyVersion = it }
    }

    override suspend fun getPublicKeyRepresentation(): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun getMeta(): KeyMeta {
        TODO("Not yet implemented")
    }

    override suspend fun deleteKey(): Boolean {
        return try {
            val keyClient = KeyVaultClientFactory.keyClient(config.auth.keyVaultUrl)
            val poller = keyClient.beginDeleteKey(id)
            poller.waitForCompletion()
            true
        } catch (e: ResourceNotFoundException) {
            throw KeyNotFoundException(id, "Key not found in Azure Key Vault", e)
        } catch (e: com.azure.core.exception.HttpResponseException) {
            when (e.response.statusCode) {
                403 -> throw UnauthorizedKeyAccess("Access denied to delete key '$id'", e)
                else -> throw KeyVaultUnavailable("Failed to delete key: ${e.message}", e)
            }
        } catch (e: Exception) {
            throw CryptoStateException("Failed to delete key: ${e.message}", e)
        }
    }


    companion object {
        private const val EC_COMPONENT_SIZE = 32

        private fun KeyVaultKey.version(): String =
            requireNotNull(properties.version?.takeIf { it.isNotBlank() }) { "Azure Key Vault did not return a key version" }

        private suspend fun KeyVaultKey.toWaltPublicKey(): JWKKey {
            val imported = JWKKey.importJWK(key.toString()).getOrThrow()
            return parseAzurePublicKey(imported.exportJWKObject()).publicKey
        }

        data class ParsedAzurePublicKey(
            val kid: String,
            val azureKeyType: String,
            val curve: String?,
            val keyType: KeyType,
            val publicKey: JWKKey,
        )

        internal fun azureKeyToKeyTypeMapping(crv: String, kty: String): KeyType =
            KeyTypes.getKeyTypeByJwkId(jwkKty = kty, jwkCrv = crv)

        internal suspend fun parseAzurePublicKey(publicKeyJson: JsonObject): ParsedAzurePublicKey {
            val kid = publicKeyJson["kid"]?.jsonPrimitive?.content ?: error("No key id in key response")
            val azureKeyType =
                publicKeyJson["kty"]?.jsonPrimitive?.content ?: error("Missing key type in public key response")
            val crvFromResponse = publicKeyJson["crv"]?.jsonPrimitive?.content
            val publicKeyJsonModified = publicKeyJson.toMutableMap().apply {
                remove("key_ops")
                remove("kid")
            }
            val materialOnly = JWKKey.importJWK(publicKeyJsonModified.toMap().toJsonElement().toString())
                .getOrElse { exception ->
                    throw IllegalArgumentException(
                        "Invalid JWK in public key: $publicKeyJson",
                        exception
                    )
                }
            val thumbprint = materialOnly.getThumbprint()
            val publicKey = JWKKey.importJWK(
                JsonObject(materialOnly.exportJWKObject() + ("kid" to JsonPrimitive(thumbprint))).toString()
            ).getOrElse { exception ->
                throw IllegalArgumentException("Failed to assign public thumbprint kid: $publicKeyJson", exception)
            }

            val keyType = azureKeyToKeyTypeMapping(crvFromResponse ?: "", azureKeyType)

            return ParsedAzurePublicKey(kid, azureKeyType, crvFromResponse, keyType, publicKey)
        }

        suspend fun generateKey(keyType: KeyType, config: AzureKeyMetadataSDK): AzureKey {
            return try {
                val keyClient = KeyVaultClientFactory.keyClient(config.auth.keyVaultUrl)


                val keyName = config.keyName
                    ?.takeIf { it.isNotBlank() }
                    ?: "key-${System.currentTimeMillis()}"


                val createKeyOptions = when (keyType) {
                    KeyType.secp256r1 -> CreateEcKeyOptions(keyName).apply {
                        curveName = KeyCurveName.P_256
                        tags = config.tags
                    }

                    KeyType.secp256k1 -> CreateEcKeyOptions(keyName).apply {
                        curveName = KeyCurveName.P_256K
                        tags = config.tags
                    }

                    KeyType.RSA -> CreateRsaKeyOptions(keyName).apply {
                        keySize = 2048
                        tags = config.tags
                    }

                    else -> {
                        throw KeyTypeNotSupportedException(keyType.name)
                    }
                }

                val keyVaultKey = keyClient.createKey(createKeyOptions)
                val editedConfig = config.copy(tags = null)
                AzureKey(
                    config = editedConfig,
                    id = keyVaultKey.name,
                    _publicKey = DirectSerializedKey(keyVaultKey.toWaltPublicKey()),
                    _keyType = keyType
                ).also { it.keyVersion = keyVaultKey.version() }
            } catch (e: com.azure.core.exception.HttpResponseException) {
                when (e.response.statusCode) {
                    403 -> throw UnauthorizedKeyAccess("Access denied. Check RBAC permissions for Key Vault.", e)
                    else -> throw KeyVaultUnavailable("Failed to create key in Azure Key Vault: ${e.message}", e)
                }
            } catch (e: KeyTypeNotSupportedException) {
                throw e
            } catch (e: Exception) {
                throw KeyCreationFailed("Failed to generate Azure key: ${e.message}", e)
            }
        }


        suspend fun getAzurePublicKey(config: AzureKeyMetadataSDK, keyName: String): Key {
            return try {
                val keyClient = KeyVaultClientFactory.keyClient(config.auth.keyVaultUrl)
                val keyVaultKey = keyClient.getKey(keyName)

                keyVaultKey.toWaltPublicKey()
            } catch (e: ResourceNotFoundException) {
                throw KeyNotFoundException(keyName, "Key not found in Azure Key Vault", e)
            } catch (e: com.azure.core.exception.HttpResponseException) {
                when (e.response.statusCode) {
                    403 -> throw UnauthorizedKeyAccess("Access denied to key '$keyName'", e)
                    else -> throw KeyVaultUnavailable("Failed to retrieve key: ${e.message}", e)
                }
            } catch (e: Exception) {
                throw CryptoStateException("Failed to retrieve Azure public key: ${e.message}", e)
            }
        }
    }
}
