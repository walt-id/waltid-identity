package id.walt.certificate.x509.signum

import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.Pkcs10CertificateSigningRequestSigner
import id.walt.certificate.x509.builder.Pkcs10CertificateSigningRequestBuilder
import id.walt.crypto.keys.Key

class SignumCsrSigner : Pkcs10CertificateSigningRequestSigner {

    override suspend fun signCsr(
        holderKey: Key,
        csrBuilder: Pkcs10CertificateSigningRequestBuilder
    ): Pkcs10CertificateSigningRequest {
        TODO("Not yet implemented")
        /*
                val subjectDn = DistinguishedName.ofString(csrBuilder.requestedCertificate.subjectDn)

                val tbsCsr = TbsCertificationRequest(
                    subjectName = profileData.subjectName.toSignumName(),
                    publicKey = signingKey.toSignumPublicKey(),
                    extensions = extensions,
                )

                csrBuilder.requestedCertificate.subjectDn.split(",")
                    .map { it.toInt() }
         */
    }
}