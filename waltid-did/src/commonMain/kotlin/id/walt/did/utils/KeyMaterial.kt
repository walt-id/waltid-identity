package id.walt.did.utils

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.LocalKey
import id.walt.did.dids.DidUtils
import kotlinx.serialization.json.*

object KeyMaterial {
    suspend fun get(element: JsonElement): Result<Key> = when (element) {
        is JsonObject -> parseKey(element)
        is JsonPrimitive -> parseBase58(element.jsonPrimitive.content)
        else -> throw Exception("Failed to find public key element: $element")
    }

    private suspend fun parseKey(element: JsonObject): Result<Key> {
        element.jsonObject["publicKeyJwk"]?.jsonObject?.run {
            return parseJwk(this)
        }
        element.jsonObject["publicKeyBase58"]?.jsonPrimitive?.content?.run {
            return parseBase58(this)
        }
        throw IllegalArgumentException("Could not parse public key format: $element.")
    }

    private suspend fun parseJwk(element: JsonObject): Result<Key> = LocalKey.importJWK(element.toString())

    private suspend fun parseBase58(content: String): Result<Key> = DidUtils.identifierFromDid(content)?.let {
        KeyUtils.fromPublicKeyMultiBase58(it)
    } ?: Result.failure(Exception("Failed to compute publicKeyBase58: $content."))
}