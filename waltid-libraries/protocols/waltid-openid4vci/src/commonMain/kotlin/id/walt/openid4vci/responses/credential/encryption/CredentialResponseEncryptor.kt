package id.walt.openid4vci.responses.credential.encryption

import id.walt.openid4vci.requests.credential.encryption.CredentialResponseEncryptionParameters
import kotlinx.serialization.json.JsonObject

fun interface CredentialResponseEncryptor {
    /** Exercise key import and agreement before durable acceptance; discard the probe ciphertext. */
    suspend fun validate(encryption: CredentialResponseEncryptionParameters) {
        encrypt(JsonObject(emptyMap()), encryption)
    }

    /**
     * Suspending because crypto2 key agreement is asynchronous on every target; a remote KMS or a device keystore
     * cannot be driven from a blocking call, and commonMain has no runBlocking to hide it behind.
     */
    suspend fun encrypt(
        payload: JsonObject,
        encryption: CredentialResponseEncryptionParameters,
    ): String
}
