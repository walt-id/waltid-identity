package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.signum.indispensable.pki.TbsCertificationRequest
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import at.asitplus.signum.indispensable.toX509SignatureAlgorithm
import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.Pkcs10CertificateSigningRequestSigner
import id.walt.certificate.x509.SignumPublicKeyInfoUtil.toSignumPublicKey
import id.walt.certificate.x509.X509SigningAlgorithmInfo
import id.walt.certificate.x509.builder.Pkcs10CertificateSigningRequestBuilder
import id.walt.certificate.x509.dn.DistinguishedName
import id.walt.certificate.x509.signum.SignumSignatureAlgorithmUtil.toSignatureAlgorithm
import id.walt.certificate.x509.signum.dn.toSignumDn
import id.walt.certificate.x509.signum.extension.SignumExtensionFactory
import id.walt.crypto.keys.Key


class SignumCsrSigner : Pkcs10CertificateSigningRequestSigner {

    override suspend fun signCsr(
        holderKey: Key,
        csrBuilder: Pkcs10CertificateSigningRequestBuilder
    ): Pkcs10CertificateSigningRequest {

        val subjectDn = DistinguishedName.ofString(csrBuilder.requestedCertificate.subjectDn)
        val extensions = csrBuilder.requestedCertificate.extensions.values.map { value ->
            SignumExtensionFactory.createExtension(value)
        }

        val tbsCsr = TbsCertificationRequest(
            subjectName = subjectDn.toSignumDn(),
            publicKey = holderKey.toSignumPublicKey(),
            extensions = extensions,
        )

        // 1. Convert the TBS data class to its canonical ASN.1 DER byte structure
        val tbsDerBytes: ByteArray = tbsCsr.encodeToDer()

        // 2. Compute the cryptographic signature using your JS/External provider
        val rawSignatureBytes: ByteArray = holderKey.signRaw(tbsDerBytes) as ByteArray

        // 3. Instantiate the appropriate CryptoSignature variant manually.
        // For EC keys (e.g., P-256), use EC.fromRawBytes. For RSA, use CryptoSignature.RSA.
        val sigAlgorithm = X509SigningAlgorithmInfo.ofKey(holderKey)
        val algorithm = sigAlgorithm.toSignatureAlgorithm()
        val signature = SignumSignatureAlgorithmUtil.evaluateSignature(sigAlgorithm, rawSignatureBytes)

        // 4. Directly construct the finished PKCS#10 Certificate Request
        val encodedSignedCsr = Pkcs10CertificationRequest(
            tbsCsr = tbsCsr,
            signatureAlgorithm = algorithm.toX509SignatureAlgorithm().getOrThrow(),
            signature = signature
        )

        return SignumCsr(encodedSignedCsr)
    }
}