package id.walt.x509.id.walt.x509

import id.walt.x509.CertificateKeyUsage
import id.walt.x509.iso.IssuerAlternativeName
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.*
import java.security.cert.X509Certificate

internal fun issuerAlternativeNameToGeneralNameArray(
    issuerAlternativeName: IssuerAlternativeName,
) = listOfNotNull(
    issuerAlternativeName.uri?.let {
        GeneralName(GeneralName.uniformResourceIdentifier, it)
    },
    issuerAlternativeName.email?.let {
        GeneralName(GeneralName.rfc822Name, it)
    }
).toTypedArray()

fun KeyUsage.toCertificateKeyUsages(): Set<CertificateKeyUsage> {
    val usages = mutableSetOf<CertificateKeyUsage>()
    fun addIf(bit: Int, usage: CertificateKeyUsage) {
        if (this.hasUsages(bit)) usages.add(usage)
    }
    addIf(KeyUsage.digitalSignature, CertificateKeyUsage.DigitalSignature)
    addIf(KeyUsage.nonRepudiation, CertificateKeyUsage.NonRepudiation)
    addIf(KeyUsage.keyEncipherment, CertificateKeyUsage.KeyEncipherment)
    addIf(KeyUsage.dataEncipherment, CertificateKeyUsage.DataEncipherment)
    addIf(KeyUsage.keyAgreement, CertificateKeyUsage.KeyAgreement)
    addIf(KeyUsage.keyCertSign, CertificateKeyUsage.KeyCertSign)
    addIf(KeyUsage.cRLSign, CertificateKeyUsage.CRLSign)
    addIf(KeyUsage.encipherOnly, CertificateKeyUsage.EncipherOnly)
    addIf(KeyUsage.decipherOnly, CertificateKeyUsage.DecipherOnly)
    return usages
}

fun CertificateKeyUsage.toBCKeyUsage() = when (this) {
    CertificateKeyUsage.DigitalSignature -> KeyUsage.digitalSignature
    CertificateKeyUsage.NonRepudiation -> KeyUsage.nonRepudiation
    CertificateKeyUsage.KeyEncipherment -> KeyUsage.keyEncipherment
    CertificateKeyUsage.DataEncipherment -> KeyUsage.dataEncipherment
    CertificateKeyUsage.KeyAgreement -> KeyUsage.keyAgreement
    CertificateKeyUsage.KeyCertSign -> KeyUsage.keyCertSign
    CertificateKeyUsage.CRLSign -> KeyUsage.cRLSign
    CertificateKeyUsage.EncipherOnly -> KeyUsage.encipherOnly
    CertificateKeyUsage.DecipherOnly -> KeyUsage.decipherOnly
}


fun Iterable<CertificateKeyUsage>.toBouncyCastleKeyUsage(): KeyUsage {
    var bits = 0
    this.forEach { certKeyUsage ->
        bits = bits or certKeyUsage.toBCKeyUsage()
    }
    return KeyUsage(bits)
}

internal fun buildX500Name(
    country: String,
    commonName: String,
    stateOrProvinceName: String? = null,
    organizationName: String? = null,
    localityName: String? = null,
): X500Name {
    val nameBuilder = X500NameBuilder()

    nameBuilder.addRDN(BCStyle.C, country)
    nameBuilder.addRDN(BCStyle.CN, commonName)

    stateOrProvinceName?.let {
        nameBuilder.addRDN(BCStyle.ST, it)
    }

    organizationName?.let {
        nameBuilder.addRDN(BCStyle.O, it)
    }

    localityName?.let {
        nameBuilder.addRDN(BCStyle.L, it)
    }

    return nameBuilder.build()
}

internal fun parseCrlDistributionPointUriFromCert(
    cert: X509Certificate,
) = cert.getExtensionValue(
    Extension.cRLDistributionPoints.id
)?.let { crlDistributionPointBytes ->
    val crlDistPoint = CRLDistPoint.getInstance(
        requireNotNull(ASN1OctetString.getInstance(crlDistributionPointBytes).octets) {
            "CRL distribution point uri extension but be specified in X509 certificate, but was found missing"
        }
    )
    require(crlDistPoint.distributionPoints.size == 1) {
        "Invalid crl distribution points size, expected: 1, found: ${crlDistPoint.distributionPoints.size}"
    }

    crlDistPoint.distributionPoints.first().distributionPoint.let { distPointName ->
        (distPointName.name as GeneralNames).let { generalNames ->
            require(generalNames.names.size == 1)
            val distPointNameGeneralName = generalNames.names.first()
            require(distPointNameGeneralName.tagNo == GeneralName.uniformResourceIdentifier)
            (distPointNameGeneralName.name as DERIA5String).string
        }
    }
}

internal fun X500Name.getCountryCode(): String? {
    return getRDNs(BCStyle.C).firstOrNull()?.first?.value?.toString()
}

internal fun X500Name.getCommonName(): String? {
    return getRDNs(BCStyle.CN).firstOrNull()?.first?.value?.toString()
}

internal fun X500Name.getStateOrProvinceName(): String? {
    return getRDNs(BCStyle.ST).firstOrNull()?.first?.value?.toString()
}

internal fun X500Name.getOrganizationName(): String? {
    return getRDNs(BCStyle.O).firstOrNull()?.first?.value?.toString()
}

internal fun X500Name.getLocalityName(): String? {
    return getRDNs(BCStyle.L).firstOrNull()?.first?.value?.toString()
}

