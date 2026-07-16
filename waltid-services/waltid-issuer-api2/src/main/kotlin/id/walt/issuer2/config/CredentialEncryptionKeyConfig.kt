package id.walt.issuer2.config

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.requests.credential.encryption.JweCredentialRequestDecryptor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

object CredentialEncryptionKeyConfig {
    private const val VALIDATION_ERROR =
        "credentialEncryptionKey must contain an exportable EC P-256 private key for ECDH-ES/A128GCM credential request encryption"

    fun validate(serializedKey: String) {
        publicMetadataJwk(serializedKey)
    }

    fun requestDecryptor(serializedKey: String): JweCredentialRequestDecryptor =
        JweCredentialRequestDecryptor(privateJwk(serializedKey))

    fun publicMetadataJwk(serializedKey: String): JsonObject = resolve {
        resolveKey(serializedKey).toCredentialRequestEncryptionJwk()
    }

    private fun privateJwk(serializedKey: String): String = resolve {
        val key = resolveKey(serializedKey)
        require(key.hasPrivateKey) { VALIDATION_ERROR }

        key.exportJWK().also { jwk ->
            require(CredentialEncryptionProfile.isSupportedCredentialRequestDecryptionJwk(jwk)) {
                VALIDATION_ERROR
            }
        }
    }

    private suspend fun resolveKey(serializedKey: String): Key {
        require(serializedKey.isNotBlank()) { "credentialEncryptionKey must not be blank when provided" }
        return KeyManager.resolveSerializedKey(serializedKey)
    }

    private suspend fun Key.toCredentialRequestEncryptionJwk(): JsonObject {
        require(hasPrivateKey) { VALIDATION_ERROR }

        val privateJwk = exportJWK()
        require(CredentialEncryptionProfile.isSupportedCredentialRequestDecryptionJwk(privateJwk)) {
            VALIDATION_ERROR
        }

        val publicKey = getPublicKey()
        val keyId = publicKey.getKeyId().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException(VALIDATION_ERROR)
        val publicJwk = publicKey.exportJWKObject()

        val encryptionJwk = buildJsonObject {
            publicJwk.forEach { (name, value) ->
                if (name != "d") put(name, value)
            }
            put("kid", JsonPrimitive(keyId))
            put("alg", CredentialEncryptionProfile.ALG_ECDH_ES)
            put("use", CredentialEncryptionProfile.KEY_USE_ENC)
        }

        require(CredentialEncryptionProfile.isSupportedCredentialRequestEncryptionJwk(encryptionJwk)) {
            VALIDATION_ERROR
        }
        return encryptionJwk
    }

    private fun <T> resolve(block: suspend () -> T): T =
        try {
            runBlocking { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException(VALIDATION_ERROR, e)
        }
}
