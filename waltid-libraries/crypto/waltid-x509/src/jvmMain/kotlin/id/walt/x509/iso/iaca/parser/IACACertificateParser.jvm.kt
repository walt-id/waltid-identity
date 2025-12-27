@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.parser

import com.nimbusds.jose.util.X509CertUtils
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.x509.CertificateDer
import id.walt.x509.id.walt.x509.*
import id.walt.x509.id.walt.x509.iso.iaca.certificate.parseFromJcaX500Name
import id.walt.x509.id.walt.x509.iso.parseFromX509Certificate
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import id.walt.x509.iso.parseCrlDistributionPointUriFromCert
import okio.ByteString.Companion.toByteString
import org.bouncycastle.cert.jcajce.JcaX500NameUtil
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

internal actual suspend fun platformParseIACACertificate(
    certificate: CertificateDer,
): IACADecodedCertificate {

    val cert = X509CertUtils.parse(certificate.bytes)

    val principalName = IACAPrincipalName.parseFromJcaX500Name(
        name = JcaX500NameUtil.getIssuer(cert),
    )

    val certificateKeyUsages = cert.certificateKeyUsages
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
        validityPeriod = CertificateValidityPeriod(
            notBefore = cert.notBefore.toInstant().toKotlinInstant(),
            notAfter = cert.notAfter.toInstant().toKotlinInstant(),
        ),
        issuerAlternativeName = IssuerAlternativeName.parseFromX509Certificate(cert),
        serialNumber = cert.serialNumber.toByteArray().toByteString(),
        basicConstraints = cert.certificateBasicConstraints,
        keyUsage = certificateKeyUsages,
        skiHex = skiHex,
        crlDistributionPointUri = parseCrlDistributionPointUriFromCert(cert),
        publicKey = JWKKey.importFromDerCertificate(certificate.bytes).getOrThrow(),
        criticalExtensionOIDs = cert.criticalX509V3ExtensionOIDs,
        nonCriticalExtensionOIDs = cert.nonCriticalX509V3ExtensionOIDs,
        certificate = JcaX509CertificateHandle(cert),
    )
}
