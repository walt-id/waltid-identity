package id.walt.mdoc.doc

/**
 * Verification type for MDOC documents
 */
enum class VerificationType {
  VALIDITY,
  DOC_TYPE,
  CERTIFICATE_CHAIN,
  ITEMS_TAMPER_CHECK,
  ISSUER_SIGNATURE,
  DEVICE_SIGNATURE;

  infix fun and(other: VerificationType): VerificationTypes = setOf(this, other)

  companion object {
    /**
     * ALL verification types enabled
     */
    val all: VerificationTypes
      get() = VerificationType.values().toSet()

    /**
     * Verification types for presentation i.e.: all verification types
     */
    val forPresentation: VerificationTypes
      get() = all

    /**
     * Verification types for issuance, i.e. all but device signature check
     */
    val forIssuance: VerificationTypes
      get() = (VALIDITY and DOC_TYPE and CERTIFICATE_CHAIN and ITEMS_TAMPER_CHECK and ISSUER_SIGNATURE)
  }
}

typealias VerificationTypes = Set<VerificationType>

infix fun VerificationTypes.has(other: VerificationType) = this.contains(other)
infix fun VerificationTypes.allOf(other: VerificationTypes) = this.containsAll(other)
infix fun VerificationTypes.and(other: VerificationType): VerificationTypes = setOf(other, *this.toTypedArray())
