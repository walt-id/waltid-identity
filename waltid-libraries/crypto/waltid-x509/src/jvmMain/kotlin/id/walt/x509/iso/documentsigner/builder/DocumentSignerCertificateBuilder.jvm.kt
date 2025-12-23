@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.parsePEMEncodedJcaPublicKey
import id.walt.x509.CertificateDer
import id.walt.x509.CertificateKeyUsage
import id.walt.x509.id.walt.x509.KeyContentSignerWrapper
import id.walt.x509.id.walt.x509.buildX500Name
import id.walt.x509.id.walt.x509.issuerAlternativeNameToGeneralNameArray
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.DocumentSignerEkuOid
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateBundle
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import id.walt.x509.iso.generateCertificateSerialNo
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
    principalName: DocumentSignerPrincipalName,
    validityPeriod: CertificateValidityPeriod,
    crlDistributionPointUri: String,
    dsPublicKey: Key,
    iacaSignerSpec: IACASignerSpecification,
): DocumentSignerCertificateBundle {

    val subjectJavaPublicKey = parsePEMEncodedJcaPublicKey(dsPublicKey.exportPEM())

    val iacaName = buildX500Name(
        country = iacaSignerSpec.data.principalName.country,
        commonName = iacaSignerSpec.data.principalName.commonName,
        stateOrProvinceName = iacaSignerSpec.data.principalName.stateOrProvinceName,
        organizationName = iacaSignerSpec.data.principalName.organizationName,
    )

    val dsName = buildX500Name(
        country = principalName.country,
        commonName = principalName.commonName,
        stateOrProvinceName = principalName.stateOrProvinceName,
        organizationName = principalName.organizationName,
        localityName = principalName.localityName,
    )

    val serialNo = generateCertificateSerialNo()

    val altNames = issuerAlternativeNameToGeneralNameArray(iacaSignerSpec.data.issuerAlternativeName)

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

    certBuilder.addExtension(
        Extension.authorityKeyIdentifier,
        false,
        extUtils.createAuthorityKeyIdentifier(
            parsePEMEncodedJcaPublicKey(iacaSignerSpec.signingKey.getPublicKey().exportPEM())
        )
    )

    certBuilder.addExtension(
        Extension.subjectKeyIdentifier,
        false,
        extUtils.createSubjectKeyIdentifier(subjectJavaPublicKey)
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
        ExtendedKeyUsage(KeyPurposeId.getInstance(ASN1ObjectIdentifier(DocumentSignerEkuOid))),
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
                                crlDistributionPointUri,
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
    return DocumentSignerCertificateBundle(
        certificateDer = CertificateDer(
            bytes = certificate.encoded,
        ),
        data = DocumentSignerDecodedCertificate(
            principalName = principalName,
            validityPeriod = CertificateValidityPeriod(
                notBefore = Instant.fromEpochSeconds(certNotBeforeDate.toInstant().epochSecond),
                notAfter = Instant.fromEpochSeconds(certNotAfterDate.toInstant().epochSecond),
            ),
            crlDistributionPointUri = crlDistributionPointUri,
            serialNumber = serialNo,
            keyUsage = setOf(
                CertificateKeyUsage.DigitalSignature
            ),
            isCA = false,
        ),
    )
}
