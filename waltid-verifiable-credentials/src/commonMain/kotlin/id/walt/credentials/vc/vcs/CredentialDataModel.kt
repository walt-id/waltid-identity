package id.walt.credentials.vc.vcs

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

sealed interface CredentialDataModel {
    fun encodeToJsonObject(): JsonObject

    companion object {
        internal val w3cJson = Json {
            @OptIn(ExperimentalSerializationApi::class)
            explicitNulls = false
        }
    }
}
