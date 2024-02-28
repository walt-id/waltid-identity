package id.walt.credentials.utils

import id.walt.crypto.utils.JsonUtils.toJsonObject
import kotlinx.serialization.json.*

@OptIn(ExperimentalJsExport::class)
@JsExport
object CredentialBuilderUtils {
    fun generateCredentialSubject(credentialSubject: String): JsonObject {
        val credentialSubjectJson = Json.parseToJsonElement(credentialSubject)
        return credentialSubjectJson.jsonObject.toJsonObject()
    }
}
