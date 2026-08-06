@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.crypto2.keys

import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.KeyWrappingAlgorithm
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

@Serializable
@JsonClassDiscriminator("kind")
sealed interface AsymmetricCiphertext {
    val algorithm: AsymmetricEncryptionAlgorithm

    @Serializable
    @SerialName("raw")
    data class Raw(
        override val algorithm: AsymmetricEncryptionAlgorithm,
        val data: BinaryData,
    ) : AsymmetricCiphertext

    @Serializable
    @SerialName("opaque")
    data class Opaque(
        override val algorithm: AsymmetricEncryptionAlgorithm,
        val provider: ProviderId,
        val keyId: KeyId,
        val blob: BinaryData,
        val keyVersion: String? = null,
        val context: Map<String, String> = emptyMap(),
        val providerData: BinaryData = BinaryData(byteArrayOf()),
    ) : AsymmetricCiphertext
}

@Serializable
data class EncodedKeyMaterial(
    val spec: KeySpec,
    val key: EncodedKey,
) {
    init {
        if (key is EncodedKey.Jwk) validateJwkSpec(spec, key)
    }
}

@Serializable
@JsonClassDiscriminator("kind")
sealed interface WrappedKey {
    val algorithm: KeyWrappingAlgorithm
    val blob: BinaryData
    val wrappedKeySpec: KeySpec

    @Serializable
    @SerialName("raw")
    data class Raw(
        override val algorithm: KeyWrappingAlgorithm,
        override val blob: BinaryData,
        override val wrappedKeySpec: KeySpec,
        val wrappingKeyId: KeyId? = null,
    ) : WrappedKey

    @Serializable
    @SerialName("opaque")
    data class Opaque(
        override val algorithm: KeyWrappingAlgorithm,
        override val blob: BinaryData,
        override val wrappedKeySpec: KeySpec,
        val provider: ProviderId,
        val wrappingKeyId: KeyId,
        val keyVersion: String? = null,
        val providerData: BinaryData = BinaryData(byteArrayOf()),
    ) : WrappedKey
}

private fun validateJwkSpec(spec: KeySpec, key: EncodedKey.Jwk) {
    val jwk = Json.parseToJsonElement(key.data.toByteArray().decodeToString()).jsonObject
    val keyType = jwk["kty"]?.jsonPrimitive?.content ?: error("JWK kty is missing")
    val containsPrivateMaterial = if (keyType == "oct") "k" in jwk else "d" in jwk
    require(key.privateMaterial == containsPrivateMaterial) {
        "JWK private-material flag does not match its contents"
    }
    when (spec) {
        is KeySpec.Ec -> {
            require(keyType == "EC") { "EC key material requires EC JWK" }
            require(jwk["crv"]?.jsonPrimitive?.content == spec.curve.name) { "EC JWK curve does not match spec" }
        }
        is KeySpec.Edwards -> {
            require(keyType == "OKP") { "Edwards key material requires OKP JWK" }
            require(jwk["crv"]?.jsonPrimitive?.content == spec.curve.name) { "OKP JWK curve does not match spec" }
        }
        is KeySpec.Montgomery -> {
            require(keyType == "OKP") { "Montgomery key material requires OKP JWK" }
            require(jwk["crv"]?.jsonPrimitive?.content == spec.curve.name) { "OKP JWK curve does not match spec" }
        }
        is KeySpec.Rsa -> {
            require(keyType == "RSA") { "RSA key material requires RSA JWK" }
            val modulus = decodeBase64Url(jwk["n"]?.jsonPrimitive?.content ?: error("RSA modulus is missing"))
            require(bitLength(modulus) == spec.bits) { "RSA JWK size does not match spec" }
        }
        is KeySpec.Symmetric -> {
            require(keyType == "oct") { "Symmetric key material requires oct JWK" }
            val secret = decodeBase64Url(jwk["k"]?.jsonPrimitive?.content ?: error("Symmetric key is missing"))
            require(secret.size * Byte.SIZE_BITS == spec.bits) { "Symmetric JWK size does not match spec" }
        }
        is KeySpec.Custom -> Unit
    }
}

private fun decodeBase64Url(value: String): ByteArray {
    require('=' !in value) { "JWK values must use unpadded base64url" }
    return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(value)
}

private fun bitLength(value: ByteArray): Int {
    require(value.isNotEmpty())
    return (value.size - 1) * Byte.SIZE_BITS +
        (Int.SIZE_BITS - (value.first().toInt() and 0xff).countLeadingZeroBits())
}
