package id.walt.openid4vci.responses.credential.encryption

import id.walt.openid4vci.requests.credential.encryption.CredentialResponseEncryptionParameters
import kotlinx.serialization.json.JsonObject

fun interface CredentialResponseEncryptor {
    fun encrypt(
        payload: JsonObject,
        encryption: CredentialResponseEncryptionParameters,
    ): String
}
