package id.walt.credentials.utils

import id.walt.crypto.utils.JsonUtils.toJsonObject
import kotlinx.serialization.json.*

@JsExport
object CredentialBuilderUtils {
    fun generateCredentialSubject(credentialSubject: String): JsonObject {
        val credentialSubjectJson = Json.parseToJsonElement(credentialSubject)
        return credentialSubjectJson.jsonObject.toJsonObject()
    }
}
