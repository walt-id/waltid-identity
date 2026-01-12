@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.parser

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.x509.CertificateDer
import id.walt.x509.JcaX509CertificateHandle
import id.walt.x509.iso.documentsigner.certificate.parseFromJcaX500Name
import id.walt.x509.iso.iaca.certificate.parseFromJcaX500Name
import id.walt.x509.iso.parseFromX509Certificate
import id.walt.x509.X509ValidityPeriod
import id.walt.x509.authorityKeyIdentifier
import id.walt.x509.criticalX509V3ExtensionOIDs
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
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

internal actual suspend fun platformParseDocumentSignerCertificate(
    certificate: CertificateDer,
): DocumentSignerDecodedCertificate {

    val cert = certificate.toJcaX509Certificate()

    val principalName = DocumentSignerPrincipalName.parseFromJcaX500Name(
        name = JcaX500NameUtil.getSubject(cert),
    )

    val iacaPrincipalName = IACAPrincipalName.parseFromJcaX500Name(
        name = JcaX500NameUtil.getIssuer(cert),
    )

    val crlDistributionPointUri = requireNotNull(
        parseCrlDistributionPointUriFromCert(cert)
    ) {
        "CRL distribution point URI must exist as part of the Document Signer X509 certificate,  but was found missing"
    }

    val certificateKeyUsages = cert.x509KeyUsages
    require(certificateKeyUsages.isNotEmpty()) {
        "KeyUsage extension must exist as part of the Document Signer X509 certificate, but was found missing (or empty)"
    }

    val eku = cert.extendedKeyUsage
    require(eku.isNotEmpty()) {
        "Extended key usage must exist as part of the Document Signer X509 certificate, but was found missing (or empty)"
    }

    val skiHex = requireNotNull(
        cert.subjectKeyIdentifier
    ) {
        "Subject key identifier must exist as part of the Document Signer X509 certificate, but was found missing"
    }.hex()

    val akiHex = requireNotNull(
        cert.authorityKeyIdentifier
    ) {
        "Authority key identifier must exist as part of the Document Signer X509 certificate, but was found missing"
    }.hex()

    return DocumentSignerDecodedCertificate(
        issuerPrincipalName = iacaPrincipalName,
        principalName = principalName,
        validityPeriod = X509ValidityPeriod(
            notBefore = cert.notBefore.toInstant().toKotlinInstant(),
            notAfter = cert.notAfter.toInstant().toKotlinInstant(),
        ),
        issuerAlternativeName = IssuerAlternativeName.parseFromX509Certificate(cert),
        crlDistributionPointUri = crlDistributionPointUri,
        serialNumber = cert.serialNumber.toByteArray().toByteString(),
        keyUsage = certificateKeyUsages,
        extendedKeyUsage = eku.toSet(),
        akiHex = akiHex,
        skiHex = skiHex,
        basicConstraints = cert.x509BasicConstraints,
        publicKey = JWKKey.importFromDerCertificate(certificate.bytes.toByteArray()).getOrThrow(),
        criticalExtensionOIDs = cert.criticalX509V3ExtensionOIDs,
        nonCriticalExtensionOIDs = cert.nonCriticalX509V3ExtensionOIDs,
        certificate = JcaX509CertificateHandle(cert),
    )
}
