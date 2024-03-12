package id.walt.oid4vc.data

import id.walt.crypto.keys.Key
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
    val jwt: String?,
    val cwt: String?,
    val ldp_vp: JsonObject?,
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
                          private val trustChain: JsonArray? = null): ProofBuilder() {
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
            put("iat", Clock.System.now().epochSeconds)
            nonce?.let { put("nonce", nonce) }
        }

        override suspend fun build(key: Key): ProofOfPossession {
            return ProofOfPossession(ProofType.jwt, key.signJws(payload.toString().toByteArray(), headers.mapValues { it.toString() }), null, null)
        }

        fun build(signedJwt: String): ProofOfPossession {
            return ProofOfPossession(ProofType.jwt, signedJwt, null, null)
        }

    }

    companion object : JsonDataObjectFactory<ProofOfPossession>() {
        const val JWT_HEADER_TYPE = "openid4vci-proof+jwt"
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement(ProofOfPossessionSerializer, jsonObject)
    }
}

object ProofOfPossessionSerializer : JsonDataObjectSerializer<ProofOfPossession>(ProofOfPossession.serializer())

enum class ProofType {
    jwt, cwt, ldp_vp
}
