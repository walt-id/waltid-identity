package id.walt.openid4vci.requests.credential.encryption

import id.walt.crypto.utils.JweUtils
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile.requireSupportedHeader
import kotlinx.serialization.json.JsonObject

class JweCredentialRequestDecryptor(
    private val privateJwk: String,
) : CredentialRequestDecryptor {
    override suspend fun decrypt(compactJwe: String): JsonObject {
        val parts = JweUtils.parseJWE(compactJwe, privateJwk)
        requireSupportedHeader(parts.header, "credential request")
        println("we are in")
        return parts.payload
    }
}
