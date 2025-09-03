@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.cose

import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborLabel

/** A base interface for all top-level COSE messages. */
interface CoseMessage

@Serializable
@CborArray
data class CoseSign1(
    @ByteString val protected: ByteArray,
    val unprotected: CoseHeaders,
    @ByteString val payload: ByteArray?,
    @ByteString val signature: ByteArray,
) : CoseMessage {

    /**
     * Verifies the signature of this COSE_Sign1 object.
     *
     * @param verifier The verifier for the cryptographic algorithm used.
     * @param externalAad Externally supplied authenticated data, if any.
     * @return True if the signature is valid, false otherwise.
     */
    suspend fun verify(verifier: CoseVerifier, externalAad: ByteArray = byteArrayOf()): Boolean {
        val dataToVerify = buildSignatureStructure(protected, payload, externalAad)
        return verifier.verify(dataToVerify, signature)
    }

    /**
     * Encodes this CoseSign1 object into its tagged CBOR representation as per RFC 9052.
     * A COSE_Sign1 object is tagged with CBOR tag 18.
     */
    fun toTagged(): ByteArray {
        val messageBytes = coseCompliantCbor.encodeToByteArray(this)
        // CBOR tag 18 (major type 6, value 18) is encoded as the single byte 0xd2
        val tag = 0xD2.toByte()
        return byteArrayOf(tag) + messageBytes
    }

    companion object {
        /**
         * Decodes a CoseSign1 object from its tagged CBOR representation.
         * The CBOR tag for a COSE Single Signer Data Object is 18.
         */
        fun fromTagged(cborBytes: ByteArray): CoseSign1 {
            if (cborBytes.isEmpty()) {
                throw IllegalArgumentException("Input CBOR data cannot be empty.")
            }

            val tag18 = 0xD2.toByte() // COSE_Sign1
            val array4InitialByte = 0x84.toByte() // CBOR Array of 4 elements

            val messageBytes = when (cborBytes[0]) {
                tag18 -> cborBytes.copyOfRange(1, cborBytes.size)

                array4InitialByte -> {
                    // Data appears to be an **untagged** COSE_Sign1 object
                    cborBytes
                }

                else -> throw IllegalArgumentException("Data is not a valid COSE_Sign1 message. Expected CBOR Tag 18 (0xD2) or an untagged array. Got byte: (hex) ${cborBytes[0].toHexString()}")
            }

            return coseCompliantCbor.decodeFromByteArray(messageBytes)
        }

        fun fromTagged(cborHex: String) = fromTagged(cborHex.hexToByteArray())

        /** Internal To Be Signed data*/
        internal data class TBS(
            val protectedBytes: ByteArray,
            val dataToSign: ByteArray
        )

        internal fun makeToBeSigned(
            protectedHeaders: CoseHeaders,
            payload: ByteArray?,
            externalAad: ByteArray = byteArrayOf()
        ): TBS {
            /* RFC 9052, Section 3: an empty protected header map should be encoded as a zero-length byte string */
            val protectedBytes = if (protectedHeaders == CoseHeaders()) {
                // Encode an empty map as an empty byte string
                byteArrayOf()
            } else {
                coseCompliantCbor.encodeToByteArray(protectedHeaders)
            }

            val dataToSign = buildSignatureStructure(protectedBytes, payload, externalAad)

            return TBS(protectedBytes, dataToSign)
        }

        /**
         * Creates and signs a CoseSign1 object.
         */
        suspend fun createAndSign(
            protectedHeaders: CoseHeaders,
            unprotectedHeaders: CoseHeaders = CoseHeaders(),
            payload: ByteArray?,
            signer: CoseSigner,
            externalAad: ByteArray = byteArrayOf()
        ): CoseSign1 {
            val (protectedBytes, dataToSign) = makeToBeSigned(protectedHeaders, payload, externalAad)
            val signature = signer.sign(dataToSign)

            return CoseSign1(
                protected = protectedBytes,
                unprotected = unprotectedHeaders,
                payload = payload,
                signature = signature
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CoseSign1) return false

        if (!protected.contentEquals(other.protected)) return false
        if (unprotected != other.unprotected) return false
        if (!payload.contentEquals(other.payload)) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = protected.contentHashCode()
        result = 31 * result + unprotected.hashCode()
        result = 31 * result + (payload?.contentHashCode() ?: 0)
        result = 31 * result + signature.contentHashCode()
        return result
    }
}

@Serializable
@CborArray
data class CoseMac0(
    @ByteString val protected: ByteArray,
    val unprotected: CoseHeaders,
    @ByteString val payload: ByteArray?,
    @ByteString val tag: ByteArray, // This is the MAC tag
) : CoseMessage {

    /**
     * Verifies the MAC tag of this COSE_Mac0 object.
     */
    suspend fun verify(verifier: CoseMacVerifier, externalAad: ByteArray = byteArrayOf()): Boolean {
        val dataToVerify = buildMacStructure(protected, payload, externalAad)
        return verifier.verify(dataToVerify, tag)
    }

    /**
     * Encodes this CoseMac0 object into its tagged CBOR representation.
     * A COSE_Mac0 object is tagged with CBOR tag 17.
     */
    fun toTagged(): ByteArray {
        val messageBytes = coseCompliantCbor.encodeToByteArray(this)
        // CBOR tag 17 (major type 6, value 17) is encoded as the single byte 0xd1
        val tag = 0xD1.toByte()
        return byteArrayOf(tag) + messageBytes
    }

    companion object {
        /**
         * Decodes a CoseMac0 object from its tagged CBOR representation.
         * The CBOR tag for a COSE MAC w/o Recipients Object is 17.
         */
        fun fromTagged(cborBytes: ByteArray): CoseMac0 {
            if (cborBytes.isEmpty()) {
                throw IllegalArgumentException("Input CBOR data cannot be empty.")
            }

            val tag17 = 0xD1.toByte()
            val array4InitialByte = 0x84.toByte()

            val messageBytes = when (cborBytes[0]) {
                tag17 -> cborBytes.copyOfRange(1, cborBytes.size)
                array4InitialByte -> cborBytes
                else -> throw IllegalArgumentException("Data is not a valid COSE_Mac0 message. Expected CBOR Tag 17 (0xD1). Got byte: (hex) ${cborBytes[0].toHexString()}")
            }
            return coseCompliantCbor.decodeFromByteArray(messageBytes)
        }

        fun fromTagged(cborHex: String) = fromTagged(cborHex.hexToByteArray())

        /**
         * Creates and authenticates a CoseMac0 object.
         */
        suspend fun createAndMac(
            protectedHeaders: CoseHeaders,
            unprotectedHeaders: CoseHeaders = CoseHeaders(),
            payload: ByteArray?,
            creator: CoseMacCreator,
            externalAad: ByteArray = byteArrayOf()
        ): CoseMac0 {
            val protectedBytes = if (protectedHeaders == CoseHeaders()) {
                byteArrayOf()
            } else {
                coseCompliantCbor.encodeToByteArray(protectedHeaders)
            }
            val dataToMac = buildMacStructure(protectedBytes, payload, externalAad)
            val tag = creator.mac(dataToMac)

            return CoseMac0(
                protected = protectedBytes,
                unprotected = unprotectedHeaders,
                payload = payload,
                tag = tag
            )
        }
    }

    // Boilerplate for correct ByteArray comparison
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CoseMac0) return false
        if (!protected.contentEquals(other.protected)) return false
        if (unprotected != other.unprotected) return false
        if (!payload.contentEquals(other.payload)) return false
        if (!tag.contentEquals(other.tag)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = protected.contentHashCode()
        result = 31 * result + unprotected.hashCode()
        result = 31 * result + (payload?.contentHashCode() ?: 0)
        result = 31 * result + tag.contentHashCode()
        return result
    }
}


