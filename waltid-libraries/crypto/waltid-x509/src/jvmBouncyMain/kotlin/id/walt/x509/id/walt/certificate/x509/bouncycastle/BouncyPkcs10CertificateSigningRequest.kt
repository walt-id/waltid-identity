package id.walt.x509.id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.X509SigningAlgorithmInfo
import id.walt.certificate.x509.extension.Extension
import id.walt.x509.id.walt.certificate.x509.bouncycastle.extension.BouncyExtensionFactory
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.pkcs.PKCS10CertificationRequest

class BouncyPkcs10CertificateSigningRequest(val csr: PKCS10CertificationRequest) : Pkcs10CertificateSigningRequest {

    override val requestedCertificate: Pkcs10CertificateSigningRequest.RequestedCertificateData =

        object : Pkcs10CertificateSigningRequest.RequestedCertificateData {
            override val subjectDn: String
                get() = csr.subject.toString()

            override val subjectPublicKeyInfo: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo =
                object : Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo {
                    override val algorithmName: String
                        get() = X509SigningAlgorithmInfo.algorithmNameByOid(algorithmOid)
                    override val algorithmOid: String
                        get() = csr.subjectPublicKeyInfo.algorithm.algorithm.toString()
                    override val ellipticCurveOid: String?
                        get() = (csr.subjectPublicKeyInfo.algorithm.parameters as? ASN1ObjectIdentifier)?.toString()
                    override val publicKeyRaw: ByteString = ByteString(csr.subjectPublicKeyInfo.publicKeyData.bytes)
                }

            override val extensions: Map<String, Extension>
                get() {
                    val attributes = csr.attributes
                    val extensions = csr.requestedExtensions
                    val mapped = csr.requestedExtensions?.extensionOIDs?.let { extensionOids ->
                        extensionOids
                            .associateWith {
                                BouncyExtensionFactory.parseExtension(
                                    extensions.getExtension(
                                        it
                                    )
                                )
                            }
                            .mapKeys { it.key.toString() }
                    } ?: emptyMap()
                    return mapped
                }
        }

    override val signatureAlgorithmOid: String =
        csr.signatureAlgorithm.algorithm.id

    override val signatureValueRaw: ByteString
        get() = ByteString(csr.signature)

    override val encodedDer: ByteString
        get() = ByteString(csr.encoded)
}