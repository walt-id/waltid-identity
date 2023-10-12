package id.walt.credentials.schemes

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive


class JwsSignatureScheme : SignatureScheme {

    object JwsHeader {
        const val KEY_ID = "kid"
    }

    object JwsOption {
        const val SUBJECT = "sub"
        const val ISSUER = "iss"
        const val EXPIRATION = "exp"
        const val NOT_BEFORE = "nbf"
        const val VC_ID = "jti"
        const val VC = "vc"
    }

    /**
     * args:
     * - kid: Key ID
     * - subjectDid: Holder DID
     * - issuerDid: Issuer DID
     */
    suspend fun sign(
        data: JsonObject, key: Key,
        /** Set additional options in the JWT header */
        jwtHeaders: Map<String, String> = emptyMap(),
        /** Set additional options in the JWT payload */
        jwtOptions: Map<String, String> = emptyMap(),
    ): String {
        val header = mapOf(
            JwsHeader.KEY_ID to jwtHeaders[JwsHeader.KEY_ID].toString(),
        ).also { println("Header: $it") }

        val payload = Json.encodeToString(
            mapOf(
                JwsOption.SUBJECT to JsonPrimitive(jwtOptions["subjectDid"]),
                JwsOption.ISSUER to JsonPrimitive(jwtOptions["issuerDid"]),
                JwsOption.VC to data,
            )
        ).also { println("Payload: $it") }.encodeToByteArray()

        return key.signJws(payload, header)
    }

    suspend fun verify(data: String): Result<JsonObject> = runCatching {
        val jws = data.decodeJws()

        val payload = jws.payload

        val issuerDid = payload[JwsOption.ISSUER]!!.jsonPrimitive.content
//        val subjectDid = payload["sub"]!!.jsonPrimitive.content
//        println("Issuer: $issuerDid")
//        println("Subject: $subjectDid")

        DidService.resolveToKey(issuerDid).getOrThrow()
            .verifyJws(data).getOrThrow()
    }
}
