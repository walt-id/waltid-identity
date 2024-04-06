package id.walt.oid4vc.responses

import kotlinx.serialization.Serializable

@Serializable
data class EntraIssuanceCompletionResponse(
  val code: EntraIssuanceCompletionCode,
  val state: String,
  val details: EntraIssuanceCompletionErrorDetails? = null
)

@Serializable
enum class EntraIssuanceCompletionCode {
  issuance_successful,
  issuance_failed
}

@Serializable
enum class EntraIssuanceCompletionErrorDetails {
  user_canceled,
  fetch_contract_error,
  issuance_service_error,
  unspecified_error
}
