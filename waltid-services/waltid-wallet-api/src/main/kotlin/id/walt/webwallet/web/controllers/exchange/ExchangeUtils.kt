package id.walt.webwallet.web.controllers.exchange

import kotlinx.serialization.json.*

object ExchangeUtils {

    fun getFirstAuthKeyIdFromDidDocument(
        didDocument: String,
    ) = Json.decodeFromString<JsonObject>(didDocument)["authentication"]!!.jsonArray.first().let {
        if (it is JsonObject) {
            it.jsonObject["id"]!!.jsonPrimitive.content
        } else {
            it.jsonPrimitive.content
        }
    }
}