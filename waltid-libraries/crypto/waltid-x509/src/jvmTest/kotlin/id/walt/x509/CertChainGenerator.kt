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

object CertChainGenerator {
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
        notBefore: Date = Date(System.currentTimeMillis() - 60_000),
        notAfter: Date = Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000)
    ): Chain {
        val rootKey = genKey()
        val interKey = genKey()
        val leafKey = genKey()

        val rootCert = selfSigned("CN=Test Root CA", rootKey, notBefore, notAfter, true)
        val interCert = signed(
            issuerCert = rootCert,
            issuerKey = rootKey.private,
            subjectDn = "CN=Test Intermediate CA",
            subjectKey = interKey.public,
            notBefore = notBefore,
            notAfter = notAfter,
            isCa = true
        )
        val leafCert = signed(
            issuerCert = interCert,
            issuerKey = interKey.private,
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

    private fun selfSigned(
        dn: String,
        key: KeyPair,
        notBefore: Date,
        notAfter: Date,
        isCa: Boolean
    ): X509Certificate {
        val subject = X500Name(dn)

        val builder = JcaX509v3CertificateBuilder(
            /* issuer  */ subject,
            /* serial  */ BigInteger(160, SecureRandom()),
            /* notBefore */ notBefore,
            /* notAfter  */ notAfter,
            /* subject */ subject,
            /* pubKey  */ key.public
        )

        val ext = JcaX509ExtensionUtils()
        builder.addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(key.public))
        builder.addExtension(Extension.authorityKeyIdentifier, false, ext.createAuthorityKeyIdentifier(key.public))

        if (isCa) {
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
            builder.addExtension(
                Extension.keyUsage, true,
                KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign)
            )
        } else {
            builder.addExtension(
                Extension.keyUsage, true,
                KeyUsage(KeyUsage.digitalSignature)
            )
        }

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(key.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }


    private fun signed(
        issuerCert: X509Certificate,
        issuerKey: PrivateKey,
        subjectDn: String,
        subjectKey: PublicKey,
        notBefore: Date,
        notAfter: Date,
        isCa: Boolean
    ): X509Certificate {
        val builder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            issuerCert,
            BigInteger.valueOf(System.nanoTime()),
            notBefore,
            notAfter,
            X500Principal(subjectDn),
            subjectKey
        )
        if (isCa) {
            builder.addExtension(
                org.bouncycastle.asn1.x509.Extension.basicConstraints, true,
                org.bouncycastle.asn1.x509.BasicConstraints(true)
            )
            builder.addExtension(
                org.bouncycastle.asn1.x509.Extension.keyUsage, true,
                org.bouncycastle.asn1.x509.KeyUsage(
                    org.bouncycastle.asn1.x509.KeyUsage.keyCertSign or org.bouncycastle.asn1.x509.KeyUsage.cRLSign
                )
            )
        } else {
            builder.addExtension(
                org.bouncycastle.asn1.x509.Extension.keyUsage, true,
                org.bouncycastle.asn1.x509.KeyUsage(org.bouncycastle.asn1.x509.KeyUsage.digitalSignature)
            )
        }
        val signer: ContentSigner = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(issuerKey)
        return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
    }

    fun der(cert: X509Certificate) = CertificateDer(cert.encoded)
    fun b64(cert: X509Certificate) = Base64.getEncoder().encodeToString(cert.encoded)
}
