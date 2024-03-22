package id.walt.cli.util

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.credentials.verification.Verifier
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class VCUtil {

    companion object {

        init {
            runBlocking {
                // TODO: What exactly is needed?
                // DidService.apply {
                //     registerResolver(LocalResolver())
                //     updateResolversForMethods()
                //     registerRegistrar(LocalRegistrar())
                //     updateRegistrarsForMethods()
                // }
                DidService.minimalInit()
            }
        }

        suspend fun sign(key: JWKKey, issuerDid: String, subjectDid: String, payload: String): String {
            val vcAsMap = Json.decodeFromString<Map<String, JsonElement>>(payload)
            val vc = W3CVC(vcAsMap)
            val jws = vc.signJws(
                issuerKey = key, issuerDid = issuerDid, subjectDid = subjectDid
            )

            return jws
        }

        suspend fun verify(jws: String): Result<JsonObject> {
            return Verifier.verifyJws(jws)
            // if (result.isSuccess) return true
            // else return false
        }
    }
}