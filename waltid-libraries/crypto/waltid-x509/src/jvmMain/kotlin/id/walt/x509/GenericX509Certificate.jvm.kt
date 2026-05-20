package id.walt.x509

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.parsePEMEncodedJcaPublicKey
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Date
import kotlin.time.Instant

actual suspend fun platformBuildGenericX509Certificate(
    profileData: GenericX509CertificateProfileData,
    subjectPublicKey: Key,
    signingKey: Key,
): GenericX509CertificateBundle {
    val subjectJavaPublicKey = parsePEMEncodedJcaPublicKey(subjectPublicKey.exportPEM())
    val issuerPublicKey = parsePEMEncodedJcaPublicKey(signingKey.getPublicKey().exportPEM())
    val issuerName = profileData.issuerName ?: profileData.subjectName
    val certNotBeforeDate =
        Date(Instant.fromEpochSeconds(profileData.validityPeriod.notBefore.epochSeconds).toEpochMilliseconds())
    val certNotAfterDate =
        Date(Instant.fromEpochSeconds(profileData.validityPeriod.notAfter.epochSeconds).toEpochMilliseconds())

    val certBuilder = JcaX509v3CertificateBuilder(
        issuerName.toX500Name(),
        generateSerialNumber(),
        certNotBeforeDate,
        certNotAfterDate,
        profileData.subjectName.toX500Name(),
        subjectJavaPublicKey,
    )

    val extUtils = JcaX509ExtensionUtils()
    certBuilder.addExtension(
        Extension.subjectKeyIdentifier,
        false,
        extUtils.createSubjectKeyIdentifier(subjectJavaPublicKey),
    )
    certBuilder.addExtension(
        Extension.authorityKeyIdentifier,
        false,
        extUtils.createAuthorityKeyIdentifier(issuerPublicKey),
    )

    certBuilder.addExtension(
        Extension.basicConstraints,
        true,
        if (profileData.isCertificateAuthority) {
            profileData.pathLengthConstraint?.let { BasicConstraints(it) } ?: BasicConstraints(true)
        } else {
            BasicConstraints(false)
        },
    )

    profileData.keyUsage.takeIf { it.isNotEmpty() }?.let { usages ->
        certBuilder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(usages.toBouncyCastleKeyUsageBits()),
        )
    }

    profileData.extendedKeyUsageOids.takeIf { it.isNotEmpty() }?.let { ekuOids ->
        certBuilder.addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(ekuOids.map { KeyPurposeId.getInstance(ASN1ObjectIdentifier(it)) }.toTypedArray()),
        )
    }

    profileData.subjectAlternativeNames
        ?.takeUnless { it.isEmpty }
        ?.let { san ->
            certBuilder.addExtension(
                Extension.subjectAlternativeName,
                false,
                san.toGeneralNames(),
            )
        }

    profileData.crlDistributionPointUri?.let { crlDistributionPointUri ->
        certBuilder.addExtension(
            Extension.cRLDistributionPoints,
            false,
            CRLDistPoint(
                arrayOf(
                    DistributionPoint(
                        DistributionPointName(
                            GeneralNames(
                                GeneralName(
                                    GeneralName.uniformResourceIdentifier,
                                    crlDistributionPointUri,
                                )
                            )
                        ),
                        null,
                        null,
                    )
                )
            ),
        )
    }

    val certificate = JcaX509CertificateConverter()
        .getCertificate(certBuilder.build(KeyContentSignerWrapper(signingKey)))

    return GenericX509CertificateBundle(
        certificateDer = CertificateDer(ByteString(certificate.encoded)),
        decodedCertificate = GenericX509DecodedCertificate(
            subjectName = profileData.subjectName,
            issuerName = issuerName,
            validityPeriod = X509ValidityPeriod(
                notBefore = Instant.fromEpochSeconds(certNotBeforeDate.toInstant().epochSecond),
                notAfter = Instant.fromEpochSeconds(certNotAfterDate.toInstant().epochSecond),
            ),
            isCertificateAuthority = profileData.isCertificateAuthority,
            pathLengthConstraint = profileData.pathLengthConstraint,
            keyUsage = profileData.keyUsage,
            extendedKeyUsageOids = profileData.extendedKeyUsageOids,
            subjectAlternativeNames = profileData.subjectAlternativeNames,
            crlDistributionPointUri = profileData.crlDistributionPointUri,
            publicKey = JWKKey.importFromDerCertificate(certificate.encoded).getOrThrow().getPublicKey(),
        )
    )
}

private fun Set<X509KeyUsage>.toBouncyCastleKeyUsageBits(): Int =
    fold(0) { bits, usage ->
        bits or when (usage) {
            X509KeyUsage.DigitalSignature -> KeyUsage.digitalSignature
            X509KeyUsage.NonRepudiation -> KeyUsage.nonRepudiation
            X509KeyUsage.KeyEncipherment -> KeyUsage.keyEncipherment
            X509KeyUsage.DataEncipherment -> KeyUsage.dataEncipherment
            X509KeyUsage.KeyAgreement -> KeyUsage.keyAgreement
            X509KeyUsage.KeyCertSign -> KeyUsage.keyCertSign
            X509KeyUsage.CRLSign -> KeyUsage.cRLSign
            X509KeyUsage.EncipherOnly -> KeyUsage.encipherOnly
            X509KeyUsage.DecipherOnly -> KeyUsage.decipherOnly
        }
    }

private fun generateSerialNumber(): BigInteger = BigInteger(160, SecureRandom()).abs()
