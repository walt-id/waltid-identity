package id.walt.oid4vc.providers

import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.TxCode
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.responses.AuthorizationCodeIDTokenRequestResponse
import kotlinx.datetime.Instant

data class IssuanceSession(
    override val id: String,
    override val authorizationRequest: AuthorizationRequest?,
    override val expirationTimestamp: Instant,
    val txCode: TxCode? = null,
    val txCodeValue: String? = null,
    override val idTokenRequestState: String?  = null,
    val credentialOffer: CredentialOffer? = null,
    val cNonce: String? = null,
    val customParameters: Map<String, Any>? = null,
) : AuthorizationSession()
