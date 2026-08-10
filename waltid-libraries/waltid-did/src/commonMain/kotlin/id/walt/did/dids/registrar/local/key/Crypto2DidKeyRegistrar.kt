package id.walt.did.dids.registrar.local.key

import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.MultiBaseUtils.convertRawKeyToMultiBase58Btc
import id.walt.crypto.utils.MultiCodecUtils
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeySpec
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidKeyDocument
import id.walt.did.dids.registrar.Crypto2DidRegistrar
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.exportPublicJwkObject
import id.walt.did.utils.JsonCanonicalization
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class Crypto2DidKeyRegistrar : Crypto2DidRegistrar {
    override suspend fun createByKey(key: Key, options: DidCreateOptions): DidResult {
        val publicJwk = key.exportPublicJwkObject()
        val (code, publicBytes) = if (options.get<Boolean>("useJwkJcsPub") == true) {
            MultiCodecUtils.JwkJcsPubMultiCodecKeyCode to jwkJcsPublicBytes(publicJwk)
        } else {
            rawIdentifierComponents(key.spec, publicJwk)
        }
        val identifier = convertRawKeyToMultiBase58Btc(publicBytes, code)
        val did = "did:key:$identifier"
        return DidResult(did, DidDocument(DidKeyDocument(did, identifier, publicJwk).toMap()))
    }

    private fun rawIdentifierComponents(spec: KeySpec, jwk: JsonObject): Pair<UInt, ByteArray> = when (spec) {
        is KeySpec.Edwards -> when (spec.curve) {
            EdwardsCurve.ED25519 -> {
                requireJwk(jwk, kty = "OKP", curve = spec.curve.name)
                ED25519_CODEC to jwk.requiredBytes("x", ED25519_PUBLIC_BYTES)
            }
            else -> unsupportedRawEncoding(spec)
        }
        is KeySpec.Ec -> ecIdentifierComponents(spec, jwk)
        is KeySpec.Rsa -> {
            requireJwk(jwk, kty = "RSA")
            RSA_CODEC to rsaPublicKeyDer(jwk.requiredBytes("n"), jwk.requiredBytes("e"))
        }
        is KeySpec.Montgomery, is KeySpec.Symmetric, is KeySpec.Custom -> unsupportedRawEncoding(spec)
    }

    private fun ecIdentifierComponents(spec: KeySpec.Ec, jwk: JsonObject): Pair<UInt, ByteArray> {
        val encoding = when (spec.curve) {
            EcCurve.SECP256K1 -> EcEncoding(SECP256K1_CODEC, 32)
            EcCurve.P256 -> EcEncoding(P256_CODEC, 32)
            EcCurve.P384 -> EcEncoding(P384_CODEC, 48)
            EcCurve.P521 -> EcEncoding(P521_CODEC, 66)
            else -> unsupportedRawEncoding(spec)
        }
        requireJwk(jwk, kty = "EC", curve = spec.curve.name)
        val x = jwk.requiredBytes("x", encoding.coordinateBytes)
        val y = jwk.requiredBytes("y", encoding.coordinateBytes)
        val prefix = if (y.last().toInt() and 1 == 0) 0x02 else 0x03
        return encoding.codec to byteArrayOf(prefix.toByte()) + x
    }

    private fun jwkJcsPublicBytes(jwk: JsonObject): ByteArray {
        val required = when (jwk.requiredString("kty")) {
            "OKP" -> jwk.requiredMembers("crv", "kty", "x")
            "EC" -> jwk.requiredMembers("crv", "kty", "x", "y")
            "RSA" -> jwk.requiredMembers("e", "kty", "n")
            else -> throw IllegalArgumentException("JWK-JCS did:key requires an asymmetric public JWK")
        }
        return JsonCanonicalization.getCanonicalBytes(Json.encodeToString(required))
    }

    private fun rsaPublicKeyDer(modulus: ByteArray, exponent: ByteArray): ByteArray =
        der(0x30, derInteger(modulus) + derInteger(exponent))

    private fun derInteger(unsigned: ByteArray): ByteArray {
        val stripped = unsigned.dropWhile { it == 0.toByte() }.toByteArray().let {
            if (it.isEmpty()) byteArrayOf(0) else it
        }
        val value = if (stripped.first().toInt() and 0x80 != 0) byteArrayOf(0) + stripped else stripped
        return der(0x02, value)
    }

    private fun der(tag: Int, value: ByteArray): ByteArray = byteArrayOf(tag.toByte()) + derLength(value.size) + value

    private fun derLength(size: Int): ByteArray {
        if (size < 0x80) return byteArrayOf(size.toByte())
        var remaining = size
        val bytes = mutableListOf<Byte>()
        while (remaining > 0) {
            bytes.add(0, remaining.toByte())
            remaining = remaining ushr 8
        }
        return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
    }

    private fun requireJwk(jwk: JsonObject, kty: String, curve: String? = null) {
        require(jwk.requiredString("kty") == kty) { "Crypto2 key spec does not match exported JWK kty" }
        curve?.let { require(jwk.requiredString("crv") == it) { "Crypto2 key spec does not match exported JWK curve" } }
    }

    private fun JsonObject.requiredMembers(vararg names: String): JsonObject = JsonObject(
        names.associateWith { name -> this[name] ?: throw IllegalArgumentException("Public JWK is missing $name") }
    )

    private fun JsonObject.requiredBytes(name: String, size: Int? = null): ByteArray =
        requiredString(name).decodeFromBase64Url().also { decoded ->
            size?.let { require(decoded.size == it) { "Public JWK $name has invalid length for its key spec" } }
        }

    private fun JsonObject.requiredString(name: String): String =
        (this[name] as? JsonPrimitive)?.content?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Public JWK is missing $name")

    private fun unsupportedRawEncoding(spec: KeySpec): Nothing = throw IllegalArgumentException(
        "Portable raw did:key encoding is not supported for crypto2 key spec $spec; set useJwkJcsPub=true"
    )

    private data class EcEncoding(val codec: UInt, val coordinateBytes: Int)

    private companion object {
        const val ED25519_CODEC = 0xEDu
        const val SECP256K1_CODEC = 0xE7u
        const val P256_CODEC = 0x1200u
        const val P384_CODEC = 0x1201u
        const val P521_CODEC = 0x1202u
        const val RSA_CODEC = 0x1205u
        const val ED25519_PUBLIC_BYTES = 32
    }
}
