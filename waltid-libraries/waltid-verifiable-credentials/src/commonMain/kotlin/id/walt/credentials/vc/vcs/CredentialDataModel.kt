package id.walt.credentials.vc.vcs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
sealed interface CredentialDataModel {
    fun encodeToJsonObject(): JsonObject
    @JsExport.Ignore
    companion object {
        internal val w3cJson = Json {
            explicitNulls = false
        }
    }
}
