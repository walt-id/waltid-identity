package id.walt.x509

import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.encoding.parse
import at.asitplus.signum.indispensable.asn1.readOid
import at.asitplus.signum.indispensable.pki.X509Certificate as SignumX509Certificate
import id.walt.certificate.x509.dn.DistinguishedName
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.model.GeneralName
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Validates the ISO/IEC 18013-5 RICAL signer-certificate profile fields understood by waltid-x509.
 *
 * Certificate-path validation and the COSE signature remain separate checks. The application still
 * decides whether the signer's certificate-policy OID represents an accepted RICAL governance policy.
 */
@Throws(X509ValidationException::class)
fun validateRicalSignerCertificateProfile(
    certificate: CertificateDer,
    acceptedCertificatePolicyOids: Set<String>,
    now: Instant = Clock.System.now(),
) {
    requireProfile(acceptedCertificatePolicyOids.isNotEmpty(), "Accepted RICAL certificate-policy OIDs are required")
    requireProfile(acceptedCertificatePolicyOids.none(String::isBlank), "RICAL certificate-policy OIDs must not be blank")

    val parsed = parseIsoCertificate(certificate, "RICAL signer certificate")
    val data = parsed.data
    val subjectPublicKey = data.subjectPublicKeyInfo

    requireProfile(data.version == 3, "RICAL signer certificate must be X.509 version 3")
    validateIsoCertificateSerialNumber(data.serialNumberRaw, "RICAL signer certificate")
    requireProfile(
        now >= data.validity.notBefore && now < data.validity.notAfter,
        "RICAL signer certificate is not currently valid",
    )
    requireProfile(
        data.validity.notAfter - data.validity.notBefore <= ISO_MAX_CERTIFICATE_VALIDITY,
        "RICAL signer certificate validity exceeds 1 187 days",
    )
    requireProfile(
        parsed.signatureAlgorithmOid in ISO_ALLOWED_CERTIFICATE_SIGNATURE_ALGORITHM_OIDS,
        "RICAL signer certificate uses an unsupported certificate-signature algorithm",
    )

    val subject = DistinguishedName.ofString(data.subjectDn).rdnList.flatten()
    requireSubjectValue(subject, COUNTRY_OID, "country")
    requireSubjectValue(subject, ORGANIZATION_OID, "organization")
    requireSubjectValue(subject, ISO_COMMON_NAME_OID, "common name")

    requireProfile(
        subjectPublicKey.algorithmOid == ISO_EC_PUBLIC_KEY_OID,
        "RICAL signer certificate must use an elliptic-curve public key",
    )
    requireProfile(
        subjectPublicKey.ellipticCurveOid in ISO_ALLOWED_ELLIPTIC_CURVE_OIDS,
        "RICAL signer certificate uses an unsupported elliptic curve",
    )
    requireProfile(
        subjectPublicKey.keyValueRaw.size > 0 &&
            subjectPublicKey.keyValueRaw[0] == ISO_UNCOMPRESSED_EC_POINT_PREFIX,
        "RICAL signer certificate EC public key must use uncompressed form",
    )

    val authorityKeyIdentifier = data.extensionAuthorityKeyIdentifier
    requireProfile(
        authorityKeyIdentifier != null &&
            !authorityKeyIdentifier.critical &&
            (authorityKeyIdentifier.keyIdentifier?.size ?: 0) > 0,
        "RICAL signer certificate requires an authority key identifier",
    )
    val subjectKeyIdentifier = data.extensionSubjectKeyIdentifier
    requireProfile(
        subjectKeyIdentifier != null &&
            !subjectKeyIdentifier.critical &&
            subjectKeyIdentifier.keyIdentifier == subjectPublicKey.keyId,
        "RICAL signer certificate requires the SHA-1 subject key identifier",
    )

    val keyUsage = data.extensionKeyUsage
        ?: throw X509ValidationException("RICAL signer certificate requires key usage")
    requireProfile(keyUsage.critical, "RICAL signer certificate key usage must be critical")
    requireProfile(
        keyUsage.keyPurposeIdList == setOf(KeyUsageExtension.KeyUsage.nonRepudiation),
        "RICAL signer certificate key usage must contain only nonRepudiation",
    )

    val crlDistributionPoints = data.extensionCrlDistributionPoints
        ?: throw X509ValidationException("RICAL signer certificate requires CRL distribution points")
    requireProfile(
        !crlDistributionPoints.critical && crlDistributionPoints.distributionPoints.isNotEmpty(),
        "RICAL signer certificate requires non-critical CRL distribution points",
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
        "RICAL signer certificate CRL distribution points must contain only full-name URIs",
    )

    val certificatePolicies = data.extensions[CERTIFICATE_POLICIES_OID]
        ?: throw X509ValidationException("RICAL signer certificate requires certificatePolicies")
    requireProfile(!certificatePolicies.critical, "RICAL signer certificate policies must be non-critical")
    requireProfile(
        certificate.certificatePolicyOids().any(acceptedCertificatePolicyOids::contains),
        "RICAL signer certificate does not contain an accepted certificate-policy OID",
    )
    data.extensions[AUTHORITY_INFORMATION_ACCESS_OID]?.let { extension ->
        requireProfile(!extension.critical, "RICAL signer authority information access must be non-critical")
    }

    val unsupportedCritical = data.extensions.values
        .filter { it.critical }
        .map { it.oid }
        .filterNot { it == KEY_USAGE_OID }
    requireProfile(unsupportedCritical.isEmpty(), "RICAL signer certificate contains an unsupported critical extension")
}

