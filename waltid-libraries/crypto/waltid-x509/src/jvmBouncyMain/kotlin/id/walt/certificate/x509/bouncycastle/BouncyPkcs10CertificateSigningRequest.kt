package id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.bouncycastle.extension.BouncyExtensionFactory
import id.walt.certificate.x509.extension.Extension
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.pkcs.PKCS10CertificationRequest

class BouncyPkcs10CertificateSigningRequest(val csr: PKCS10CertificationRequest) : Pkcs10CertificateSigningRequest {

    override val requestedCertificate: Pkcs10CertificateSigningRequest.RequestedCertificateData =

        object : Pkcs10CertificateSigningRequest.RequestedCertificateData {
            override val subjectDn: String
                get() = csr.subject.toString()

            override val subjectDnRaw: ByteString
                get() = ByteString(csr.subject.encoded)

            override val subjectPublicKeyInfo: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo =
                BouncyPublicKeyInfo(csr.subjectPublicKeyInfo)

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