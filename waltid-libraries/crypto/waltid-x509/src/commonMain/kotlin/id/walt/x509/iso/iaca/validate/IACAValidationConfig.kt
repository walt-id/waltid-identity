package id.walt.x509.iso.iaca.validate

/**
 * Configuration for [IACAValidator].
 *
 * By default, all validations required by the ISO profile are enabled.
 * Callers can selectively disable checks (for example, for parsing or inspection
 * use cases) by setting the corresponding flags to false.
 *
 * Each flag controls a cohesive group of validations. This keeps the configuration
 * manageable while still allowing targeted relaxation of profile requirements when needed.
 *
 * @param keyType Enforce profile-allowed key types for IACA keys (subject to waltid-crypto support).
 * @param principalName Validate the X.500 principal name fields.
 * @param serialNo Enforce ISO serial number size/entropy constraints.
 * @param basicConstraints Require CA=true and pathLengthConstraint=0.
 * @param keyUsage Require the exact ISO key usage set for IACA certificates.
 * @param issuerAlternativeName Require IssuerAlternativeName to include at least one non-blank entry.
 * @param validityPeriod Enforce notBefore < notAfter, notAfter > now, and ISO max validity window of 20 years.
 * @param crlDistributionPointUri Require optional CRL distribution point values to be non-blank.
 * @param requiredCriticalExtensionOIDs Require the ISO-mandated critical extension OIDs.
 * @param requiredNonCriticalExtensionOIDs Require the ISO-mandated non-critical extension OIDs.
 * @param signature Verify the certificate signature using the IACA public key.
 */
data class IACAValidationConfig(
    val keyType: Boolean = true,
    val principalName: Boolean = true,
    val serialNo: Boolean = true,
    val basicConstraints: Boolean = true,
    val keyUsage: Boolean = true,
    val issuerAlternativeName: Boolean = true,
    val validityPeriod: Boolean = true,
    val crlDistributionPointUri: Boolean = true,
    val requiredCriticalExtensionOIDs: Boolean = true,
    val requiredNonCriticalExtensionOIDs: Boolean = true,
    val signature: Boolean = true,
)
