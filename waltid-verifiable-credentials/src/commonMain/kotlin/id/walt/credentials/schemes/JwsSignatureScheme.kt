package id.walt.credentials.schemes

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

private val log = KotlinLogging.logger {  }

@OptIn(ExperimentalJsExport::class)
@JsExport
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
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
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
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun verify(data: String): Result<JsonElement> = runCatching {
        val jws = data.decodeJws()

        val header = jws.header
        val payload = jws.payload

        val issuerDid = (payload[JwsOption.ISSUER] ?: header[JwsHeader.KEY_ID])!!.jsonPrimitive.content
        log.trace { "Verifying with issuer did: $issuerDid" }

//        val subjectDid = payload["sub"]!!.jsonPrimitive.content
//        println("Issuer: $issuerDid")
//        println("Subject: $subjectDid")

        DidService.resolveToKey(issuerDid)
            .also { log.trace ( "Imported key: $it from did: $issuerDid, public is: ${it.getOrNull()?.getPublicKey()?.exportJWK()}" ) }
            .getOrThrow()
            .verifyJws(data.split("~")[0])
            .also { log.trace { "Verification result: $it" } }
            .getOrThrow()
    }
}
