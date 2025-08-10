package id.walt.crypto.keys

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object KeyUtils {


    fun rawSignaturePayloadForJws(
        plaintext: ByteArray,
        headers: Map<String, JsonElement>,
        keyType: KeyType,
    ): Triple<String, String, ByteArray> {
        val appendedHeader = HashMap(headers).apply {
            put("alg", keyType.jwsAlg.toJsonElement())
        }

        val header = Json.encodeToString(appendedHeader).encodeToByteArray().encodeToBase64Url()
        val payload = plaintext.encodeToBase64Url()

        return Triple(header, payload, "$header.$payload".encodeToByteArray())
    }

    fun signJwsWithRawSignature(
        rawSignature: ByteArray,
        header: String,
        payload: String
    ): String {
        val encodedSignature = rawSignature.encodeToBase64Url()
        val jws = "$header.$payload.$encodedSignature"

        return jws
    }

}
