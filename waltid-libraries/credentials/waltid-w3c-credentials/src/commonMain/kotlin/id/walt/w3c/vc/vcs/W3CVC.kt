package id.walt.w3c.vc.vcs

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDMapBuilder
import id.walt.sdjwt.SDPayload
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.w3c.schemes.JwsSignatureScheme.JwsHeader
import id.walt.w3c.schemes.JwsSignatureScheme.JwsOption
import io.ktor.utils.io.core.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class W3CVCSerializer : KSerializer<W3CVC> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor
    override fun deserialize(decoder: Decoder): W3CVC = W3CVC(decoder.decodeSerializableValue(JsonObject.serializer()))
    override fun serialize(encoder: Encoder, value: W3CVC) =
        encoder.encodeSerializableValue(JsonObject.serializer(), value.toJsonObject())
}

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable(with = W3CVCSerializer::class)
data class W3CVC(
    private val content: Map<String, JsonElement> = emptyMap()
) : Map<String, JsonElement> by content {


    fun getType() = (get("type") ?: error("No `type` in W3C VC!")).jsonArray.map { it.jsonPrimitive.content }

    fun toJsonObject(additionalProperties: Map<String, JsonElement> = emptyMap()): JsonObject =
        JsonObject(content.plus(additionalProperties))

    fun toJson(): String = Json.encodeToString(content)
    fun toPrettyJson(): String = prettyJson.encodeToString(content)

    fun isV2(): Boolean {
        val context = get("@context") ?: return false
        return when (context) {
            is JsonArray -> context.any { it.jsonPrimitive.contentOrNull == "https://www.w3.org/ns/credentials/v2" }
            is JsonPrimitive -> context.contentOrNull == "https://www.w3.org/ns/credentials/v2"
            else -> false
        }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun signSdJwt(
        issuerKey: Key,
        issuerId: String,
        issuerKid: String?,
        subjectDid: String,
        disclosureMap: SDMap,
        /** Set additional options in the JWT header */
        additionalJwtHeaders: Map<String, JsonElement> = emptyMap(),
        /** Set additional options in the JWT payload */
        additionalJwtOptions: Map<String, JsonElement> = emptyMap()
    ): String {
        val kid = issuerKid ?: issuerKey.getKeyId()
        val wrapInVc = !isV2()
        val payload = JwsSignatureScheme().toPayload(
            data = this.toJsonObject(),
            jwtOptions = mapOf(
                JwsOption.ISSUER to JsonPrimitive(issuerId),
                JwsOption.SUBJECT to JsonPrimitive(subjectDid),
                *(additionalJwtOptions.entries.map { it.toPair() }.toTypedArray())
            ),
            wrapInVc = wrapInVc
        )

        val sdPayload = SDPayload.createSDPayload(
            payload,
            if (wrapInVc) {
                SDMapBuilder(disclosureMap.decoyMode).addField(
                    JwsOption.VC,
                    sd = false,
                    children = disclosureMap
                ).build()
            } else {
                disclosureMap
            }
        )
        val signable = Json.encodeToString(sdPayload.undisclosedPayload).toByteArray()

        val signed = issuerKey.signJws(
            signable, additionalJwtHeaders.plus(
                mapOf(
                    JwsHeader.KEY_ID to kid.toJsonElement()
                )
            )
        )
        return SDJwt.createFromSignedJwt(
            signedJwt = signed,
            sdPayload = sdPayload
        ).toString().plus("~")
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun signJws(
        issuerKey: Key,
        issuerId: String?,
        issuerKid: String? = null,
        subjectDid: String,
        /** Set additional options in the JWT header */
        additionalJwtHeader: Map<String, JsonElement> = emptyMap(),
        /** Set additional options in the JWT payload */
        additionalJwtOptions: Map<String, JsonElement> = emptyMap()
    ): String {
        val kid = issuerKid ?: issuerKey.getKeyId()
        val typHeader = if (isV2()) "vc+jwt" else "JWT"
        val baseHeaders = mapOf(
            JwsHeader.KEY_ID to kid.toJsonElement(),
            "typ" to typHeader.toJsonElement(),
        )

        return JwsSignatureScheme().sign(
            data = this.toJsonObject(),
            key = issuerKey,
            jwtHeaders = baseHeaders + additionalJwtHeader,
            jwtOptions = mapOf(
                JwsOption.ISSUER to JsonPrimitive(issuerId),
                JwsOption.SUBJECT to JsonPrimitive(subjectDid),
                *(additionalJwtOptions.entries.map { it.toPair() }.toTypedArray())
            ),
            wrapInVc = !isV2()
        )
    }

    companion object {
        fun build(
            context: List<String>,
            type: List<String>,
            vararg data: Pair<String, Any>
        ): W3CVC {
            return W3CVC(
                mutableMapOf(
                    "@context" to context.toJsonElement(),
                    "type" to type.toJsonElement()
                ).apply { putAll(data.toMap().mapValues { it.value.toJsonElement() }) }
            )
        }


        fun fromJson(json: String) =
            W3CVC(Json.decodeFromString<Map<String, JsonElement>>(json))

        private val prettyJson = Json { prettyPrint = true }
    }

}
