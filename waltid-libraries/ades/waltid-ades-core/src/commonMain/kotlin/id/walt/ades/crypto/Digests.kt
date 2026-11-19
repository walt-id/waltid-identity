@file:OptIn(dev.whyoleg.cryptography.DelicateCryptographyApi::class)

package id.walt.ades.crypto

import dev.whyoleg.cryptography.CryptographyAlgorithmId
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.SHA1 as WhyolegSha1
import dev.whyoleg.cryptography.algorithms.SHA224 as WhyolegSha224
import dev.whyoleg.cryptography.algorithms.SHA256 as WhyolegSha256
import dev.whyoleg.cryptography.algorithms.SHA384 as WhyolegSha384
import dev.whyoleg.cryptography.algorithms.SHA3_256 as WhyolegSha3256
import dev.whyoleg.cryptography.algorithms.SHA3_384 as WhyolegSha3384
import dev.whyoleg.cryptography.algorithms.SHA3_512 as WhyolegSha3512
import dev.whyoleg.cryptography.algorithms.SHA512 as WhyolegSha512
import id.walt.ades.asn1.DerWriter
import id.walt.ades.asn1.Oids
import id.walt.crypto2.algorithms.DigestAlgorithm

/**
 * Digest algorithms with their identifiers in every AdES serialization:
 * ASN.1 OID (CAdES/PAdES, RFC 3161), COSE algorithm id (CB-AdES), XMLDSig URI (XAdES) and the JAdES/JWS name.
 */
enum class AdesDigestAlgorithm(
    val displayName: String,
    val oid: String,
    val coseId: Int?,
    val xmlUri: String,
    val jadesName: String,
    val outputSize: Int,
    private val whyolegId: CryptographyAlgorithmId<Digest>,
) {
    SHA1("SHA-1", Oids.SHA1, -14, "http://www.w3.org/2000/09/xmldsig#sha1", "S1", 20, WhyolegSha1),
    SHA224("SHA-224", Oids.SHA224, null, "http://www.w3.org/2001/04/xmldsig-more#sha224", "S224", 28, WhyolegSha224),
    SHA256("SHA-256", Oids.SHA256, -16, "http://www.w3.org/2001/04/xmlenc#sha256", "S256", 32, WhyolegSha256),
    SHA384("SHA-384", Oids.SHA384, -43, "http://www.w3.org/2001/04/xmldsig-more#sha384", "S384", 48, WhyolegSha384),
    SHA512("SHA-512", Oids.SHA512, -44, "http://www.w3.org/2001/04/xmlenc#sha512", "S512", 64, WhyolegSha512),
    SHA3_256("SHA3-256", Oids.SHA3_256, null, "http://www.w3.org/2007/05/xmldsig-more#sha3-256", "S3-256", 32, WhyolegSha3256),
    SHA3_384("SHA3-384", Oids.SHA3_384, null, "http://www.w3.org/2007/05/xmldsig-more#sha3-384", "S3-384", 48, WhyolegSha3384),
    SHA3_512("SHA3-512", Oids.SHA3_512, null, "http://www.w3.org/2007/05/xmldsig-more#sha3-512", "S3-512", 64, WhyolegSha3512),
    ;

    suspend fun digest(data: ByteArray): ByteArray = Digests.provider.get(whyolegId).hasher().hash(data)

    /** DER AlgorithmIdentifier (parameters absent, as recommended by RFC 5754). */
    fun algorithmIdentifier(): ByteArray = DerWriter.algorithmIdentifier(oid)

    fun toCrypto2(): DigestAlgorithm = DigestAlgorithm(displayName)

    companion object {
        fun fromOid(oid: String): AdesDigestAlgorithm =
            entries.firstOrNull { it.oid == oid } ?: throw IllegalArgumentException("Unsupported digest algorithm OID $oid")

        fun fromOidOrNull(oid: String): AdesDigestAlgorithm? = entries.firstOrNull { it.oid == oid }

        fun fromCoseId(id: Int): AdesDigestAlgorithm =
            entries.firstOrNull { it.coseId == id } ?: throw IllegalArgumentException("Unsupported COSE digest algorithm $id")

        fun fromCoseIdOrNull(id: Int): AdesDigestAlgorithm? = entries.firstOrNull { it.coseId == id }

        /** Accepts COSE textual names too ("SHA-256"), as allowed by `hashAlg: int / tstr`. */
        fun fromName(name: String): AdesDigestAlgorithm = entries.firstOrNull {
            it.displayName.equals(name, true) || it.jadesName.equals(name, true) || it.name.equals(name, true) ||
                it.displayName.replace("-", "").equals(name.replace("-", ""), true)
        } ?: throw IllegalArgumentException("Unsupported digest algorithm $name")

        fun fromXmlUri(uri: String): AdesDigestAlgorithm =
            entries.firstOrNull { it.xmlUri == uri } ?: throw IllegalArgumentException("Unsupported XML digest algorithm $uri")

        fun fromJadesName(name: String): AdesDigestAlgorithm =
            entries.firstOrNull { it.jadesName == name } ?: throw IllegalArgumentException("Unsupported JAdES digest algorithm $name")

        fun fromCrypto2(algorithm: DigestAlgorithm): AdesDigestAlgorithm =
            entries.firstOrNull { it.displayName == algorithm.name } ?: throw IllegalArgumentException("Unsupported digest $algorithm")
    }
}

internal object Digests {
    val provider: CryptographyProvider by lazy { CryptographyProvider.Default }
}

suspend fun ByteArray.sha256(): ByteArray = AdesDigestAlgorithm.SHA256.digest(this)
suspend fun ByteArray.sha1(): ByteArray = AdesDigestAlgorithm.SHA1.digest(this)
