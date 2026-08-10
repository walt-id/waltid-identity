package id.walt.w3c

import id.walt.crypto.keys.Key
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.did.dids.DidService
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalJsExport::class)
@JsExport
class PresentationBuilder {

    /***
     * VP subject/issuer
     */
    var did: String? = null
    var holderPubKeyJwk: JsonObject? = null

    /**
     * the timestamp after which the JWT shall be considered valid
     * by default 1 min before the current time to not break with slight clock skew
     */
    var jwtNotBefore: Instant? = Clock.System.now().minus(1.minutes)

    /**
     * when the JWT was issued at
     */
    var jwtIssuedAt: Instant? = Clock.System.now()

    /**
     * jti: JWT ID
     */
    var presentationId = "urn:uuid:" + randomUUIDString()

    /**
     * single use Nonce (usually set to a challenge requested by the relying party)
     */
    var nonce: String? = null

    /**
     * aud: ID of verifier or likewise
     */
    var audience: String? = null

    var vpContext = listOf("https://www.w3.org/2018/credentials/v1")
    var vpType = listOf("VerifiablePresentation")

    val verifiableCredentials = ArrayList<JsonElement>()
    fun addCredential(vararg credential: JsonElement) = verifiableCredentials.addAll(credential)
    fun addCredentials(credentials: Collection<JsonElement>) = verifiableCredentials.addAll(credentials)

    fun buildPresentationMap() = mapOf(
        "sub" to (did ?: holderPubKeyJwk?.get("kid")?.jsonPrimitive?.content),
        "nbf" to jwtNotBefore?.epochSeconds,
        "iat" to jwtIssuedAt?.epochSeconds,
        "jti" to presentationId,
        "iss" to (did ?: holderPubKeyJwk?.get("kid")?.jsonPrimitive?.content),
        "nonce" to (nonce ?: ""),
        "aud" to (audience ?: ""),
        "vp" to mapOf(
            "@context" to vpContext,
            "type" to vpType,
            "id" to presentationId,
            "holder" to (did ?: holderPubKeyJwk?.get("kid")?.jsonPrimitive?.content),
            "cnf" to holderPubKeyJwk,
            "verifiableCredential" to verifiableCredentials
        )
    )

    fun buildPresentationJson() = buildPresentationMap().toJsonElement()
    fun buildPresentationJsonString() = Json.encodeToString(buildPresentationJson())

    @Deprecated("Use the crypto2 overload accepting a Key and JwsAlgorithm")
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun buildAndSign(key: Key): String {
        return key.signJws(
            plaintext = buildPresentationJsonString().encodeToByteArray(),
            headers = mapOf(
                "kid" to (did?.let { DidService.resolveAuthenticationMethodId(it, key.getKeyId()) }
                    ?: key.getKeyId()).toJsonElement(),
                "typ" to "JWT".toJsonElement()
            )
        )
    }

    @JsExport.Ignore
    suspend fun buildAndSign(
        key: Crypto2Key,
        algorithm: JwsAlgorithm,
    ): String {
        val keyId = did?.let { DidService.resolveAuthenticationMethodId(it, key.id.value) } ?: key.id.value
        return CompactJws.sign(
            payload = buildPresentationJsonString().encodeToByteArray(),
            key = key,
            algorithm = algorithm,
            protectedHeader = buildJsonObject {
                put("kid", keyId)
                put("typ", "JWT")
            },
        )
    }

}
