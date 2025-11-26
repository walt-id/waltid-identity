package id.walt.mdoc.crypto

import id.walt.cose.CoseKey
import id.walt.cose.CoseMac0
import id.walt.cose.CoseSign1
import id.walt.cose.toCoseVerifier
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.mdoc.encoding.MdocCbor
import id.walt.mdoc.encoding.startsWith
import id.walt.mdoc.objects.SessionTranscript
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.hash.sha2.SHA384
import org.kotlincrypto.hash.sha2.SHA512
import org.kotlincrypto.macs.hmac.sha2.HmacSHA256

/**
 * Provides cryptographic functions required for mdoc verification, such as hashing and signature validation.
 */
object MdocCrypto {

    private val log = KotlinLogging.logger { }

    private val sha256 = SHA256()
    private val sha384 = SHA384()
    private val sha512 = SHA512()
    val mdocDigestTable = mapOf(
        "SHA-256" to sha256,
        "SHA-384" to sha384,
        "SHA-512" to sha512
    )

    fun isSupportedDigest(mdocDigestAlgorithm: String) = mdocDigestTable.containsKey(mdocDigestAlgorithm)

    fun ByteArray.digest(digestAlgorithm: String): ByteArray {
        val digest = when (digestAlgorithm) {
            "SHA-256" -> sha256
            "SHA-384" -> sha384
            "SHA-512" -> sha512
            else -> throw IllegalArgumentException("Unsupported digest algorithm: $digestAlgorithm")
        }
        return digest.digest(this)
    }

    /**
     * Verifies the device's signature (`COSE_Sign1`).
     *
     * @param payloadToVerify (deviceAuthBytes) The data that was signed.
     * @param deviceSignature The COSE_Sign1 signature object.
     * @param sDevicePublicKey The public key from the MSO to verify the signature.
     * @return True if the signature is valid, false otherwise.
     */
    suspend fun verifyDeviceSignature(
        payloadToVerify: ByteArray,
        deviceSignature: CoseSign1,
        sDevicePublicKey: Key
    ): Boolean {
        log.trace { "-- Verifying device signature --" }
        log.trace { "> Payload to verify (hex): ${payloadToVerify.toHexString()}" }
        log.trace { "> Device signature: $deviceSignature" }
        val tmpJwk = sDevicePublicKey.exportJWK()
        log.trace { "> sDevicePublicKey: $sDevicePublicKey: $tmpJwk" }
        return deviceSignature.verifyDetached(
            verifier = sDevicePublicKey.toCoseVerifier(),
            detachedPayload = payloadToVerify
        )
    }

    // stub as waltid-crypto does not yet do ECDH
    fun Key.getSharedSecret(other: Key): ByteArray {
        TODO("")
        /*
        Java impl:

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(eReaderPrivateKey.toJavaPrivateKey()) // Your private key
        keyAgreement.doPhase(sDevicePublicKey.toJavaPublicKey(), true) // Their public key
        val sharedSecret: ByteArray = keyAgreement.generateSecret()

        JavaScript:

        const sharedSecret = await crypto.subtle.deriveBits(
          { name: "ECDH", public: theirPublicKey },
          yourPrivateKey,
          256 // The number of bits to derive
        );
        // Result is an ArrayBuffer, which is converted to ByteArray

        Swift/CryptoKit:
        // Example in Swift
        let sharedSecret = try yourPrivateKey.sharedSecretFromKeyAgreement(
            with: theirPublicKey
        )
        // The result is a SharedSecret object -> get raw bytes from that
        */
    }

    /**
     * Verifies the device's MAC (`COSE_Mac0`).
     * This involves deriving the shared key and then verifying the HMAC tag.
     *
     * @param deviceAuthBytes The data that was MAC'd.
     * @param deviceMac The COSE_Mac0 object containing the tag.
     * @param sessionTranscript The session transcript.
     * @param eReaderPrivateKey The ephemeral private key of the verifier.
     * @param sDevicePublicKey The public key of the device from the MSO.
     * @return True if the MAC is valid, false otherwise.
     */
    suspend fun verifyDeviceMac(
        deviceAuthBytes: ByteArray,
        deviceMac: CoseMac0,
        sessionTranscript: ByteArray,
        eReaderPrivateKey: Key,
        sDevicePublicKey: Key
    ): Boolean {
        // 1. Perform ECDH to get shared secret
        val sharedSecret = eReaderPrivateKey.getSharedSecret(sDevicePublicKey)

        // 2. Perform HKDF to derive EMacKey
        val salt = SHA256().digest(sessionTranscript)
        val info = "EMacKey".encodeToByteArray()
        val eMacKey = performHkdf(sharedSecret, salt, info, 32)

        // 3. Compute HMAC and verify
        val hmac = HmacSHA256(eMacKey)
        val computedTag = hmac.doFinal(deviceAuthBytes)

        return computedTag.contentEquals(deviceMac.tag)
    }

    // --- Helper Functions ---

    /**
     * A simplified HKDF-SHA256 implementation based on RFC 5869.
     * This is a stub for what would ideally be a full-featured KMP crypto library function.
     */
    private fun performHkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hmac = HmacSHA256(salt)
        val prk = hmac.doFinal(ikm) // Extract

        val hmacPrk = HmacSHA256(prk)
        val t = hmacPrk.doFinal(info + 0x01.toByte()) // Expand
        return t.take(length).toByteArray()
    }


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
