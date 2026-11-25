package id.walt.openid4vp.conformance.fixtures

import com.nimbusds.jose.jwk.ECKey
import id.walt.openid4vp.conformance.testplans.keys.TestKeyMaterial
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.CRLNumber
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.DistributionPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.cert.X509v2CRLBuilder
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

/** Per-run credential signer and fresh, signed CRL; only committed public test keys are used. */
internal class IssuerTestPki(
    crlUrl: String,
    signingJwk: String = TestKeyMaterial.CREDENTIAL_ISSUER_KEY_WITH_X5C,
    countryCode: String? = null,
    documentSigner: Boolean = true,
) {
    val trustAnchorPem: String
    val signerCertificate: ByteArray
    val crl: ByteArray

    init {
        val originalRoot = CertificateFactory.getInstance("X.509").generateCertificate(
            TestKeyMaterial.CREDENTIAL_ISSUER_CA_PEM.byteInputStream(),
        ) as X509Certificate
        val privateKey = PEMParser(checkNotNull(javaClass.getResourceAsStream("/certs/root-ca.key")).reader()).use { parser ->
            val converter = JcaPEMKeyConverter()
            when (val pem = parser.readObject()) {
                is PEMKeyPair -> converter.getKeyPair(pem).private
                is PrivateKeyInfo -> converter.getPrivateKey(pem)
                else -> error("Unsupported conformance test CA key")
            }
        }
        require(countryCode == null || countryCode.matches(Regex("[A-Z]{2}")))
        val rootName = countryCode?.let {
            X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.C, it)
                .addRDN(BCStyle.O, "walt.id").addRDN(BCStyle.CN, "Conformance mDL test IACA").build()
        } ?: X500Name.getInstance(originalRoot.subjectX500Principal.encoded)
        // Reuse only the public test CA key, adding the IACA issuer alternative name.
        val rootBuilder = JcaX509v3CertificateBuilder(
            rootName, originalRoot.serialNumber,
            originalRoot.notBefore, originalRoot.notAfter,
            rootName, originalRoot.publicKey,
        )
        (originalRoot.criticalExtensionOIDs + originalRoot.nonCriticalExtensionOIDs).forEach { oid ->
            rootBuilder.addExtension(ASN1ObjectIdentifier(oid), oid in originalRoot.criticalExtensionOIDs,
                JcaX509ExtensionUtils.parseExtensionValue(originalRoot.getExtensionValue(oid)))
        }
        rootBuilder.addExtension(Extension.issuerAlternativeName, false,
            GeneralNames(GeneralName(GeneralName.uniformResourceIdentifier, "https://walt.id")))
        val rootDer = rootBuilder.build(JcaContentSignerBuilder("SHA256withECDSA").build(privateKey)).encoded
        val root = CertificateFactory.getInstance("X.509").generateCertificate(rootDer.inputStream()) as X509Certificate
        trustAnchorPem = "-----BEGIN CERTIFICATE-----\n" +
            java.util.Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(rootDer) +
            "\n-----END CERTIFICATE-----"
        val issuer = X500Name.getInstance(root.subjectX500Principal.encoded)
        val country = issuer.getRDNs(BCStyle.C).single().first.value.toString()
        val subject = X500NameBuilder(BCStyle.INSTANCE)
            .addRDN(BCStyle.C, country)
            .addRDN(BCStyle.CN, if (documentSigner) "Conformance mDL document signer" else "Conformance SD-JWT credential signer")
            .build()
        val now = Instant.now()
        root.checkValidity(Date.from(now))
        val publicKey = ECKey.parse(signingJwk).toECPublicKey()
        val extensions = JcaX509ExtensionUtils()
        val authorityKeyId = extensions.createAuthorityKeyIdentifier(root)
        val builder = JcaX509v3CertificateBuilder(
            issuer, BigInteger(128, SecureRandom()).setBit(127),
            Date.from(now.minusSeconds(60)), Date.from(minOf(now.plus(400, ChronoUnit.DAYS),
                root.notAfter.toInstant().minusSeconds(60))), subject, publicKey,
        )
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(publicKey))
        builder.addExtension(Extension.authorityKeyIdentifier, false, authorityKeyId)
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        if (documentSigner) {
            builder.addExtension(Extension.extendedKeyUsage, true,
                ExtendedKeyUsage(KeyPurposeId.getInstance(ASN1ObjectIdentifier("1.0.18013.5.1.2"))))
        }
        builder.addExtension(Extension.issuerAlternativeName, false,
            GeneralNames(GeneralName(GeneralName.uniformResourceIdentifier, "https://walt.id")))
        builder.addExtension(Extension.cRLDistributionPoints, false, CRLDistPoint(arrayOf(
            DistributionPoint(DistributionPointName(GeneralNames(
                GeneralName(GeneralName.uniformResourceIdentifier, crlUrl))), null, null),
        )))
        // ISO 18013-5 Table B.3 permits critical keyUsage/EKU; a generic TLS leaf is unsuitable.
        signerCertificate = builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(privateKey)).encoded
        crl = X509v2CRLBuilder(issuer, Date.from(now.minusSeconds(60)))
            .setNextUpdate(Date.from(now.plus(7, ChronoUnit.DAYS)))
            .addExtension(Extension.authorityKeyIdentifier, false, authorityKeyId)
            .addExtension(Extension.cRLNumber, false, CRLNumber(BigInteger.ONE))
            .build(JcaContentSignerBuilder("SHA256withECDSA").build(privateKey)).encoded
    }
}
