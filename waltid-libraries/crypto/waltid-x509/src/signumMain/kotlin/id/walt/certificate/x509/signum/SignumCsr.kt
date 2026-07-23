package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.asn1.Asn1BitString
import at.asitplus.signum.indispensable.asn1.KnownOIDs
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.extensionRequest
import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.extension.Extension
import id.walt.certificate.x509.signum.dn.toDistinguishedName
import id.walt.certificate.x509.signum.extension.SignumExtensionFactory
import kotlinx.io.bytestring.ByteString
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest as SignumCertificateRequest

class SignumCsr(val csr: SignumCertificateRequest) : Pkcs10CertificateSigningRequest {

    override val requestedCertificate = SignumRequestedCertificate()

    override val signatureAlgorithmOid: String
        get() = csr.signatureAlgorithm.oid.toString()

    override val signatureValueRaw: ByteString
        get() = ByteString(Asn1BitString.decodeFromTlv(csr.rawSignature).rawBytes)

    override val encodedDer: ByteString
        get() = ByteString(csr.encodeToDer())

    inner class SignumRequestedCertificate : Pkcs10CertificateSigningRequest.RequestedCertificateData {
        override val subjectDn: String =
            csr.tbsCsr.subjectName.toDistinguishedName().toString()

        override val subjectPublicKeyInfo: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo
            get() = SignumPublicKeyInfo.ofCryptoPublicKey(csr.tbsCsr.publicKey)

        override val extensions: Map<String, Extension>
            get() = csr.tbsCsr.attributes.firstOrNull {
                it.oid == KnownOIDs.extensionRequest
            }?.let { extensionRequestAttributes ->
                extensionRequestAttributes.value.map { extensionAttributeValueRaw ->
                    extensionAttributeValueRaw.asSequence().children[0]
                }.map { extensionRaw ->
                    SignumExtensionFactory.parseExtension(extensionRaw)
                }.associateBy { it.oid }
            } ?: emptyMap()

    }
}