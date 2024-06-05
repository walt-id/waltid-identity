package id.walt.credentials

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.DidService
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalJsExport::class)
@JsExport
class PresentationBuilder {

    /***
     * VP subject/issuer
     */
    var did: String? = null

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
    var presentationId = "urn:uuid:" + UUID.generateUUID().toString()

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
        "sub" to did,
        "nbf" to jwtNotBefore?.epochSeconds,
        "iat" to jwtIssuedAt?.epochSeconds,
        "jti" to presentationId,
        "iss" to did,
        "nonce" to (nonce ?: ""),
        "aud" to (audience ?: ""),
        "vp" to mapOf(
            "@context" to vpContext,
            "type" to vpType,
            "id" to presentationId,
            "holder" to did,
            "verifiableCredential" to verifiableCredentials
        )
    )

    fun buildPresentationJson() = buildPresentationMap().toJsonElement()
    fun buildPresentationJsonString() = Json.encodeToString(buildPresentationJson())
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun buildAndSign(key: Key): String {
        return key.signJws(
            plaintext = buildPresentationJsonString().encodeToByteArray(),
            headers = mapOf(
                "kid" to resolveDidAuthentication(did ?: throw IllegalStateException("No DID set in PresentationBuilder")),
                "typ" to "JWT"
            )
        )
    }

    private suspend fun resolveDidAuthentication(did: String): String {
        return DidService.resolve(did).getOrThrow()["authentication"]!!.jsonArray.first().let {
            if (it is JsonObject) {
                it.jsonObject["id"]!!.jsonPrimitive.content
            } else {
                it.jsonPrimitive.content
            }
        }
    }
}
