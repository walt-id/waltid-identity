@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.cose

import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

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

    inline fun <reified T> decodePayload(): T {
        return coseCompliantCbor.decodeFromByteArray<T>(payload!!)
    }

    fun ByteArray.stripCborTag(tag: Byte): ByteArray {
        val tagBytes = byteArrayOf(0xd8.toByte(), tag)
        return if (this.size >= 2 && this[0] == tagBytes[0] && this[1] == tagBytes[1]) {
            this.drop(2).toByteArray()
        } else {
            this
        }
    }

    inline fun <reified T> decodeIsoPayload(): T {
        // 1. Strip the outer tag (e.g., #6.24) from the payload.
        val taggedContent = payload!!.stripCborTag(24)

        // 2. Decode the now-exposed byte string (bstr) to get its inner content.
        val msoBytes = coseCompliantCbor.decodeFromByteArray<ByteArray>(taggedContent)

        // 3. Decode the actual MobileSecurityObject from the inner content bytes.
        return coseCompliantCbor.decodeFromByteArray<T>(msoBytes)
    }

    /**
     * Verifies the signature of this COSE_Sign1 object - against an ATTACHED payload.
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
     * Verifies the signature of this COSE_Sign1 object against a DETACHED payload.
     * The payload field of this CoseSign1 object MUST be null.
     */
    suspend fun verifyDetached(verifier: CoseVerifier, detachedPayload: ByteArray, externalAad: ByteArray = byteArrayOf()): Boolean {
        require(payload == null) { "COSE_Sign1 payload must be null for detached signature verification." }
        val dataToVerify = buildSignatureStructure(protected, detachedPayload, externalAad)
        return verifier.verify(dataToVerify, signature)
    }

    fun serialize() = coseCompliantCbor.encodeToByteArray(this)

    /**
     * Encodes this CoseSign1 object into its tagged CBOR representation as per RFC 9052.
     * A COSE_Sign1 object is tagged with CBOR tag 18.
     */
    fun toTagged(): ByteArray {
        val messageBytes = serialize()
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
            /** This can be either the attached payload or the detached payload */
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

            // The signature is calculated over the Sig_structure, which includes the payload
            val dataToSign = buildSignatureStructure(protectedBytes, payload, externalAad)

            return TBS(protectedBytes, dataToSign)
        }

        /**
         * Creates and signs a CoseSign1 object with an ATTACHED payload.
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

        /**
         * Creates and signs a CoseSign1 object with a DETACHED payload.
         * The payload of the returned CoseSign1 object will be null.
         */
        suspend fun createAndSignDetached(
            protectedHeaders: CoseHeaders,
            unprotectedHeaders: CoseHeaders = CoseHeaders(),
            detachedPayload: ByteArray,
            signer: CoseSigner,
            externalAad: ByteArray = byteArrayOf()
        ): CoseSign1 {
            // For detached signatures, the payload in the Sig_structure is the detached content.
            val (protectedBytes, dataToSign) = makeToBeSigned(protectedHeaders, detachedPayload, externalAad)
            val signature = signer.sign(dataToSign)

            return CoseSign1(
                protected = protectedBytes,
                unprotected = unprotectedHeaders,
                payload = null, // Payload is null for detached signatures
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
    @CborLabel(5) @ByteString val `Base IV`: ByteArray? = null,
    /** for OKP and EC2 */
    @CborLabel(-1) val crv: Int? = null,
    /** for OKP and EC2 */
    @CborLabel(-2) @ByteString val x: ByteArray? = null,
    /** for EC2 */
    @CborLabel(-3) @ByteString val y: ByteArray? = null,
    /** for OKP and EC2 (private key) */
    @CborLabel(-4) @ByteString val d: ByteArray? = null,
) {

    companion object {
        private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
        private fun ByteArray.toBase64Url(): String = base64Url.encode(this)
    }


    /**
     * Converts this COSE Key into a [JsonObject] representing a JSON Web Key (JWK).
     * The conversion maps COSE integer constants to their standard JWK string equivalents
     * and Base64Url-encodes binary fields.
     *
     * @return The [JsonObject] representation of the JWK.
     * @throws IllegalArgumentException if the COSE key type is unsupported for JWK conversion.
     */
    fun toJWK(): JsonObject = buildJsonObject {
        // 1. Map Key Type (kty)
        val jwkKty = when (kty) {
            Cose.KeyTypes.OKP -> "OKP" // Octet Key Pair
            Cose.KeyTypes.EC2 -> "EC"  // Elliptic Curve
            Cose.KeyTypes.SYMMETRIC -> "oct" // Octet sequence (Symmetric)
            else -> throw IllegalArgumentException("Unsupported COSE key type for JWK conversion: $kty")
        }
        put("kty", jwkKty)

        // 2. Map Key ID (kid)
        kid?.let { put("kid", it.toBase64Url()) }

        // 3. Map Algorithm (alg)
        alg?.let {
            val jwkAlg = when (it) {
                Cose.Algorithm.ES256K -> "ES256K"
                Cose.Algorithm.ES256 -> "ES256"
                Cose.Algorithm.ES384 -> "ES384"
                Cose.Algorithm.ES512 -> "ES512"
                Cose.Algorithm.EdDSA -> "EdDSA"
                Cose.Algorithm.PS256 -> "PS256"
                Cose.Algorithm.PS384 -> "PS384"
                Cose.Algorithm.PS512 -> "PS512"
                Cose.Algorithm.RS256 -> "RS256"
                Cose.Algorithm.RS384 -> "RS384"
                Cose.Algorithm.RS512 -> "RS512"
                Cose.Algorithm.HMAC_256, Cose.Algorithm.HMAC_256_64 -> "HS256"
                Cose.Algorithm.HMAC_384 -> "HS384"
                Cose.Algorithm.HMAC_512 -> "HS512"
                else -> null // Ignore unsupported algorithms
            }
            jwkAlg?.let { algStr -> put("alg", algStr) }
        }

        // 4. Map Key Operations (key_ops)
        key_ops?.let { ops ->
            // Use a Set to avoid duplicates (e.g., COSE MAC ops map to JWK sign/verify)
            val jwkOps = ops.mapNotNull { op ->
                when (op) {
                    1, 9 -> "sign"        // 1: sign, 9: MAC create
                    2, 10 -> "verify"      // 2: verify, 10: MAC verify
                    3 -> "encrypt"
                    4 -> "decrypt"
                    5 -> "wrapKey"
                    6 -> "unwrapKey"
                    7 -> "deriveKey"
                    8 -> "deriveBits"
                    else -> null
                }
            }.toSet()

            if (jwkOps.isNotEmpty()) {
                put("key_ops", JsonArray(jwkOps.map { JsonPrimitive(it) }))
            }
        }

        // 5. Map Curve (crv) and key parameters (x, y, d)
        if (kty == Cose.KeyTypes.OKP || kty == Cose.KeyTypes.EC2) {
            crv?.let {
                val jwkCrv = when (it) {
                    Cose.EllipticCurves.P_256 -> "P-256"
                    Cose.EllipticCurves.P_384 -> "P-384"
                    Cose.EllipticCurves.P_521 -> "P-521"
                    Cose.EllipticCurves.Ed25519 -> "Ed25519"
                    Cose.EllipticCurves.Ed448 -> "Ed448"
                    Cose.EllipticCurves.secp256k1 -> "secp256k1"
                    else -> null // Ignore unsupported curves
                }
                jwkCrv?.let { crvStr -> put("crv", crvStr) }
            }

            // Public key component 'x'
            x?.let { put("x", it.toBase64Url()) }
        }

        // EC-specific public key component 'y'
        if (kty == Cose.KeyTypes.EC2) {
            y?.let { put("y", it.toBase64Url()) }
        }

        // Private key component 'd'
        d?.let { put("d", it.toBase64Url()) }
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CoseKey) return false

        if (kty != other.kty) return false
        if (alg != other.alg) return false
        if (crv != other.crv) return false
        if (!kid.contentEquals(other.kid)) return false
        if (key_ops != other.key_ops) return false
        if (!`Base IV`.contentEquals(other.`Base IV`)) return false
        if (!x.contentEquals(other.x)) return false
        if (!y.contentEquals(other.y)) return false
        if (!d.contentEquals(other.d)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = kty
        result = 31 * result + (alg ?: 0)
        result = 31 * result + (crv ?: 0)
        result = 31 * result + (kid?.contentHashCode() ?: 0)
        result = 31 * result + (key_ops?.hashCode() ?: 0)
        result = 31 * result + (`Base IV`?.contentHashCode() ?: 0)
        result = 31 * result + (x?.contentHashCode() ?: 0)
        result = 31 * result + (y?.contentHashCode() ?: 0)
        result = 31 * result + (d?.contentHashCode() ?: 0)
        return result
    }
}

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

        // X25519: 4 // X25519 for use w/ ECDH only
        // X448: 5 // X448 for use w/ ECDH only

        /** Ed25519 for use w/ EdDSA only */
        const val Ed25519 = 6

        /** Ed448 for use w/ EdDSA only */
        const val Ed448 = 7

        /** SECG secp256k1 curve */
        const val secp256k1 = 8
    }

}

