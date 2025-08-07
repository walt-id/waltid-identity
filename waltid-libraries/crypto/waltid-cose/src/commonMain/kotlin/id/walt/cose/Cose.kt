@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.cose

import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** A base interface for all top-level COSE messages. */
interface CoseMessage

/**
 * Represents a COSE_Sign1 object, which contains a single signature.
 * Aligned with RFC 8152, Section 4.2.
 */
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
        val messageBytes = coseCbor.encodeToByteArray(this)
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
            // CBOR tag 18 (major type 6, value 18) is encoded as the single byte 0xd2
            val expectedTag = 0xD2.toByte()
            if (cborBytes.isEmpty() || cborBytes[0] != expectedTag) {
                throw IllegalArgumentException("Data is not a valid tagged COSE_Sign1 message (Tag 18 not found).")
            }
            // Decode the byte array starting *after* the tag byte
            val messageBytes = cborBytes.copyOfRange(1, cborBytes.size)
            return coseCbor.decodeFromByteArray(messageBytes)
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
                coseCbor.encodeToByteArray(protectedHeaders)
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

/**
 * Represents COSE Header Parameters.
 * See: https://www.iana.org/assignments/cose/cose.xhtml#header-parameters
 *
 * **IMPORTANT**: For COSE compliance, properties **MUST** be declared in
 * ascending order of their integer `@CborLabel`. This class adheres to that rule.
 * The library relies on `kotlinx.serialization`'s behaviour of serializing properties
 * in their declaration order.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CoseHeaders(
    /** 1: Cryptographic algorithm to use */
    @CborLabel(1) @SerialName("alg") val algorithm: Int? = null,
    /** 2: Critical headers to be understood */
    @CborLabel(2) val crit: List<Int>? = null,
    /** 3: Content type of the payload */
    @CborLabel(3) val contentType: CoseContentType? = null,
    /** 4: Key identifier */
    @CborLabel(4) @ByteString val kid: ByteArray? = null,
    /** 5: Full Initialization Vector */
    @CborLabel(5) @ByteString val iv: ByteArray? = null,
    /** 6: Partial Initialization Vector */
    @CborLabel(6) @ByteString val partialIv: ByteArray? = null,
    /** 10: Identifies the context for the key identifier (RFC 8613) */
    @CborLabel(10) @ByteString val kidContext: ByteArray? = null,
    /** 16: Content type of the complete COSE object (RFC 9596) */
    @CborLabel(16) val typ: CoseContentType? = null,
    /** 32: An unordered bag of X.509 certificates (RFC 9360) */
    @CborLabel(32) @ByteString val x5bag: List<ByteArray>? = null,
    /** 33: An ordered chain of X.509 certificates (RFC 9360) */
    @CborLabel(33) @ByteString val x5chain: List<ByteArray>? = null,
    /** 45: Hash of an X.509 certificate (RFC 9360) */
    @CborLabel(34) @ByteString val x5t: ByteArray? = null,
    /** 35: URI pointing to an X.509 certificate (RFC 9360) */
    @CborLabel(35) val x5u: String? = null,
) {
//    /**
//     * Convenience property to get the content type, regardless of whether it was
//     * encoded as an Int or a String.
//     */
//    @Transient
//    val contentType = contentTypeString ?: contentTypeInt
}

/**
 * Represents the COSE Content Type header, which can be an Int or a String.
 * This is used for both the 'content type' (3) and 'typ' (16) headers.
 */
@Serializable(with = CoseContentTypeSerializer::class)
sealed class CoseContentType {
    data class AsInt(val value: Int) : CoseContentType()
    data class AsString(val value: String) : CoseContentType()
}

/**
 * Custom serializer for the CoseContentType sealed class to handle the tstr/uint union type.
 */
object CoseContentTypeSerializer : KSerializer<CoseContentType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CoseContentType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CoseContentType) {
        when (value) {
            is CoseContentType.AsInt -> encoder.encodeInt(value.value)
            is CoseContentType.AsString -> encoder.encodeString(value.value)
        }
    }

    /**
     * This custom deserialization is required, as the content type could be either a RFC6838 Section 4.2 string
     * in the form of "<type-name>/<subtype-name>", or it can be a Integer from the "CoAP Content-Formats" IANA registry table.
     * And there is no possible differentiator in the headers, so during deserialization we don't actually know if
     * we handle a string content-type, or an int content-type. Thus, we can only try with `decodeString()` and `decodeInt()`.
     *
     * NOTE: It is required to give priority to `decodeString()`, and use `decodeInt()` as an fallback. The reason
     * is that calling `decodeInt()` on a string content type (like "text/plain") will work, e.g. it reads 10. But then,
     * the rest of the data stream will be corrupted. The reverse is not possible however, as strings are prefixed with 0B,
     * so calling `decodeString()` on an int content type will throw an error. This is the only way I found to decode them
     * to the correct data type.
     */
    override fun deserialize(decoder: Decoder): CoseContentType {
        // Probe the type by attempting to decode as a String first, falling back to Int.
        return try {
            CoseContentType.AsString(decoder.decodeString())
        } catch (e: SerializationException) {
            require(e.message?.startsWith("Expected start of string, but found") == true) { "Error deserializing CoseContentType: ${e.stackTraceToString()}" }
            CoseContentType.AsInt(decoder.decodeInt())
        }
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
