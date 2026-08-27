@file:OptIn(
    dev.whyoleg.cryptography.CryptographyProviderApi::class,
    dev.whyoleg.cryptography.DelicateCryptographyApi::class,
)

package id.walt.mdoc.proximity

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.XDH
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.MontgomeryCurve
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

internal object MdocSessionKeyValidator {
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    suspend fun requireCompatiblePeerKey(
        localKey: Key,
        peerKey: EncodedKey.Jwk,
        provider: CryptographyProvider,
    ): EncodedKey.Jwk {
        requireSupportedLocalKey(localKey)
        require(!peerKey.privateMaterial) { "Ephemeral peer key must contain public material only" }
        val peerSpec = specAndValidatePoint(peerKey, provider)
        require(localKey.spec == peerSpec) { "Ephemeral session key curves do not match" }
        return peerKey
    }

    fun requireSupportedLocalKey(key: Key) {
        val algorithm = agreementAlgorithm(key.spec)
        require(key.capabilities.supportsKeyAgreementAlgorithm(algorithm)) {
            "Session key does not support ${algorithm.displayName}"
        }
    }

    fun agreementAlgorithm(key: Key): KeyAgreementAlgorithm = agreementAlgorithm(key.spec).also { algorithm ->
        require(key.capabilities.supportsKeyAgreementAlgorithm(algorithm)) {
            "Session key does not support ${algorithm.displayName}"
        }
    }

    private fun agreementAlgorithm(spec: KeySpec): KeyAgreementAlgorithm = when (spec) {
        KeySpec.Ec(EcCurve.P256), KeySpec.Ec(EcCurve.P384), KeySpec.Ec(EcCurve.P521) ->
            KeyAgreementAlgorithm.Ecdh
        KeySpec.Montgomery(MontgomeryCurve.X25519), KeySpec.Montgomery(MontgomeryCurve.X448) ->
            KeyAgreementAlgorithm.Xdh
        else -> throw IllegalArgumentException("Unsupported cipher-suite 1 session key specification: $spec")
    }

    private suspend fun specAndValidatePoint(key: EncodedKey.Jwk, provider: CryptographyProvider): KeySpec {
        val jwk = Json.parseToJsonElement(key.data.toByteArray().decodeToString()) as? JsonObject
            ?: throw IllegalArgumentException("Ephemeral peer JWK must be an object")
        require("d" !in jwk) { "Ephemeral peer key cannot contain private key material" }
        require("alg" !in jwk || jwk.required("alg").isNotBlank()) { "Ephemeral peer key algorithm metadata is malformed" }
        return when (jwk.required("kty")) {
            "EC" -> validateEcPoint(jwk, provider)
            "OKP" -> validateXdhPoint(jwk, provider)
            else -> throw IllegalArgumentException("Cipher-suite 1 requires an EC2 or XDH session key")
        }
    }

    private suspend fun validateEcPoint(jwk: JsonObject, provider: CryptographyProvider): KeySpec.Ec {
        val (curve, coordinateBytes, providerCurve) = when (jwk.required("crv")) {
            "P-256" -> Triple(EcCurve.P256, 32, EC.Curve.P256)
            "P-384" -> Triple(EcCurve.P384, 48, EC.Curve.P384)
            "P-521" -> Triple(EcCurve.P521, 66, EC.Curve.P521)
            else -> throw IllegalArgumentException("Unsupported EC cipher-suite 1 session curve")
        }
        val x = jwk.requiredCoordinate("x", coordinateBytes)
        val y = jwk.requiredCoordinate("y", coordinateBytes)
        val raw = byteArrayOf(0x04) + x + y
        provider.get(ECDH).publicKeyDecoder(providerCurve).decodeFromByteArray(EC.PublicKey.Format.RAW, raw)
        return KeySpec.Ec(curve)
    }

    private suspend fun validateXdhPoint(jwk: JsonObject, provider: CryptographyProvider): KeySpec.Montgomery {
        require("y" !in jwk) { "XDH session keys cannot contain a y coordinate" }
        val (curve, coordinateBytes, providerCurve) = when (jwk.required("crv")) {
            "X25519" -> Triple(MontgomeryCurve.X25519, 32, XDH.Curve.X25519)
            "X448" -> Triple(MontgomeryCurve.X448, 56, XDH.Curve.X448)
            else -> throw IllegalArgumentException("Unsupported XDH cipher-suite 1 session curve")
        }
        val x = jwk.requiredCoordinate("x", coordinateBytes)
        provider.get(XDH).publicKeyDecoder(providerCurve).decodeFromByteArray(XDH.PublicKey.Format.RAW, x)
        return KeySpec.Montgomery(curve)
    }

    private fun JsonObject.required(name: String): String = this[name]?.jsonPrimitive?.content
        ?: throw IllegalArgumentException("Ephemeral peer JWK member $name is missing")

    private fun JsonObject.requiredCoordinate(name: String, size: Int): ByteArray = required(name).let { value ->
        require('=' !in value) { "Ephemeral peer coordinates must use unpadded base64url" }
        base64Url.decode(value).also { require(it.size == size && base64Url.encode(it) == value) { "Ephemeral peer coordinate $name is not canonical" } }
    }

    private val KeyAgreementAlgorithm.displayName: String get() = when (this) {
        KeyAgreementAlgorithm.Ecdh -> "ECDH"
        KeyAgreementAlgorithm.Xdh -> "XDH"
        is KeyAgreementAlgorithm.Custom -> id
        is KeyAgreementAlgorithm.Named -> id
    }
}
