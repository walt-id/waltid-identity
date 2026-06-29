package id.walt.x509.iso.iaca.parser

import at.asitplus.signum.indispensable.pki.X509Certificate
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.x509.CertificateDer
import id.walt.x509.SignumX509CertificateHandle
import id.walt.x509.X509ValidityPeriod
import id.walt.x509.iso.signumBasicConstraints
import id.walt.x509.iso.signumCrlDistributionPointUriOrNull
import id.walt.x509.iso.signumCriticalExtensionOids
import id.walt.x509.iso.signumIssuerAlternativeName
import id.walt.x509.iso.signumKeyUsages
import id.walt.x509.iso.signumNonCriticalExtensionOids
import id.walt.x509.iso.signumSubjectKeyIdentifierHex
import id.walt.x509.iso.signumAttributeValue
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import kotlinx.io.bytestring.ByteString

internal actual suspend fun platformParseIACACertificate(
    certificate: CertificateDer,
): IACADecodedCertificate {
    val cert = X509Certificate.decodeFromByteArray(certificate.bytes.toByteArray())
        ?: throw IllegalArgumentException("Invalid X.509 DER certificate")
    val issuerName = cert.tbsCertificate.issuerName

    val principalName = IACAPrincipalName(
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
        "KeyUsage extension must exist as part of the IACA X509 certificate, but was found missing (or empty)"
    }

    return IACADecodedCertificate(
        principalName = principalName,
        validityPeriod = X509ValidityPeriod(
            notBefore = cert.tbsCertificate.validFrom.instant,
            notAfter = cert.tbsCertificate.validUntil.instant,
        ),
        issuerAlternativeName = cert.signumIssuerAlternativeName(),
        serialNumber = ByteString(cert.tbsCertificate.serialNumber),
        basicConstraints = cert.signumBasicConstraints(),
        keyUsage = certificateKeyUsages,
        skiHex = cert.signumSubjectKeyIdentifierHex(),
        crlDistributionPointUri = cert.signumCrlDistributionPointUriOrNull(),
        publicKey = JWKKey.importFromDerCertificate(certificate.bytes.toByteArray()).getOrThrow(),
        criticalExtensionOIDs = cert.signumCriticalExtensionOids(),
        nonCriticalExtensionOIDs = cert.signumNonCriticalExtensionOids(),
        certificate = SignumX509CertificateHandle(cert, certificate),
    )
}
