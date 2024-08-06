package id.walt.webwallet.service.exchange

import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.EntraIssuanceRequest
import id.walt.oid4vc.responses.CredentialResponse

data class ProcessedCredentialOffer(
  val credentialResponse: CredentialResponse,
  val credentialRequest: CredentialRequest?,
  val entraIssuanceRequest: EntraIssuanceRequest? = null
)
