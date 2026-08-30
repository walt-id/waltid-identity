package id.walt.x509

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.dn.DistinguishedName
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension.Companion.extensionExtendedKeyUsage
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.model.GeneralName
import kotlinx.io.bytestring.ByteString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Mandatory ISO/IEC 18013-5 reader-authentication extended-key-usage OID. */
const val MdocReaderAuthenticationEkuOid: String = "1.0.18013.5.1.6"

/** Recommended ISO/IEC 23220-4 reader-authentication extended-key-usage OID. */
const val MdocReaderAuthentication23220EkuOid: String = "1.0.23220.4.1.6"

/**
 * Validates an mdoc reader-authentication leaf certificate against the ISO/IEC 18013-5 profile.
 *
 * This validates certificate contents only. Call [validateMdocReaderAuthenticationCertificateChain]
 * to additionally establish an RFC 5280-style path to an explicit application trust anchor.
 */
@Throws(X509ValidationException::class)
fun validateMdocReaderAuthenticationCertificateProfile(
    certificate: CertificateDer,
    now: Instant = Clock.System.now(),
) {
    val parsed = parseIsoCertificate(certificate, "reader certificate")
    val data = parsed.data
    val subjectPublicKey = data.subjectPublicKeyInfo

    requireProfile(data.version == 3, "Reader certificate must be X.509 version 3")
    validateIsoCertificateSerialNumber(data.serialNumberRaw, "Reader certificate")
    requireProfile(
        now >= data.validity.notBefore && now < data.validity.notAfter,
        "Reader certificate is not currently valid",
    )
    requireProfile(
        data.validity.notAfter - data.validity.notBefore <= MAX_READER_CERTIFICATE_VALIDITY,
        "Reader certificate validity exceeds 1 187 days",
    )
    requireProfile(
        parsed.signatureAlgorithmOid in ISO_ALLOWED_CERTIFICATE_SIGNATURE_ALGORITHM_OIDS,
        "Reader certificate uses an unsupported certificate-signature algorithm",
    )

    val commonNames = DistinguishedName.ofString(data.subjectDn).rdnList
        .flatten()
        .filter { it.type.oid == COMMON_NAME_OID }
        .map { it.value }
    requireProfile(commonNames.size == 1 && commonNames.single().isNotBlank(), "Reader certificate requires one common name")

    requireProfile(
        subjectPublicKey.algorithmOid in ISO_ALLOWED_READER_SUBJECT_PUBLIC_KEY_ALGORITHM_OIDS,
        "Reader certificate uses an unsupported subject-public-key algorithm",
    )
    if (subjectPublicKey.algorithmOid == EC_PUBLIC_KEY_OID) {
        requireProfile(
            subjectPublicKey.ellipticCurveOid in ISO_ALLOWED_ELLIPTIC_CURVE_OIDS,
            "Reader certificate uses an unsupported elliptic curve",
        )
        requireProfile(
            subjectPublicKey.keyValueRaw.size > 0 &&
                subjectPublicKey.keyValueRaw[0] == UNCOMPRESSED_EC_POINT_PREFIX,
            "Reader certificate EC public key must use uncompressed form",
        )
    } else {
        requireProfile(subjectPublicKey.ellipticCurveOid == null, "Edwards-curve certificates must omit EC parameters")
    }

    val authorityKeyIdentifier = data.extensionAuthorityKeyIdentifier
    requireProfile(
        authorityKeyIdentifier != null &&
            !authorityKeyIdentifier.critical &&
            (authorityKeyIdentifier.keyIdentifier?.size ?: 0) > 0,
        "Reader certificate requires an authority key identifier",
    )
    val subjectKeyIdentifier = data.extensionSubjectKeyIdentifier
    requireProfile(
        subjectKeyIdentifier != null &&
            !subjectKeyIdentifier.critical &&
            subjectKeyIdentifier.keyIdentifier == subjectPublicKey.keyId,
        "Reader certificate requires the SHA-1 subject key identifier",
    )

    val keyUsage = data.extensionKeyUsage
        ?: throw X509ValidationException("Reader certificate requires key usage")
    requireProfile(keyUsage.critical, "Reader certificate key usage must be critical")
    requireProfile(
        keyUsage.keyPurposeIdList == setOf(KeyUsageExtension.KeyUsage.digitalSignature),
        "Reader certificate key usage must contain only digitalSignature",
    )

    val extendedKeyUsage = data.extensionExtendedKeyUsage
        ?: throw X509ValidationException("Reader certificate requires extended key usage")
    requireProfile(extendedKeyUsage.critical, "Reader certificate extended key usage must be critical")
    requireProfile(
        MdocReaderAuthenticationEkuOid in extendedKeyUsage.keyPurposeIdList,
        "Reader certificate extended key usage must contain $MdocReaderAuthenticationEkuOid",
    )

    val crlDistributionPoints = data.extensionCrlDistributionPoints
        ?: throw X509ValidationException("Reader certificate requires CRL distribution points")
    requireProfile(
        !crlDistributionPoints.critical && crlDistributionPoints.distributionPoints.isNotEmpty(),
        "Reader certificate requires a non-critical CRL distribution-points extension",
    )
    requireProfile(
        crlDistributionPoints.distributionPoints.all { point ->
            val fullNames = point.distributionPointFullName
            point.reason == null &&
                point.cRLIssuer == null &&
                point.distributionPointNameRelativeToCrlIssuer == null &&
                fullNames?.isNotEmpty() == true &&
                fullNames.all { name ->
                    name.type == GeneralName.NameType.uniformResourceIdentifier && name.value.isNotBlank()
                }
        },
        "Reader certificate CRL distribution points must contain only full-name URIs",
    )

    val unsupportedCritical = data.extensions.values
        .filter { it.critical }
        .map { it.oid }
        .filterNot { it == KEY_USAGE_OID || it == EXTENDED_KEY_USAGE_OID }
    requireProfile(unsupportedCritical.isEmpty(), "Reader certificate contains an unsupported critical extension")
}

