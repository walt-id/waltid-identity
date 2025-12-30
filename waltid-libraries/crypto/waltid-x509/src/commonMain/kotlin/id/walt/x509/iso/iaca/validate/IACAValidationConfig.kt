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
