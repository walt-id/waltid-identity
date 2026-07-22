package id.walt.crypto2.keys

import id.walt.crypto2.algorithms.AeadAlgorithm
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
data class AeadCiphertext(
    val algorithm: AeadAlgorithm,
    val nonce: BinaryData,
    val ciphertext: BinaryData,
    val authenticationTag: BinaryData,
) {
    init {
        require(nonce.toByteArray().isNotEmpty()) { "AEAD nonce cannot be empty" }
        val tagSize = authenticationTag.toByteArray().size
        when (algorithm) {
            is AeadAlgorithm.AesGcm -> require(tagSize * Byte.SIZE_BITS == algorithm.tagSizeBits) {
                "AES-GCM authentication tag size does not match algorithm"
            }
            AeadAlgorithm.ChaCha20Poly1305 -> {
                require(nonce.toByteArray().size == 12) { "ChaCha20-Poly1305 nonce must be 96 bits" }
                require(tagSize == 16) { "ChaCha20-Poly1305 authentication tag must be 128 bits" }
            }
            is AeadAlgorithm.Custom -> require(tagSize > 0) { "AEAD authentication tag cannot be empty" }
        }
    }
}

@Serializable
@JvmInline
value class HpkeKemId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object {
        val DHKEM_P256_HKDF_SHA256 = HpkeKemId("DHKEM(P-256,HKDF-SHA256)")
        val DHKEM_X25519_HKDF_SHA256 = HpkeKemId("DHKEM(X25519,HKDF-SHA256)")
    }
}

@Serializable
@JvmInline
value class HpkeKdfId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object {
        val HKDF_SHA256 = HpkeKdfId("HKDF-SHA256")
    }
}

@Serializable
@JvmInline
value class HpkeAeadId(val value: String) {
    init { require(value.isNotBlank()) }
    companion object {
        val AES_128_GCM = HpkeAeadId("AES-128-GCM")
        val AES_256_GCM = HpkeAeadId("AES-256-GCM")
        val CHACHA20_POLY1305 = HpkeAeadId("ChaCha20Poly1305")
    }
}

@Serializable
data class HpkeSuite(
    val kem: HpkeKemId,
    val kdf: HpkeKdfId,
    val aead: HpkeAeadId,
)

@Serializable
data class HpkeCiphertext(
    val suite: HpkeSuite,
    val encapsulatedKey: BinaryData,
    val ciphertext: BinaryData,
) {
    init {
        val encapsulatedKeySize = encapsulatedKey.toByteArray().size
        when (suite.kem) {
            HpkeKemId.DHKEM_P256_HKDF_SHA256 -> require(encapsulatedKeySize == 65) {
                "P-256 HPKE encapsulated key must be 65 bytes"
            }.also {
                require(encapsulatedKey.toByteArray().first() == 0x04.toByte()) {
                    "P-256 HPKE encapsulated key must use uncompressed point encoding"
                }
            }
            HpkeKemId.DHKEM_X25519_HKDF_SHA256 -> require(encapsulatedKeySize == 32) {
                "X25519 HPKE encapsulated key must be 32 bytes"
            }
        }
        when (suite.aead) {
            HpkeAeadId.AES_128_GCM,
            HpkeAeadId.AES_256_GCM,
            HpkeAeadId.CHACHA20_POLY1305,
            -> require(ciphertext.toByteArray().size >= 16) { "HPKE ciphertext must include an authentication tag" }
        }
    }
}
