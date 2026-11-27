package id.walt.wallet2.mobile.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal fun JsonObject.encodeJsonObject(): String =
    Json.encodeToString(JsonObject.serializer(), this)
