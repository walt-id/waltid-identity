package id.walt.ades.x509

import id.walt.ades.asn1.Der
import id.walt.ades.asn1.DerElement
import id.walt.ades.asn1.DerWriter
import id.walt.ades.asn1.Oids
import id.walt.ades.crypto.AdesCrypto
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.crypto.AdesSignatureAlgorithm
import id.walt.ades.crypto.PublicKeyInfo
import kotlin.time.Instant

class X509Extension(val oid: String, val critical: Boolean, val value: ByteArray) {
    val parsed: DerElement get() = Der.parse(value)
}

enum class KeyUsageBit { DIGITAL_SIGNATURE, NON_REPUDIATION, KEY_ENCIPHERMENT, DATA_ENCIPHERMENT, KEY_AGREEMENT, KEY_CERT_SIGN, CRL_SIGN, ENCIPHER_ONLY, DECIPHER_ONLY }

/** Multiplatform, read-only X.509 v3 certificate (RFC 5280). */
class X509Certificate(encoded: ByteArray) {
    val encoded: ByteArray = encoded.copyOf()
    private val root = Der.parse(this.encoded).expectSequence()
    private val tbs = root.child(0).expectSequence()
    private val offset = if (tbs.child(0).isContext(0)) 1 else 0

    val tbsEncoded: ByteArray get() = tbs.encoded
    val version: Int = if (offset == 1) tbs.child(0).explicitInner().asInt() + 1 else 1
    val serialNumber: ByteArray = tbs.child(offset).asIntegerBytes()
    val serialNumberHex: String get() = serialNumber.toHexString().trimStart('0').ifEmpty { "0" }
    val issuer: DistinguishedName = DistinguishedName.of(tbs.child(offset + 2))
    val notBefore: Instant = tbs.child(offset + 3).child(0).asTime()
    val notAfter: Instant = tbs.child(offset + 3).child(1).asTime()
    val subject: DistinguishedName = DistinguishedName.of(tbs.child(offset + 4))
    val publicKey: PublicKeyInfo = PublicKeyInfo(tbs.child(offset + 5).encoded)
    val signatureAlgorithmElement: DerElement = root.child(1)
    val signatureValue: ByteArray = root.child(2).asBitStringBytes()

    val extensions: List<X509Extension> = tbs.contextChild(3)?.explicitInner()?.children?.map { ext ->
        val critical = ext.children.size == 3 && ext.child(1).asBoolean()
        X509Extension(ext.child(0).asOid(), critical, ext.children.last().asOctetString())
    } ?: emptyList()

    fun extension(oid: String): X509Extension? = extensions.firstOrNull { it.oid == oid }

    val isSelfIssued: Boolean get() = issuer.matches(subject)

    val isCa: Boolean
        get() = extension(Oids.EXT_BASIC_CONSTRAINTS)?.parsed?.children?.firstOrNull()
            ?.takeIf { it.isUniversal(Der.BOOLEAN) }?.asBoolean() ?: false

    val pathLengthConstraint: Int?
        get() = extension(Oids.EXT_BASIC_CONSTRAINTS)?.parsed?.children?.firstOrNull { it.isUniversal(Der.INTEGER) }?.asInt()

    val keyUsage: Set<KeyUsageBit>?
        get() = extension(Oids.EXT_KEY_USAGE)?.parsed?.asBitList()?.let { bits ->
            bits.mapIndexedNotNull { i, set -> if (set) KeyUsageBit.entries.getOrNull(i) else null }.toSet()
        }

    val extendedKeyUsage: List<String>
        get() = extension(Oids.EXT_EXTENDED_KEY_USAGE)?.parsed?.children?.map { it.asOid() } ?: emptyList()

    val subjectKeyIdentifier: ByteArray?
        get() = extension(Oids.EXT_SUBJECT_KEY_ID)?.parsed?.asOctetString()

    val authorityKeyIdentifier: ByteArray?
        get() = extension(Oids.EXT_AUTHORITY_KEY_ID)?.parsed?.contextChild(0)?.content

    val crlDistributionPoints: List<String>
        get() = extension(Oids.EXT_CRL_DISTRIBUTION_POINTS)?.parsed?.children?.flatMap { dp ->
            dp.contextChild(0)?.contextChild(0)?.let(::uris) ?: emptyList()
        } ?: emptyList()

    val ocspResponderUrls: List<String> get() = accessLocations(Oids.AD_OCSP)
    val caIssuersUrls: List<String> get() = accessLocations(Oids.AD_CA_ISSUERS)

    val hasOcspNoCheck: Boolean get() = extension(Oids.OCSP_NOCHECK) != null

    private fun accessLocations(method: String): List<String> =
        extension(Oids.EXT_AUTHORITY_INFO_ACCESS)?.parsed?.children
            ?.filter { it.child(0).asOid() == method }
            ?.mapNotNull { ad -> ad.child(1).takeIf { it.isContext(6) }?.content?.decodeToString() } ?: emptyList()

    private fun uris(generalNames: DerElement): List<String> =
        generalNames.children.filter { it.isContext(6) }.map { it.content.decodeToString() }

    fun isValidAt(time: Instant): Boolean = time >= notBefore && time <= notAfter

    suspend fun digest(algorithm: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA256): ByteArray = algorithm.digest(encoded)

    /** DER IssuerSerial (RFC 5035): SEQUENCE { GeneralNames{ directoryName [4] issuer }, serialNumber }. */
    fun issuerSerial(): ByteArray = DerWriter.sequence(
        DerWriter.sequence(DerWriter.explicit(4, issuer.encoded)),
        DerWriter.integerContent(serialNumber),
    )

    /** Verifies this certificate's signature with [issuerKey] (the issuer's subject public key). */
    suspend fun verifySignature(issuerKey: PublicKeyInfo): Boolean {
        val alg = runCatching { AdesSignatureAlgorithm.fromAlgorithmIdentifier(signatureAlgorithmElement) }.getOrNull() ?: return false
        return AdesCrypto.verify(issuerKey, alg, tbsEncoded, signatureValue, derEcdsa = true)
    }

    suspend fun isIssuedBy(candidate: X509Certificate): Boolean {
        if (!issuer.matches(candidate.subject)) return false
        val aki = authorityKeyIdentifier
        val ski = candidate.subjectKeyIdentifier
        if (aki != null && ski != null && !aki.contentEquals(ski)) return false
        return verifySignature(candidate.publicKey)
    }

    fun toPem(): String = Pem.encode("CERTIFICATE", encoded)

    override fun equals(other: Any?): Boolean = other is X509Certificate && encoded.contentEquals(other.encoded)
    override fun hashCode(): Int = encoded.contentHashCode()
    override fun toString(): String = "X509Certificate(subject=$subject, serial=$serialNumberHex)"

    companion object {
        fun fromPem(pem: String): X509Certificate = X509Certificate(Pem.decodeAll(pem, "CERTIFICATE").single())
    }
}
