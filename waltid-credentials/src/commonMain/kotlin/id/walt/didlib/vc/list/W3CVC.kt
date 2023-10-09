package id.walt.didlib.vc.list

import id.walt.core.crypto.keys.Key
import id.walt.core.crypto.utils.JsonUtils.toJsonElement
import id.walt.didlib.schemes.JwsSignatureScheme
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class W3CVC(
    private val content: Map<String, JsonElement>
) : Map<String, JsonElement> by content {


    fun toJsonObject(): JsonObject = JsonObject(content)
    fun toJson(): String = Json.encodeToString(content)
    fun toPrettyJson(): String = prettyJson.encodeToString(content)

    suspend fun signJws(issuerKey: Key, issuerDid: String, subjectDid: String): String {
        return JwsSignatureScheme().sign(this.toJsonObject(), issuerKey, mapOf(
            //"kid" to issuerKey.getKeyId(), // TO//DO
            "kid" to issuerDid,
            "issuerDid" to issuerDid,
            "subjectDid" to subjectDid
        ))
    }

    companion object {
        fun build(
            context: List<String>,
            type: List<String>,
            vararg data: Pair<String, Any>
        ): W3CVC {
            return W3CVC(
                mutableMapOf(
                    "@context" to context.toJsonElement(),
                    "type" to type.toJsonElement()
                ).apply { putAll(data.toMap().mapValues { it.value.toJsonElement() }) }
            )
        }

        fun fromJson(json: String) =
            W3CVC(Json.decodeFromString<Map<String, JsonElement>>(json))

        private val prettyJson = Json { prettyPrint = true }
    }

}
