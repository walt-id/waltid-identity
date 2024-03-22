package id.walt.cli.util

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class VCUtil {

    suspend fun sign(key: JWKKey, issuerDid: String, subjectDid: String, payload: String): String {
        val vcAsMap = Json.decodeFromString<Map<String, JsonElement>>(payload)
        val vc = W3CVC(vcAsMap)
        val jws = vc.signJws(
            issuerKey = key, issuerDid = issuerDid, subjectDid = subjectDid
        )

        return jws
    }
}