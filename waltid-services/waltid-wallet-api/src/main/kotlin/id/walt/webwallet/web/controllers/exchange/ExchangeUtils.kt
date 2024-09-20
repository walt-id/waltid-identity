package id.walt.webwallet.web.controllers.exchange

import kotlinx.serialization.json.*

object ExchangeUtils {

    fun getFirstAuthKeyIdFromDidDocument(
        didDocument: String,
    ) = runCatching {
        Json.decodeFromString<JsonObject>(didDocument).let { document ->
            checkNotNull(document["authentication"]) { "no authentication relationship defined in resolved did document" }
            check(document["authentication"] is JsonArray) { "resolved did document is invalid: authentication relationship ${document["authentication"]} is not a json array" }
            check(document["authentication"]!!.jsonArray.size > 0) { "resolved did document's authentication relationship is an empty json array" }
            when (val firstAuthRelEntry = document["authentication"]!!.jsonArray.first()) {
                is JsonObject -> {
                    checkNotNull(firstAuthRelEntry["id"]) { "resolved did document's authentication relationship first entry does not contain an id property" }
                    check(
                        (firstAuthRelEntry["id"] is JsonPrimitive) &&
                                (firstAuthRelEntry["id"]!!.jsonPrimitive.isString)
                    ) {
                        "id property of the first entry of the authentication relationship of the resolved did document is not a string"
                    }
                    firstAuthRelEntry["id"]!!.jsonPrimitive.content
                }

                is JsonPrimitive -> {
                    check(firstAuthRelEntry.isString) { "first entry of the authentication relationship of the resolved did document is encoded as a reference to a verification method but is not of type string" }
                    firstAuthRelEntry.content
                }

                else -> {
                    throw IllegalArgumentException("resolved did document's authentication relationship first entry is neither a json object nor a json primitive of type string")
                }
            }
        }
    }.onFailure {
        throw it
    }
}