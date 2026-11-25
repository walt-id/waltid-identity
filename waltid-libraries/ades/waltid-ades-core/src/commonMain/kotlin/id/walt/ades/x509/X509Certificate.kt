package id.walt.ades.x509

import id.walt.ades.asn1.Der
import id.walt.ades.asn1.DerElement
import id.walt.ades.asn1.DerWriter
import id.walt.ades.asn1.Oids
import id.walt.ades.crypto.AdesCrypto
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.crypto.AdesSignatureAlgorithm
import id.walt.ades.crypto.PublicKeyInfo
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import kotlinx.io.bytestring.ByteString
import kotlin.time.Instant

class X509Extension(val oid: String, val critical: Boolean, val value: ByteArray) {
    val parsed: DerElement get() = Der.parse(value)
}

enum class KeyUsageBit { DIGITAL_SIGNATURE, NON_REPUDIATION, KEY_ENCIPHERMENT, DATA_ENCIPHERMENT, KEY_AGREEMENT, KEY_CERT_SIGN, CRL_SIGN, ENCIPHER_ONLY, DECIPHER_ONLY }

/**
 * Multiplatform, read-only X.509 v3 certificate (RFC 5280), backed by waltid-x509.
 * The AdES view retains original DER for signed structures, ESS issuerSerial and arbitrary extension values;
 * the shared certificate model does not expose those raw fields. Parsing and path validation use waltid-x509.
 */
class X509Certificate(encoded: ByteArray) {
    val encoded: ByteArray = encoded.copyOf()
    internal val sharedCertificate = X509CertificateUtil.Default.parseCertificateDerEncoded(ByteString(this.encoded))
    private val data = sharedCertificate.data
    private val root = Der.parse(this.encoded).expectSequence()
    private val tbs = root.child(0).expectSequence()

    val tbsEncoded: ByteArray get() = tbs.encoded
    val version: Int = data.version
    val serialNumber: ByteArray = data.serialNumberRaw.toByteArray()
    val serialNumberHex: String get() = serialNumber.toHexString().trimStart('0').ifEmpty { "0" }
    val issuer: DistinguishedName = DistinguishedName(data.issuerDnRaw.toByteArray())
    val notBefore: Instant = data.validity.notBefore
    val notAfter: Instant = data.validity.notAfter
    val subject: DistinguishedName = DistinguishedName(data.subjectDnRaw.toByteArray())
    val publicKey: PublicKeyInfo = PublicKeyInfo(data.subjectPublicKeyInfo.encodedDer.toByteArray())
    val signatureAlgorithmElement: DerElement = root.child(1)
    val signatureValue: ByteArray = sharedCertificate.signatureValueRaw.toByteArray()

    val extensions: List<X509Extension> = tbs.contextChild(3)?.explicitInner()?.children?.map { ext ->
        val critical = ext.children.size == 3 && ext.child(1).asBoolean()
        X509Extension(ext.child(0).asOid(), critical, ext.children.last().asOctetString())
    } ?: emptyList()

    fun extension(oid: String): X509Extension? = extensions.firstOrNull { it.oid == oid }

    val isSelfIssued: Boolean get() = issuer.matches(subject)

    val isCa: Boolean get() = data.extensionBasicConstraints?.cA ?: false

    val pathLengthConstraint: Int? get() = data.extensionBasicConstraints?.pathLenConstraint

    val keyUsage: Set<KeyUsageBit>?
        get() = data.extensionKeyUsage?.keyPurposeIdList?.map { KeyUsageBit.entries[it.ordinal] }?.toSet()

    // Preserve multiplicity: the shared Set view would hide duplicate EKUs from the strict RFC 3161 check.
    val extendedKeyUsage: List<String>
        get() = extension(Oids.EXT_EXTENDED_KEY_USAGE)?.parsed?.children?.map { it.asOid() } ?: emptyList()

    val subjectKeyIdentifier: ByteArray? get() = data.extensionSubjectKeyIdentifier?.keyIdentifier?.toByteArray()

    val authorityKeyIdentifier: ByteArray? get() = data.extensionAuthorityKeyIdentifier?.keyIdentifier?.toByteArray()

    val crlDistributionPoints: List<String>
        get() = extension(Oids.EXT_CRL_DISTRIBUTION_POINTS)?.parsed?.children?.flatMap { dp ->
            dp.contextChild(0)?.contextChild(0)?.let(::uris) ?: emptyList()
        } ?: emptyList()

    /** RFC 5280 reason flags shared by every advertised distribution point; null means unrestricted. */
    val crlDistributionPointReasons: Set<Int>?
        get() = extension(Oids.EXT_CRL_DISTRIBUTION_POINTS)?.parsed?.children
            ?.mapNotNull { point ->
                point.contextChild(1)?.asBitList()
                    ?.mapIndexedNotNull { index, included -> index.takeIf { included } }?.toSet()
            }?.reduceOrNull { common, reasons -> common intersect reasons }

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
