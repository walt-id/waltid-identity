package id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.BouncyPublicKeyInfoUtil
import id.walt.certificate.x509.BouncyPublicKeyInfoUtil.bouncyCastleSubjectPublicKeyInfo
import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.Pkcs10CertificateSigningRequestSigner
import id.walt.certificate.x509.PublicKeyInfo
import id.walt.certificate.x509.bouncycastle.extension.BouncyExtensionFactory
import id.walt.certificate.x509.builder.Pkcs10CertificateSigningRequestBuilder
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.Key
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder
import id.walt.crypto.keys.Key as Crypto1Key


class BouncyPkcs10CertificateSigningRequestSigner : Pkcs10CertificateSigningRequestSigner {

    override suspend fun signCsr(
        holderKey: Key,
        signatureAlgorithm: SignatureAlgorithm,
        csrBuilder: Pkcs10CertificateSigningRequestBuilder
    ): Pkcs10CertificateSigningRequest {
        val keyInfo = BouncyPublicKeyInfoUtil.publicKeyInfoOfKey(holderKey)
        val bouncyBuilder = buildBouncyCsr(csrBuilder, keyInfo)
        val signed = bouncyBuilder.build(BouncyContentSigner(holderKey, signatureAlgorithm))
        return BouncyPkcs10CertificateSigningRequest(signed)
    }

    override suspend fun signCsr(
        holderKey: Crypto1Key,
        csrBuilder: Pkcs10CertificateSigningRequestBuilder
    ): Pkcs10CertificateSigningRequest {
        val keyInfo = BouncyPublicKeyInfoUtil.publicKeyInfoOfKey(holderKey)
        val bouncyBuilder = buildBouncyCsr(csrBuilder, keyInfo)
        val signed = bouncyBuilder.build(BouncyCrypto1ContentSigner(holderKey))
        return BouncyPkcs10CertificateSigningRequest(signed)
    }

    private fun buildBouncyCsr(
        csrBuilder: Pkcs10CertificateSigningRequestBuilder,
        publicKeyInfo: PublicKeyInfo
    ): PKCS10CertificationRequestBuilder {

        val subject = X500Name(csrBuilder.requestedCertificate.subjectDn)
        val bouncyBuilder = PKCS10CertificationRequestBuilder(subject, publicKeyInfo.bouncyCastleSubjectPublicKeyInfo)

        if (csrBuilder.requestedCertificate.extensions.isNotEmpty()) {
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
        }
        return bouncyBuilder
    }

}