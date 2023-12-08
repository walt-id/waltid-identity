package id.walt.service.oidc4vc

import id.walt.oid4vc.providers.SIOPSession
import id.walt.oid4vc.requests.AuthorizationRequest
import kotlinx.datetime.Instant

data class VPresentationSession(
    override val id: String,
    override val authorizationRequest: AuthorizationRequest?,
    override val expirationTimestamp: Instant,
    val selectedCredentialIds: Set<String>
) : SIOPSession(id, authorizationRequest, expirationTimestamp)