/**
 * Validates a reader-authentication certificate and builds its path only to [trustAnchors].
 * Certificates supplied by the reader are path-construction inputs and never implicit anchors.
 */
@Throws(X509ValidationException::class)
fun validateMdocReaderAuthenticationCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>,
    now: Instant = Clock.System.now(),
) {
    requireProfile(trustAnchors.isNotEmpty(), "At least one explicit reader trust anchor is required")
    requireProfile(leaf !in trustAnchors, "A reader end-entity certificate cannot be its own trust anchor")
    validateMdocReaderAuthenticationCertificateProfile(leaf, now)

    val parsedLeaf = parseIsoCertificate(leaf, "reader certificate")
    val leafAuthorityKeyIdentifier = requireNotNull(
        parsedLeaf.data.extensionAuthorityKeyIdentifier?.keyIdentifier
    ) { "Reader certificate requires an authority key identifier" }
    val possibleIssuers = (chain + trustAnchors)
        .filterNot { it == leaf }
        .map { parseIsoCertificate(it, "reader issuer certificate") }
    requireProfile(
        possibleIssuers.any {
            it.data.subjectDnRaw == parsedLeaf.data.issuerDnRaw &&
                it.data.extensionSubjectKeyIdentifier?.keyIdentifier == leafAuthorityKeyIdentifier
        },
        "Reader certificate issuer and authority key identifier do not exactly match an available CA",
    )

    validateCertificateChainWithExplicitTrust(
        leaf = leaf,
        chain = chain,
        trustAnchors = trustAnchors,
        enableTrustedChainRoot = false,
        now = now,
        additionalProcessedCriticalExtensionOids = setOf(EXTENDED_KEY_USAGE_OID),
    )
}

/** Returns the common name from a reader certificate that has already passed profile validation. */
@Throws(X509ValidationException::class)
fun mdocReaderAuthenticationCommonName(certificate: CertificateDer): String {
    val parsed = parseIsoCertificate(certificate, "reader certificate")
    return DistinguishedName.ofString(parsed.data.subjectDn).rdnList
        .flatten()
        .single { it.type.oid == COMMON_NAME_OID }
        .value
}

internal fun parseIsoCertificate(certificate: CertificateDer, description: String): X509Certificate = runCatching {
    X509CertificateUtil.parseCertificateDerEncoded(certificate.bytes)
}.getOrElse { cause ->
    throw X509ValidationException("Invalid $description", cause)
}

internal fun validateIsoCertificateSerialNumber(serialNumber: ByteString, owner: String) {
    requireProfile(serialNumber.size in 8..20, "$owner serial number must contain 63 to 159 bits")
    requireProfile(serialNumber[0] >= 0, "$owner serial number must be positive")
    val serialNumberBytes = serialNumber.toByteArray()
    val firstNonZeroIndex = serialNumberBytes.indexOfFirst { it != 0.toByte() }
    requireProfile(firstNonZeroIndex >= 0, "$owner serial number must be non-zero")
    val mostSignificantByte = serialNumberBytes[firstNonZeroIndex].toInt() and 0xff
    val bitLength = (serialNumberBytes.size - firstNonZeroIndex - 1) * 8 +
        (Int.SIZE_BITS - mostSignificantByte.countLeadingZeroBits())
    requireProfile(
        bitLength >= 63,
        "$owner serial number must contain at least 63 bits",
    )
}

internal fun requireProfile(condition: Boolean, message: String) {
    if (!condition) throw X509ValidationException(message)
}

internal val ISO_MAX_CERTIFICATE_VALIDITY = 1_187.days
private val MAX_READER_CERTIFICATE_VALIDITY = ISO_MAX_CERTIFICATE_VALIDITY
internal const val ISO_COMMON_NAME_OID = "2.5.4.3"
private const val COMMON_NAME_OID = ISO_COMMON_NAME_OID
internal const val ISO_EC_PUBLIC_KEY_OID = "1.2.840.10045.2.1"
private const val EC_PUBLIC_KEY_OID = ISO_EC_PUBLIC_KEY_OID
private const val KEY_USAGE_OID = "2.5.29.15"
private const val EXTENDED_KEY_USAGE_OID = "2.5.29.37"
internal const val ISO_UNCOMPRESSED_EC_POINT_PREFIX: Byte = 0x04
private const val UNCOMPRESSED_EC_POINT_PREFIX = ISO_UNCOMPRESSED_EC_POINT_PREFIX
internal val ISO_ALLOWED_CERTIFICATE_SIGNATURE_ALGORITHM_OIDS = setOf(
    "1.2.840.10045.4.3.2",
    "1.2.840.10045.4.3.3",
    "1.2.840.10045.4.3.4",
)
private val ISO_ALLOWED_READER_SUBJECT_PUBLIC_KEY_ALGORITHM_OIDS = setOf(
    EC_PUBLIC_KEY_OID,
    "1.3.101.112",
    "1.3.101.113",
)
internal val ISO_ALLOWED_ELLIPTIC_CURVE_OIDS = setOf(
    "1.2.840.10045.3.1.7",
    "1.3.132.0.34",
    "1.3.132.0.35",
    "1.3.36.3.3.2.8.1.1.7",
    "1.3.36.3.3.2.8.1.1.9",
    "1.3.36.3.3.2.8.1.1.11",
    "1.3.36.3.3.2.8.1.1.13",
)
