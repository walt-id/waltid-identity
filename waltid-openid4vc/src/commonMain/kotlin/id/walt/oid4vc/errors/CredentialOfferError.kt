package id.walt.oid4vc.errors

import id.walt.oid4vc.requests.CredentialOfferRequest

class CredentialOfferError(
  val credentialOfferRequest: CredentialOfferRequest, val errorCode: CredentialOfferErrorCode, override val message: String? = null
): Exception() {
}

enum class CredentialOfferErrorCode {
  invalid_request,
  invalid_issuer
}
