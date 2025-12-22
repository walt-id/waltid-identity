@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner.parser

import com.nimbusds.jose.util.X509CertUtils
import id.walt.x509.CertificateDer
import id.walt.x509.id.walt.x509.*
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate
import okio.ByteString.Companion.toByteString
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX500NameUtil
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

actual class DocumentSignerCertificateParser actual constructor(val certificate: CertificateDer) {

    actual suspend fun parse(): DocumentSignerDecodedCertificate {

        val cert = X509CertUtils.parse(certificate.bytes)

        val subjectName = JcaX500NameUtil.getSubject(cert)

        val country = requireNotNull(
            subjectName.getCountryCode()
        ) {
            "Subject country code must exist as part of principal name in X509 certificate , but was found missing"
        }
        val commonName = requireNotNull(
            subjectName.getCommonName()
        ) {
            "Subject common name must exist as part of principal name in X509 certificate , but was found missing"
        }

        val crlDistributionPointUri = requireNotNull(
            parseCrlDistributionPointUriFromCert(cert)
        ) {
            "CRL distribution point URI must exist as part of the X509 certificate but was found missing"
        }

        val keyUsageSet = requireNotNull(
            cert.getExtensionValue(Extension.keyUsage.id)
        ) {
            "KeyUsage extension must exist as part of the X509 certificate, but was found missing"
        }.let { keyUsageExtRaw ->
            KeyUsage.getInstance(ASN1OctetString.getInstance(keyUsageExtRaw).octets).toCertificateKeyUsages()
        }

        return DocumentSignerDecodedCertificate(
            country = country,
            commonName = commonName,
            notBefore = cert.notBefore.toInstant().toKotlinInstant(),
            notAfter = cert.notAfter.toInstant().toKotlinInstant(),
            crlDistributionPointUri = crlDistributionPointUri,
            serialNumber = cert.serialNumber.toByteArray().toByteString(),
            keyUsage = keyUsageSet,
            isCA = (cert.basicConstraints != -1),
            stateOrProvinceName = subjectName.getStateOrProvinceName(),
            organizationName = subjectName.getOrganizationName(),
            localityName = subjectName.getLocalityName(),
        )
    }
}