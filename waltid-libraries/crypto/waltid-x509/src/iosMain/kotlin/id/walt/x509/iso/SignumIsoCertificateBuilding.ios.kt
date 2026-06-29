package id.walt.x509.iso

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.SignatureAlgorithm
import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.Asn1Primitive
import at.asitplus.signum.indispensable.asn1.Asn1String
import at.asitplus.signum.indispensable.asn1.Asn1Time
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.pki.AttributeTypeAndValue
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName
import at.asitplus.signum.indispensable.pki.SubjectAltNameImplicitTags
import at.asitplus.signum.indispensable.pki.TbsCertificate
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import at.asitplus.signum.indispensable.toX509SignatureAlgorithm
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.ShaUtils
import id.walt.x509.CertificateDer
import id.walt.x509.X509KeyUsage
import id.walt.x509.X509ValidityPeriod
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import kotlinx.io.bytestring.ByteString

internal suspend fun buildSignumIsoCertificateDer(
    serialNumber: ByteString,
    issuerName: List<RelativeDistinguishedName>,
    subjectName: List<RelativeDistinguishedName>,
    validityPeriod: X509ValidityPeriod,
    subjectPublicKey: Key,
    signingKey: Key,
    extensions: List<X509CertificateExtension>,
): CertificateDer {
    val subjectCryptoPublicKey = subjectPublicKey.toSignumPublicKey()
    val signatureAlgorithm = signingKey.keyType.toSignumSignatureAlgorithm()
    val x509SignatureAlgorithm = signatureAlgorithm.toX509SignatureAlgorithm().getOrThrow()

    val tbsCertificate = TbsCertificate(
        serialNumber = serialNumber.toByteArray(),
        signatureAlgorithm = x509SignatureAlgorithm,
        issuerName = issuerName,
        validFrom = Asn1Time(validityPeriod.notBefore),
        validUntil = Asn1Time(validityPeriod.notAfter),
        subjectName = subjectName,
        publicKey = subjectCryptoPublicKey,
        extensions = extensions,
    )
    val signature = signingKey.signX509Raw(tbsCertificate)
    return CertificateDer(
        X509Certificate(
            tbsCertificate = tbsCertificate,
            signatureAlgorithm = x509SignatureAlgorithm,
            signature = signature,
        ).encodeToDer()
    )
}

internal suspend fun Key.toSignumPublicKey(): CryptoPublicKey =
    CryptoPublicKey.decodeFromDer(getPublicKeyRepresentation())

internal fun IACAPrincipalName.toSignumName(): List<RelativeDistinguishedName> =
    buildSignumName(
        country = country,
        commonName = commonName,
        stateOrProvinceName = stateOrProvinceName,
        organizationName = organizationName,
    )

internal fun DocumentSignerPrincipalName.toSignumName(): List<RelativeDistinguishedName> =
    buildSignumName(
        country = country,
        commonName = commonName,
        stateOrProvinceName = stateOrProvinceName,
        organizationName = organizationName,
        localityName = localityName,
    )

internal fun subjectKeyIdentifierExtension(publicKey: CryptoPublicKey): X509CertificateExtension =
    extension(
        oid = "2.5.29.14",
        critical = false,
        value = Asn1.OctetString(publicKey.subjectKeyIdentifier()),
    )

internal fun authorityKeyIdentifierExtension(publicKey: CryptoPublicKey): X509CertificateExtension =
    extension(
        oid = "2.5.29.35",
        critical = false,
        value = Asn1.Sequence {
            +(Asn1.OctetString(publicKey.subjectKeyIdentifier()) withImplicitTag 0uL)
        },
    )

internal fun basicConstraintsExtension(
    isCa: Boolean,
    pathLengthConstraint: Int? = null,
): X509CertificateExtension =
    extension(
        oid = "2.5.29.19",
        critical = true,
        value = Asn1.Sequence {
            if (isCa) +Asn1.Bool(true)
            pathLengthConstraint?.let { +Asn1.Int(it) }
        },
    )

internal fun keyUsageExtension(usages: Set<X509KeyUsage>): X509CertificateExtension =
    extension(
        oid = "2.5.29.15",
        critical = true,
        value = Asn1.BitString(usages.toKeyUsageBytes()),
    )

internal fun issuerAlternativeNameExtension(
    issuerAlternativeName: IssuerAlternativeName,
): X509CertificateExtension =
    extension(
        oid = "2.5.29.18",
        critical = false,
        value = Asn1.Sequence {
            issuerAlternativeName.email?.let {
                +Asn1Primitive(SubjectAltNameImplicitTags.rfc822Name, it.encodeToByteArray())
            }
            issuerAlternativeName.uri?.let {
                +Asn1Primitive(SubjectAltNameImplicitTags.uniformResourceIdentifier, it.encodeToByteArray())
            }
        },
    )

internal fun extendedKeyUsageExtension(oids: Set<String>): X509CertificateExtension =
    extension(
        oid = "2.5.29.37",
        critical = true,
        value = Asn1.Sequence {
            oids.forEach { +ObjectIdentifier(it) }
        },
    )

