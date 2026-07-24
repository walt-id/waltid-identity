package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.asn1.Asn1Time
import at.asitplus.signum.indispensable.pki.TbsCertificate
import at.asitplus.signum.indispensable.toX509SignatureAlgorithm
import id.walt.certificate.x509.SignumPublicKeyInfoUtil.toSignumPublicKey
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateSigner
import id.walt.certificate.x509.X509SigningAlgorithmInfo
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.dn.DistinguishedName
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension
import id.walt.certificate.x509.signum.SignumSignatureAlgorithmUtil.toSignatureAlgorithm
import id.walt.certificate.x509.signum.dn.toSignumDn
import id.walt.certificate.x509.signum.extension.SignumExtensionFactory
import id.walt.crypto.keys.Key
import at.asitplus.signum.indispensable.pki.X509Certificate as SigX509Certificate

class SignumCertificateSigner : X509CertificateSigner {

    override suspend fun signCertificate(
        issuerKey: Key,
        builder: X509CertificateDataBuilder
    ): X509Certificate {
        require(builder.version == 3) { "Only version 3 certificates are supported by Signum" }
        // 1. Evaluate public key material
        val publicKeyInfo =
            (builder.subjectPublicKeyInfo as X509CertificateDataBuilder.WaltIdKeySubjectPublicKeyInfoBuilder).let {
                require(it.selfSigned) { "Only self-signed certificates are supported by Signum" }
                issuerKey.toSignumPublicKey()
            }

        // 2. Define the Identity (Subject & Issuer are identical for self-signed)
        val subjectDn = DistinguishedName.ofString(builder.subjectDn)
        val issuerDn = DistinguishedName.ofString(builder.issuerDn)

        // 3. Configure Validity Timestamps using Asn1Time wrapper
        val notBefore = Asn1Time(builder.validity.notBefore)
        val notAfter = Asn1Time(builder.validity.notAfter)

        //4. Convert extensions
        val extensions = builder.extensions.values.map { value ->
            if (value.oid == SubjectKeyIdentifierExtension.OID) {
                SignumExtensionFactory.createSubjectKeyIdentifierExtension(
                    value,
                    publicKeyInfo
                )
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
            issuerName = issuerDn.toSignumDn(),
            validFrom = notBefore,
            validUntil = notAfter,
            subjectName = subjectDn.toSignumDn(),
            publicKey = publicKeyInfo,
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