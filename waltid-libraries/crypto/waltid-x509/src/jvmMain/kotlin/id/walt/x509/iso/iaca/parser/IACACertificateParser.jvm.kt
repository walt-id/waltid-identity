@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.parser

import com.nimbusds.jose.util.X509CertUtils
import id.walt.x509.CertificateDer
import id.walt.x509.id.walt.x509.iso.iaca.certificate.parseFromJcaX500Name
import id.walt.x509.id.walt.x509.iso.parseFromX509Certificate
import id.walt.x509.id.walt.x509.mustParseCertificateKeyUsageSetFromX509Certificate
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import id.walt.x509.iso.parseCrlDistributionPointUriFromCert
import okio.ByteString.Companion.toByteString
import org.bouncycastle.cert.jcajce.JcaX500NameUtil
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

actual class IACACertificateParser actual constructor(val certificate: CertificateDer) {

    actual suspend fun parse(): IACADecodedCertificate {

        val cert = X509CertUtils.parse(certificate.bytes)

        val principalName = IACAPrincipalName.parseFromJcaX500Name(
            name = JcaX500NameUtil.getIssuer(cert),
        )

        val keyUsageSet = mustParseCertificateKeyUsageSetFromX509Certificate(cert)

        return IACADecodedCertificate(
            principalName = principalName,
            validityPeriod = CertificateValidityPeriod(
                notBefore = cert.notBefore.toInstant().toKotlinInstant(),
                notAfter = cert.notAfter.toInstant().toKotlinInstant(),
            ),
            issuerAlternativeName = IssuerAlternativeName.parseFromX509Certificate(cert),
            serialNumber = cert.serialNumber.toByteArray().toByteString(),
            isCA = (cert.basicConstraints != -1),
            pathLengthConstraint = cert.basicConstraints,
            keyUsage = keyUsageSet,
            crlDistributionPointUri = parseCrlDistributionPointUriFromCert(cert),
        )
    }

}
