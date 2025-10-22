package id.walt.x509

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.util.*
import javax.security.auth.x500.X500Principal

private const val oneYearInMillis = 365L * 24 * 3600 * 1000

object TestCA {
    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    data class Chain(
        val rootKey: KeyPair,
        val rootCert: X509Certificate,
        val interKey: KeyPair,
        val interCert: X509Certificate,
        val leafKey: KeyPair,
        val leafCert: X509Certificate
    )

    fun generateChain(
        notBefore: Date = Date(System.currentTimeMillis() - 1000),
        notAfter: Date = Date(System.currentTimeMillis() + oneYearInMillis)
    ): Chain {
        val rootKey = genKey()
        val interKey = genKey()
        val leafKey = genKey()

        val rootCert = selfSignedCertificate(
            issuerKey = rootKey,
            subjectDn = "CN=Test Root CA",
            subjectKey = rootKey.public,
            notBefore = notBefore, notAfter = notAfter,
            isCa = true
        )

        val interCert = signedCertificate(
            issuerCert = rootCert,
            issuerKey = rootKey,
            subjectDn = "CN=Test Intermediate CA",
            subjectKey = interKey.public,
            notBefore = notBefore,
            notAfter = notAfter,
            isCa = true
        )

        val leafCert = signedCertificate(
            issuerCert = interCert,
            issuerKey = interKey,
            subjectDn = "CN=Leaf",
            subjectKey = leafKey.public,
            notBefore = notBefore,
            notAfter = notAfter,
            isCa = false
        )

        return Chain(rootKey, rootCert, interKey, interCert, leafKey, leafCert)
    }

    fun genKey(): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()

    private fun selfSignedCertificate(
        issuerKey: KeyPair,
        subjectDn: String,
        subjectKey: PublicKey,
        notBefore: Date,
        notAfter: Date,
        isCa: Boolean
    ): X509Certificate {

        val subject = X500Name(subjectDn)

        val builder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            X500Name(subjectDn), // subjectDN == issuerDN
            generateSerial(),
            notBefore,
            notAfter,
            subject,
            issuerKey.public
        )

        addExtension(isCa, subjectKey, issuerKey.public, builder)

        return sign(issuerKey.private, builder)
    }


    private fun signedCertificate(
        issuerCert: X509Certificate,
        issuerKey: KeyPair,
        subjectDn: String,
        subjectKey: PublicKey,
        notBefore: Date,
        notAfter: Date,
        isCa: Boolean
    ): X509Certificate {
        val builder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            issuerCert,
            generateSerial(),
            notBefore,
            notAfter,
            X500Principal(subjectDn),
            subjectKey
        )

        addExtension(isCa, subjectKey, issuerKey.public, builder)

        return sign(issuerKey.private, builder)
    }

    private fun addExtension(
        isCa: Boolean,
        subjectKey: PublicKey,
        issuerPublicKey: PublicKey,
        builder: X509v3CertificateBuilder
    ) {

        val ext = JcaX509ExtensionUtils()
        builder.addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(subjectKey))

        if (isCa) {

            builder.addExtension(
                Extension.authorityKeyIdentifier,
                false,
                ext.createAuthorityKeyIdentifier(issuerPublicKey)
            )

            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
            builder.addExtension(
                Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign)
            )
        } else {
            builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        }
    }

    private fun generateSerial(): BigInteger = BigInteger(160, SecureRandom())

    private fun sign(
        issuerKey: PrivateKey,
        builder: X509v3CertificateBuilder
    ): X509Certificate {
        val signer: ContentSigner = JcaContentSignerBuilder("SHA256withRSA").build(issuerKey)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

}
