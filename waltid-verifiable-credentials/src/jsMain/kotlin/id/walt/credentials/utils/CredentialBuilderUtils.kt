package id.walt.credentials.utils

import id.walt.crypto.utils.JsonUtils.toJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@ExperimentalJsExport
@JsExport
object credentialBuilderUtils {
    fun generateCredentialSubject(credentialSubject: String): JsonObject {
        val credentialSubjectJson = Json.parseToJsonElement(credentialSubject)
        return credentialSubjectJson.jsonObject.toJsonObject()
    }
}