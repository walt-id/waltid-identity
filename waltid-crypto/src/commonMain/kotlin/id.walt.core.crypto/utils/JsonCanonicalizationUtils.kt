package id.walt.core.crypto.utils

import id.walt.core.crypto.keys.Key
import id.walt.core.crypto.keys.KeyType
import id.walt.core.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.json.JsonObject

object JsonCanonicalizationUtils {

    /**
     * Converts the public key to a required-only members JSON string
     * @param - the key
     * @return - the JSON string representing the public key having only the required members
     */
    suspend fun convertToRequiredMembersJsonString(key: Key): String = key.exportJWKObject().let {
        when (key.keyType) {
            KeyType.Ed25519 -> okpPublicKeyRequiredMembers(it)
            KeyType.secp256k1, KeyType.secp256r1 -> ecPublicKeyRequiredMembers(it)
            KeyType.RSA -> rsaPublicKeyRequiredMembers(it)
            null -> throw IllegalArgumentException("KeyType is not initialized!")
        }
    }.toString()

    private fun okpPublicKeyRequiredMembers(okp: JsonObject): JsonObject = mapOf(
        "crv" to okp["crv"].toJsonElement(),
        "kty" to okp["kty"].toJsonElement(),
        "x" to okp["x"].toJsonElement(),
    ).let {
        JsonObject(it)
    }

    private fun ecPublicKeyRequiredMembers(ec: JsonObject): JsonObject = mapOf(
        "crv" to ec["crv"].toJsonElement(),
        "kty" to ec["kty"].toJsonElement(),
        "x" to ec["x"].toJsonElement(),
        "y" to ec["y"].toJsonElement(),
    ).let {
        JsonObject(it)
    }

    private fun rsaPublicKeyRequiredMembers(rsa: JsonObject): JsonObject = mapOf(
        "e" to rsa["e"].toJsonElement(),
        "kty" to rsa["kty"].toJsonElement(),
        "n" to rsa["n"].toJsonElement(),
    ).let {
        JsonObject(it)
    }
}
