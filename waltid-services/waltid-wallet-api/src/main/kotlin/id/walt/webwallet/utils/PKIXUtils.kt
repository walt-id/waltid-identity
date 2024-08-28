package id.walt.webwallet.utils

import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.*

object PKIXUtils {

    fun generateRootCACertificate(
        caKeyPair: KeyPair,
        validFrom: Date,
        validTo: Date,
        caName: X500Name,
    ) = generateCASignedCertificate(caKeyPair, caKeyPair, validFrom, validTo, caName, caName)

    fun generateIntermediateCACertificate(
        parentCAKeyPair: KeyPair,
        intermediateCAKeyPair: KeyPair,
        validFrom: Date,
        validTo: Date,
        parentCAName: X500Name,
        intermediateCAName: X500Name,
    ) = generateCASignedCertificate(
        parentCAKeyPair,
        intermediateCAKeyPair,
        validFrom,
        validTo,
        parentCAName,
        intermediateCAName
    )

    fun generateSubjectCertificate(
        caKeyPair: KeyPair,
        subjectKeyPair: KeyPair,
        validFrom: Date,
        validTo: Date,
        caName: X500Name,
        subjectName: X500Name,
    ) = generateCASignedCertificate(caKeyPair, subjectKeyPair, validFrom, validTo, caName, subjectName, false)

    fun generateCASignedCertificate(
        caKeyPair: KeyPair,
        clientKeyPair: KeyPair,
        validFrom: Date,
        validTo: Date,
        issuer: X500Name,
        subject: X500Name,
        isSubjectCA: Boolean = true,
    ): X509Certificate {
        val certBuilder = JcaX509v3CertificateBuilder(
            issuer,
            BigInteger.valueOf(System.currentTimeMillis()),
            validFrom,
            validTo,
            subject,
            clientKeyPair.public
        )
        if (isSubjectCA) certBuilder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        val signerBuilder = JcaContentSignerBuilder("SHA256WithRSAEncryption").build(caKeyPair.private)
        val certificateHolder = certBuilder.build(signerBuilder)
        val certificate = JcaX509CertificateConverter().getCertificate(certificateHolder)
        return certificate
    }

    fun javaPublicKeyToJWKKey(javaPublicKey: PublicKey) = runBlocking {
        val keyPemString = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getEncoder().encodeToString(javaPublicKey.encoded) +
                "\n-----END PUBLIC KEY-----\n"
        JWKKey.importPEM(keyPemString).getOrThrow()
    }

    fun javaPrivateKeyToJWKKey(javaPrivateKey: PrivateKey) = runBlocking {
        val keyPemString = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getEncoder().encodeToString(javaPrivateKey.encoded) +
                "\n-----END PRIVATE KEY-----\n"
        JWKKey.importPEM(keyPemString).getOrThrow()
    }

    fun exportX509CertificateToPEM(certificate: X509Certificate) = runBlocking {
        "-----BEGIN CERTIFICATE-----\n" +
                Base64.getEncoder().encodeToString(certificate.encoded) +
                "\n-----END CERTIFICATE-----\n"
    }
}