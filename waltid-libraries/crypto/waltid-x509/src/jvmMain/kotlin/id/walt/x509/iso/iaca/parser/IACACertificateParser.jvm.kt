@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca.parser

import com.nimbusds.jose.util.X509CertUtils
import id.walt.x509.CertificateDer
import id.walt.x509.id.walt.x509.*
import id.walt.x509.iso.CertificateValidityPeriod
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import okio.ByteString.Companion.toByteString
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX500NameUtil
import java.security.cert.X509Certificate
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

actual class IACACertificateParser actual constructor(val certificate: CertificateDer) {

    actual suspend fun parse(): IACADecodedCertificate {

        val cert = X509CertUtils.parse(certificate.bytes)

        val issuerName = JcaX500NameUtil.getIssuer(cert)

        val country = requireNotNull(
            issuerName.getCountryCode()
        ) {
            "Issuer country code must exist as part of principal name in X509 certificate , but was found missing"
        }
        val commonName = requireNotNull(
            issuerName.getCommonName()
        ) {
            "Issuer common name must exist as part of principal name in X509 certificate , but was found missing"
        }

        val keyUsageSet = requireNotNull(
            cert.getExtensionValue(Extension.keyUsage.id)
        ) {
            "KeyUsage extension must exist as part of the X509 certificate, but was found missing"
        }.let { keyUsageExtRaw ->
            KeyUsage.getInstance(ASN1OctetString.getInstance(keyUsageExtRaw).octets).toCertificateKeyUsages()
        }

        return IACADecodedCertificate(
            principalName = IACAPrincipalName(
                country = country,
                commonName = commonName,
                stateOrProvinceName = issuerName.getStateOrProvinceName(),
                organizationName = issuerName.getOrganizationName(),
            ),
            validityPeriod = CertificateValidityPeriod(
                notBefore = cert.notBefore.toInstant().toKotlinInstant(),
                notAfter = cert.notAfter.toInstant().toKotlinInstant(),
            ),
            issuerAlternativeName = parseIssuerAlternativeNameFromX509Certificate(cert),
            serialNumber = cert.serialNumber.toByteArray().toByteString(),
            isCA = (cert.basicConstraints != -1),
            pathLengthConstraint = cert.basicConstraints,
            keyUsage = keyUsageSet,
            crlDistributionPointUri = parseCrlDistributionPointUriFromCert(cert),
        )
    }

    private fun parseIssuerAlternativeNameFromX509Certificate(
        cert: X509Certificate,
    ): IssuerAlternativeName {
        val issAltNamesBytes = requireNotNull(cert.getExtensionValue(Extension.issuerAlternativeName.id)) {
            "Issuer alternative name X509 certificate extension must exist, but was found missing from input certificate"
        }
        val asn1OctetStr = ASN1OctetString.getInstance(issAltNamesBytes)
        val names = GeneralNames.getInstance(asn1OctetStr.octets).names
        require(names.isNotEmpty() && names.size <= 2)
        return IssuerAlternativeName(
            email = names.find { it.tagNo == GeneralName.rfc822Name }?.name?.toString(),
            uri = names.find { it.tagNo == GeneralName.uniformResourceIdentifier }?.name?.toString(),
        )
    }
}
