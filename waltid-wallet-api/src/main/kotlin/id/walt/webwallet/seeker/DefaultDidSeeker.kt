package id.walt.webwallet.seeker

import id.walt.webwallet.db.models.WalletCredential
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DefaultDidSeeker : Seeker<String> {
    override fun get(credential: WalletCredential): String = credential.manifest?.let {
        Json.decodeFromString<JsonObject>(it)
    }?.let {
        it.jsonObject["iss"]?.jsonPrimitive?.content
    } ?: "n/a"
}