/** Validates a RICAL signer path only to application-provisioned provider roots. */
@Throws(X509ValidationException::class)
fun validateRicalSignerCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>,
    now: Instant = Clock.System.now(),
) {
    requireProfile(trustAnchors.isNotEmpty(), "At least one explicit RICAL provider root is required")
    requireProfile(
        leaf !in trustAnchors && chain.none { it in trustAnchors },
        "The RICAL protected x5chain must not carry its provider root",
    )
    validateCertificateChainWithExplicitTrust(
        leaf = leaf,
        chain = chain,
        trustAnchors = trustAnchors,
        enableTrustedChainRoot = false,
        now = now,
    )
}

private fun requireSubjectValue(
    subject: List<id.walt.certificate.x509.dn.AttributeTypeAndValue>,
    oid: String,
    label: String,
) {
    requireProfile(
        subject.count { it.type.oid == oid && it.value.isNotBlank() } == 1,
        "RICAL signer certificate requires one $label",
    )
}

private fun CertificateDer.certificatePolicyOids(): Set<String> {
    val certificate = SignumX509Certificate.decodeFromByteArray(bytes.toByteArray())
        ?: throw X509ValidationException("Invalid RICAL signer certificate")
    val extension = certificate.tbsCertificate.extensions
        ?.firstOrNull { it.oid == ObjectIdentifier(CERTIFICATE_POLICIES_OID) }
        ?: return emptySet()
    return runCatching {
        Asn1Element.parse(extension.value.asOctetString().content)
            .asSequence()
            .children
            .map { policyInformation ->
                policyInformation.asSequence().children.first().asPrimitive().readOid().toString()
            }
            .toSet()
    }.getOrElse { cause ->
        throw X509ValidationException("Invalid RICAL signer certificatePolicies", cause)
    }
}

private const val COUNTRY_OID = "2.5.4.6"
private const val ORGANIZATION_OID = "2.5.4.10"
private const val KEY_USAGE_OID = "2.5.29.15"
private const val CERTIFICATE_POLICIES_OID = "2.5.29.32"
private const val AUTHORITY_INFORMATION_ACCESS_OID = "1.3.6.1.5.5.7.1.1"
