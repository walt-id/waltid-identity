package id.walt.cli.util

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.LocalKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class VCUtil {

    fun sign(key: LocalKey, issuerDid: String, subjectDid: String, payload: String) {
        println("VC signed")
        val vcAsMap = Json.decodeFromString<Map<String, JsonElement>>(payload)
        val vc = W3CVC(vcAsMap)

    }
}