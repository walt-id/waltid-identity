package id.walt.x509.id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.BouncyPublicKeyInfoUtil
import id.walt.certificate.x509.BouncyPublicKeyInfoUtil.bouncyCastleSubjectPublicKeyInfo
import id.walt.certificate.x509.PublicKeyInfo
import id.walt.certificate.x509.SignatureValidator
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateSigner
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.builder.X509CertificateDataBuilder.WaltIdKeySubjectPublicKeyInfoBuilder
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension
import id.walt.crypto.keys.Key
import id.walt.x509.id.walt.certificate.x509.bouncycastle.extension.BouncyExtensionFactory
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentVerifierProvider
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import java.math.BigInteger
import java.security.Security
import java.util.*


class BouncyX509CertificateSigner : X509CertificateSigner, SignatureValidator {

    companion object {
        init {
            // Register Bouncy Castle Provider
            Security.addProvider(BouncyCastleProvider())
        }
    }

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
            (builder.subjectPublicKeyInfo as WaltIdKeySubjectPublicKeyInfoBuilder).let { builder ->
                if (builder.selfSigned) {
                    BouncyPublicKeyInfoUtil.publicKeyInfoOfKey(issuerKey)
                } else {
                    checkNotNull(builder.key) { "Certificate subject public key missing" }
                    BouncyPublicKeyInfoUtil.publicKeyInfoOfKey(builder.key)
                }
            }
        val bouncyBuilder = X509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            subject,
            keyInfo.bouncyCastleSubjectPublicKeyInfo
        )

        builder.extensions.values.forEach {
            if (it is SubjectKeyIdentifierExtension) {
                bouncyBuilder.addExtension(
                    BouncyExtensionFactory.createSubjectKeyIdentifierExtension(
                        it,
                        keyInfo.bouncyCastleSubjectPublicKeyInfo.publicKeyData
                    )
                )
            } else {
                bouncyBuilder.addExtension(BouncyExtensionFactory.createExtension(it))
            }
        }

        val signed = bouncyBuilder.build(BouncyContentSigner(issuerKey))
        return BouncyX509Certificate(signed)
    }

    override suspend fun validateCertificateSignature(
        issuerPublicKey: X509Certificate.SubjectPublicKeyInfo,
        certificate: X509Certificate
    ): Boolean {

        val bouncyCertificate = if (certificate is BouncyX509Certificate) {
            certificate.certificate
        } else {
            X509CertificateHolder(certificate.encodedDer.toByteArray())
        }

        val publicKey = SubjectPublicKeyInfo(
            AlgorithmIdentifier(
                ASN1ObjectIdentifier(issuerPublicKey.algorithmOid),
                issuerPublicKey.ellipticCurveOid?.let { ASN1ObjectIdentifier(it) }
            ),
            issuerPublicKey.keyValueRaw.toByteArray())

        // Build the verifier provider using the issuer's public key
        val verifierProvider: ContentVerifierProvider? = JcaContentVerifierProviderBuilder()
            .setProvider("BC")
            .build(publicKey)

        return bouncyCertificate.isSignatureValid(verifierProvider)
    }

    override suspend fun validateCsrSignature(
        subjectPublicKey: PublicKeyInfo,
        certificate: X509Certificate
    ): Boolean {
        TODO("Not yet implemented")
    }
}