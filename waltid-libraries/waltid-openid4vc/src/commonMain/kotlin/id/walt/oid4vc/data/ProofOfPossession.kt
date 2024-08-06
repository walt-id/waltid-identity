package id.walt.oid4vc.data

import cbor.Cbor
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.mdoc.cose.COSECryptoProvider
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.*
import id.walt.oid4vc.util.JwtUtils
import io.ktor.utils.io.core.*
import kotlinx.datetime.Clock
import kotlinx.serialization.*
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

    abstract class ProofBuilder {
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

        override suspend fun build(key: Key) =
            ProofOfPossession(ProofType.jwt, key.signJws(payload.toString().toByteArray(), headers), null, null)

        fun build(signedJwt: String) = ProofOfPossession(ProofType.jwt, signedJwt, null, null)

    }

    /**
     * @param coseKey Cose Key structure, for device/holder key, mutually exclusive with x5Cert and x5Chain!
     * @param x5Cert X509 certificate, for device/holder key, mutually exclusive with coseKey and x5Chain!
     * @param x5Chain X509 certificate chain, for device/holder key, mutually exclusive with x5Cert and coseKey!
     *
     */
    class CWTProofBuilder(private val issuerUrl: String,
                          private val clientId: String?, private val nonce: String?,
                          private val coseKey: ByteArray? = null, private val x5Cert: ByteArray? = null, private val x5Chain: List<ByteArray>? = null): ProofBuilder() {
        val headers = MapElement(buildMap {
            put(MapKey(HEADER_LABEL_CONTENT_TYPE), StringElement(CWT_HEADER_TYPE))
            coseKey?.let { put(MapKey(HEADER_LABEL_COSE_KEY), ByteStringElement(it)) }
            x5Cert?.let { put(MapKey(HEADER_LABEL_X5CHAIN), ByteStringElement(it)) }
            x5Chain?.let { x5c -> put(MapKey(HEADER_LABEL_X5CHAIN), ListElement(x5c.map { ByteStringElement(it) })) }
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

        fun build(cryptoProvider: COSECryptoProvider, keyId: String): ProofOfPossession {
            val signedPayload = cryptoProvider.sign1(payload.toCBOR(), headers, null, keyId)
            return ProofOfPossession(ProofType.cwt, cwt = signedPayload.toCBOR().encodeToBase64Url())
        }

        fun build(signedCwt: String): ProofOfPossession {
            return ProofOfPossession(ProofType.cwt, null, signedCwt, null)
        }

        companion object {
            const val HEADER_LABEL_ALG = 1
            const val HEADER_LABEL_CONTENT_TYPE = 3
            const val HEADER_LABEL_COSE_KEY = "COSE_Key"
            const val HEADER_LABEL_X5CHAIN = 33
            const val LABEL_ISS = 1
            const val LABEL_AUD = 3
            const val LABEL_IAT = 6
            const val LABEL_NONCE = 10
        }
    }

    companion object : JsonDataObjectFactory<ProofOfPossession>() {
        const val JWT_HEADER_TYPE = "openid4vci-proof+jwt"
        const val CWT_HEADER_TYPE = "openid4vci-proof+cwt"
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement(ProofOfPossessionSerializer, jsonObject)
    }

    @Transient
    val isCwtProofType get() = proofType == ProofType.cwt && !cwt.isNullOrEmpty()

    @Transient
    val isJwtProofType get() = proofType == ProofType.jwt && !jwt.isNullOrEmpty()
}

object ProofOfPossessionSerializer : JsonDataObjectSerializer<ProofOfPossession>(ProofOfPossession.serializer())

enum class ProofType {
    jwt, cwt, ldp_vp
}

@Serializable
data class ProofTypeMetadata (
    @SerialName("proof_signing_alg_values_supported") val proofSigningAlgValuesSupported: Set<String>
)
