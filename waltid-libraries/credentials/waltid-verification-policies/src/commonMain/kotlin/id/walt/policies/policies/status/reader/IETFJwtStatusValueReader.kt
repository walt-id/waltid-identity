package id.walt.policies.policies.status.reader

import id.walt.policies.policies.status.content.ContentParser
import id.walt.policies.policies.status.model.IETFStatusContent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

class IETFJwtStatusValueReader(
    parser: ContentParser<String, JsonObject>,
) : JwtStatusValueReaderBase<IETFStatusContent>(parser) {
    override fun parseStatusList(payload: JsonObject): IETFStatusContent {
        val statusList = payload["status_list"]?.jsonObject!!
        logger.debug { "status_list: $statusList" }
        return jsonModule.decodeFromJsonElement<IETFStatusContent>(statusList)
    }
}
