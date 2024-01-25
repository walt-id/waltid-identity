package id.walt.did.utils

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.keys.LocalKeyMetadata
import id.walt.did.utils.KeyUtils.fromPublicKeyMultiBase
import id.walt.did.utils.KeyUtils.getKeyTypeForVerificationMaterialType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object KeyMaterial {
    suspend fun get(element: JsonElement): Result<Key> = when (element) {
        is JsonObject -> importKey(element)
//        is JsonPrimitive -> TODO: did
        else -> throw Exception("Failed to find public key element: $element")
    }

    private suspend fun importKey(element: JsonObject): Result<Key> {
        val type = element.jsonObject["type"]!!.jsonPrimitive.content
        element.jsonObject["publicKeyJwk"]?.jsonObject?.run {
            return importJwk(this)
        }
        element.jsonObject["publicKeyBase58"]?.jsonPrimitive?.content?.run {
            return importBase58(this, getKeyTypeForVerificationMaterialType(type))
        }
        element.jsonObject["publicKeyMultibase"]?.jsonPrimitive?.content?.run {
            return importMultibase(this)
        }
        element.jsonObject["publicKeyHex"]?.jsonPrimitive?.content?.run {
            return importHex(this, getKeyTypeForVerificationMaterialType(type))
        }
        //TODO: blockchainAccountId
        throw IllegalArgumentException("Public key format not supported: $element.")
    }

    private suspend fun importJwk(element: JsonObject): Result<Key> = LocalKey.importJWK(element.toString())

    private suspend fun importBase58(content: String, type: KeyType): Result<Key> = runCatching {
        LocalKey.importRawPublicKey(type, EncodingUtils.base58Decode(content), LocalKeyMetadata())
    }

    private suspend fun importMultibase(content: String): Result<Key> = fromPublicKeyMultiBase(content)

    private suspend fun importHex(content: String, type: KeyType): Result<Key> = runCatching {
        LocalKey.importRawPublicKey(type, EncodingUtils.fromHexString(content), LocalKeyMetadata())
    }
}