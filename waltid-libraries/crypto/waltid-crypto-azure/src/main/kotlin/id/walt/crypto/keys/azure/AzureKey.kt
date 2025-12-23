package id.walt.crypto.keys.azure

import com.azure.core.exception.ResourceNotFoundException
import com.azure.security.keyvault.keys.cryptography.models.SignResult
import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm
import com.azure.security.keyvault.keys.models.CreateEcKeyOptions
import com.azure.security.keyvault.keys.models.CreateRsaKeyOptions
import com.azure.security.keyvault.keys.models.KeyCurveName
import id.walt.crypto.exceptions.*
import id.walt.crypto.keys.*
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.hash.sha2.SHA384
import org.kotlincrypto.hash.sha2.SHA512

@Serializable
@SerialName("azure")
class AzureKey(
    val config: AzureKeyMetadataSDK,
    val id: String,
    private var _publicKey: String? = null,
    private var _keyType: KeyType? = null,
) : Key() {

    @Transient
    override var keyType: KeyType
        get() = _keyType!!
        set(value) {
            _keyType = value
        }

    override val hasPrivateKey: Boolean
        get() = false

    override fun toString(): String = "[Azure ${keyType.name} key @KeyVault - $id]"

    override suspend fun getKeyId(): String = getPublicKey().getKeyId()

    override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    override suspend fun exportJWK(): String = throw NotImplementedError("JWK export is not available for remote keys.")

    override suspend fun exportJWKObject(): JsonObject = Json.parseToJsonElement(_publicKey!!).jsonObject

    override suspend fun exportPEM(): String = throw NotImplementedError("PEM export is not available for remote keys.")


    private val azureSignatureAlgorithm: SignatureAlgorithm
        get() = when (keyType) {
            KeyType.secp256r1 -> SignatureAlgorithm.ES256
            KeyType.secp384r1 -> SignatureAlgorithm.ES384
            KeyType.secp521r1 -> SignatureAlgorithm.ES512
            KeyType.secp256k1 -> SignatureAlgorithm.ES256K
            KeyType.RSA -> SignatureAlgorithm.RS256
            KeyType.RSA3072 -> SignatureAlgorithm.RS384
            KeyType.RSA4096 -> SignatureAlgorithm.RS512
            KeyType.Ed25519 -> throw KeyTypeNotSupportedException(keyType.name)
        }


    private fun getHashFunction(): (ByteArray) -> ByteArray = when (keyType) {
        KeyType.secp256r1, KeyType.secp256k1, KeyType.RSA -> { data -> SHA256().digest(data) }
        KeyType.secp384r1, KeyType.RSA3072 -> { data -> SHA384().digest(data) }
        KeyType.secp521r1, KeyType.RSA4096 -> { data -> SHA512().digest(data) }
        KeyType.Ed25519 -> throw KeyTypeNotSupportedException(keyType.name)
    }

    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
        return try {
            val hashFunction = getHashFunction()
            val digest = hashFunction(plaintext)

            val cryptoClient = KeyVaultClientFactory.cryptoClient(config.vaultUrl, id)
            val signResult: SignResult = cryptoClient.sign(azureSignatureAlgorithm, digest)

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

        if (keyType in KeyTypes.EC_KEYS) {
            rawSignature = EccUtils.convertDERtoIEEEP1363(rawSignature)
        }

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

            val cryptoClient = KeyVaultClientFactory.cryptoClient(config.vaultUrl, id)
            val verifyResult = cryptoClient.verify(azureSignatureAlgorithm, digest, signed)

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
        _publicKey != null -> _publicKey!!.let { JWKKey.importJWK(it).getOrThrow() }
        else -> retrievePublicKey()
    }.also { newBackedKey -> backedKey = newBackedKey }

    private suspend fun retrievePublicKey(): Key {
        val publicKey = getAzurePublicKey(config, id)
        _publicKey = publicKey.exportJWK()
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
            val keyClient = KeyVaultClientFactory.keyClient(config.vaultUrl)
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
            throw KeyCreationFailed("Failed to delete key: ${e.message}", e)
        }
    }

    companion object {

        suspend fun generateKey(keyType: KeyType, config: AzureKeyMetadataSDK): AzureKey {
            return try {
                val keyClient = KeyVaultClientFactory.keyClient(config.vaultUrl)
                val keyName = "key-${System.currentTimeMillis()}"

                val createKeyOptions = when (keyType) {
                    KeyType.secp256r1 -> CreateEcKeyOptions(keyName).apply {
                        curveName = KeyCurveName.P_256
                    }

                    KeyType.secp384r1 -> CreateEcKeyOptions(keyName).apply {
                        curveName = KeyCurveName.P_384
                    }

                    KeyType.secp521r1 -> CreateEcKeyOptions(keyName).apply {
                        curveName = KeyCurveName.P_521
                    }

                    KeyType.secp256k1 -> CreateEcKeyOptions(keyName).apply {
                        curveName = KeyCurveName.P_256K
                    }

                    KeyType.RSA -> CreateRsaKeyOptions(keyName).apply {
                        keySize = 2048
                    }

                    KeyType.RSA3072 -> CreateRsaKeyOptions(keyName).apply {
                        keySize = 3072
                    }

                    KeyType.RSA4096 -> CreateRsaKeyOptions(keyName).apply {
                        keySize = 4096
                    }

                    KeyType.Ed25519 -> throw KeyTypeNotSupportedException(keyType.name)
                }

                val keyVaultKey = keyClient.createKey(createKeyOptions)
                val publicKey = getAzurePublicKey(config, keyVaultKey.name)

                AzureKey(
                    config = config,
                    id = keyVaultKey.name,
                    _publicKey = publicKey.exportJWK(),
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
                val keyClient = KeyVaultClientFactory.keyClient(config.vaultUrl)
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
                throw KeyCreationFailed("Failed to retrieve Azure public key: ${e.message}", e)
            }
        }
    }
}
