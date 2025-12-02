package id.walt.policies2.vc.policies.status.reader

import id.walt.policies2.vc.policies.status.content.ContentParser
import id.walt.policies2.vc.policies.status.model.W3CStatusContent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

class W3CStatusValueReader(
    parser: ContentParser<String, JsonObject>,
) : id.walt.policies2.vc.policies.status.reader.JwtStatusValueReaderBase<W3CStatusContent>(parser) {

    override fun parseStatusList(payload: JsonObject): W3CStatusContent {
        val credentialSubject = payload["vc"]!!.jsonObject["credentialSubject"]?.jsonObject!!
        _root_ide_package_.id.walt.policies2.vc.policies.status.reader.JwtStatusValueReaderBase.logger.debug { "CredentialSubject: $credentialSubject" }
        return _root_ide_package_.id.walt.policies2.vc.policies.status.reader.JwtStatusValueReaderBase.jsonModule.decodeFromJsonElement<W3CStatusContent>(credentialSubject)
    }
}
