package id.walt.w3c.utils

import id.walt.crypto.utils.JsonUtils.toJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalJsExport::class)
@JsExport
object CredentialBuilderUtils {
    fun generateCredentialSubject(credentialSubject: String): JsonObject {
        val credentialSubjectJson = Json.parseToJsonElement(credentialSubject)
        return credentialSubjectJson.jsonObject.toJsonObject()
    }
}
