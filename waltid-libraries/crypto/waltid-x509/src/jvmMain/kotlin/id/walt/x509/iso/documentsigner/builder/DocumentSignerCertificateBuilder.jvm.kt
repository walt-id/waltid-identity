package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.parsePEMEncodedJcaPublicKey
import id.walt.x509.CertificateDer
import id.walt.x509.KeyContentSignerWrapper
import id.walt.x509.iso.DocumentSignerEkuOID
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateBundle
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.certificate.toJcaX500Name
import id.walt.x509.iso.documentsigner.parser.DocumentSignerCertificateParser
import id.walt.x509.iso.generateIsoCompliantX509CertificateSerialNo
import id.walt.x509.iso.iaca.certificate.toJcaX500Name
import id.walt.x509.iso.issuerAlternativeNameToGeneralNameArray
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import java.math.BigInteger
import java.util.Date
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
        bytes = ByteString(certificate.encoded),
    )

    return DocumentSignerCertificateBundle(
        certificateDer = certificateDer,
        decodedCertificate = DocumentSignerCertificateParser().parse(certificateDer),
    )
}
