package id.walt.policies.policies.status.reader

import id.walt.policies.policies.status.W3CStatusContent
import id.walt.policies.policies.status.parser.ContentParser
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

class W3CStatusValueReader(
    parser: ContentParser<String, JsonObject>,
) : JwtStatusValueReaderBase<W3CStatusContent>(parser) {

    override fun parseStatusList(payload: JsonObject): W3CStatusContent {
        val credentialSubject = payload["vc"]!!.jsonObject["credentialSubject"]?.jsonObject!!
        logger.debug { "CredentialSubject: $credentialSubject" }
        return jsonModule.decodeFromJsonElement<W3CStatusContent>(credentialSubject)
    }
}