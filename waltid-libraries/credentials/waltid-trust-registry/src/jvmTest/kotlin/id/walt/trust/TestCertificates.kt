package id.walt.trust

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import java.util.Date

internal object TestCertificates {

    data class Chain(
        val rootKeyPair: KeyPair,
        val root: X509Certificate,
        val leafKeyPair: KeyPair,
        val leaf: X509Certificate
    )

    data class RsaPssSigner(
        val keyPair: KeyPair,
        val certificate: X509Certificate
    )

    /**
     * Self-signed certificate whose SubjectPublicKeyInfo uses the explicit id-RSASSA-PSS OID
     * (rather than the generic rsaEncryption OID), as issued by CAs that restrict a key to
     * RSASSA-PSS use only. Java reports [java.security.PublicKey.getAlgorithm] for such a key as
     * "RSASSA-PSS", not "RSA". Some EU national trusted lists (e.g. Germany's) are signed with
     * exactly this kind of key using xmldsig-more#sha256-rsa-MGF1.
     */
    fun createRsaPssSelfSigned(commonName: String = "RSASSA-PSS Trust List Signer"): RsaPssSigner {
        val keyPair = KeyPairGenerator.getInstance("RSASSA-PSS").apply {
            initialize(2048, SecureRandom())
        }.generateKeyPair()

        val subject = X500Name("CN=$commonName,O=walt.id,C=DE")
        val extensions = JcaX509ExtensionUtils()
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(128, SecureRandom()),
            Date.from(Instant.now().minusSeconds(60)),
            Date.from(Instant.now().plusSeconds(20L * 365 * 86_400)),
            subject,
            keyPair.public
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        builder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            extensions.createSubjectKeyIdentifier(keyPair.public)
        )
        val signer = JcaContentSignerBuilder("SHA256withRSAandMGF1")
            .setProvider(BouncyCastleProvider())
            .build(keyPair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        return RsaPssSigner(keyPair, certificate)
    }

    fun createChain(commonName: String = "WAL-1186"): Chain {
        val rootKeyPair = keyPair()
        val rootName = X500Name("CN=$commonName Root,O=walt.id,C=AT")
        val root = certificate(
            subject = rootName,
            subjectKeyPair = rootKeyPair,
            issuer = rootName,
            issuerPrivateKey = rootKeyPair.private,
            issuerCertificate = null,
            isCa = true
        )

        val leafKeyPair = keyPair()
        val leaf = certificate(
            subject = X500Name("CN=$commonName Leaf,O=walt.id,C=AT"),
            subjectKeyPair = leafKeyPair,
            issuer = rootName,
            issuerPrivateKey = rootKeyPair.private,
            issuerCertificate = root,
            isCa = false
        )
        return Chain(rootKeyPair, root, leafKeyPair, leaf)
    }

    fun derBase64(certificate: X509Certificate): String =
        Base64.getEncoder().encodeToString(certificate.encoded)

    fun pem(certificate: X509Certificate): String = buildString {
        appendLine("-----BEGIN CERTIFICATE-----")
        derBase64(certificate).chunked(64).forEach(::appendLine)
        appendLine("-----END CERTIFICATE-----")
    }

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(256, SecureRandom())
    }.generateKeyPair()

    private fun certificate(
        subject: X500Name,
        subjectKeyPair: KeyPair,
        issuer: X500Name,
        issuerPrivateKey: PrivateKey,
        issuerCertificate: X509Certificate?,
        isCa: Boolean
    ): X509Certificate {
        val extensions = JcaX509ExtensionUtils()
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            BigInteger(128, SecureRandom()),
            Date.from(Instant.now().minusSeconds(60)),
            Date.from(Instant.now().plusSeconds(86_400)),
            subject,
            subjectKeyPair.public
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(isCa))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(if (isCa) KeyUsage.keyCertSign or KeyUsage.cRLSign else KeyUsage.digitalSignature)
        )
        builder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            extensions.createSubjectKeyIdentifier(subjectKeyPair.public)
        )
        builder.addExtension(
            Extension.authorityKeyIdentifier,
            false,
            issuerCertificate?.let(extensions::createAuthorityKeyIdentifier)
                ?: extensions.createAuthorityKeyIdentifier(subjectKeyPair.public)
        )
        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(issuerPrivateKey)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }
}
