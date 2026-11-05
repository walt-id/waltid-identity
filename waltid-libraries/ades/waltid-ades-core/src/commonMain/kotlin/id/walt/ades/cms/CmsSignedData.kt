package id.walt.ades.cms

import id.walt.ades.asn1.Der
import id.walt.ades.asn1.DerElement
import id.walt.ades.asn1.DerWriter
import id.walt.ades.asn1.Oids
import id.walt.ades.crypto.AdesCrypto
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.crypto.AdesSignatureAlgorithm
import id.walt.ades.x509.DistinguishedName
import id.walt.ades.x509.X509Certificate
import id.walt.ades.x509.X509Crl

class CmsAttribute(val oid: String, val values: List<DerElement>, val encoded: ByteArray)

class SignerInfo internal constructor(private val element: DerElement) {
    val encoded: ByteArray get() = element.encoded
    private val sid = element.child(1)

    val sidIssuer: DistinguishedName? = sid.takeIf { it.isUniversal(Der.SEQUENCE) }?.child(0)?.let(DistinguishedName::of)
    val sidSerial: ByteArray? = sid.takeIf { it.isUniversal(Der.SEQUENCE) }?.child(1)?.asIntegerBytes()
    val sidSubjectKeyIdentifier: ByteArray? = sid.takeIf { it.isContext(0) }?.content

    val digestAlgorithm: AdesDigestAlgorithm = AdesDigestAlgorithm.fromOid(element.child(2).child(0).asOid())

    private val signedAttrsElement: DerElement? = element.children.firstOrNull { it.isContext(0) && it.constructed }
    private val signatureAlgIndex = if (signedAttrsElement != null) 4 else 3

    /** DER of the signed attributes re-tagged as SET OF, i.e. the bytes actually signed (RFC 5652 5.4). */
    val signedAttributesDer: ByteArray? = signedAttrsElement?.let { DerWriter.retag(it.encoded, 0x31) }

    val signedAttributes: List<CmsAttribute> = signedAttrsElement?.children?.map(::attribute) ?: emptyList()

    val unsignedAttributes: List<CmsAttribute> =
        element.children.firstOrNull { it.isContext(1) && it.constructed }?.children?.map(::attribute) ?: emptyList()

    val signatureAlgorithmElement: DerElement = element.child(signatureAlgIndex)
    val signatureAlgorithm: AdesSignatureAlgorithm
        get() = AdesSignatureAlgorithm.fromAlgorithmIdentifier(signatureAlgorithmElement, digestAlgorithm)
    val signature: ByteArray = element.child(signatureAlgIndex + 1).asOctetString()

    fun signedAttribute(oid: String): CmsAttribute? = signedAttributes.firstOrNull { it.oid == oid }

    val messageDigest: ByteArray? get() = signedAttribute(Oids.ATTR_MESSAGE_DIGEST)?.values?.firstOrNull()?.asOctetString()

    fun matchesCertificate(cert: X509Certificate): Boolean =
        (sidIssuer != null && sidSerial != null && cert.issuer.matches(sidIssuer) && cert.serialNumber.contentEquals(sidSerial)) ||
            (sidSubjectKeyIdentifier != null && cert.subjectKeyIdentifier?.contentEquals(sidSubjectKeyIdentifier) == true)

    /** ESS signing-certificate(-v2) references: (digest algorithm, hash, issuerSerial DER?) tuples. */
    val essCertIds: List<Triple<AdesDigestAlgorithm, ByteArray, DerElement?>>
        get() {
            signedAttribute(Oids.ATTR_SIGNING_CERTIFICATE_V2)?.let { attr ->
                return attr.values.first().child(0).children.map { id ->
                    var i = 0
                    val alg = if (id.child(0).isUniversal(Der.SEQUENCE)) AdesDigestAlgorithm.fromOid(id.child(i++).child(0).asOid()) else AdesDigestAlgorithm.SHA256
                    Triple(alg, id.child(i).asOctetString(), id.children.getOrNull(i + 1))
                }
            }
            signedAttribute(Oids.ATTR_SIGNING_CERTIFICATE)?.let { attr ->
                return attr.values.first().child(0).children.map { id -> Triple(AdesDigestAlgorithm.SHA1, id.child(0).asOctetString(), id.children.getOrNull(1)) }
            }
            return emptyList()
        }

    suspend fun verifySignature(signer: X509Certificate, content: ByteArray?): Boolean {
        val algorithm = runCatching { signatureAlgorithm }.getOrNull() ?: return false
        val signed = signedAttributesDer
        return if (signed != null) {
            val md = messageDigest ?: return false
            if (content != null && !md.contentEquals(digestAlgorithm.digest(content))) return false
            AdesCrypto.verify(signer.publicKey, algorithm, signed, signature, derEcdsa = true)
        } else {
            content != null && AdesCrypto.verify(signer.publicKey, algorithm, content, signature, derEcdsa = true)
        }
    }

    private fun attribute(e: DerElement) = CmsAttribute(e.child(0).asOid(), e.child(1).children, e.encoded)
}

/** CMS SignedData (RFC 5652) parser; [encoded] is the full ContentInfo. */
class CmsSignedData(encoded: ByteArray) {
    val encoded: ByteArray = encoded.copyOf()
    private val contentInfo = Der.parseFirst(this.encoded).expectSequence()
    private val signedData: DerElement

    init {
        require(contentInfo.child(0).asOid() == Oids.CMS_SIGNED_DATA) { "Not a CMS SignedData ContentInfo" }
        signedData = contentInfo.child(1).explicitInner().expectSequence()
    }

    private val encap = signedData.child(2)
    val encapsulatedContentType: String = encap.child(0).asOid()
    val encapsulatedContent: ByteArray? = encap.contextChild(0)?.explicitInner()?.let { inner ->
        // DER OCTET STRING, tolerate BER constructed octet strings from legacy producers
        if (inner.constructed) DerWriter.concat(inner.children.map { it.content }) else inner.content
    }

    val certificates: List<X509Certificate> = signedData.children.firstOrNull { it.isContext(0) && it.constructed }
        ?.children?.filter { it.isUniversal(Der.SEQUENCE) }?.mapNotNull { runCatching { X509Certificate(it.encoded) }.getOrNull() } ?: emptyList()

    val crls: List<X509Crl> = signedData.children.firstOrNull { it.isContext(1) && it.constructed }
        ?.children?.filter { it.isUniversal(Der.SEQUENCE) }?.mapNotNull { runCatching { X509Crl(it.encoded) }.getOrNull() } ?: emptyList()

    /** RFC 5940 OtherRevocationInfoFormat entries carrying OCSP responses inside [1] crls. */
    val ocspResponses: List<ByteArray> = signedData.children.firstOrNull { it.isContext(1) && it.constructed }
        ?.children?.filter { it.isContext(1) }?.mapNotNull { other ->
            runCatching { if (other.child(0).asOid() == "1.3.6.1.5.5.7.16.2") other.child(1).encoded else null }.getOrNull()
        } ?: emptyList()

    val signerInfos: List<SignerInfo> = signedData.children.last().expectSet().children.map(::SignerInfo)

    fun findSigner(signerInfo: SignerInfo, extra: Collection<X509Certificate> = emptyList()): X509Certificate? =
        (certificates + extra).firstOrNull { signerInfo.matchesCertificate(it) }
}
