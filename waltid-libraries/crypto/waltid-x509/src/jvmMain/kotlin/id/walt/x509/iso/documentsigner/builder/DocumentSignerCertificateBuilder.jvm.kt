@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.parsePEMEncodedJcaPublicKey
import id.walt.x509.CertificateDer
import id.walt.x509.JcaX509CertificateHandle
import id.walt.x509.KeyContentSignerWrapper
import id.walt.x509.X509KeyUsage
import id.walt.x509.iso.documentsigner.certificate.toJcaX500Name
import id.walt.x509.iso.iaca.certificate.toJcaX500Name
import id.walt.x509.X509ValidityPeriod
import id.walt.x509.criticalX509V3ExtensionOIDs
import id.walt.x509.iso.DocumentSignerEkuOID
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateBundle
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate
import id.walt.x509.iso.generateIsoCompliantX509CertificateSerialNo
import id.walt.x509.iso.issuerAlternativeNameToGeneralNameArray
import id.walt.x509.nonCriticalX509V3ExtensionOIDs
import id.walt.x509.x509BasicConstraints
import okio.ByteString.Companion.toByteString
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import java.math.BigInteger
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal actual suspend fun platformSignDocumentSignerCertificate(
    profileData: DocumentSignerCertificateProfileData,
    publicKey: Key,
    iacaSignerSpec: IACASignerSpecification,
): DocumentSignerCertificateBundle {

    val validityPeriod = profileData.validityPeriod

    val subjectJavaPublicKey = parsePEMEncodedJcaPublicKey(publicKey.exportPEM())

    val iacaName = iacaSignerSpec.profileData.principalName.toJcaX500Name()

    val dsName = profileData.principalName.toJcaX500Name()

    val serialNo = generateIsoCompliantX509CertificateSerialNo()

    val altNames = issuerAlternativeNameToGeneralNameArray(iacaSignerSpec.profileData.issuerAlternativeName)

    val certNotBeforeDate = Date(Instant.fromEpochSeconds(validityPeriod.notBefore.epochSeconds).toEpochMilliseconds())
    val certNotAfterDate = Date(Instant.fromEpochSeconds(validityPeriod.notAfter.epochSeconds).toEpochMilliseconds())

    val certBuilder = JcaX509v3CertificateBuilder(
        iacaName,
        BigInteger(serialNo.toByteArray()),
        certNotBeforeDate,
        certNotAfterDate,
        dsName,
        subjectJavaPublicKey,
    )

    val extUtils = JcaX509ExtensionUtils()

    val akiExt = extUtils.createAuthorityKeyIdentifier(
        parsePEMEncodedJcaPublicKey(iacaSignerSpec.signingKey.getPublicKey().exportPEM())
    )
    certBuilder.addExtension(
        Extension.authorityKeyIdentifier,
        false,
        akiExt,
    )

    val skiExt = extUtils.createSubjectKeyIdentifier(subjectJavaPublicKey)
    certBuilder.addExtension(
        Extension.subjectKeyIdentifier,
        false,
        skiExt,
    )

    // Key Usage: Digital Signature only
    certBuilder.addExtension(
        Extension.keyUsage,
        true,
        KeyUsage(KeyUsage.digitalSignature),
    )

    certBuilder.addExtension(
        Extension.issuerAlternativeName,
        false,
        GeneralNames(altNames),
    )

    // Extended key usage: mdlDS
    certBuilder.addExtension(
        Extension.extendedKeyUsage,
        true,
        ExtendedKeyUsage(KeyPurposeId.getInstance(ASN1ObjectIdentifier(DocumentSignerEkuOID))),
    )

    // CRL distribution points is mandatory
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
                                profileData.crlDistributionPointUri,
                            )
                        )
                    ),
                    null,
                    null,
                )
            )
        )
    )


    val keySignerBuilder = KeyContentSignerWrapper(
        key = iacaSignerSpec.signingKey,
    )

    val certificateHolder = certBuilder.build(keySignerBuilder)
    val certificate = JcaX509CertificateConverter().getCertificate(certificateHolder)
    val certificateDer = CertificateDer(
        bytes = certificate.encoded.toByteString(),
    )

    return DocumentSignerCertificateBundle(
        certificateDer = certificateDer,
        decodedCertificate = DocumentSignerDecodedCertificate(
            issuerPrincipalName = iacaSignerSpec.profileData.principalName,
            principalName = profileData.principalName,
            validityPeriod = X509ValidityPeriod(
                notBefore = Instant.fromEpochSeconds(certNotBeforeDate.toInstant().epochSecond),
                notAfter = Instant.fromEpochSeconds(certNotAfterDate.toInstant().epochSecond),
            ),
            issuerAlternativeName = iacaSignerSpec.profileData.issuerAlternativeName,
            crlDistributionPointUri = profileData.crlDistributionPointUri,
            serialNumber = serialNo,
            keyUsage = setOf(
                X509KeyUsage.DigitalSignature
            ),
            extendedKeyUsage = setOf(DocumentSignerEkuOID),
            akiHex = akiExt.keyIdentifierOctets.toHexString(),
            skiHex = skiExt.keyIdentifier.toHexString(),
            basicConstraints = certificate.x509BasicConstraints,
            publicKey = JWKKey.importFromDerCertificate(certificate.encoded).getOrThrow(),
            criticalExtensionOIDs = certificate.criticalX509V3ExtensionOIDs,
            nonCriticalExtensionOIDs = certificate.nonCriticalX509V3ExtensionOIDs,
            certificate = JcaX509CertificateHandle(certificate),
        ),
    )
}
