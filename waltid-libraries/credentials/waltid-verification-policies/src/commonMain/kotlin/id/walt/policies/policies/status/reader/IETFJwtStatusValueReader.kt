package id.walt.policies.policies.status.reader

import id.walt.policies.policies.status.content.ContentParser
import id.walt.policies.policies.status.model.IETFStatusContent
import id.walt.policies.policies.status.reader.format.FormatMatcher
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

class IETFJwtStatusValueReader(
    formatMatcher: FormatMatcher,
    parser: ContentParser<String, JsonObject>,
) : JwtStatusValueReaderBase<IETFStatusContent>(formatMatcher, parser) {
    override fun parseStatusList(payload: JsonObject): IETFStatusContent {
        val statusList = payload["status_list"]?.jsonObject
        requireNotNull(statusList) { "Missing or invalid 'status_list' in JWT payload" }
        logger.debug { "status_list: $statusList" }
        return jsonModule.decodeFromJsonElement<IETFStatusContent>(statusList)
    }
}
