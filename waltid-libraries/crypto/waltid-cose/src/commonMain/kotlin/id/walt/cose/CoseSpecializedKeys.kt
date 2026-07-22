@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.cose

import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

@Serializable
data class CoseRsaKey(
    @CborLabel(1) val kty: Int,
    @CborLabel(2) @ByteString val kid: ByteArray? = null,
    @CborLabel(3) val alg: Int? = null,
    @CborLabel(4) val keyOperations: List<Int>? = null,
    @CborLabel(-1) @ByteString val n: ByteArray,
    @CborLabel(-2) @ByteString val e: ByteArray,
    @CborLabel(-3) @ByteString val d: ByteArray? = null,
    @CborLabel(-4) @ByteString val p: ByteArray? = null,
    @CborLabel(-5) @ByteString val q: ByteArray? = null,
    @CborLabel(-6) @ByteString val dp: ByteArray? = null,
    @CborLabel(-7) @ByteString val dq: ByteArray? = null,
    @CborLabel(-8) @ByteString val qi: ByteArray? = null,
) {
    init {
        require(kty == Cose.KeyTypes.RSA)
        require(n.isNotEmpty() && n.first() != 0.toByte()) { "RSA modulus must be a canonical unsigned integer" }
        require(e.isNotEmpty() && e.first() != 0.toByte()) { "RSA exponent must be a canonical unsigned integer" }
        val privateValues = listOf(d, p, q, dp, dq, qi)
        require(privateValues.all { it == null } || privateValues.all { it != null }) {
            "RSA COSE_Key must contain either all or no private CRT parameters"
        }
    }

    fun serialize(): ByteArray = coseCompliantCbor.encodeToByteArray(this)

    override fun equals(other: Any?): Boolean = other is CoseRsaKey &&
        kty == other.kty && kid.contentEquals(other.kid) && alg == other.alg && keyOperations == other.keyOperations &&
        n.contentEquals(other.n) && e.contentEquals(other.e) && d.contentEquals(other.d) &&
        p.contentEquals(other.p) && q.contentEquals(other.q) && dp.contentEquals(other.dp) &&
        dq.contentEquals(other.dq) && qi.contentEquals(other.qi)

    override fun hashCode(): Int = listOf(
        kty,
        kid.contentHashCode(),
        alg,
        keyOperations,
        n.contentHashCode(),
        e.contentHashCode(),
        d.contentHashCode(),
        p.contentHashCode(),
        q.contentHashCode(),
        dp.contentHashCode(),
        dq.contentHashCode(),
        qi.contentHashCode(),
    ).fold(1) { result, value -> 31 * result + (value?.hashCode() ?: 0) }

    fun toEncodedJwk(): EncodedKey.Jwk {
        val jwk = buildJsonObject {
            put("kty", "RSA")
            put("n", n.encodeBase64Url())
            put("e", e.encodeBase64Url())
            d?.let { put("d", it.encodeBase64Url()) }
            p?.let { put("p", it.encodeBase64Url()) }
            q?.let { put("q", it.encodeBase64Url()) }
            dp?.let { put("dp", it.encodeBase64Url()) }
            dq?.let { put("dq", it.encodeBase64Url()) }
            qi?.let { put("qi", it.encodeBase64Url()) }
        }
        return EncodedKey.Jwk(
            BinaryData(Json.encodeToString(jwk).encodeToByteArray()),
            privateMaterial = d != null,
        )
    }

    companion object {
        fun deserialize(encoded: ByteArray): CoseRsaKey = coseCompliantCbor.decodeFromByteArray(encoded)
    }
}

