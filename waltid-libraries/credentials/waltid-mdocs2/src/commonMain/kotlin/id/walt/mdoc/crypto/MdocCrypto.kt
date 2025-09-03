package id.walt.mdoc.crypto

import id.walt.cose.CoseKey
import id.walt.cose.CoseMac0
import id.walt.cose.CoseSign1
import id.walt.cose.toCoseVerifier
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.mdoc.MdocCbor
import id.walt.mdoc.credsdata.DeviceNameSpaces
import id.walt.mdoc.credsdata.IssuerSignedItem
import id.walt.mdoc.utils.startsWith
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborArray
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

    /**
     * Computes the digest of an issuer-signed item.
     * It first wraps the item in a tagged CBOR bytestring (`#6.24`) and then computes the hash.
     *
     * @param item The issuer-signed item to digest.
     * @param algorithm The digest algorithm to use (e.g., "SHA-256").
     * @return The computed digest as a ByteArray.
     */
    fun digest(item: IssuerSignedItem, algorithm: String): ByteArray {
        val cborDataItem = MdocCbor.encodeToByteArray(item)
        val taggedBytes = wrapInTaggedCbor(24, cborDataItem)

        val digest = when (algorithm) {
            "SHA-256" -> SHA256()
            "SHA-384" -> SHA384()
            "SHA-512" -> SHA512()
            else -> throw IllegalArgumentException("Unsupported digest algorithm: $algorithm")
        }
        return digest.digest(taggedBytes)
    }

    /**
     * Constructs and serializes the `DeviceAuthentication` structure.
     *
     * @param sessionTranscript The session transcript from the engagement.
     * @param docType The document type.
     * @param deviceNameSpaces The device-signed namespaces.
     * @return The tagged and CBOR-encoded `DeviceAuthenticationBytes`.
     */
    fun buildDeviceAuthenticationBytes(
        sessionTranscript: ByteArray,
        docType: String,
        deviceNameSpaces: DeviceNameSpaces
    ): ByteArray {
        val deviceNameSpacesBytes = MdocCbor.encodeToByteArray(deviceNameSpaces)
        val deviceAuth = DeviceAuthentication(
            sessionTranscriptBytes = sessionTranscript,
            docType = docType,
            deviceNameSpacesBytes = deviceNameSpacesBytes
        )
        val cborDeviceAuth = MdocCbor.encodeToByteArray(deviceAuth)
        return wrapInTaggedCbor(24, cborDeviceAuth)
    }

    /**
     * Verifies the device's signature (`COSE_Sign1`).
     *
     * @param deviceAuthBytes The data that was signed.
     * @param deviceSignature The COSE_Sign1 signature object.
     * @param sDevicePublicKey The public key from the MSO to verify the signature.
     * @return True if the signature is valid, false otherwise.
     */
    suspend fun verifyDeviceSignature(
        deviceAuthBytes: ByteArray,
        deviceSignature: CoseSign1,
        sDevicePublicKey: Key
    ): Boolean {
        // We cannot use CoseSign1.verify() here because the signed data (`deviceAuthBytes`)
        // is detached content, not the payload of the COSE object itself.
        // We use the toCoseVerifier adapter which correctly handles signature format conversion (P1363->DER).
        return sDevicePublicKey.toCoseVerifier().verify(deviceAuthBytes, deviceSignature.signature)
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

    // --- Internal Data Classes for Serialization ---

    /**
     * Internal structure for the data that gets authenticated by the device.
     * `DeviceAuthentication = [ "DeviceAuthentication", SessionTranscript, DocType, DeviceNameSpacesBytes ]`
     */
    @Serializable
    @CborArray
    internal data class DeviceAuthentication(
        val context: String = "DeviceAuthentication",
        val sessionTranscriptBytes: ByteArray,
        val docType: String,
        val deviceNameSpacesBytes: ByteArray
    )

    /**
     * Internal structure for parsing the SessionTranscript.
     * `SessionTranscript = [ DeviceEngagementBytes, EReaderKeyBytes, Handover ]`
     */
    @Serializable
    @CborArray
    data class SessionTranscript(
        val deviceEngagementBytes: ByteArray,
        val eReaderKeyBytes: ByteArray,
        val handover: ByteArray // Assuming handover is just bytes for now
    )
}
