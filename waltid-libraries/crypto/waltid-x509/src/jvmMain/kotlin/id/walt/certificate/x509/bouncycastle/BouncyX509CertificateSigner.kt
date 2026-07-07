package id.walt.x509.id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateSigner
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.crypto.keys.Key
import id.walt.x509.id.walt.certificate.x509.bouncycastle.extension.BouncyExtensionFactory
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import java.math.BigInteger
import java.util.*

internal class BouncyX509CertificateSigner : X509CertificateSigner {

    override suspend fun signCertificate(
        issuerKey: Key,
        builder: X509CertificateDataBuilder
    ): X509Certificate {
        val serial = BigInteger(builder.serialNumberRaw.toByteArray())
        val issuer = X500Name(builder.issuerDn)
        val notBefore = Date(builder.validity.notBefore.toEpochMilliseconds())
        val notAfter = Date(builder.validity.notAfter.toEpochMilliseconds())
        val subject = X500Name(builder.subjectDn)

        val keyInfo =
            if (builder.subjectPublicKeyInfo is X509CertificateDataBuilder.SelfSignedSubjectPublicKeyInfo) {
                SubjectPublicKeyInfoUtil.subjectKeyInfoOfKey(issuerKey)
            } else {
                SubjectPublicKeyInfoUtil.subjectKeyInfoOfBuilder(builder.subjectPublicKeyInfo)
            }
        val bouncyBuilder = X509v3CertificateBuilder(issuer, serial, notBefore, notAfter, subject, keyInfo)

        builder.extensions.values.forEach {
            bouncyBuilder.addExtension(BouncyExtensionFactory.createExtension(it))
        }

        val signed = bouncyBuilder.build(BouncyContentSigner(issuerKey))
        return BouncyX509Certificate(signed)
    }

}