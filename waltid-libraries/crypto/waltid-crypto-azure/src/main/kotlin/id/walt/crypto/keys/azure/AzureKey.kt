package id.walt.crypto.keys.azure

import com.azure.core.exception.ResourceNotFoundException
import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm
import com.azure.security.keyvault.keys.models.CreateEcKeyOptions
import com.azure.security.keyvault.keys.models.CreateRsaKeyOptions
import com.azure.security.keyvault.keys.models.KeyCurveName
import id.walt.crypto.exceptions.*
import id.walt.crypto.keys.*
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.coroutines.reactor.awaitSingle
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

    @Transient
    override var keyType: KeyType
        get() = _keyType!!
        set(value) {
            _keyType = value
        }

    override val hasPrivateKey: Boolean
        get() = true

    override fun toString(): String = "[Azure ${keyType.name} key @KeyVault - $id]"

    override suspend fun getKeyId(): String = getPublicKey().getKeyId()

    override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    override suspend fun exportJWK(): String = throw NotImplementedError("JWK export is not available for remote keys.")

    override suspend fun exportJWKObject(): JsonObject = Json.parseToJsonElement(_publicKey!!.toString()).jsonObject

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

            val cryptoClient = KeyVaultClientFactory.cryptoClient(config.auth.keyVaultUrl, id)
            val signResult = cryptoClient.sign(azureSignatureAlgorithm, digest).awaitSingle()

            signResult.signature ?: throw SigningException("Azure Key Vault returned null signature")
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

        var rawSignature = signRaw("$header.$payload".encodeToByteArray())

        val encodedSignature = rawSignature.encodeToBase64Url()
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

            val cryptoClient = KeyVaultClientFactory.cryptoClient(config.auth.keyVaultUrl, id)
            val verifyResult = cryptoClient.verify(azureSignatureAlgorithm, digest, signed).awaitSingle()

            if (verifyResult.isValid) {
                Result.success(messageToVerify)
            } else {
                Result.failure(VerificationException("Signature verification failed"))
            }
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
    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        val publicKey = getPublicKey()
        return publicKey.verifyJws(signedJws)
    }

    @Transient
    private var backedKey: Key? = null

    override suspend fun getPublicKey(): Key = backedKey ?: when {
        _publicKey != null -> _publicKey!!.key
        else -> retrievePublicKey()
    }.also { newBackedKey -> backedKey = newBackedKey }

    private suspend fun retrievePublicKey(): Key {
        val publicKey = getAzurePublicKey(config, id)
        _publicKey = DirectSerializedKey(publicKey)
        return publicKey
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
            val publicKeyJsonModified = publicKeyJson.toMutableMap()
            publicKeyJsonModified.remove("key_ops")
            val publicKey = JWKKey.importJWK(publicKeyJsonModified.toMap().toJsonElement().toString())
                .getOrElse { exception ->
                    throw IllegalArgumentException(
                        "Invalid JWK in public key: $publicKeyJson",
                        exception
                    )
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
                val publicKey = getAzurePublicKey(config, keyVaultKey.name)

                val normalizedPublicKey = parseAzurePublicKey(publicKey.exportJWKObject()).publicKey
                val editedConfig = config.copy(tags = null)
                AzureKey(
                    config = editedConfig,
                    id = keyVaultKey.name,
                    _publicKey = DirectSerializedKey(normalizedPublicKey),
                    _keyType = keyType
                )
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

                val jwk = keyVaultKey.key
                val jwkJson = jwk.toString() // Azure SDK's JsonWebKey has a toString that outputs JWK JSON

                JWKKey.importJWK(jwkJson).getOrThrow()
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
