package id.walt.policies2.vc.policies.status.reader

import id.walt.policies2.vc.policies.status.content.ContentParser
import id.walt.policies2.vc.policies.status.model.W3CStatusContent
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
