@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package id.walt.oid4vc.data

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.mdoc.cose.COSECryptoProvider
import id.walt.mdoc.dataelement.*
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.util.JwtUtils
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

@ConsistentCopyVisibility
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = ProofOfPossessionSerializer::class)
data class ProofOfPossession private constructor(
    @EncodeDefault @SerialName("proof_type") val proofType: ProofType,
    val jwt: String? = null,
    val cwt: String? = null,
    val ldp_vp: JsonObject? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(ProofOfPossessionSerializer, this).jsonObject

    suspend fun validateJwtProof(
        key: Key,
        issuerUrl: String,
        clientId: String?,
        nonce: String?,
        keyId: String?
    ): Boolean {
        return proofType == ProofType.jwt && jwt != null &&
                key.verifyJws(jwt).isSuccess &&
                JwtUtils.parseJWTHeader(jwt).let { header ->
                    header.containsKey(JWTClaims.Header.type) && header[JWTClaims.Header.type]?.jsonPrimitive?.content?.equals(
                        JWT_HEADER_TYPE
                    ) ?: false &&
                            (keyId.isNullOrEmpty() || header.containsKey(JWTClaims.Header.keyID) && header[JWTClaims.Header.keyID]!!.jsonPrimitive.content == keyId)
                } &&
                JwtUtils.parseJWTPayload(jwt).let { payload ->
                    (issuerUrl.isNotEmpty() && payload.containsKey(JWTClaims.Payload.audience) && payload[JWTClaims.Payload.audience]!!.jsonPrimitive.content == issuerUrl) &&
                            (clientId.isNullOrEmpty() || payload.containsKey(JWTClaims.Payload.issuer) && payload[JWTClaims.Payload.issuer]!!.jsonPrimitive.content == clientId) &&
                            (nonce.isNullOrEmpty() || payload.containsKey(JWTClaims.Payload.nonce) && payload[JWTClaims.Payload.nonce]!!.jsonPrimitive.content == nonce)
                }
    }

    abstract class ProofBuilder {
        abstract suspend fun build(key: Key): ProofOfPossession
    }

    class JWTProofBuilder(
        private val issuerUrl: String,
        private val clientId: String? = null,
        private val nonce: String? = null,
        private val keyId: String? = null,
        private val keyJwk: JsonObject? = null,
        private val x5c: JsonArray? = null,
        private val trustChain: JsonArray? = null,
        private val audience: String? = null,
    ) : ProofBuilder() {

        // TODO: fix the initial issue of the jwk formatting      
        fun normalizeKeyOps(jwk: JsonObject): JsonObject {
            val normalized = jwk.toMutableMap()

            val keyOps = jwk["key_ops"]
            if (keyOps is JsonPrimitive && keyOps.isString) {
                val ops = keyOps.content
                    .removePrefix("[")
                    .removeSuffix("]")
                    .split(",")
                    .map { it.trim() }

                normalized["key_ops"] = JsonArray(
                    ops.map { JsonPrimitive(it) }
                )
            }

            return JsonObject(normalized)
        }

        val fixedJwk = keyJwk?.let { normalizeKeyOps(it) }
        val headers = buildJsonObject {
            put(JWTClaims.Header.type, JWT_HEADER_TYPE)
            keyId?.let { put(JWTClaims.Header.keyID, it) }
            fixedJwk?.let { put(JWTClaims.Header.jwk, it) }
            x5c?.let { put(JWTClaims.Header.x5c, it) }
            trustChain?.let { put("trust_chain", it) }
        }
        @OptIn(ExperimentalUuidApi::class)
        val payload = buildJsonObject {
            clientId?.let {
                put(JWTClaims.Payload.issuer, it)
                put(JWTClaims.Payload.subject, it)
            }
            put(JWTClaims.Payload.jwtID, randomUUIDString())
            audience?.let {
                put(JWTClaims.Payload.audience, it)
            } ?: put(JWTClaims.Payload.audience, issuerUrl)
            put(JWTClaims.Payload.issuedAtTime, Clock.System.now().epochSeconds)
            put(JWTClaims.Payload.expirationTime, Clock.System.now().epochSeconds + 300)
            nonce?.let { put(JWTClaims.Payload.nonce, it) }
        }

        override suspend fun build(key: Key) =
            ProofOfPossession(
                proofType = ProofType.jwt,
                jwt = key.signJws(
                    plaintext = payload.toString().toByteArray(),
                    headers = headers
                ),
                cwt = null,
                ldp_vp = null
            )

        fun build(signedJwt: String) = ProofOfPossession(
            proofType = ProofType.jwt,
            jwt = signedJwt,
            cwt = null,
            ldp_vp = null
        )
    }

    /**
     * @param coseKey Cose Key structure, for device/holder key, mutually exclusive with x5Cert and x5Chain!
     * @param x5Cert X509 certificate, for device/holder key, mutually exclusive with coseKey and x5Chain!
     * @param x5Chain X509 certificate chain, for device/holder key, mutually exclusive with x5Cert and coseKey!
     *
     */
    class CWTProofBuilder(
        private val issuerUrl: String,
        private val clientId: String? = null,
        private val nonce: String? = null,
        private val coseKey: ByteArray? = null,
        private val x5Cert: ByteArray? = null,
        private val x5Chain: List<ByteArray>? = null,
    ) : ProofBuilder() {
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
        override fun fromJSON(jsonObject: JsonObject): ProofOfPossession =
            Json.decodeFromJsonElement(ProofOfPossessionSerializer, jsonObject)
    }

    val isCwtProofType get() = proofType == ProofType.cwt && !cwt.isNullOrEmpty()

    val isJwtProofType get() = proofType == ProofType.jwt && !jwt.isNullOrEmpty()
}

internal object ProofOfPossessionSerializer :
    JsonDataObjectSerializer<ProofOfPossession>(ProofOfPossession.generatedSerializer())

enum class ProofType {
    jwt, cwt, ldp_vp
}

@Serializable
data class ProofTypeMetadata(
    @SerialName("proof_signing_alg_values_supported") val proofSigningAlgValuesSupported: Set<String>
)

object ProofOfPossessionSerializerExternal :
    JsonDataObjectSerializer<ProofOfPossession>(ProofOfPossession.generatedSerializer())