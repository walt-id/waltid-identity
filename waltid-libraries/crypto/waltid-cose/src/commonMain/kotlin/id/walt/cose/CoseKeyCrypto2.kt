package id.walt.cose

import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

fun EncodedKey.Jwk.toCoseKey(
    algorithm: Int? = null,
    keyOperations: List<Int>? = null,
    keyId: ByteArray? = null,
): CoseKey {
    val jwk = Json.parseToJsonElement(data.toByteArray().decodeToString()).let {
        it as? JsonObject ?: throw IllegalArgumentException("JWK must be a JSON object")
    }
    val keyType = jwk.requiredString("kty")
    val curve = jwk["crv"]?.jsonPrimitive?.content
    val coseKeyType = when (keyType) {
        "EC" -> Cose.KeyTypes.EC2
        "OKP" -> Cose.KeyTypes.OKP
        "RSA" -> throw IllegalArgumentException("RSA COSE_Key requires the RSA key model")
        "oct" -> throw IllegalArgumentException("Symmetric COSE_Key requires the symmetric key model")
        else -> throw IllegalArgumentException("Unsupported JWK key type: $keyType")
    }
    val coseCurve = when (keyType) {
        "EC" -> when (curve) {
            "P-256" -> Cose.EllipticCurves.P_256
            "P-384" -> Cose.EllipticCurves.P_384
            "P-521" -> Cose.EllipticCurves.P_521
            "secp256k1" -> Cose.EllipticCurves.secp256k1
            else -> throw IllegalArgumentException("Unsupported EC JWK curve: $curve")
        }
        "OKP" -> when (curve) {
            "X25519" -> Cose.EllipticCurves.X25519
            "X448" -> Cose.EllipticCurves.X448
            "Ed25519" -> Cose.EllipticCurves.Ed25519
            "Ed448" -> Cose.EllipticCurves.Ed448
            else -> throw IllegalArgumentException("Unsupported OKP JWK curve: $curve")
        }
        else -> error("Unsupported JWK key type: $keyType")
    }
    val privateMembers = jwk.keys intersect privateJwkMemberNames
    require(privateMembers.all { it == "d" }) { "EC and OKP JWKs cannot contain $privateMembers" }
    require(privateMaterial == ("d" in jwk)) { "JWK private-material metadata does not match contents" }
    require(keyType != "OKP" || "y" !in jwk) { "OKP JWK cannot contain a y coordinate" }
    return CoseKey(
        kty = coseKeyType,
        kid = keyId,
        alg = algorithm,
        key_ops = keyOperations,
        crv = coseCurve,
        x = jwk.requiredBase64Url("x"),
        y = jwk["y"]?.let { jwk.requiredBase64Url("y") },
        d = if (privateMaterial) jwk.requiredBase64Url("d") else null,
    ).also {
        require(coseKeyType != Cose.KeyTypes.EC2 || it.y != null) { "EC JWK y coordinate is missing" }
    }
}

fun CoseKey.toEncodedJwk(): EncodedKey.Jwk {
    val jwk = toJWK()
    return EncodedKey.Jwk(
        data = BinaryData(Json.encodeToString(jwk).encodeToByteArray()),
        privateMaterial = "d" in jwk,
    )
}

private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
private val privateJwkMemberNames = setOf("d", "p", "q", "dp", "dq", "qi", "oth", "k")

private fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.also { require(it.isString) { "JWK $name member must be a string" } }?.content
        ?: throw IllegalArgumentException("JWK $name member is missing")

private fun JsonObject.requiredBase64Url(name: String): ByteArray = requiredString(name).decodeBase64Url()

private fun String.decodeBase64Url(): ByteArray {
    require('=' !in this) { "JWK members must use unpadded base64url" }
    return base64Url.decode(this).also { require(base64Url.encode(it) == this) { "JWK member is not canonical base64url" } }
}
