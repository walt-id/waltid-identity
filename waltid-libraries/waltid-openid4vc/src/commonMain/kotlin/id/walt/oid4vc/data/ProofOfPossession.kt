package id.walt.oid4vc.data

import id.walt.crypto.keys.Key
import id.walt.mdoc.dataelement.*
import id.walt.oid4vc.util.JwtUtils
import io.ktor.utils.io.core.*
import kotlinx.datetime.Clock
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class ProofOfPossession @OptIn(ExperimentalSerializationApi::class) private constructor(
    @EncodeDefault @SerialName("proof_type") val proofType: ProofType,
    val jwt: String? = null,
    val cwt: String? = null,
    val ldp_vp: JsonObject? = null,
    override val customParameters: Map<String, JsonElement> = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(ProofOfPossessionSerializer, this).jsonObject

    suspend fun validateJwtProof(key: Key,
                                 issuerUrl: String, clientId: String?, nonce: String?, keyId: String?): Boolean {
        return proofType == ProofType.jwt && jwt != null &&
            key.verifyJws(jwt).isSuccess &&
            JwtUtils.parseJWTHeader(jwt).let { header ->
                header.containsKey("typ") && header["typ"]?.jsonPrimitive?.content?.equals(JWT_HEADER_TYPE) ?: false &&
                (keyId.isNullOrEmpty() || header.containsKey("kid") && header["kid"]!!.jsonPrimitive.content == keyId)
            } &&
            JwtUtils.parseJWTPayload(jwt).let { payload ->
                (issuerUrl.isNotEmpty() && payload.containsKey("aud") && payload["aud"]!!.jsonPrimitive.content == issuerUrl) &&
                (clientId.isNullOrEmpty() || payload.containsKey("iss") && payload["iss"]!!.jsonPrimitive.content == clientId) &&
                (nonce.isNullOrEmpty() || payload.containsKey("nonce") && payload["nonce"]!!.jsonPrimitive.content == nonce)
            }
    }

    abstract class ProofBuilder() {
        abstract suspend fun build(key: Key): ProofOfPossession
    }

    class JWTProofBuilder(private val issuerUrl: String, private val clientId: String?,
                          private val nonce: String?, private val keyId: String?,
                          private val keyJwk: JsonObject? = null, private val x5c: JsonArray? = null,
                          private val trustChain: JsonArray? = null, private val audience: String? = null): ProofBuilder() {
        val headers = buildJsonObject {
            put("typ", JWT_HEADER_TYPE)
            keyId?.let { put("kid", it) }
            keyJwk?.let { put("jwk", it) }
            x5c?.let { put("x5c", it) }
            trustChain?.let { put("trust_chain", it) }
        }
        val payload = buildJsonObject {
            clientId?.let { put("iss", it) }
            put("aud", issuerUrl)
            audience?.let { put("aud", it) }
            put("iat", Clock.System.now().epochSeconds)
            nonce?.let { put("nonce", it) }
        }

        override suspend fun build(key: Key): ProofOfPossession {
            return ProofOfPossession(ProofType.jwt, key.signJws(payload.toString().toByteArray(),
                // NOTE: this assumes every header to be string valued, due to the Key interface of crypto-lib
                headers.mapValues { it.value.jsonPrimitive.content }),
                null, null)
        }

        fun build(signedJwt: String): ProofOfPossession {
            return ProofOfPossession(ProofType.jwt, signedJwt, null, null)
        }

    }

    class CWTProofBuilder(private val issuerUrl: String,
                          private val clientId: String?, private val nonce: String?,
                          private val coseKey: ByteArray?, private val x5Chain: ByteArray?): ProofBuilder() {
        val HEADER_LABEL_ALG = 1
        val HEADER_LABEL_CONTENT_TYPE = 3
        val HEADER_LABEL_COSE_KEY = "COSE_Key"
        val HEADER_LABEL_X5CHAIN = 33
        val LABEL_ISS = 1
        val LABEL_AUD = 3
        val LABEL_IAT = 6
        val LABEL_NONCE = 10
        val headers = MapElement(buildMap {
            put(MapKey(HEADER_LABEL_CONTENT_TYPE), StringElement(CWT_HEADER_TYPE))
            coseKey?.let { put(MapKey(HEADER_LABEL_COSE_KEY), ByteStringElement(it)) }
            x5Chain?.let { put(MapKey(HEADER_LABEL_X5CHAIN), ByteStringElement(it)) }
        })
        val payload = MapElement(buildMap {
            clientId?.let { put(MapKey(LABEL_ISS), StringElement(it)) }
            put(MapKey(LABEL_AUD), StringElement(issuerUrl))
            put(MapKey(LABEL_IAT), NumberElement(Clock.System.now().epochSeconds))
            nonce?.let { put(MapKey(LABEL_NONCE), ByteStringElement(it.toByteArray())) }
        })

        override suspend fun build(key: Key): ProofOfPossession {
            TODO("Not yet implemented")
        }

    }

    companion object : JsonDataObjectFactory<ProofOfPossession>() {
        const val JWT_HEADER_TYPE = "openid4vci-proof+jwt"
        const val CWT_HEADER_TYPE = "openid4vci-proof+cwt"
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement(ProofOfPossessionSerializer, jsonObject)
    }
}

object ProofOfPossessionSerializer : JsonDataObjectSerializer<ProofOfPossession>(ProofOfPossession.serializer())

enum class ProofType {
    jwt, cwt, ldp_vp
}
