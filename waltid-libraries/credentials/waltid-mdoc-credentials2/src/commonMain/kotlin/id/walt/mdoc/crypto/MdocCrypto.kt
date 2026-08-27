@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.crypto

import id.walt.cose.*
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.encoding.MdocCbor
import id.walt.mdoc.encoding.startsWith
import id.walt.mdoc.objects.SessionTranscript
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.hash.sha2.SHA384
import org.kotlincrypto.hash.sha2.SHA512
import id.walt.crypto2.keys.Key as Crypto2Key

/**
 * Provides cryptographic functions required for mdoc verification, such as hashing and signature validation.
 */
object MdocCrypto {

    private val crypto2Runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    val mdocDigests = listOf(
        "SHA-256",
        "SHA-384",
        "SHA-512"
    )

    fun isSupportedDigest(mdocDigestAlgorithm: String) = mdocDigests.contains(mdocDigestAlgorithm)

    fun ByteArray.digest(digestAlgorithm: String): ByteArray {
        val digest = when (digestAlgorithm) {
            "SHA-256" -> SHA256()
            "SHA-384" -> SHA384()
            "SHA-512" -> SHA512()
            else -> throw IllegalArgumentException("Unsupported digest algorithm: $digestAlgorithm")
        }
        return digest.digest(this)
    }

    /**
     * Verifies the device's signature (`COSE_Sign1`).
     *
     * @param payloadToVerify The detached `DeviceAuthenticationBytes` payload that was signed.
     * @param deviceSignature The COSE_Sign1 signature object.
     * @param devicePublicKey The device public key from the MSO.
     * @param allowedAlgorithms The COSE signature algorithms permitted by the active verification policy.
     * @return True if the signature is valid, false otherwise.
     */
    suspend fun verifyDeviceSignature(
        payloadToVerify: ByteArray,
        deviceSignature: CoseSign1,
        devicePublicKey: Crypto2Key,
        allowedAlgorithms: Set<Int>,
    ): Boolean = deviceSignature.verifyDetached(
        key = devicePublicKey,
        detachedPayload = payloadToVerify,
        allowedAlgorithms = allowedAlgorithms,
    )

    suspend fun Crypto2Key.getSharedSecret(other: EncodedKey): ByteArray {
        val algorithm = when (spec) {
            is KeySpec.Ec -> KeyAgreementAlgorithm.Ecdh
            is KeySpec.Montgomery -> KeyAgreementAlgorithm.Xdh
            else -> throw IllegalArgumentException("Key specification does not support key agreement: $spec")
        }
        val agreement = capabilities.keyAgreement
            ?: throw IllegalArgumentException("Key does not permit key agreement")
        require(capabilities.supportsKeyAgreementAlgorithm(algorithm)) {
            "Key does not support key-agreement algorithm: $algorithm"
        }
        return agreement.generateSharedSecret(other, algorithm).toByteArray()
    }

    suspend fun verifyDeviceMac(
        deviceAuthBytes: ByteArray,
        deviceMac: CoseMac0,
        sessionTranscript: ByteArray,
        eReaderPrivateKey: Crypto2Key,
        devicePublicKey: EncodedKey,
    ): Boolean {
        require(deviceMac.protected.isNotEmpty()) { "DeviceMac algorithm must be protected" }
        val protectedHeaders = coseCompliantCbor.decodeFromByteArray<CoseHeaders>(deviceMac.protected)
        require(protectedHeaders.algorithm == Cose.Algorithm.HMAC_256) { "DeviceMac must use HMAC 256/256" }
        require(deviceMac.unprotected.algorithm == null) { "DeviceMac algorithm cannot be unprotected" }
        val sharedSecret = eReaderPrivateKey.getSharedSecret(devicePublicKey)
        val salt = SHA256().digest(sessionTranscript)
        val eMacKey = try {
            MdocKdf.deriveSha256(sharedSecret, salt, "EMacKey".encodeToByteArray(), 32)
        } finally {
            sharedSecret.fill(0)
            salt.fill(0)
        }
        return try {
            deviceMac.verifyDetached(
                CoseHmacKey(eMacKey).toCoseMacVerifier(Cose.Algorithm.HMAC_256),
                deviceAuthBytes,
            )
        } finally {
            eMacKey.fill(0)
        }
    }

