package id.walt.did.utils

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.decodeBase58
import id.walt.did.utils.KeyUtils.fromPublicKeyMultiBase
import id.walt.did.utils.KeyUtils.getKeyTypeForVerificationMaterialType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object KeyMaterial {
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
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

    private suspend fun importJwk(element: JsonObject): Result<Key> = JWKKey.importJWK(element.toString())

    private suspend fun importBase58(content: String, type: KeyType): Result<Key> = runCatching {
        JWKKey.importRawPublicKey(type, content.decodeBase58(), null)
    }

    private suspend fun importMultibase(content: String): Result<Key> = fromPublicKeyMultiBase(content)

    private suspend fun importHex(content: String, type: KeyType): Result<Key> = runCatching {
        JWKKey.importRawPublicKey(type, fromHexString(content), null)
    }

    private fun fromHexString(hexString: String) =
        hexString.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /*private fun toHexString(byteArray: ByteArray) =
        byteArray.joinToString("") { String.format("%02X ", (it.toInt() and 0xFF)) }*/
}