/** Represents a COSE Key object. Aligned with RFC 8152, Section 7. */
@Serializable
data class CoseKey(
    @CborLabel(1) val kty: Int,
    @CborLabel(2) @ByteString val kid: ByteArray? = null,
    @CborLabel(3) val alg: Int? = null,
    @CborLabel(4) val key_ops: List<Int>? = null,
    @CborLabel(5) @ByteString val base_iv: ByteArray? = null,
    /** for OKP and EC2 */
    @CborLabel(-1) val crv: Int? = null,
    /** for OKP and EC2 */
    @CborLabel(-2) @ByteString val x: ByteArray? = null,
    /** for EC2 */
    @CborLabel(-3) @ByteString val y: ByteArray? = null,
    /** for OKP and EC2 (private key) */
    @CborLabel(-4) @ByteString val d: ByteArray? = null,
)

/** Represents a COSE Key Set. Aligned with RFC 8152, Section 7. */
@Serializable
@CborArray
data class CoseKeySet(val keys: List<CoseKey>)


// --- Official IANA/RFC 8152 Constants ---

object Cose {
    /** CBOR Tags for COSE message structures.*/
    object MessageTag {
        /** COSE Signed Data Object */
        const val SIGN = 98L

        /** COSE Single Signer Data Object */
        const val SIGN1 = 18L

