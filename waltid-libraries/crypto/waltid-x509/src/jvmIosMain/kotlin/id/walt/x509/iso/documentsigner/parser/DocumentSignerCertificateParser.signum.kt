package id.walt.x509.iso.documentsigner.parser

import at.asitplus.signum.indispensable.pki.X509Certificate
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.x509.CertificateDer
import id.walt.x509.SignumX509CertificateHandle
import id.walt.x509.X509ValidityPeriod
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import id.walt.x509.iso.signumAttributeValue
import id.walt.x509.iso.signumAuthorityKeyIdentifierHex
import id.walt.x509.iso.signumBasicConstraints
import id.walt.x509.iso.signumCriticalExtensionOids
import id.walt.x509.iso.signumCrlDistributionPointUri
import id.walt.x509.iso.signumExtendedKeyUsageOids
import id.walt.x509.iso.signumIssuerAlternativeName
import id.walt.x509.iso.signumKeyUsages
import id.walt.x509.iso.signumNonCriticalExtensionOids
import id.walt.x509.iso.signumSubjectKeyIdentifierHex
import kotlinx.io.bytestring.ByteString

internal actual suspend fun platformParseDocumentSignerCertificate(
    certificate: CertificateDer,
): DocumentSignerDecodedCertificate {
    val cert = X509Certificate.decodeFromByteArray(certificate.bytes.toByteArray())
        ?: throw IllegalArgumentException("Invalid X.509 DER certificate")
    val subjectName = cert.tbsCertificate.subjectName
    val issuerName = cert.tbsCertificate.issuerName

    val principalName = DocumentSignerPrincipalName(
        country = requireNotNull(subjectName.signumAttributeValue("2.5.4.6")) {
            "Document signer country code must exist as part of principal name in X509 certificate, but was found missing"
        },
        commonName = requireNotNull(subjectName.signumAttributeValue("2.5.4.3")) {
            "Document signer common name must exist as part of principal name in X509 certificate, but was found missing"
        },
        stateOrProvinceName = subjectName.signumAttributeValue("2.5.4.8"),
        organizationName = subjectName.signumAttributeValue("2.5.4.10"),
        localityName = subjectName.signumAttributeValue("2.5.4.7"),
    )

    val iacaPrincipalName = IACAPrincipalName(
        country = requireNotNull(issuerName.signumAttributeValue("2.5.4.6")) {
            "IACA country code must exist as part of principal name in X509 certificate, but was found missing"
        },
        commonName = requireNotNull(issuerName.signumAttributeValue("2.5.4.3")) {
            "IACA common name must exist as part of principal name in X509 certificate, but was found missing"
        },
        stateOrProvinceName = issuerName.signumAttributeValue("2.5.4.8"),
        organizationName = issuerName.signumAttributeValue("2.5.4.10"),
    )

    val certificateKeyUsages = cert.signumKeyUsages()
    require(certificateKeyUsages.isNotEmpty()) {
        "KeyUsage extension must exist as part of the Document Signer X509 certificate, but was found missing (or empty)"
    }

    return DocumentSignerDecodedCertificate(
        issuerPrincipalName = iacaPrincipalName,
        principalName = principalName,
        validityPeriod = X509ValidityPeriod(
            notBefore = cert.tbsCertificate.validFrom.instant,
            notAfter = cert.tbsCertificate.validUntil.instant,
        ),
        issuerAlternativeName = cert.signumIssuerAlternativeName(),
        crlDistributionPointUri = cert.signumCrlDistributionPointUri(),
        serialNumber = ByteString(cert.tbsCertificate.serialNumber),
        keyUsage = certificateKeyUsages,
        extendedKeyUsage = cert.signumExtendedKeyUsageOids(),
        akiHex = cert.signumAuthorityKeyIdentifierHex(),
        skiHex = cert.signumSubjectKeyIdentifierHex(),
        basicConstraints = cert.signumBasicConstraints(),
        publicKey = JWKKey.importFromDerCertificate(certificate.bytes.toByteArray()).getOrThrow(),
        criticalExtensionOIDs = cert.signumCriticalExtensionOids(),
        nonCriticalExtensionOIDs = cert.signumNonCriticalExtensionOids(),
        certificate = SignumX509CertificateHandle(cert, certificate),
    )
}
