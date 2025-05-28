package id.walt.policies.policies.status.reader

import id.walt.policies.policies.status.BitValueReader
import id.walt.policies.policies.status.IETFStatusContent
import id.walt.policies.policies.status.parser.ContentParser
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

class IETFJwtStatusValueReader(
    parser: ContentParser<JsonObject>,
    bitValueReader: BitValueReader,
) : StatusValueReaderBase<JsonObject, IETFStatusContent>(parser, bitValueReader) {
    override fun parseStatusList(payload: JsonObject): IETFStatusContent {
        val statusList = payload["status_list"]?.jsonObject!!
        logger.debug { "status_list: $statusList" }
        return jsonModule.decodeFromJsonElement<IETFStatusContent>(statusList)
    }
}