@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.parser

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.x509.CertificateDer
import id.walt.x509.JcaX509CertificateHandle
import id.walt.x509.iso.iaca.certificate.parseFromJcaX500Name
import id.walt.x509.iso.parseFromX509Certificate
import id.walt.x509.X509ValidityPeriod
import id.walt.x509.criticalX509V3ExtensionOIDs
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import id.walt.x509.iso.parseCrlDistributionPointUriFromCert
import id.walt.x509.nonCriticalX509V3ExtensionOIDs
import id.walt.x509.subjectKeyIdentifier
import id.walt.x509.toJcaX509Certificate
import id.walt.x509.x509BasicConstraints
import id.walt.x509.x509KeyUsages
import okio.ByteString.Companion.toByteString
import org.bouncycastle.cert.jcajce.JcaX500NameUtil
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

internal actual suspend fun platformParseIACACertificate(
    certificate: CertificateDer,
): IACADecodedCertificate {

    val cert = certificate.toJcaX509Certificate()

    val principalName = IACAPrincipalName.parseFromJcaX500Name(
        name = JcaX500NameUtil.getIssuer(cert),
    )

    val certificateKeyUsages = cert.x509KeyUsages
    require(certificateKeyUsages.isNotEmpty()) {
        "KeyUsage extension must exist as part of the IACA X509 certificate, but was found missing (or empty)"
    }

    val skiHex = requireNotNull(
        cert.subjectKeyIdentifier
    ) {
        "Subject key identifier must exist as part of the IACA X509 certificate, but was found missing"
    }.hex()

    return IACADecodedCertificate(
        principalName = principalName,
        validityPeriod = X509ValidityPeriod(
            notBefore = cert.notBefore.toInstant().toKotlinInstant(),
            notAfter = cert.notAfter.toInstant().toKotlinInstant(),
        ),
        issuerAlternativeName = IssuerAlternativeName.parseFromX509Certificate(cert),
        serialNumber = cert.serialNumber.toByteArray().toByteString(),
        basicConstraints = cert.x509BasicConstraints,
        keyUsage = certificateKeyUsages,
        skiHex = skiHex,
        crlDistributionPointUri = parseCrlDistributionPointUriFromCert(cert),
        publicKey = JWKKey.importFromDerCertificate(certificate.bytes.toByteArray()).getOrThrow(),
        criticalExtensionOIDs = cert.criticalX509V3ExtensionOIDs,
        nonCriticalExtensionOIDs = cert.nonCriticalX509V3ExtensionOIDs,
        certificate = JcaX509CertificateHandle(cert),
    )
}
