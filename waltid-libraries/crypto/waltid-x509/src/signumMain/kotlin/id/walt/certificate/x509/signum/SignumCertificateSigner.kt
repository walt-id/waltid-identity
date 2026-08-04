package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.Asn1Time
import at.asitplus.signum.indispensable.asn1.encoding.parse
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName
import at.asitplus.signum.indispensable.pki.TbsCertificate
import at.asitplus.signum.indispensable.toX509SignatureAlgorithm
import id.walt.certificate.x509.SignumPublicKeyInfoUtil.toSignumPublicKey
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateSigner
import id.walt.certificate.x509.X509SigningAlgorithmInfo
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.dn.DistinguishedName
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension
import id.walt.certificate.x509.signum.SignumSignatureAlgorithmUtil.toSignatureAlgorithm
import id.walt.certificate.x509.signum.dn.toSignumDn
import id.walt.certificate.x509.signum.extension.SignumExtensionFactory
import id.walt.crypto.keys.Key
import kotlinx.io.bytestring.isNotEmpty
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName as SigRdn
import at.asitplus.signum.indispensable.pki.X509Certificate as SigX509Certificate

class SignumCertificateSigner : X509CertificateSigner {

    override suspend fun signCertificate(
        issuerKey: Key,
        builder: X509CertificateDataBuilder
    ): X509Certificate {
        require(builder.version == 3) { "Only version 3 certificates are supported by Signum" }
        val subjectDn: List<RelativeDistinguishedName> =
            DistinguishedName.ofString(builder.subjectDn).toSignumDn()

        var issuerDn: List<RelativeDistinguishedName> = subjectDn

        // Evaluate public key material
        val authorityPublicKeyInfo = issuerKey.toSignumPublicKey()
        val subjectPublicKeyInfo =
            (builder.subjectPublicKeyInfo as X509CertificateDataBuilder.WaltIdKeySubjectPublicKeyInfoBuilder).let {
                if (it.selfSigned) {
                    issuerDn = subjectDn
                    authorityPublicKeyInfo
                } else {
                    require(builder.issuerDnRaw.isNotEmpty()) { "Issuer DN must be set for non-self-signed certificates." }
                    issuerDn = Asn1Element.parse(builder.issuerDnRaw.toByteArray())
                        .asSequence().children.map {
                            SigRdn.decodeFromTlv(it.asSet())
                        }
                    requireNotNull(it.key) { "Subject key must be provided for non-self-signed certificates." }
                    it.key.toSignumPublicKey()
                }
            }

        // Configure Validity Timestamps using Asn1Time wrapper
        val notBefore = Asn1Time(builder.validity.notBefore)
        val notAfter = Asn1Time(builder.validity.notAfter)

        //4. Convert extensions
        val extensions = builder.extensions.values.map { value ->
            if (value.oid == SubjectKeyIdentifierExtension.OID) {
                SignumExtensionFactory.createSubjectKeyIdentifierExtension(
                    value,
                    subjectPublicKeyInfo
                )
            } else if (value.oid == AuthorityKeyIdentifierExtension.OID) {
                SignumExtensionFactory.createAuthorityKeyIdentifierExtension(value, authorityPublicKeyInfo)
            } else {
                SignumExtensionFactory.createExtension(value)
            }
        }

        // 5. Construct the Certificate Structure (TBSCertificate)
        val sigAlgorithm = X509SigningAlgorithmInfo.ofKey(issuerKey)
        val signumSigAlgorithm = X509SigningAlgorithmInfo.ofKey(issuerKey).toSignatureAlgorithm()
        val signumSigAlgorithmDescription = signumSigAlgorithm
            .toX509SignatureAlgorithm()
            .getOrThrow()

        val tbsCertificate = TbsCertificate(
            version = builder.version - 1,
            serialNumber = builder.serialNumberRaw.toByteArray(),
            signatureAlgorithm = signumSigAlgorithmDescription,
            issuerName = issuerDn,
            validFrom = notBefore,
            validUntil = notAfter,
            subjectName = subjectDn,
            publicKey = subjectPublicKeyInfo,
            issuerUniqueID = null,
            subjectUniqueID = null,
            extensions = extensions
        )

        // 7. Sign the payload using the Issuer Private Key
        // Signum abstracts encoding the payload block structure into ASN.1
        val tbsDerBytes: ByteArray = tbsCertificate.encodeToDer()
        val rawSignatureBytes: ByteArray = issuerKey.signRaw(tbsDerBytes) as ByteArray
        val signature = SignumSignatureAlgorithmUtil.evaluateSignature(sigAlgorithm, rawSignatureBytes)

        // 6. Combine the TBS block and Signature into a definitive X509 Certificate
        val certificate = SigX509Certificate(
            tbsCertificate = tbsCertificate,
            signatureAlgorithm = signumSigAlgorithmDescription,
            signature = signature
        )
        return SignumX509Certificate(certificate)
    }
}