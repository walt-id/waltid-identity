package id.walt.sdjwt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@JsExport
class SDPayloadBuilder(
    val fullPayload: dynamic
) {
    fun buildForUndisclosedPayload(undisclosedSDPayload: dynamic): SDPayload {
        return SDPayload.createSDPayload(
            Json.parseToJsonElement(JSON.stringify(fullPayload)).jsonObject,
            Json.parseToJsonElement(JSON.stringify(undisclosedSDPayload)).jsonObject
        )
    }

    fun buildForSDMap(sdMap: dynamic): SDPayload {
        return SDPayload.createSDPayload(
            Json.parseToJsonElement(JSON.stringify(fullPayload)).jsonObject,
            SDMap.Companion.fromJSON(JSON.stringify(sdMap))
        )
    }
}
