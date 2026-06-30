package id.walt.x509

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.x509.iso.authorityKeyIdentifierExtension
import id.walt.x509.iso.basicConstraintsExtension
import id.walt.x509.iso.buildSignumIsoCertificateDer
import id.walt.x509.iso.crlDistributionPointExtension
import id.walt.x509.iso.extendedKeyUsageExtension
import id.walt.x509.iso.generateIsoCompliantX509CertificateSerialNo
import id.walt.x509.iso.keyUsageExtension
import id.walt.x509.iso.subjectAlternativeNamesExtension
import id.walt.x509.iso.subjectKeyIdentifierExtension
import id.walt.x509.iso.toSignumName
import id.walt.x509.iso.toSignumPublicKey
import kotlin.time.Instant

actual suspend fun platformBuildGenericX509Certificate(
    profileData: GenericX509CertificateProfileData,
    subjectPublicKey: Key,
    signingKey: Key,
): GenericX509CertificateBundle {
    val issuerName = profileData.issuerName ?: profileData.subjectName
    val subjectSignumPublicKey = subjectPublicKey.toSignumPublicKey()
    val issuerSignumPublicKey = signingKey.toSignumPublicKey()
    val certificateDer = buildSignumIsoCertificateDer(
        serialNumber = generateIsoCompliantX509CertificateSerialNo(),
        issuerName = issuerName.toSignumName(),
        subjectName = profileData.subjectName.toSignumName(),
        validityPeriod = profileData.validityPeriod,
        subjectPublicKey = subjectPublicKey,
        signingKey = signingKey,
        extensions = buildList {
            add(subjectKeyIdentifierExtension(subjectSignumPublicKey))
            add(authorityKeyIdentifierExtension(issuerSignumPublicKey))
            add(
                basicConstraintsExtension(
                    isCa = profileData.isCertificateAuthority,
                    pathLengthConstraint = profileData.pathLengthConstraint,
                )
            )
            profileData.keyUsage.takeIf { it.isNotEmpty() }?.let {
                add(keyUsageExtension(it))
            }
            profileData.extendedKeyUsageOids.takeIf { it.isNotEmpty() }?.let {
                add(extendedKeyUsageExtension(it, critical = false))
            }
            profileData.subjectAlternativeNames
                ?.takeUnless { it.isEmpty }
                ?.let { add(subjectAlternativeNamesExtension(it)) }
            profileData.crlDistributionPointUri?.let {
                add(crlDistributionPointExtension(it))
            }
        },
    )

    return GenericX509CertificateBundle(
        certificateDer = certificateDer,
        decodedCertificate = GenericX509DecodedCertificate(
            subjectName = profileData.subjectName,
            issuerName = issuerName,
            validityPeriod = profileData.validityPeriod.truncatedToSeconds(),
            isCertificateAuthority = profileData.isCertificateAuthority,
            pathLengthConstraint = profileData.pathLengthConstraint,
            keyUsage = profileData.keyUsage,
            extendedKeyUsageOids = profileData.extendedKeyUsageOids,
            subjectAlternativeNames = profileData.subjectAlternativeNames,
            crlDistributionPointUri = profileData.crlDistributionPointUri,
            publicKey = JWKKey.importFromDerCertificate(certificateDer.bytes.toByteArray()).getOrThrow().getPublicKey(),
        ),
    )
}

private fun X509ValidityPeriod.truncatedToSeconds(): X509ValidityPeriod =
    X509ValidityPeriod(
        notBefore = Instant.fromEpochSeconds(notBefore.epochSeconds),
        notAfter = Instant.fromEpochSeconds(notAfter.epochSeconds),
    )
