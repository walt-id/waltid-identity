package id.walt.x509.iso

import id.walt.crypto.keys.KeyType
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import java.math.BigInteger
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*


internal actual fun generateIsoCompliantX509CertificateSerialNo(): ByteString {
    val random = SecureRandom()
    val randomBytes = ByteArray(ISO_CERT_SERIAL_NUMBER_REQUIRED_LENGTH)
    random.nextBytes(randomBytes)
    return BigInteger(randomBytes).abs().toByteArray().toByteString()
}

internal fun getJcaSigningAlgorithmNameFromKeyType(
    keyType: KeyType,
) = DefaultSignatureAlgorithmIdentifierFinder().run {
    when (keyType) {
        KeyType.secp256r1 -> find("SHA256withECDSA")

        KeyType.secp384r1 -> find("SHA384withECDSA")

        KeyType.secp521r1 -> find("SHA512withECDSA")

        else -> throw IllegalArgumentException("Unsupported key type $keyType for ISO certificate signing operation")
    }
}


internal actual fun isValidIsoCountryCode(countryCode: String): Boolean {
    return Locale.getISOCountries().find { it == countryCode }?.let {
        true
    } ?: false
}

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


internal fun parseCrlDistributionPointUriFromCert(
    cert: X509Certificate,
) = cert.getExtensionValue(
    Extension.cRLDistributionPoints.id
)?.let { crlDistributionPointBytes ->
    val crlDistPoint = CRLDistPoint.getInstance(
        requireNotNull(ASN1OctetString.getInstance(crlDistributionPointBytes).octets) {
            "CRL distribution point uri extension must be specified in X509 certificate, but was found missing"
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