        /** COSE Encrypted Data Object */
        const val ENCRYPT = 96L

        /** COSE Single Recipient Encrypted Data Object */
        const val ENCRYPT0 = 16L

        /** COSE MACed Data Object */
        const val MAC = 97L

        /** COSE MAC w/o Recipients Object */
        const val MAC0 = 17L
    }

    /** COSE Algorithm Identifiers.*/
    object Algorithm {

        /** ECDSA using secp256k1 curve and SHA-256 */
        const val ES256K = -47

        /** ECDSA w/ SHA-256 */
        const val ES256 = -7

        /** ECDSA w/ SHA-384 */
        const val ES384 = -35

        /** ECDSA w/ SHA-512 */
        const val ES512 = -36

        /** EdDSA */
        const val EdDSA = -8

        /** RSASSA-PSS w/ SHA-256 */
        const val PS256 = -37

        /** RSASSA-PSS w/ SHA-384 */
        const val PS384 = -38

        /** RSASSA-PSS w/ SHA-512 */
        const val PS512 = -39

        /** RSASSA-PKCS1-v1_5 using SHA-256 */
        const val RS256 = -257

        /** RSASSA-PKCS1-v1_5 using SHA-384 */
        const val RS384 = -258

        /** RSASSA-PKCS1-v1_5 using SHA-512 */
        const val RS512 = -259

        // HMAC:
        /** HMAC w/ SHA-256 truncated to 64 bits */
        const val HMAC_256_64 = 4
        /** HMAC w/ SHA-256 */
        const val HMAC_256 = 5
        /** HMAC w/ SHA-384 */
        const val HMAC_384 = 6
        /** HMAC w/ SHA-512 */
        const val HMAC_512 = 7
    }
}

/** COSE Header Parameter Labels.*/
object HeaderLabel {
    /** Cryptographic algorithm to use */
    const val ALG = 1

    /** Critical headers to be understood */
    const val CRIT = 2

    /** Content type of the payload */
    const val CONTENT_TYPE = 3

    /** Key identifier */
    const val KID = 4

    /** Full Initialization Vector */
    const val IV = 5

    /** Partial Initialization Vector */
    const val PARTIAL_IV = 6

    /** Counter signature */
    const val COUNTER_SIGNATURE = 7

    /** An ordered chain of X.509 certificates */
    const val X5_CHAIN = 33
}

/** COSE Key Common Parameter Labels.*/
object KeyTypeParameters {
    /** Identification of the key type */
    const val KTY = 1

    /** Key identification value */
    const val KID = 2

    /** Key usage restriction to this algorithm */
    const val ALG = 3

    /** Restrict set of permissible operations */
    const val KEY_OPS = 4

    /** Base IV to be XORed with Partial IVs */
    const val BASE_IV = 5
}

/** COSE Key Type values.*/
object KeyTypes {
    /** Octet Key Pair */
    const val OKP = 1

    /** Elliptic Curve Keys w/ x- and y-coordinate pair */
    const val EC2 = 2

    /** Symmetric Keys */
    const val SYMMETRIC = 4
}

/** COSE Elliptic Curve identifiers.*/
object EllipticCurves {
    /** NIST P-256 also known as secp256r1 */
    const val P_256 = 1

    /** NIST P-384 also known as secp384r1 */
    const val P_384 = 2

    /** NIST P-521 also known as secp521r1 */
    const val P_521 = 3

    /** Ed25519 for use w/ EdDSA only */
    const val Ed25519 = 6

    /** Ed448 for use w/ EdDSA only */
    const val Ed448 = 7
}
