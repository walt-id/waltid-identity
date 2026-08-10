package id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.*
import id.walt.certificate.x509.BouncyPublicKeyInfoUtil.bouncyCastleSubjectPublicKeyInfo
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.bouncycastle.extension.BouncyExtensionFactory
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.builder.X509CertificateDataBuilder.WaltIdKeySubjectPublicKeyInfoBuilder
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.Key
import kotlinx.io.bytestring.isNotEmpty
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentVerifierProvider
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.math.BigInteger
import java.security.Security
import java.util.*
import id.walt.crypto.keys.Key as Crypto1Key


class BouncyX509CertificateSigner : X509CertificateSigner, SignatureValidator {

    override val name: String = "BouncyCastle"

    companion object {
        init {
            // Register Bouncy Castle Provider
            Security.addProvider(BouncyCastleProvider())
        }
    }

    override suspend fun convertKeyToPublicKeyInfo(key: Key): PublicKeyInfo =
        BouncyPublicKeyInfoUtil.publicKeyInfoOfKey(key)

    override suspend fun convertKeyToPublicKeyInfo(key: Crypto1Key): PublicKeyInfo =
        BouncyPublicKeyInfoUtil.publicKeyInfoOfKey(key)

    override suspend fun signCertificate(
        issuerKey: Key,
        signatureAlgorithm: SignatureAlgorithm,
        builder: X509CertificateDataBuilder
    ): X509Certificate {
        val issuerPublicKeyInfo = BouncyPublicKeyInfoUtil.publicKeyInfoOfKey(issuerKey)
        val subject = X500Name(builder.subjectDn)
        var issuer: X500Name? = null

        val subjectPublicKeyInfo =
            (builder.subjectPublicKeyInfo as WaltIdKeySubjectPublicKeyInfoBuilder).let { subjectKeyBuilder ->
                if (subjectKeyBuilder.selfSigned) {
                    issuer = subject
                    issuerPublicKeyInfo
                } else {
                    val issuerDnRaw = builder.issuerDnRaw
                    require(issuerDnRaw.isNotEmpty()) { "Issuer DN must be set for non-self-signed certificates" }
                    issuer = X500Name.getInstance(issuerDnRaw.toByteArray())
                    checkNotNull(subjectKeyBuilder.key) { "Certificate subject public key missing" }
                    BouncyPublicKeyInfoUtil.publicKeyInfoOfKey(subjectKeyBuilder.key)
                }
            }

        val bouncyBuilder = buildCertificateTbs(
            builder,
            issuer,
            subject,
            issuerPublicKeyInfo,
            subjectPublicKeyInfo
        )
        val signed = bouncyBuilder.build(BouncyContentSigner(issuerKey, signatureAlgorithm))
        return BouncyX509Certificate(signed)
    }

    override suspend fun signCertificate(
        issuerKey: Crypto1Key,
        builder: X509CertificateDataBuilder
    ): X509Certificate {
        val issuerPublicKeyInfo = BouncyPublicKeyInfoUtil.publicKeyInfoOfKey(issuerKey)
        val subject = X500Name(builder.subjectDn)
        var issuer: X500Name? = null

        val subjectPublicKeyInfo =
            (builder.subjectPublicKeyInfo as WaltIdKeySubjectPublicKeyInfoBuilder).let { subjectKeyBuilder ->
                if (subjectKeyBuilder.selfSigned) {
                    issuer = subject
                    issuerPublicKeyInfo
                } else {
                    val issuerDnRaw = builder.issuerDnRaw
                    require(issuerDnRaw.isNotEmpty()) { "Issuer DN must be set for non-self-signed certificates" }
                    issuer = X500Name.getInstance(issuerDnRaw.toByteArray())

                    checkNotNull(subjectKeyBuilder.crypto1key) { "Certificate subject public key missing" }
                    BouncyPublicKeyInfoUtil.publicKeyInfoOfKey(subjectKeyBuilder.crypto1key)
                }
            }

        val bouncyBuilder = buildCertificateTbs(
            builder,
            issuer,
            subject,
            issuerPublicKeyInfo,
            subjectPublicKeyInfo
        )
        val signed = bouncyBuilder.build(BouncyCrypto1ContentSigner(issuerKey))
        return BouncyX509Certificate(signed)
    }

    override suspend fun validateCertificateSignature(
        cryptoRuntime: CryptoRuntime,
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
        cryptoRuntime: CryptoRuntime,
        csr: Pkcs10CertificateSigningRequest
    ): Boolean {
        val bouncyCsr = if (csr is BouncyPkcs10CertificateSigningRequest) {
            csr.csr
        } else {
            PKCS10CertificationRequest(csr.encodedDer.toByteArray())
        }

        // Build the provider-backed verifier using the public key embedded inside the CSR
        val verifierProvider: ContentVerifierProvider? = JcaContentVerifierProviderBuilder()
            .setProvider("BC")
            .build(bouncyCsr.getSubjectPublicKeyInfo())

        // Cryptographically validate the signature
        return bouncyCsr.isSignatureValid(verifierProvider)
    }

    private fun buildCertificateTbs(
        builder: X509CertificateDataBuilder,
        issuer: X500Name?,
        subject: X500Name,
        issuerPublicKeyInfo: PublicKeyInfo,
        subjectPublicKeyInfo: PublicKeyInfo
    ): X509v3CertificateBuilder {
        val serial = BigInteger(builder.serialNumberRaw.toByteArray())
        val notBefore = Date(builder.validity.notBefore.toEpochMilliseconds())
        val notAfter = Date(builder.validity.notAfter.toEpochMilliseconds())

        check(issuer != null)
        val bouncyBuilder = X509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            subject,
            subjectPublicKeyInfo.bouncyCastleSubjectPublicKeyInfo
        )

        builder.extensions.values.forEach {
            if (it is SubjectKeyIdentifierExtension) {
                bouncyBuilder.addExtension(
                    BouncyExtensionFactory.createSubjectKeyIdentifierExtension(
                        it,
                        subjectPublicKeyInfo
                    )
                )
            } else if (it is AuthorityKeyIdentifierExtension) {
                bouncyBuilder.addExtension(
                    BouncyExtensionFactory.createAuthorityKeyIdentifierExtension(
                        it,
                        issuerPublicKeyInfo
                    )
                )
            } else {
                bouncyBuilder.addExtension(BouncyExtensionFactory.createExtension(it))
            }
        }

        return bouncyBuilder
    }
}