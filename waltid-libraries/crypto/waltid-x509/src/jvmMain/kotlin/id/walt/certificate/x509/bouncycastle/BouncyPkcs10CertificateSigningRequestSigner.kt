package id.walt.x509.id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.Pkcs10CertificateSigningRequestSigner
import id.walt.certificate.x509.builder.Pkcs10CertificateSigningRequestBuilder
import id.walt.crypto.keys.Key
import id.walt.x509.id.walt.certificate.x509.bouncycastle.extension.BouncyExtensionFactory
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder


class BouncyPkcs10CertificateSigningRequestSigner : Pkcs10CertificateSigningRequestSigner {

    override suspend fun signCsr(
        holderKey: Key,
        csrBuilder: Pkcs10CertificateSigningRequestBuilder
    ): Pkcs10CertificateSigningRequest {
        val subject = X500Name(csrBuilder.requestedCertificate.subjectDn)
        val keyInfo = SubjectPublicKeyInfoUtil.subjectKeyInfoOfKey(holderKey)

        val bouncyBuilder = PKCS10CertificationRequestBuilder(subject, keyInfo)

        val extGen = ExtensionsGenerator()
        csrBuilder.requestedCertificate.extensions.values.map {
            BouncyExtensionFactory.createExtension(it)
        }.forEach {
            extGen.addExtension(it)
        }
        bouncyBuilder.setAttribute(
            PKCSObjectIdentifiers.pkcs_9_at_extensionRequest,
            extGen.generate()
        )
        val signed = bouncyBuilder.build(BouncyContentSigner(holderKey))
        return BouncyPkcs10CertificateSigningRequest(signed)
    }

}