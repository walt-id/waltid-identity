@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.builder

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.parsePEMEncodedJcaPublicKey
import id.walt.x509.CertificateDer
import id.walt.x509.JcaX509CertificateHandle
import id.walt.x509.KeyContentSignerWrapper
import id.walt.x509.X509KeyUsage
import id.walt.x509.iso.iaca.certificate.toJcaX500Name
import id.walt.x509.X509ValidityPeriod
import id.walt.x509.criticalX509V3ExtensionOIDs
import id.walt.x509.iso.generateIsoCompliantX509CertificateSerialNo
import id.walt.x509.iso.iaca.certificate.IACACertificateBundle
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import id.walt.x509.iso.issuerAlternativeNameToGeneralNameArray
import id.walt.x509.nonCriticalX509V3ExtensionOIDs
import id.walt.x509.x509BasicConstraints
import okio.ByteString.Companion.toByteString
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import java.math.BigInteger
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


internal actual suspend fun platformSignIACACertificate(
    profileData: IACACertificateProfileData,
    signingKey: Key,
): IACACertificateBundle {

    val javaPublicKey = parsePEMEncodedJcaPublicKey(signingKey.getPublicKey().exportPEM())

    val issuer = profileData.principalName.toJcaX500Name()

    val altNames = issuerAlternativeNameToGeneralNameArray(profileData.issuerAlternativeName)

    val serialNo = generateIsoCompliantX509CertificateSerialNo()

    val certNotBeforeDate =
        Date(Instant.fromEpochSeconds(profileData.validityPeriod.notBefore.epochSeconds).toEpochMilliseconds())
    val certNotAfterDate =
        Date(Instant.fromEpochSeconds(profileData.validityPeriod.notAfter.epochSeconds).toEpochMilliseconds())

    val certBuilder = JcaX509v3CertificateBuilder(
        issuer,
        BigInteger(serialNo.toByteArray()),
        certNotBeforeDate,
        certNotAfterDate,
        issuer,
        javaPublicKey,
    )

    // Extensions
    val extUtils = JcaX509ExtensionUtils()

    val skiExt = extUtils.createSubjectKeyIdentifier(javaPublicKey)
    certBuilder.addExtension(
        Extension.subjectKeyIdentifier,
        false,
        skiExt,
    )

    // Basic constraints: CA=true, pathLen=0
    certBuilder.addExtension(
        Extension.basicConstraints,
        true,
        BasicConstraints(0)
    )

    // Issuer alternative names extension
    certBuilder.addExtension(
        Extension.issuerAlternativeName,
        false,
        GeneralNames(altNames),
    )

    // Key Usage: keyCertSign and cRLSign
    certBuilder.addExtension(
        Extension.keyUsage,
        true,
        KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign)
    )

    // CRL Distribution point extension
    profileData.crlDistributionPointUri?.let {
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
                                    profileData.crlDistributionPointUri
                                )
                            )
                        ),
                        null,
                        null,
                    )
                )
            )
        )
    }

    val keySignerBuilder = KeyContentSignerWrapper(
        key = signingKey,
    )

    val certificateHolder = certBuilder.build(keySignerBuilder)
    val certificate = JcaX509CertificateConverter().getCertificate(certificateHolder)
    val certificateDer = CertificateDer(
        bytes = certificate.encoded.toByteString(),
    )

    return IACACertificateBundle(
        certificateDer = certificateDer,
        decodedCertificate = IACADecodedCertificate(
            principalName = profileData.principalName,
            validityPeriod = X509ValidityPeriod(
                notBefore = Instant.fromEpochSeconds(certNotBeforeDate.toInstant().epochSecond),
                notAfter = Instant.fromEpochSeconds(certNotAfterDate.toInstant().epochSecond),
            ),
            issuerAlternativeName = profileData.issuerAlternativeName,
            serialNumber = serialNo.toByteArray().toByteString(),
            basicConstraints = certificate.x509BasicConstraints,
            keyUsage = setOf(
                X509KeyUsage.KeyCertSign,
                X509KeyUsage.CRLSign,
            ),
            skiHex = skiExt.keyIdentifier.toHexString(),
            crlDistributionPointUri = profileData.crlDistributionPointUri,
            publicKey = JWKKey.importFromDerCertificate(certificate.encoded).getOrThrow(),
            criticalExtensionOIDs = certificate.criticalX509V3ExtensionOIDs,
            nonCriticalExtensionOIDs = certificate.nonCriticalX509V3ExtensionOIDs,
            certificate = JcaX509CertificateHandle(certificate),
        )
    )
}