@Serializable
data class CoseSymmetricKey(
    @CborLabel(1) val kty: Int,
    @CborLabel(2) @ByteString val kid: ByteArray? = null,
    @CborLabel(3) val alg: Int? = null,
    @CborLabel(4) val keyOperations: List<Int>? = null,
    @CborLabel(-1) @ByteString val k: ByteArray,
) {
    init {
        require(kty == Cose.KeyTypes.SYMMETRIC)
        require(k.isNotEmpty())
    }

    fun serialize(): ByteArray = coseCompliantCbor.encodeToByteArray(this)

    override fun equals(other: Any?): Boolean = other is CoseSymmetricKey &&
        kty == other.kty && kid.contentEquals(other.kid) && alg == other.alg &&
        keyOperations == other.keyOperations && k.contentEquals(other.k)

    override fun hashCode(): Int = listOf(kty, kid.contentHashCode(), alg, keyOperations, k.contentHashCode())
        .fold(1) { result, value -> 31 * result + (value?.hashCode() ?: 0) }

    fun toEncodedJwk(): EncodedKey.Jwk = EncodedKey.Jwk(
        BinaryData(Json.encodeToString(buildJsonObject {
            put("kty", "oct")
            put("k", k.encodeBase64Url())
        }).encodeToByteArray()),
        privateMaterial = true,
    )

    companion object {
        fun deserialize(encoded: ByteArray): CoseSymmetricKey = coseCompliantCbor.decodeFromByteArray(encoded)
    }
}

fun EncodedKey.Jwk.toCoseRsaKey(
    algorithm: Int? = null,
    keyOperations: List<Int>? = null,
    keyId: ByteArray? = null,
): CoseRsaKey {
    val jwk = parseJwk()
    require(jwk.requiredString("kty") == "RSA") { "RSA COSE_Key requires RSA JWK" }
    require("oth" !in jwk) { "Multi-prime RSA JWK is not supported" }
    return CoseRsaKey(
        kty = Cose.KeyTypes.RSA,
        kid = keyId,
        alg = algorithm,
        keyOperations = keyOperations,
        n = jwk.requiredBase64UrlUInt("n"),
        e = jwk.requiredBase64UrlUInt("e"),
        d = jwk.optionalBase64UrlUInt("d"),
        p = jwk.optionalBase64UrlUInt("p"),
        q = jwk.optionalBase64UrlUInt("q"),
        dp = jwk.optionalBase64UrlUInt("dp"),
        dq = jwk.optionalBase64UrlUInt("dq"),
        qi = jwk.optionalBase64UrlUInt("qi"),
    )
}

fun EncodedKey.Jwk.toCoseSymmetricKey(
    algorithm: Int? = null,
    keyOperations: List<Int>? = null,
    keyId: ByteArray? = null,
): CoseSymmetricKey {
    val jwk = parseJwk()
    require(jwk.requiredString("kty") == "oct") { "Symmetric COSE_Key requires oct JWK" }
    return CoseSymmetricKey(
        kty = Cose.KeyTypes.SYMMETRIC,
        kid = keyId,
        alg = algorithm,
        keyOperations = keyOperations,
        k = jwk.requiredBase64Url("k"),
    )
}

private val specializedKeyBase64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

private fun EncodedKey.Jwk.parseJwk(): JsonObject =
    Json.parseToJsonElement(data.toByteArray().decodeToString()) as? JsonObject
        ?: throw IllegalArgumentException("JWK must be a JSON object")

private fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.also { require(it.isString) }?.content
        ?: throw IllegalArgumentException("JWK $name member is missing")

private fun JsonObject.requiredBase64Url(name: String): ByteArray =
    optionalBase64Url(name) ?: throw IllegalArgumentException("JWK $name member is missing")

private fun JsonObject.optionalBase64Url(name: String): ByteArray? =
    this[name]?.jsonPrimitive?.also { require(it.isString) }?.content?.let { value ->
        require('=' !in value)
        specializedKeyBase64Url.decode(value).also { require(specializedKeyBase64Url.encode(it) == value) }
    }

private fun JsonObject.requiredBase64UrlUInt(name: String): ByteArray =
    optionalBase64UrlUInt(name) ?: throw IllegalArgumentException("JWK $name member is missing")

private fun JsonObject.optionalBase64UrlUInt(name: String): ByteArray? = optionalBase64Url(name)?.also {
    require(it.isNotEmpty() && it.first() != 0.toByte()) { "JWK $name must be a canonical Base64urlUInt" }
}

private fun ByteArray.encodeBase64Url(): String = specializedKeyBase64Url.encode(this)