internal fun crlDistributionPointExtension(uri: String): X509CertificateExtension =
    extension(
        oid = "2.5.29.31",
        critical = false,
        value = Asn1.Sequence {
            +Asn1.Sequence {
                +Asn1.ExplicitlyTagged(0uL) {
                    +(Asn1.Sequence {
                        +Asn1Primitive(
                            SubjectAltNameImplicitTags.uniformResourceIdentifier,
                            uri.encodeToByteArray(),
                        )
                    } withImplicitTag 0uL)
                }
            }
        },
    )

private fun extension(
    oid: String,
    critical: Boolean,
    value: Asn1Element,
): X509CertificateExtension =
    X509CertificateExtension(
        oid = ObjectIdentifier(oid),
        critical = critical,
        value = Asn1.OctetStringEncapsulating {
            +value
        },
    )

private suspend fun Key.signX509Raw(
    tbsCertificate: TbsCertificate,
): CryptoSignature {
    val signatureBytes = signRaw(tbsCertificate.encodeToDer()) as? ByteArray
        ?: error("X.509 signing returned a non-ByteArray signature")
    return when (keyType) {
        KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1 ->
            CryptoSignature.EC.fromRawBytes(keyType.toSignumCurve(), signatureBytes)
        KeyType.RSA, KeyType.RSA3072, KeyType.RSA4096 ->
            CryptoSignature.RSA(signatureBytes)
        else -> error("Unsupported X.509 signing key type: $keyType")
    }
}

private fun KeyType.toSignumSignatureAlgorithm(): SignatureAlgorithm =
    when (this) {
        KeyType.secp256r1 -> SignatureAlgorithm.ECDSAwithSHA256
        KeyType.secp384r1 -> SignatureAlgorithm.ECDSAwithSHA384
        KeyType.secp521r1 -> SignatureAlgorithm.ECDSAwithSHA512
        KeyType.RSA -> SignatureAlgorithm.RSAwithSHA256andPSSPadding
        KeyType.RSA3072 -> SignatureAlgorithm.RSAwithSHA384andPSSPadding
        KeyType.RSA4096 -> SignatureAlgorithm.RSAwithSHA512andPSSPadding
        else -> error("Unsupported X.509 signing key type: $this")
    }

private fun KeyType.toSignumCurve(): ECCurve =
    when (this) {
        KeyType.secp256r1 -> ECCurve.SECP_256_R_1
        KeyType.secp384r1 -> ECCurve.SECP_384_R_1
        KeyType.secp521r1 -> ECCurve.SECP_521_R_1
        else -> error("Unsupported EC key type: $this")
    }

private fun buildSignumName(
    country: String? = null,
    commonName: String? = null,
    stateOrProvinceName: String? = null,
    organizationName: String? = null,
    localityName: String? = null,
): List<RelativeDistinguishedName> = buildList {
    country?.let {
        add(RelativeDistinguishedName(AttributeTypeAndValue.Country(Asn1String.Printable(it))))
    }
    commonName?.let {
        add(RelativeDistinguishedName(AttributeTypeAndValue.CommonName(Asn1String.UTF8(it))))
    }
    stateOrProvinceName?.let {
        add(RelativeDistinguishedName(AttributeTypeAndValue.Other(ObjectIdentifier("2.5.4.8"), Asn1String.UTF8(it))))
    }
    organizationName?.let {
        add(RelativeDistinguishedName(AttributeTypeAndValue.Organization(Asn1String.UTF8(it))))
    }
    localityName?.let {
        add(RelativeDistinguishedName(AttributeTypeAndValue.Other(ObjectIdentifier("2.5.4.7"), Asn1String.UTF8(it))))
    }
}

private fun CryptoPublicKey.subjectKeyIdentifier(): ByteArray =
    ShaUtils.sha1(iosEncoded)

private fun Set<X509KeyUsage>.toKeyUsageBytes(): ByteArray {
    val bitIndexes = map { it.keyUsageBitIndex() }
    val bytes = ByteArray(bitIndexes.maxOrNull()?.div(8)?.plus(1) ?: 1)
    bitIndexes.forEach { bitIndex ->
        val byteIndex = bitIndex / 8
        bytes[byteIndex] = (bytes[byteIndex].toInt() or (0x80 ushr (bitIndex % 8))).toByte()
    }
    return bytes
}

private fun X509KeyUsage.keyUsageBitIndex(): Int =
    when (this) {
        X509KeyUsage.DigitalSignature -> 0
        X509KeyUsage.NonRepudiation -> 1
        X509KeyUsage.KeyEncipherment -> 2
        X509KeyUsage.DataEncipherment -> 3
        X509KeyUsage.KeyAgreement -> 4
        X509KeyUsage.KeyCertSign -> 5
        X509KeyUsage.CRLSign -> 6
        X509KeyUsage.EncipherOnly -> 7
        X509KeyUsage.DecipherOnly -> 8
    }
