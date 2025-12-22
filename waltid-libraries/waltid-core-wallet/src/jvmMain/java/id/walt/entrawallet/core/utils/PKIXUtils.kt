package id.walt.webwallet.utils

import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.StringReader
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
    ) = generateCASignedCertificate(caKeyPair.private, caKeyPair.public, validFrom, validTo, caName, caName)

    fun generateIntermediateCACertificate(
        parentCAPrivateKey: PrivateKey,
        intermediateCAPrivateKey: PublicKey,
        validFrom: Date,
        validTo: Date,
        parentCAName: X500Name,
        intermediateCAName: X500Name,
    ) = generateCASignedCertificate(
        parentCAPrivateKey,
        intermediateCAPrivateKey,
        validFrom,
        validTo,
        parentCAName,
        intermediateCAName
    )

    fun generateSubjectCertificate(
        caPrivateKey: PrivateKey,
        subjectPublicKey: PublicKey,
        validFrom: Date,
        validTo: Date,
        caName: X500Name,
        subjectName: X500Name,
    ) = generateCASignedCertificate(caPrivateKey, subjectPublicKey, validFrom, validTo, caName, subjectName, false)

    fun generateCASignedCertificate(
        caPrivateKey: PrivateKey,
        clientPublicKey: PublicKey,
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
            clientPublicKey,
        )
        if (isSubjectCA) certBuilder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        val signerBuilder = JcaContentSignerBuilder("SHA256WithRSAEncryption").build(caPrivateKey)
        val certificateHolder = certBuilder.build(signerBuilder)
        val certificate = JcaX509CertificateConverter().getCertificate(certificateHolder)
        return certificate
    }

    fun javaPublicKeyToJWKKey(javaPublicKey: PublicKey) = runBlocking {
        val keyPemString = pemEncodeJavaPublicKey(javaPublicKey)
        JWKKey.importPEM(keyPemString).getOrThrow()
    }

    fun javaPrivateKeyToJWKKey(javaPrivateKey: PrivateKey) = runBlocking {
        val keyPemString = pemEncodeJavaPrivateKey(javaPrivateKey)
        JWKKey.importPEM(keyPemString).getOrThrow()
    }

    fun exportX509CertificateToPEM(certificate: X509Certificate) = runBlocking {
        "-----BEGIN CERTIFICATE-----\n" +
                Base64.getEncoder().encodeToString(certificate.encoded) +
                "\n-----END CERTIFICATE-----\n"
    }

    fun pemEncodeJavaPrivateKey(javaPrivateKey: PrivateKey) = runBlocking {
        "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getEncoder().encodeToString(javaPrivateKey.encoded) +
                "\n-----END PRIVATE KEY-----\n"
    }

    fun pemEncodeJavaPublicKey(javaPublicKey: PublicKey) = runBlocking {
        "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getEncoder().encodeToString(javaPublicKey.encoded) +
                "\n-----END PUBLIC KEY-----\n"
    }

    fun pemDecodeJavaPrivateKey(pemEncodedPrivateKey: String) = runBlocking {
        val pemKey = PEMParser(StringReader(pemEncodedPrivateKey)).readObject()
        val privateKeyInfo = PrivateKeyInfo.getInstance(pemKey)
        JcaPEMKeyConverter().getPrivateKey(privateKeyInfo)
    }

    fun convertToPemFormat(text: String): String = let {
        text.replace(System.lineSeparator(), "")
    }.chunked(64).joinToString(System.lineSeparator()).let {
        "-----BEGIN CERTIFICATE-----" +
                System.lineSeparator() +
                it +
                System.lineSeparator() +
                "-----END CERTIFICATE-----"
    }
}
