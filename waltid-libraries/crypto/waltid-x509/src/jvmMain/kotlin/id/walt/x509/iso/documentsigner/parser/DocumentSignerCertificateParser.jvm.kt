@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.parser

import com.nimbusds.jose.util.X509CertUtils
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.x509.CertificateDer
import id.walt.x509.id.walt.x509.JcaX509CertificateHandle
import id.walt.x509.id.walt.x509.iso.documentsigner.certificate.parseFromJcaX500Name
import id.walt.x509.id.walt.x509.iso.iaca.certificate.parseFromJcaX500Name
import id.walt.x509.id.walt.x509.iso.parseFromX509Certificate
import id.walt.x509.id.walt.x509.mustParseCertificateKeyUsageSetFromX509Certificate
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import id.walt.x509.iso.parseCrlDistributionPointUriFromCert
import okio.ByteString.Companion.toByteString
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.bouncycastle.cert.jcajce.JcaX500NameUtil
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

internal actual suspend fun platformParseDocumentSignerCertificate(
    certificate: CertificateDer,
): DocumentSignerDecodedCertificate {

    val cert = X509CertUtils.parse(certificate.bytes)

    val principalName = DocumentSignerPrincipalName.parseFromJcaX500Name(
        name = JcaX500NameUtil.getSubject(cert),
    )

    val iacaPrincipalName = IACAPrincipalName.parseFromJcaX500Name(
        name = JcaX500NameUtil.getIssuer(cert),
    )

    val crlDistributionPointUri = requireNotNull(
        parseCrlDistributionPointUriFromCert(cert)
    ) {
        "CRL distribution point URI must exist as part of the X509 certificate but was found missing"
    }

    val keyUsageSet = mustParseCertificateKeyUsageSetFromX509Certificate(cert)

    val eku = cert.extendedKeyUsage
    require(eku.isNotEmpty()) {
        "Extended key usage must exist and must not be empty in the X509 certificate"
    }

    val skiHex = requireNotNull(
        cert.getExtensionValue(Extension.subjectKeyIdentifier.id)
    ).let {
        SubjectKeyIdentifier.getInstance(
            ASN1OctetString.getInstance(it).octets
        ).keyIdentifier.toHexString()
    }

    val akiHex = requireNotNull(
        cert.getExtensionValue(Extension.authorityKeyIdentifier.id)
    ).let {
        AuthorityKeyIdentifier.getInstance(
            ASN1OctetString.getInstance(it).octets
        ).keyIdentifierOctets.toHexString()
    }

    return DocumentSignerDecodedCertificate(
        issuerPrincipalName = iacaPrincipalName,
        principalName = principalName,
        validityPeriod = CertificateValidityPeriod(
            notBefore = cert.notBefore.toInstant().toKotlinInstant(),
            notAfter = cert.notAfter.toInstant().toKotlinInstant(),
        ),
        issuerAlternativeName = IssuerAlternativeName.parseFromX509Certificate(cert),
        crlDistributionPointUri = crlDistributionPointUri,
        serialNumber = cert.serialNumber.toByteArray().toByteString(),
        keyUsage = keyUsageSet,
        extendedKeyUsage = eku.toSet(),
        akiHex = akiHex,
        skiHex = skiHex,
        isCA = (cert.basicConstraints != -1),
        publicKey = JWKKey.importFromDerCertificate(certificate.bytes).getOrThrow(),
        certificate = JcaX509CertificateHandle(cert),
    )
}
