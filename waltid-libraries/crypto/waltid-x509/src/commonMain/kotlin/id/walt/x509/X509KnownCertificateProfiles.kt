package id.walt.x509

import id.walt.x509.iso.DS_CERT_MAX_VALIDITY_SECONDS
import id.walt.x509.iso.DocumentSignerEkuOID
import id.walt.x509.iso.IACA_CERT_MAX_VALIDITY_SECONDS
import kotlin.time.Duration.Companion.seconds

object X509KnownProfileIds {
    val IsoIaca = X509ProfileId("iso.iaca")
    val IsoDocumentSigner = X509ProfileId("iso.document-signer")
    val Qwac = X509ProfileId("etsi.qwac")
    val Qsealc = X509ProfileId("etsi.qsealc")
    val Psd2Transport = X509ProfileId("etsi.psd2.transport")
    val GenericCa = X509ProfileId("generic-ca")
    val GenericEndEntity = X509ProfileId("generic-end-entity")
}

object X509KnownCertificateProfiles {
    val IsoIaca = X509CertificateProfile(
        profileId = X509KnownProfileIds.IsoIaca,
        keyUsages = setOf(
            X509KeyUsage.KeyCertSign,
            X509KeyUsage.CRLSign,
        ),
        basicConstraints = X509BasicConstraints(
            isCA = true,
            pathLengthConstraint = 0,
        ),
        validityPolicy = X509ValidityPolicy(
            maximumValidity = IACA_CERT_MAX_VALIDITY_SECONDS.seconds,
        ),
        description = "ISO/IEC 18013-5 IACA root certificate profile",
    )

    val IsoDocumentSigner = X509CertificateProfile(
        profileId = X509KnownProfileIds.IsoDocumentSigner,
        keyUsages = setOf(X509KeyUsage.DigitalSignature),
        extendedKeyUsages = setOf(X509ExtendedKeyUsage(DocumentSignerEkuOID, "mdlDS")),
        basicConstraints = X509BasicConstraints(
            isCA = false,
            pathLengthConstraint = 0,
        ),
        validityPolicy = X509ValidityPolicy(
            maximumValidity = DS_CERT_MAX_VALIDITY_SECONDS.seconds,
        ),
        description = "ISO/IEC 18013-5 document signer certificate profile",
    )

    val GenericCa = X509CertificateProfile(
        profileId = X509KnownProfileIds.GenericCa,
        keyUsages = setOf(
            X509KeyUsage.KeyCertSign,
            X509KeyUsage.CRLSign,
        ),
        basicConstraints = X509BasicConstraints(
            isCA = true,
            pathLengthConstraint = 0,
        ),
        description = "Generic certificate authority profile",
    )

    val GenericEndEntity = X509CertificateProfile(
        profileId = X509KnownProfileIds.GenericEndEntity,
        keyUsages = setOf(X509KeyUsage.DigitalSignature),
        basicConstraints = X509BasicConstraints(
            isCA = false,
            pathLengthConstraint = 0,
        ),
        description = "Generic end-entity certificate profile",
    )

    val Qwac = X509CertificateProfile(
        profileId = X509KnownProfileIds.Qwac,
        keyUsages = setOf(
            X509KeyUsage.DigitalSignature,
            X509KeyUsage.KeyEncipherment,
        ),
        extendedKeyUsages = setOf(
            X509ExtendedKeyUsage.ServerAuth,
            X509ExtendedKeyUsage.ClientAuth,
        ),
        basicConstraints = X509BasicConstraints(
            isCA = false,
            pathLengthConstraint = 0,
        ),
        description = "Qualified website authentication certificate style profile",
    )

    val Qsealc = X509CertificateProfile(
        profileId = X509KnownProfileIds.Qsealc,
        keyUsages = setOf(
            X509KeyUsage.DigitalSignature,
            X509KeyUsage.NonRepudiation,
        ),
        basicConstraints = X509BasicConstraints(
            isCA = false,
            pathLengthConstraint = 0,
        ),
        description = "Qualified electronic seal certificate style profile",
    )

    val Psd2Transport = X509CertificateProfile(
        profileId = X509KnownProfileIds.Psd2Transport,
        keyUsages = setOf(
            X509KeyUsage.DigitalSignature,
            X509KeyUsage.KeyEncipherment,
        ),
        extendedKeyUsages = setOf(
            X509ExtendedKeyUsage.ServerAuth,
            X509ExtendedKeyUsage.ClientAuth,
        ),
        basicConstraints = X509BasicConstraints(
            isCA = false,
            pathLengthConstraint = 0,
        ),
        description = "PSD2 transport certificate style profile",
    )

    val all = listOf(
        IsoIaca,
        IsoDocumentSigner,
        GenericCa,
        GenericEndEntity,
        Qwac,
        Qsealc,
        Psd2Transport,
    )

    val registry = StaticX509CertificateProfileRegistry(all)
}
