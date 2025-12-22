@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.builder

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.parsePEMEncodedJcaPublicKey
import id.walt.x509.CertificateDer
import id.walt.x509.CertificateKeyUsage
import id.walt.x509.id.walt.x509.KeyContentSignerWrapper
import id.walt.x509.id.walt.x509.buildX500Name
import id.walt.x509.id.walt.x509.issuerAlternativeNameToGeneralNameArray
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.generateCertificateSerialNo
import id.walt.x509.iso.iaca.certificate.IACACertificateBundle
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
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
    country: String,
    commonName: String,
    validityPeriod: CertificateValidityPeriod,
    issuerAlternativeName: IssuerAlternativeName,
    signingKey: Key,
    stateOrProvinceName: String?,
    organizationName: String?,
    crlDistributionPointUri: String?
): IACACertificateBundle {
    val javaPublicKey = parsePEMEncodedJcaPublicKey(signingKey.getPublicKey().exportPEM())

    val issuer = buildX500Name(
        country = country,
        commonName = commonName,
        stateOrProvinceName = stateOrProvinceName,
        organizationName = organizationName,
    )

    val altNames = issuerAlternativeNameToGeneralNameArray(issuerAlternativeName)

    val serialNo = generateCertificateSerialNo()

    val certNotBeforeDate = Date(Instant.fromEpochSeconds(validityPeriod.notBefore.epochSeconds).toEpochMilliseconds())
    val certNotAfterDate = Date(Instant.fromEpochSeconds(validityPeriod.notAfter.epochSeconds).toEpochMilliseconds())

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

    certBuilder.addExtension(
        Extension.subjectKeyIdentifier,
        false,
        extUtils.createSubjectKeyIdentifier(javaPublicKey)
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
    crlDistributionPointUri?.let {
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
                                    crlDistributionPointUri
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



    return IACACertificateBundle(
        certificateDer = CertificateDer(
            bytes = certificate.encoded,
        ),
        decodedData = IACADecodedCertificate(
            country = country,
            commonName = commonName,
            validityPeriod = CertificateValidityPeriod(
                notBefore = Instant.fromEpochSeconds(certNotBeforeDate.toInstant().epochSecond),
                notAfter = Instant.fromEpochSeconds(certNotAfterDate.toInstant().epochSecond),
            ),
            issuerAlternativeName = issuerAlternativeName,
            serialNumber = serialNo.toByteArray().toByteString(),
            isCA = true,
            pathLengthConstraint = 0,
            keyUsage = setOf(
                CertificateKeyUsage.KeyCertSign,
                CertificateKeyUsage.CRLSign,
            ),
            stateOrProvinceName = stateOrProvinceName,
            organizationName = organizationName,
            crlDistributionPointUri = crlDistributionPointUri,
        )
    )
}
