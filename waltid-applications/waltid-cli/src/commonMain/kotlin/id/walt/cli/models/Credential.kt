package id.walt.cli.models

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class Credential(
    val id: String,
    val type: String,
    val parsedDocument: JsonObject,
    val serializedCredential: String,
) {
    companion object {
        fun parseFromJwsString(jwsStr: String): Credential {
            val jwsParts = jwsStr.decodeJws(withSignature = true)
            val credentialId =
                jwsParts.payload["vc"]!!.jsonObject["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: randomUUIDString()
            when (val type = jwsParts.header["typ"]?.jsonPrimitive?.content?.lowercase()) {
                "jwt" -> {
                    return Credential(
                        id = credentialId,
                        type = type,
                        parsedDocument = jwsParts.payload["vc"]?.jsonObject ?: throw IllegalArgumentException("JWT does not have \"vc\" payload field"),
                        serializedCredential = jwsStr,
                    )
                }
                null -> throw IllegalArgumentException("JWT does not have \"typ\" header field")
                else -> throw IllegalArgumentException("Invalid credential \"typ\": $type")
            }
        }
    }
}