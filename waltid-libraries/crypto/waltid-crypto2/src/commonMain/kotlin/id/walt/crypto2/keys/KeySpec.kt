@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.crypto2.keys

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.jvm.JvmInline

@Serializable
@JsonClassDiscriminator("type")
sealed interface KeySpec {

    @Serializable
    @SerialName("ec")
    data class Ec(val curve: EcCurve) : KeySpec

    @Serializable
    @SerialName("edwards")
    data class Edwards(val curve: EdwardsCurve) : KeySpec

    @Serializable
    @SerialName("montgomery")
    data class Montgomery(val curve: MontgomeryCurve) : KeySpec

    @Serializable
    @SerialName("rsa")
    data class Rsa(val bits: Int) : KeySpec {
        init {
            require(bits > 0) { "RSA key size must be positive" }
        }
    }

    @Serializable
    @SerialName("symmetric")
    data class Symmetric(
        val family: SymmetricKeyType,
        val bits: Int,
    ) : KeySpec {
        init {
            require(bits > 0) { "Symmetric key size must be positive" }
        }
    }

    @Serializable
    @SerialName("custom")
    data class Custom(
        val family: String,
        val parameters: Map<String, String> = emptyMap(),
    ) : KeySpec {
        init {
            require(family.isNotBlank()) { "Custom key family cannot be blank" }
        }
    }
}

@Serializable
@JvmInline
value class EcCurve(val name: String) {
    companion object {
        val P256 = EcCurve("P-256")
        val P384 = EcCurve("P-384")
        val P521 = EcCurve("P-521")
        val SECP256K1 = EcCurve("secp256k1")
        val BRAINPOOL_P256R1 = EcCurve("brainpoolP256r1")
        val BRAINPOOL_P384R1 = EcCurve("brainpoolP384r1")
        val BRAINPOOL_P512R1 = EcCurve("brainpoolP512r1")
    }
}

@Serializable
@JvmInline
value class EdwardsCurve(val name: String) {
    companion object {
        val ED25519 = EdwardsCurve("Ed25519")
        val ED448 = EdwardsCurve("Ed448")
    }
}

@Serializable
@JvmInline
value class MontgomeryCurve(val name: String) {
    companion object {
        val X25519 = MontgomeryCurve("X25519")
        val X448 = MontgomeryCurve("X448")
    }
}

@Serializable
@JvmInline
value class SymmetricKeyType(val name: String) {
    companion object {
        val AES = SymmetricKeyType("AES")
        val HMAC = SymmetricKeyType("HMAC")
        val CHACHA20 = SymmetricKeyType("ChaCha20")
    }
}

@Serializable
enum class KeyUsage {
    SIGN,
    VERIFY,
    ENCRYPT,
    DECRYPT,
    KEY_AGREEMENT,
    WRAP,
    UNWRAP,
}
