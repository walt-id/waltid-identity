package id.walt.credentials.schemes

import id.walt.core.crypto.keys.Key
import id.walt.core.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*


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
        jwtOptions: Map<String, JsonElement> = emptyMap(),
    ): String {
        val payload = Json.encodeToString(
            mapOf(
                JwsOption.ISSUER to jwtOptions[JwsOption.ISSUER],
                JwsOption.SUBJECT to jwtOptions[JwsOption.SUBJECT],
                JwsOption.VC to data,
                *(jwtOptions.entries.map { it.toPair() }.toTypedArray())
            )
        ).encodeToByteArray()

        return key.signJws(payload, jwtHeaders)
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
