package id.walt.x509.iso.documentsigner.validate

/**
 * Configuration for [DocumentSignerValidator].
 *
 * By default, all validations required by the ISO profile are enabled.
 * Callers can selectively disable checks (for example, for parsing or inspection
 * use cases) by setting the corresponding flags to false.
 *
 * Each flag controls a cohesive group of validations. This keeps the configuration
 * manageable while still allowing targeted relaxation of profile requirements when needed.
 *
 * @param keyType Enforce profile-allowed key types for the Document Signer public key (subject to waltid-crypto support).
 * @param principalName Validate the Document Signer X.500 principal name fields.
 * @param serialNo Enforce ISO serial number size/entropy constraints.
 * @param keyUsage Require the exact ISO key usage set for Document Signer certificates.
 * @param extendedKeyUsage Require the Document Signer EKU OID to be present.
 * @param authorityKeyIdentifier Require AKI to match the issuing IACA SKI.
 * @param basicConstraints Require CA=false for Document Signer certificates.
 * @param validityPeriod Enforce notBefore < notAfter, notAfter > now, and ISO max validity window of 457 days.
 * @param crlDistributionPointUri Require CRL distribution point URI to be non-blank.
 * @param profileDataAgainstIACAProfileData Enforce cross-validation of Document Signer profile data against that of the IACA.
 * @param requiredCriticalExtensionOIDs Require the ISO-mandated critical extension OIDs.
 * @param requiredNonCriticalExtensionOIDs Require the ISO-mandated non-critical extension OIDs.
 * @param signature Verify the certificate signature using the IACA public key.
 */
data class DocumentSignerValidationConfig(
    val keyType: Boolean = true,
    val principalName: Boolean = true,
    val serialNo: Boolean = true,
    val keyUsage: Boolean = true,
    val extendedKeyUsage: Boolean = true,
    val authorityKeyIdentifier: Boolean = true,
    val basicConstraints: Boolean = true,
    val validityPeriod: Boolean = true,
    val crlDistributionPointUri: Boolean = true,
    val profileDataAgainstIACAProfileData: Boolean = true,
    val requiredCriticalExtensionOIDs: Boolean = true,
    val requiredNonCriticalExtensionOIDs: Boolean = true,
    val signature: Boolean = true,
)
