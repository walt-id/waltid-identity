package id.walt.openid4vci.requests.credential.encryption

import kotlinx.serialization.json.JsonObject

fun interface CredentialRequestDecryptor {
    suspend fun decrypt(compactJwe: String): JsonObject
}