    suspend fun verifyDeviceMac(
        deviceAuthBytes: ByteArray,
        deviceMac: CoseMac0,
        sessionTranscript: ByteArray,
        eReaderPrivateKey: Crypto2Key,
        devicePublicKey: CoseKey,
    ): Boolean = verifyDeviceMac(
        deviceAuthBytes,
        deviceMac,
        sessionTranscript,
        eReaderPrivateKey,
        devicePublicKey.toEncodedJwk(),
    )

    // --- Helper Functions ---

    /**
     * Wraps a ByteArray in a CBOR tag and bytestring structure (`#6.24(bstr)`).
     */
    private fun wrapInTaggedCbor(tag: Long, data: ByteArray): ByteArray {
        // Tag 24 is encoded as 0xD8 0x18
        val tagBytes = byteArrayOf(0xD8.toByte(), tag.toByte())
        // CBOR bstr encoding requires a length prefix.
        // kotlinx.serialization does this automatically if we treat it as a top-level byte array.
        val cborBstr = Cbor.encodeToByteArray(data)
        return tagBytes + cborBstr
    }

    internal suspend fun coseKeyToJwkKey(coseKey: CoseKey): JWKKey {
        val crv = when (coseKey.crv) {
            1 -> "P-256"
            2 -> "P-384"
            3 -> "P-521"
            6 -> "Ed25519"
            7 -> "Ed448"
            else -> throw IllegalArgumentException("Unsupported curve: ${coseKey.crv}")
        }
        val kty = when (coseKey.kty) {
            1 -> "OKP"
            2 -> "EC"
            else -> throw IllegalArgumentException("Unsupported key type: ${coseKey.kty}")
        }
        val jwkJson = buildString {
            append("""{"kty":"$kty","crv":"$crv"""")
            coseKey.x?.let { append(""","x":"${it.encodeToBase64Url()}"""") }
            coseKey.y?.let { append(""","y":"${it.encodeToBase64Url()}"""") }
            append("}")
        }
        return JWKKey.importJWK(jwkJson).getOrThrow()
    }

    suspend fun coseKeyToCrypto2Key(coseKey: CoseKey): Crypto2Key = restoreCoseVerificationKey(coseKey)

    suspend fun coseKeyToCrypto2Key(coseKey: CoseKey, expectedAlgorithm: Int): Crypto2Key {
        require(coseKey.alg == null || coseKey.alg == expectedAlgorithm) {
            "Device COSE_Key algorithm ${coseKey.alg} is incompatible with device signature algorithm $expectedAlgorithm"
        }
        return restoreCoseVerificationKey(coseKey)
    }

    private suspend fun restoreCoseVerificationKey(coseKey: CoseKey): Crypto2Key {
        coseKey.key_ops?.let { operations ->
            require(operations.isNotEmpty() && COSE_KEY_OPERATION_VERIFY in operations) {
                "Device COSE_Key does not permit signature verification"
            }
        }
        val publicJwk = coseKey.toEncodedJwk()
        require(!publicJwk.privateMaterial) { "Device authentication verification key must be public" }
        return crypto2Runtime.restore(
            publicJwk.toStoredSoftwareKey(
                KeyId(Jwk.sha256Thumbprint(publicJwk)),
                setOf(KeyUsage.VERIFY),
            )
        )
    }

    private const val COSE_KEY_OPERATION_VERIFY = 2

    internal fun decodeCoseKey(cborBytes: ByteArray): CoseKey {
        // The EReaderKeyBytes is tagged with #6.24
        val untaggedCbor = if (cborBytes.startsWith(byteArrayOf(0xD8.toByte(), 24.toByte()))) {
            // This is a tagged bytestring, so we need to decode the inner bytestring first
            val innerBstr = Cbor.decodeFromByteArray<ByteArray>(cborBytes.drop(2).toByteArray())
            innerBstr
        } else {
            cborBytes
        }
        return MdocCbor.decodeFromByteArray(untaggedCbor)
    }

    fun parseSessionTranscript(cborBytes: ByteArray): SessionTranscript {
        // The SessionTranscriptBytes is tagged with #6.24
        val untaggedCbor = if (cborBytes.startsWith(byteArrayOf(0xD8.toByte(), 24.toByte()))) {
            val innerBstr = Cbor.decodeFromByteArray<ByteArray>(cborBytes.drop(2).toByteArray())
            innerBstr
        } else {
            cborBytes
        }
        return MdocCbor.decodeFromByteArray<SessionTranscript>(untaggedCbor)
    }
}
