package id.walt.mdoc.dataretrieval

/**
 * Device response status
 */
enum class DeviceResponseStatus(val status: UInt) {
  OK(0u),
  GENERAL_ERROR(10u),
  CBOR_DECODING_ERROR(11u),
  CBOR_VALIDATION_ERROR(12u)
}