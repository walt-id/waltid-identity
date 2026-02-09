package id.walt.policies.policies.status.reader

import id.walt.policies.policies.status.content.ContentParser
import id.walt.policies.policies.status.model.W3CStatusContent
import id.walt.policies.policies.status.reader.format.FormatMatcher
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

class W3CStatusValueReader(
    formatMatcher: FormatMatcher,
    parser: ContentParser<String, JsonObject>,
) : JwtStatusValueReaderBase<W3CStatusContent>(formatMatcher, parser) {

    override fun parseStatusList(payload: JsonObject): W3CStatusContent {
        val credentialSubject = payload["vc"]!!.jsonObject["credentialSubject"]?.jsonObject!!
        logger.debug { "CredentialSubject: $credentialSubject" }
        return jsonModule.decodeFromJsonElement<W3CStatusContent>(credentialSubject)
    }
}
